package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalEntryType;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecordRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.CorrectableEntryStore;
import ar.turnosodontologia.consultorio.historiaclinica.domain.Correction;
import ar.turnosodontologia.consultorio.historiaclinica.domain.CorrectionReason;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mecanismo generico de correccion de entradas clinicas: toda correccion es una entrada nueva, del mismo tipo y de la
 * misma historia clinica, que referencia a la original con motivo, autor e instante del servidor. C-17/C-18 lo
 * reutilizan aportando solo su almacen y su fabrica.
 */
@Service
public class ClinicalCorrections {

    private final ClinicalPatientGuard patientGuard;
    private final ClinicalRecordRepository records;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public ClinicalCorrections(
            ClinicalPatientGuard patientGuard, ClinicalRecordRepository records, AuditLogger auditLogger, Clock clock) {
        this.patientGuard = patientGuard;
        this.records = records;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional
    public <E extends ClinicalEntry> E correct(
            ClinicalEntryType type,
            CorrectableEntryStore<E> store,
            UUID patientId,
            UUID entryId,
            String rawReason,
            UUID actorId,
            RequestContext context,
            CorrectionFactory<E> factory) {
        CorrectionReason reason = CorrectionReason.of(rawReason);
        patientGuard.requireWritable(patientId);
        // Sin historia clinica no se revela si la entrada existe en otra: mismo 404 que una entrada inexistente.
        ClinicalRecord record = records.findByPatientId(patientId)
                .orElseThrow(() -> new NotFoundException("CLINICAL_ENTRY_NOT_FOUND"));
        E original = store.findInRecord(record.id(), entryId)
                .orElseThrow(() -> new NotFoundException("CLINICAL_ENTRY_NOT_FOUND"));
        if (store.isCorrected(entryId)) {
            throw new ConflictException("CLINICAL_ENTRY_ALREADY_CORRECTED");
        }
        store.assertCorrectable(original);

        E created = factory.create(
                record.id(),
                new Correction(entryId, reason),
                new EntryProvenance(actorId, clock.instant()),
                original);
        E saved;
        try {
            saved = store.save(created);
        } catch (DataIntegrityViolationException error) {
            throw ClinicalWriteConflicts.translate(error);
        }
        auditLogger.log(AuditEvent.of(
                AuditAction.HC_CORRECT, actorId, patientId, type.auditEntityType(), saved.id().toString(),
                context.ip(), context.userAgent(),
                Map.of("entry_type", type.name(), "corrects_entry_id", entryId.toString())));
        return saved;
    }
}
