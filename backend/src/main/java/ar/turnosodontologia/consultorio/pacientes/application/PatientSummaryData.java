package ar.turnosodontologia.consultorio.pacientes.application;

import java.util.UUID;

public record PatientSummaryData(
        UUID id,
        String firstName,
        String lastName,
        String dni,
        String phone,
        boolean active) {
}
