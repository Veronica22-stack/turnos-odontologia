package ar.turnosodontologia.consultorio.pacientes.api;

import java.util.UUID;

/** Resultado de resolver un paciente para una reserva: solo identificadores y banderas, sin datos personales. */
public record PatientBookingResult(UUID patientId, boolean created, boolean divergentData) {
}
