package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecordRepository;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Apertura (al primer registro) y resumen de la historia clinica; toda lectura queda auditada. */
@Service
public class ClinicalRecordService {

    private final ClinicalPatientGuard patientGuard;
    private final ClinicalRecordRepository records;
    private final AnamnesisEntryRepository anamnesis;
    private final InstallationPractice practice;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public ClinicalRecordService(
            ClinicalPatientGuard patientGuard,
            ClinicalRecordRepository records,
            AnamnesisEntryRepository anamnesis,
            InstallationPractice practice,
            AuditLogger auditLogger,
            Clock clock) {
        this.patientGuard = patientGuard;
        this.records = records;
        this.anamnesis = anamnesis;
        this.practice = practice;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    // HC_READ se persiste en la misma transaccion que la lectura: no puede ser readOnly.
    @Transactional
    public ClinicalRecordSummaryData summary(UUID patientId, UUID userId, RequestContext context) {
        patientGuard.requireReadable(patientId);
        Optional<ClinicalRecord> record = records.findByPatientId(patientId);
        ClinicalRecordSummaryData summary = record
                .map(found -> new ClinicalRecordSummaryData(
                        true, found.id(), found.openedAt(), currentAnamnesis(found.id())))
                .orElseGet(() -> new ClinicalRecordSummaryData(false, null, null, null));
        auditLogger.log(AuditEvent.of(
                AuditAction.HC_READ, userId, patientId, "clinical_record",
                record.map(found -> found.id().toString()).orElse(null),
                context.ip(), context.userAgent(), Map.of()));
        return summary;
    }

    /** Abre la historia clinica del paciente si aun no existe; exige paciente existente y activo. */
    @Transactional
    public ClinicalRecord openForWrite(UUID patientId, UUID userId) {
        patientGuard.requireWritable(patientId);
        return records.openIfAbsent(practice.id(), patientId, userId, clock.instant());
    }

    private AnamnesisEntryData currentAnamnesis(UUID clinicalRecordId) {
        List<AnamnesisEntry> newestFirst = anamnesis.findNewestFirst(clinicalRecordId);
        if (newestFirst.isEmpty()) {
            return null;
        }
        Map<UUID, UUID> correctedBy = anamnesis.correctedByIndex(clinicalRecordId);
        AnamnesisEntry current = newestFirst.get(0);
        return AnamnesisEntryData.from(current, correctedBy.get(current.id()));
    }
}
