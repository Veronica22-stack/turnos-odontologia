package ar.turnosodontologia.consultorio.shared.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Limitador de tasa reutilizable por rutas públicas; la clave se trata como secreta (design D5). */
public interface RateLimiter {

    /** Vacío si el intento entra en la ventana; en su defecto, el instante en que se podrá reintentar. */
    Optional<Instant> tryAcquire(String key, Duration window, Clock clock);

    /** Segundos sugeridos para {@code Retry-After}, siempre al menos 1 y sin datos de la clave. */
    static long retryAfterSeconds(Instant now, Instant retryAt) {
        return Math.max(1, Duration.between(now, retryAt).getSeconds());
    }
}
