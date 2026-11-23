package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalEntryType;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecordRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionDescription;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionHistory;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de las evoluciones: alta (con turno opcional), historial (mas reciente primero) y correccion. */
@Service
public class EvolutionService {

    private final ClinicalPatientGuard patientGuard;
    private final ClinicalRecordService recordService;
    private final ClinicalRecordRepository records;
    private final EvolutionEntryRepository evolutions;
    private final EvolutionAppointmentGuard appointmentGuard;
    private final EvolutionCorrectionStore correctionStore;
    private final ClinicalCorrections corrections;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public EvolutionService(
            ClinicalPatientGuard patientGuard,
            ClinicalRecordService recordService,
            ClinicalRecordRepository records,
            EvolutionEntryRepository evolutions,
            EvolutionAppointmentGuard appointmentGuard,
            EvolutionCorrectionStore correctionStore,
            ClinicalCorrections corrections,
            AuditLogger auditLogger,
            Clock clock) {
        this.patientGuard = patientGuard;
        this.recordService = recordService;
        this.records = records;
        this.evolutions = evolutions;
        this.appointmentGuard = appointmentGuard;
        this.correctionStore = correctionStore;
        this.corrections = corrections;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** Una sola transaccion: cualquier falla (dominio, base, auditoria) revierte la historia abierta y la entrada. */
    @Transactional
    public EvolutionEntryData register(UUID patientId, UUID userId, EvolutionCommand command, RequestContext context) {
        patientGuard.requireWritable(patientId);
        // El turno se valida antes de abrir la historia clinica: un rechazo no deja una historia vacia.
        if (command.appointmentId() != null) {
            appointmentGuard.check(patientId, command.appointmentId());
        }
        ClinicalRecord record = recordService.openForWrite(patientId, userId);
        EvolutionEntry entry = EvolutionEntry.register(
                record.id(), command.description(), command.appointmentId(),
                new EntryProvenance(userId, clock.instant()));
        EvolutionEntry saved;
        try {
            saved = evolutions.save(entry);
        } catch (DataIntegrityViolationException error) {
            throw ClinicalWriteConflicts.translate(error);
        }
        auditLogger.log(AuditEvent.of(
                AuditAction.HC_CREATE, userId, patientId, ClinicalEntryType.EVOLUTION.auditEntityType(),
                saved.id().toString(), context.ip(), context.userAgent(),
                Map.of("entry_type", ClinicalEntryType.EVOLUTION.name())));
        return EvolutionEntryData.from(saved, null);
    }

    // HC_READ se persiste en la misma transaccion que la lectura: no puede ser readOnly.
    @Transactional
    public List<EvolutionEntryData> read(UUID patientId, UUID userId, RequestContext context) {
        patientGuard.requireReadable(patientId);
        List<EvolutionEntryData> data = records.findByPatientId(patientId)
                .map(record -> EvolutionHistory.of(evolutions.findNewestFirst(record.id())).stream()
                        .map(item -> EvolutionEntryData.from(item.entry(), item.correctedByEntryId()))
                        .toList())
                .orElseGet(List::of);
        auditLogger.log(AuditEvent.of(
                AuditAction.HC_READ, userId, patientId, ClinicalEntryType.EVOLUTION.auditEntityType(), null,
                context.ip(), context.userAgent(), Map.of()));
        return data;
    }

    @Transactional
    public EvolutionEntryData correct(
            UUID patientId, UUID entryId, String reason, EvolutionDescription description, UUID userId,
            RequestContext context) {
        EvolutionEntry created = corrections.correct(
                ClinicalEntryType.EVOLUTION, correctionStore, patientId, entryId, reason, userId, context,
                (recordId, correction, provenance, original) ->
                        EvolutionEntry.correct(recordId, original, description, correction, provenance));
        return EvolutionEntryData.from(created, null);
    }
}
