package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.reservas.application.BookingRateLimiter;
import ar.turnosodontologia.consultorio.shared.api.RateLimiter;
import ar.turnosodontologia.consultorio.shared.infrastructure.InMemoryRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Limitador dedicado a las reservas y al uso de tokens: clave hasheada, nunca en claro ni en logs (H4). */
@Component
public class PublicBookingRateLimiter implements BookingRateLimiter {

    private final Duration window;
    private final InMemoryRateLimiter limiter;

    public PublicBookingRateLimiter(@Value("${RATE_LIMIT_PUBLIC_PER_MIN:30}") int maxRequests) {
        this.limiter = new InMemoryRateLimiter(maxRequests);
        // Ventana fija de un minuto: no se introduce ninguna variable nueva (KB 08 / ConfigurationHygieneTest).
        this.window = Duration.ofMinutes(1);
    }

    @Override
    public Optional<Instant> tryAcquire(String key, Clock clock) {
        return limiter.tryAcquire(key, window, clock);
    }

    public static long retryAfter(Clock clock, Instant retryAt) {
        return RateLimiter.retryAfterSeconds(clock.instant(), retryAt);
    }
}
