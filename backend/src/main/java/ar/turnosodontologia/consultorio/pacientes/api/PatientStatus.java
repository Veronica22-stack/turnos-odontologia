package ar.turnosodontologia.consultorio.pacientes.api;

import java.util.UUID;

/** Existencia y estado de un paciente para otros modulos; sin datos personales. */
public record PatientStatus(UUID id, boolean active) {
}
