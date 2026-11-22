package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Salida pública del bloqueo, con los turnos afectados sin datos personales. */
public record ScheduleBlockResponse(
        UUID id,
        Instant startsAt,
        Instant endsAt,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        List<AffectedAppointment> affectedAppointments) {
}
