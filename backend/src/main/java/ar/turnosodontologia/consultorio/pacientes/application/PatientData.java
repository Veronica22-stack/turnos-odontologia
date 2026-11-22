package ar.turnosodontologia.consultorio.pacientes.application;

import ar.turnosodontologia.consultorio.pacientes.domain.Patient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientData(
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

    public static PatientData from(Patient p) {
        return new PatientData(
                p.id(),
                p.firstName(),
                p.lastName(),
                p.dni(),
                p.phone(),
                p.email(),
                p.birthDate(),
                p.address(),
                p.source().name(),
                p.isActive(),
                p.createdAt(),
                p.updatedAt());
    }
}
