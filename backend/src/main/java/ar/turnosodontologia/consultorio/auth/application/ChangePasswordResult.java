package ar.turnosodontologia.consultorio.auth.application;

import java.util.UUID;

/** Solo el identificador de la cuenta cambiada; la contraseña nunca sale del caso de uso. */
public record ChangePasswordResult(UUID userId) {
}
