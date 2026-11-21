package ar.turnosodontologia.consultorio.auth.application;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import java.time.Instant;

/** Resultado de un login correcto, con el instante UTC que sirve de base al vencimiento de la sesión. */
public record LoginResult(AuthenticatedUser user, Instant authenticatedAt) {
}
