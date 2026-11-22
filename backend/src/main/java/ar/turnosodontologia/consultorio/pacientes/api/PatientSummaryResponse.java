package ar.turnosodontologia.consultorio.pacientes.api;

import java.util.UUID;

public record PatientSummaryResponse(
        UUID id,
        String firstName,
        String lastName,
        String dni,
        String phone,
        boolean active) {
}
