package ar.turnosodontologia.consultorio.agenda.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Alta manual de turno (design D17). El fin y la duracion no se aceptan: los calcula el servidor. */
public record CreateAppointmentRequest(
        @NotNull UUID patientId,
        @NotNull UUID serviceId,
        @NotNull Instant startsAt,
        Boolean overbooking,
        @Size(max = 1000) String notes) {
}
