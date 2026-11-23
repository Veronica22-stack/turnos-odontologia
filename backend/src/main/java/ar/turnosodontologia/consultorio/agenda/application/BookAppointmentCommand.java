package ar.turnosodontologia.consultorio.agenda.application;

import ar.turnosodontologia.consultorio.agenda.domain.AppointmentOrigin;
import java.time.Instant;
import java.util.UUID;

/**
 * Datos del alta de un turno (design D7, D17); el fin y la duración los calcula el servidor. El constructor de cinco
 * argumentos conserva el comportamiento de C-11 ({@link AppointmentOrigin#INTERNAL}) para no tocar a sus consumidores.
 */
public record BookAppointmentCommand(UUID patientId, UUID serviceId, Instant startsAt, boolean overbooking,
        String notes, AppointmentOrigin origin) {

    public BookAppointmentCommand(UUID patientId, UUID serviceId, Instant startsAt, boolean overbooking, String notes) {
        this(patientId, serviceId, startsAt, overbooking, notes, AppointmentOrigin.INTERNAL);
    }

    public static BookAppointmentCommand publicBooking(UUID patientId, UUID serviceId, Instant startsAt) {
        return new BookAppointmentCommand(patientId, serviceId, startsAt, false, null, AppointmentOrigin.PUBLIC);
    }
}
