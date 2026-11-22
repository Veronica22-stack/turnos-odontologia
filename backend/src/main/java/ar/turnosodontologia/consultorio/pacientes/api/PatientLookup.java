package ar.turnosodontologia.consultorio.pacientes.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de consulta de pacientes para otros modulos. Solo informa si el paciente existe en la instalacion y si esta
 * activo: no expone datos personales ni registra {@code PATIENT_READ}.
 */
public interface PatientLookup {

    Optional<PatientStatus> findStatus(UUID patientId);
}
