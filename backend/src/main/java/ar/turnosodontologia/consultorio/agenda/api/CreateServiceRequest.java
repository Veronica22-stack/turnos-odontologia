package ar.turnosodontologia.consultorio.agenda.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Creación de una prestación: nombre obligatorio, descripción opcional, duración y visibilidad pública (design D5). */
public record CreateServiceRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @Min(1) int durationMin,
        Boolean publicVisible) {}
