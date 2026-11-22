package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Turno reprogramado (design D11): instantes anteriores y nuevos. Consumir con
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 */
public record AppointmentRescheduled(UUID appointmentId, UUID practiceId, UUID patientId, Instant previousStartsAt,
        Instant previousEndsAt, Instant startsAt, Instant endsAt) {
}
