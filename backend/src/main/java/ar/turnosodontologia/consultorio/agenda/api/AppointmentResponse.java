package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.UUID;

/** Turno para el odontologo (design D17): solo identificadores del paciente y la prestacion, sin datos personales. */
public record AppointmentResponse(UUID id, UUID patientId, UUID serviceId, Instant startsAt, Instant endsAt,
        int durationMin, AppointmentStatusView status, AppointmentOriginView origin, boolean overbooking,
        CancelledByView cancelledBy, Instant cancelledAt, String notes, Instant createdAt, Instant updatedAt) {
}
