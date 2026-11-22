package ar.turnosodontologia.consultorio.agenda.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplaceWorkingHoursRequest(
        @NotNull(message = "La lista de tramos no puede ser nula")
        @Valid
        List<WorkingHoursDto> workingHours
) {}
