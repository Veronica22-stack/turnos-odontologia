package ar.turnosodontologia.consultorio.agenda.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Reprogramacion de un turno (design D17). */
public record RescheduleAppointmentRequest(@NotNull Instant startsAt, Boolean overbooking) {
}
