package ar.turnosodontologia.consultorio.auth.application;

/** Contraseña actual y nueva en claro; nunca se registran ni se devuelven. */
public record ChangePasswordCommand(String currentPassword, String newPassword) {
}
