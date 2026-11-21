package ar.turnosodontologia.consultorio.auth.application;

/** Datos del intento de login. La contraseña en claro solo vive durante la ejecución del caso de uso. */
public record LoginCommand(String email, String password, String ip, String userAgent) {
}
