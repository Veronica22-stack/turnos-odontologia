package ar.turnosodontologia.consultorio.reservas.application;

import java.time.Instant;
import java.util.UUID;

/** Detalles del turno accesibles con el token, sin datos personales (C-15). */
public record BookingDetails(UUID bookingId, String serviceName, String practiceName, Instant startsAt,
        Instant endsAt, String status, boolean cancellable, boolean reschedulable) {
}
