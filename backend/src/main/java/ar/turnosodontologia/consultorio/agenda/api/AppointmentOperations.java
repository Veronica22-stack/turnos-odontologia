package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Operaciones de turno que agenda ofrece a otros módulos (design D16, extendido en C-14/C-15). Todas delegan en el
 * mismo servicio de aplicación que usa el controller: mismo lock por consultorio, orden de validaciones, traducción
 * de errores y eventos. Las reglas de reserva pública que no dependen de datos concurrentes (anticipación, horizonte,
 * granularidad y visibilidad) las valida el consumidor antes de llamar.
 */
public interface AppointmentOperations {

    /** Alta de un turno de reserva pública ({@code origin = PUBLIC}, {@code overbooking = false}). */
    AppointmentView book(PublicBookingCommand command);

    /** Mueve un turno {@code CONFIRMED} futuro conservando identificador, paciente, prestación y duración snapshot. */
    AppointmentView reschedule(UUID appointmentId, Instant newStartsAt, boolean overbooking);

    /** Cancela un turno registrando quién lo canceló (el paciente, desde C-15). */
    AppointmentView cancel(UUID appointmentId, CancelledByView cancelledBy);
}
