package ar.turnosodontologia.consultorio.auth.application;

import java.util.UUID;

/** Puerto para invalidar sesiones del registro de Spring Security sin acoplar el caso de uso a HTTP. */
public interface SessionInvalidation {

    void invalidateAllSessions(UUID userId);

    void invalidateCurrentSession();
}
