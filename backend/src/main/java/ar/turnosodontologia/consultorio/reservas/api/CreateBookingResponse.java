package ar.turnosodontologia.consultorio.reservas.api;

import java.time.Instant;
import java.util.UUID;

/** Confirmación de la reserva. Nunca incluye el token de gestión (viaja solo por email) ni datos de contacto. */
public record CreateBookingResponse(UUID bookingId, String serviceName, Instant startsAt, Instant endsAt,
        String status, String practiceName) {

    /** Respuesta genérica ante honeypot: no revela si se procesó una reserva. */
    public static CreateBookingResponse generic() {
        return new CreateBookingResponse(null, null, null, null, "ACCEPTED", null);
    }
}
