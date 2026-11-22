package ar.turnosodontologia.consultorio.agenda.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record WorkingHoursDto(
        @NotNull(message = "El día de la semana es obligatorio")
        Integer weekday,

        @NotNull(message = "La hora de inicio es obligatoria")
        LocalTime startTime,

        @NotNull(message = "La hora de fin es obligatoria")
        LocalTime endTime
) {}
