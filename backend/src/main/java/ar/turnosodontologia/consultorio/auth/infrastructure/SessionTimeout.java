package ar.turnosodontologia.consultorio.auth.infrastructure;

import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Vencimiento por inactividad guardado como instante UTC opaco en la sesión del servidor (D1). */
final class SessionTimeout {

    static final String EXPIRES_AT = SessionTimeout.class.getName() + ".expiresAt";

    private SessionTimeout() {
    }

    static void schedule(HttpSession session, Clock clock, int timeoutMinutes) {
        session.setAttribute(EXPIRES_AT,
                clock.instant().plus(Duration.ofMinutes(timeoutMinutes)).toEpochMilli());
    }

    static boolean isExpired(HttpSession session, Instant now) {
        Instant expiresAt = expiresAt(session);
        return !expiresAt.isAfter(now);
    }

    static Instant expiresAt(HttpSession session) {
        Object value = session.getAttribute(EXPIRES_AT);
        return value instanceof Long epochMillis ? Instant.ofEpochMilli(epochMillis) : Instant.EPOCH;
    }
}
