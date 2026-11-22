package ar.turnosodontologia.consultorio.auth.api;

import java.time.Instant;
import java.util.UUID;

/** Estado opaco de la sesión: identificadores y banderas, sin email, DNI, teléfono ni datos clínicos. */
public record AuthMeResponse(UUID userId, String role, boolean mustChangePassword, Instant expiresAt) {
}
