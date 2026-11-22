package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.UUID;

/** Identificador e instantes de un turno afectado; sin datos personales ni clínicos. */
public record AffectedAppointment(UUID appointmentId, Instant startsAt, Instant endsAt) {
}
