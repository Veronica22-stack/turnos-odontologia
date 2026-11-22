package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.UUID;

/** Vista de un turno para otros modulos: identificadores, instantes y enums; sin notas ni datos personales. */
public record AppointmentView(UUID id, UUID patientId, UUID serviceId, Instant startsAt, Instant endsAt,
        int durationMin, AppointmentStatusView status, AppointmentOriginView origin, boolean overbooking) {
}
