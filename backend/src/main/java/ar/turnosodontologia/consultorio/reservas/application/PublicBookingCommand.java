package ar.turnosodontologia.consultorio.reservas.application;

import java.time.Instant;
import java.util.UUID;

/** Datos de la reserva pública en la capa de aplicación (sin anotaciones de API). */
public record PublicBookingCommand(UUID serviceId, Instant startsAt, String firstName, String lastName,
        String dni, String phone, String email) {
}
