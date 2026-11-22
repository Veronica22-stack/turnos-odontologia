package ar.turnosodontologia.consultorio.agenda.domain;

import java.time.Instant;
import java.util.UUID;

/** Turno comprendido en un rango bloqueado, sin datos personales ni clínicos. */
public record AffectedAppointmentSlot(UUID appointmentId, Instant startsAt, Instant endsAt) {
}
