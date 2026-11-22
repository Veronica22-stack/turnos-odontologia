package ar.turnosodontologia.consultorio.shared.infrastructure;

import ar.turnosodontologia.consultorio.shared.api.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limitador de tasa para lecturas públicas (design D7): encapsula una instancia dedicada de
 * {@link InMemoryRateLimiter}; no es un bean de tipo {@link RateLimiter}, para evitar la inyección
 * ambigua de {@code auth} que un segundo bean del mismo tipo crearía. La clave nunca viaja en claro ni
 * se loguea.
 */
@Component
public class PublicReadRateLimiter {

    private final Duration window;
    private final InMemoryRateLimiter limiter;

    public PublicReadRateLimiter(
            @Value("${app.public-read.rate-limit.max-requests:120}") int maxRequests,
            @Value("${app.public-read.rate-limit.window-seconds:60}") int windowSeconds) {
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds debe ser mayor que 0");
        }
        // InMemoryRateLimiter rechaza maxRequests no positivo al construirse.
        this.limiter = new InMemoryRateLimiter(maxRequests);
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /** Vacío si el intento entra en la ventana; en su defecto, el instante en que se podrá reintentar. */
    public Optional<Instant> tryAcquire(String key, Clock clock) {
        return limiter.tryAcquire(key, window, clock);
    }
}
