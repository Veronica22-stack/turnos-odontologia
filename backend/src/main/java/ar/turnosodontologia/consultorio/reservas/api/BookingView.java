package ar.turnosodontologia.consultorio.reservas.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista del turno que el token permite ver: sin DNI, email, teléfono ni notas.
 * {@code serviceId} se expone para que la SPA pueda consultar disponibilidad al reprogramar (pedido P1 de C-25).
 */
public record BookingView(UUID bookingId, UUID serviceId, String serviceName, String practiceName,
        Instant startsAt, Instant endsAt, String status, boolean cancellable, boolean reschedulable) {
}
