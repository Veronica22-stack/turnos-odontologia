package ar.turnosodontologia.consultorio.agenda.application;

import ar.turnosodontologia.consultorio.agenda.domain.Appointment;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentOrigin;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentStatus;
import ar.turnosodontologia.consultorio.agenda.domain.CancelledBy;
import java.time.Instant;
import java.util.UUID;

/** Resultado de las operaciones de turno para la capa api; sin datos personales del paciente. */
public record AppointmentResult(UUID id, UUID patientId, UUID serviceId, Instant startsAt, Instant endsAt,
        int durationMin, AppointmentStatus status, AppointmentOrigin origin, boolean overbooking,
        CancelledBy cancelledBy, Instant cancelledAt, String notes, Instant createdAt, Instant updatedAt) {

    static AppointmentResult of(Appointment appointment) {
        return new AppointmentResult(appointment.id(), appointment.patientId(), appointment.serviceId(),
                appointment.startsAt(), appointment.endsAt(), appointment.durationMinSnapshot(),
                appointment.status(), appointment.origin(), appointment.overbooking(), appointment.cancelledBy(),
                appointment.cancelledAt(), appointment.notes(), appointment.createdAt(), appointment.updatedAt());
    }
}
