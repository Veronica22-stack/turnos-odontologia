package ar.turnosodontologia.consultorio.agenda.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Entrada pública del bloqueo; las marcas de tiempo de auditoría no son editables por el cliente. */
public record ScheduleBlockRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotBlank @Size(max = 500) String reason) {
}
