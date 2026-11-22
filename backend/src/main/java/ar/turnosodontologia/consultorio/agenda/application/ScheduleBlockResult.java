package ar.turnosodontologia.consultorio.agenda.application;

import ar.turnosodontologia.consultorio.agenda.domain.AffectedAppointmentSlot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Resultado interno del caso de uso; el controlador lo traduce al DTO público. */
public record ScheduleBlockResult(
        UUID id,
        UUID practiceId,
        Instant startsAt,
        Instant endsAt,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        List<AffectedAppointmentSlot> affectedAppointments) {
}
