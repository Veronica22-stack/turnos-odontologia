package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalEntryType;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecordRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramState;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCondition;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso del odontograma: alta por lote, consulta (estado actual e historial) y correccion de la vigente. */
@Service
public class OdontogramService {

    private final ClinicalPatientGuard patientGuard;
    private final ClinicalRecordService recordService;
    private final ClinicalRecordRepository records;
    private final OdontogramEntryRepository odontogram;
    private final OdontogramCorrectionStore correctionStore;
    private final ClinicalCorrections corrections;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public OdontogramService(
            ClinicalPatientGuard patientGuard,
            ClinicalRecordService recordService,
            ClinicalRecordRepository records,
            OdontogramEntryRepository odontogram,
            OdontogramCorrectionStore correctionStore,
            ClinicalCorrections corrections,
            AuditLogger auditLogger,
            Clock clock) {
        this.patientGuard = patientGuard;
        this.recordService = recordService;
        this.records = records;
        this.odontogram = odontogram;
        this.correctionStore = correctionStore;
        this.corrections = corrections;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** Registra el lote en una transaccion: cualquier falla (dominio, base, auditoria) revierte todo. */
    @Transactional
    public List<OdontogramEntryData> register(
            UUID patientId, UUID userId, List<OdontogramEntryCommand> commands, RequestContext context) {
        ClinicalRecord record = recordService.openForWrite(patientId, userId);
        EntryProvenance provenance = new EntryProvenance(userId, clock.instant());
        List<OdontogramEntryData> created = new ArrayList<>(commands.size());
        for (OdontogramEntryCommand command : commands) {
            OdontogramEntry entry = OdontogramEntry.register(
                    record.id(), command.position(), command.condition(), command.notes(), provenance);
            OdontogramEntry saved;
            try {
                saved = odontogram.save(entry);
            } catch (DataIntegrityViolationException error) {
                throw ClinicalWriteConflicts.translate(error);
            }
            auditLogger.log(AuditEvent.of(
                    AuditAction.HC_CREATE, userId, patientId, ClinicalEntryType.ODONTOGRAM.auditEntityType(),
                    saved.id().toString(), context.ip(), context.userAgent(),
                    Map.of("entry_type", ClinicalEntryType.ODONTOGRAM.name())));
            created.add(OdontogramEntryData.from(saved, null));
        }
        return created;
    }

    // HC_READ se persiste en la misma transaccion que la lectura: no puede ser readOnly.
    @Transactional
    public OdontogramData read(UUID patientId, UUID userId, RequestContext context) {
        patientGuard.requireReadable(patientId);
        OdontogramData data = records.findByPatientId(patientId)
                .map(record -> stateOf(record.id()))
                .orElseGet(OdontogramData::empty);
        auditLogger.log(AuditEvent.of(
                AuditAction.HC_READ, userId, patientId, ClinicalEntryType.ODONTOGRAM.auditEntityType(), null,
                context.ip(), context.userAgent(), Map.of()));
        return data;
    }

    @Transactional
    public OdontogramEntryData correct(
            UUID patientId, UUID entryId, String reason, ToothCondition condition, String notes, UUID userId,
            RequestContext context) {
        OdontogramEntry created = corrections.correct(
                ClinicalEntryType.ODONTOGRAM, correctionStore, patientId, entryId, reason, userId, context,
                (recordId, correction, provenance, original) -> {
                    // La cara no se corrige: el estado nuevo debe admitir la cara de la original.
                    if (original.position().hasSurface() && !condition.allowsSurface()) {
                        throw new BusinessRuleException("ODONTOGRAM_SURFACE_NOT_ALLOWED");
                    }
                    return OdontogramEntry.correct(recordId, original, condition, notes, correction, provenance);
                });
        return OdontogramEntryData.from(created, null);
    }

    private OdontogramData stateOf(UUID clinicalRecordId) {
        OdontogramState state = OdontogramState.derive(odontogram.findNewestFirst(clinicalRecordId));
        List<OdontogramEntryData> current = state.current().stream()
                .map(entry -> OdontogramEntryData.from(entry, null))
                .toList();
        List<OdontogramEntryData> history = state.history().stream()
                .map(item -> OdontogramEntryData.from(item.entry(), item.correctedByEntryId()))
                .toList();
        return new OdontogramData(current, history);
    }
}
