package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Turno confirmado (design D11). Solo identificadores, instantes y enums: sin notas ni datos personales.
 * Se publica dentro de la transaccion; los consumidores deben usar {@code @TransactionalEventListener(phase =
 * AFTER_COMMIT)} para no ver eventos de transacciones revertidas ni revertir el turno si falla su procesamiento.
 */
public record AppointmentConfirmed(UUID appointmentId, UUID practiceId, UUID patientId, UUID serviceId,
        Instant startsAt, Instant endsAt, AppointmentOriginView origin) {
}
