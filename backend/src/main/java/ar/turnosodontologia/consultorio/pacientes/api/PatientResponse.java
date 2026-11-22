package ar.turnosodontologia.consultorio.pacientes.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String firstName,
        String lastName,
        String dni,
        String phone,
        String email,
        LocalDate birthDate,
        String address,
        String source,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
