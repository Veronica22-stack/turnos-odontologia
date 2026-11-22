package ar.turnosodontologia.consultorio.auth.domain;

import java.security.Principal;
import java.util.UUID;

/** Principal de la sesión: solo identificadores y estado; nunca email, DNI, teléfono ni datos clínicos. */
public record AuthenticatedUser(UUID userId, UUID practiceId, Role role, boolean mustChangePassword)
        implements Principal {

    /** El nombre del principal es el id opaco de la cuenta, nunca el email. */
    @Override
    public String getName() {
        return userId.toString();
    }
}
