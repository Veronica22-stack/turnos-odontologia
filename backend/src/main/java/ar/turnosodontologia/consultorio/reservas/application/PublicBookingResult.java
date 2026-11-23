package ar.turnosodontologia.consultorio.reservas.application;

import java.time.Instant;
import java.util.UUID;

/** Resultado interno de la reserva; sin token ni datos de contacto. */
public record PublicBookingResult(UUID bookingId, String serviceName, Instant startsAt, Instant endsAt,
        String status, String practiceName) {
}
