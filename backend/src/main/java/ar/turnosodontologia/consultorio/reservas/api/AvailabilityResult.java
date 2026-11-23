package ar.turnosodontologia.consultorio.reservas.api;

import java.util.List;

/** Resultado de disponibilidad con tipos del JDK y sin datos de pacientes ni de turnos ocupados. */
public record AvailabilityResult(AvailabilityStatus status, List<AvailableSlot> slots) {
}
