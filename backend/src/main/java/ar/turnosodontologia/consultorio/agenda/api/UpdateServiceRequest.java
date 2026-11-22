package ar.turnosodontologia.consultorio.agenda.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reemplazo completo de una prestación: permite reactivar con `active = true` (design D5). */
public record UpdateServiceRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @Min(1) int durationMin,
        Boolean publicVisible,
        Boolean active) {}
