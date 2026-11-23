package ar.turnosodontologia.consultorio.reservas.api;

import java.time.LocalDate;
import java.util.UUID;

/** Consulta de disponibilidad: sin datos del paciente ni de la prestación más allá de su id. */
public record AvailabilityQuery(
        UUID practiceId,
        UUID serviceId,
        LocalDate date,
        AvailabilityAudience audience) {
}
