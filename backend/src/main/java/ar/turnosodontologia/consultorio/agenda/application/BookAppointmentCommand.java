package ar.turnosodontologia.consultorio.agenda.application;

import java.time.Instant;
import java.util.UUID;

/** Datos del alta manual de un turno (design D7, D17); el fin y la duracion los calcula el servidor. */
public record BookAppointmentCommand(UUID patientId, UUID serviceId, Instant startsAt, boolean overbooking,
        String notes) {
}
