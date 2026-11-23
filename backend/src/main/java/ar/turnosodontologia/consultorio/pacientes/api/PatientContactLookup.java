package ar.turnosodontologia.consultorio.pacientes.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta del email de contacto de un paciente para otros modulos (notificaciones, design D10). Es una lectura del
 * sistema para avisar al paciente: no expone ningun otro dato personal, no registra {@code PATIENT_READ} y nunca
 * loguea el email. Devuelve el email aunque el paciente este inactivo (sus turnos existentes siguen notificandose) y
 * vacio si el paciente no existe o no tiene email.
 */
public interface PatientContactLookup {

    Optional<String> findEmail(UUID patientId);
}
