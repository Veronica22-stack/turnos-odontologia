package ar.turnosodontologia.consultorio.historiaclinica.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de la historia clinica (contenedor). No expone ninguna operacion de borrado. */
public interface ClinicalRecordRepository {

    Optional<ClinicalRecord> findByPatientId(UUID patientId);

    /**
     * Abre la historia clinica del paciente si no existe (idempotente y segura ante aperturas concurrentes) y devuelve
     * la existente en caso contrario.
     */
    ClinicalRecord openIfAbsent(UUID practiceId, UUID patientId, UUID openedBy, Instant openedAt);
}
