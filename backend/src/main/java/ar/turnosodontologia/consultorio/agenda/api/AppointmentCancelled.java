package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Turno cancelado (design D11). Solo identificadores, instantes y enums. Consumir con
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 */
public record AppointmentCancelled(UUID appointmentId, UUID practiceId, UUID patientId, Instant startsAt,
        Instant endsAt, CancelledByView cancelledBy) {
}
