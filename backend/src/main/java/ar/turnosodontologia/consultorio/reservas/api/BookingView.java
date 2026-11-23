package ar.turnosodontologia.consultorio.reservas.api;

import java.time.Instant;
import java.util.UUID;

/** Vista del turno que el token permite ver: sin DNI, email, teléfono ni notas. */
public record BookingView(UUID bookingId, String serviceName, String practiceName, Instant startsAt, Instant endsAt,
        String status, boolean cancellable, boolean reschedulable) {
}
