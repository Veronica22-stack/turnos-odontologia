package ar.turnosodontologia.consultorio.reservas.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Nuevo inicio solicitado para la reprogramación; el fin lo calcula el servidor (duración snapshot). */
public record RescheduleBookingRequest(@NotNull Instant startsAt) {
}
