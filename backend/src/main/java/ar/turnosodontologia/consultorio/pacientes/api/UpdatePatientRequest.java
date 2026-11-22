package ar.turnosodontologia.consultorio.pacientes.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePatientRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 50) String phone,
        @Size(max = 255) String email,
        String birthDate,
        @Size(max = 255) String address) {
}
