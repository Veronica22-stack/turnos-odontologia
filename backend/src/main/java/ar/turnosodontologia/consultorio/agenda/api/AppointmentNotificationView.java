package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.UUID;

/** Datos de un turno para notificar: sin notas ni datos personales del paciente (solo su identificador). */
public record AppointmentNotificationView(UUID appointmentId, UUID patientId, String serviceName, Instant startsAt,
        Instant endsAt, AppointmentStatusView status) {
}
