package ar.turnosodontologia.consultorio.auth.application;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import org.springframework.stereotype.Service;

/** Cierre de sesión idempotente: sin sesión no hay nada que invalidar ni evento que auditar. */
@Service
public class LogoutUser {

    private final SessionInvalidation sessions;

    public LogoutUser(SessionInvalidation sessions) {
        this.sessions = sessions;
    }

    public void execute(AuthenticatedUser principal) {
        if (principal != null) {
            sessions.invalidateCurrentSession();
        }
    }
}
