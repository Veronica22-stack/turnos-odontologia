package ar.turnosodontologia.consultorio.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo del cambio de contraseña propio; la política comparte {@code PasswordPolicy} (12–128). */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 12, max = 128) String newPassword) {
}
