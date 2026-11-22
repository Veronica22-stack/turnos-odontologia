package ar.turnosodontologia.consultorio.shared.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Solicitud para actualizar configuracion y parametros de reserva del consultorio. */
public record PracticeSettingsRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
        String name,

        @NotBlank(message = "La zona horaria es obligatoria")
        String timezone,

        @Size(max = 254, message = "El email no puede superar 254 caracteres")
        String contactEmail,

        @Size(max = 50, message = "El teléfono no puede superar 50 caracteres")
        String contactPhone,

        @Size(max = 300, message = "La dirección no puede superar 300 caracteres")
        String address,

        @NotNull(message = "La granularidad es obligatoria")
        Integer slotGranularityMin,

        @NotNull(message = "La anticipación mínima es obligatoria")
        @Min(value = 0, message = "La anticipación mínima no puede ser negativa")
        Integer minNoticeMin,

        @NotNull(message = "El horizonte máximo es obligatorio")
        @Min(value = 1, message = "El horizonte máximo debe ser de al menos 1 día")
        @Max(value = 365, message = "El horizonte máximo no puede superar 365 días")
        Integer maxAdvanceDays,

        @NotNull(message = "El recordatorio es obligatorio")
        @Min(value = 1, message = "El recordatorio debe ser de al menos 1 hora")
        @Max(value = 168, message = "El recordatorio no puede superar 168 horas")
        Integer reminderHoursBefore
) {}
