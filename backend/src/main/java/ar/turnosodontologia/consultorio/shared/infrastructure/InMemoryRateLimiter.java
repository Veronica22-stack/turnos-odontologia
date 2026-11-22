package ar.turnosodontologia.consultorio.shared.infrastructure;

import ar.turnosodontologia.consultorio.shared.api.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Ventanas fijas en memoria, indexadas por el digest de la clave; nunca guarda IP ni email en claro. */
@Component
public final class InMemoryRateLimiter implements RateLimiter {

    private static final int DEFAULT_MAX_ATTEMPTS = 10;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public InMemoryRateLimiter() {
        this(DEFAULT_MAX_ATTEMPTS);
    }

    public InMemoryRateLimiter(int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts debe ser mayor que 0");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public Optional<Instant> tryAcquire(String key, Duration window, Clock clock) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        }
        AtomicReference<Optional<Instant>> result = new AtomicReference<>(Optional.empty());
        windows.compute(digest(key), (ignored, current) -> {
            if (current == null || !now.isBefore(current.expiresAt())) {
                return new Window(now.plus(window), 1);
            }
            if (current.attempts() < maxAttempts) {
                return new Window(current.expiresAt(), current.attempts() + 1);
            }
            result.set(Optional.of(current.expiresAt()));
            return current;
        });
        return result.get();
    }

    /** {@code now == expiresAt} ya pertenece a la ventana siguiente (comparación estricta). */
    private record Window(Instant expiresAt, int attempts) {
    }

    private static String digest(String key) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
        }
    }
}
