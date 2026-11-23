package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.UUID;

/** Datos mínimos para el alta pública de un turno; el fin y la duración los calcula el servidor. */
public record PublicBookingCommand(UUID patientId, UUID serviceId, Instant startsAt) {
}
