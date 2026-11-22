package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalEntryType;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecordRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de la anamnesis: alta de version, consulta (vigente e historial) y correccion de la vigente. */
@Service
public class AnamnesisService {

    private final ClinicalPatientGuard patientGuard;
    private final ClinicalRecordService recordService;
    private final ClinicalRecordRepository records;
    private final AnamnesisEntryRepository anamnesis;
    private final AnamnesisCorrectionStore correctionStore;
    private final ClinicalCorrections corrections;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public AnamnesisService(
            ClinicalPatientGuard patientGuard,
            ClinicalRecordService recordService,
            ClinicalRecordRepository records,
            AnamnesisEntryRepository anamnesis,
            AnamnesisCorrectionStore correctionStore,
            ClinicalCorrections corrections,
            AuditLogger auditLogger,
            Clock clock) {
        this.patientGuard = patientGuard;
        this.recordService = recordService;
        this.records = records;
        this.anamnesis = anamnesis;
        this.correctionStore = correctionStore;
        this.corrections = corrections;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional
    public AnamnesisEntryData register(UUID patientId, UUID userId, AnamnesisContent content, RequestContext context) {
        ClinicalRecord record = recordService.openForWrite(patientId, userId);
        int version = anamnesis.maxVersion(record.id()) + 1;
        AnamnesisEntry entry = AnamnesisEntry.register(
                record.id(), version, content, new EntryProvenance(userId, clock.instant()));
        AnamnesisEntry saved;
        try {
            saved = anamnesis.save(entry);
        } catch (DataIntegrityViolationException error) {
            throw ClinicalWriteConflicts.translate(error);
        }
        auditLogger.log(AuditEvent.of(
                AuditAction.HC_CREATE, userId, patientId, ClinicalEntryType.ANAMNESIS.auditEntityType(),
                saved.id().toString(), context.ip(), context.userAgent(),
                Map.of("entry_type", ClinicalEntryType.ANAMNESIS.name(), "version", saved.version())));
        return AnamnesisEntryData.from(saved, null);
    }

    // HC_READ se persiste en la misma transaccion que la lectura: no puede ser readOnly.
    @Transactional
    public AnamnesisData read(UUID patientId, UUID userId, RequestContext context) {
        patientGuard.requireReadable(patientId);
        AnamnesisData data = records.findByPatientId(patientId)
                .map(record -> historyOf(record.id()))
                .orElseGet(() -> new AnamnesisData(null, List.of()));
        auditLogger.log(AuditEvent.of(
                AuditAction.HC_READ, userId, patientId, ClinicalEntryType.ANAMNESIS.auditEntityType(),
                data.current() == null ? null : data.current().id().toString(),
                context.ip(), context.userAgent(), Map.of()));
        return data;
    }

    @Transactional
    public AnamnesisEntryData correct(
            UUID patientId, UUID entryId, String reason, AnamnesisContent content, UUID userId,
            RequestContext context) {
        AnamnesisEntry created = corrections.correct(
                ClinicalEntryType.ANAMNESIS, correctionStore, patientId, entryId, reason, userId, context,
                (recordId, correction, provenance, original) -> AnamnesisEntry.correct(
                        recordId, anamnesis.maxVersion(recordId) + 1, content, correction, provenance));
        return AnamnesisEntryData.from(created, null);
    }

    private AnamnesisData historyOf(UUID clinicalRecordId) {
        Map<UUID, UUID> correctedBy = anamnesis.correctedByIndex(clinicalRecordId);
        List<AnamnesisEntryData> history = anamnesis.findNewestFirst(clinicalRecordId).stream()
                .map(entry -> AnamnesisEntryData.from(entry, correctedBy.get(entry.id())))
                .toList();
        return new AnamnesisData(history.isEmpty() ? null : history.get(0), history);
    }
}
