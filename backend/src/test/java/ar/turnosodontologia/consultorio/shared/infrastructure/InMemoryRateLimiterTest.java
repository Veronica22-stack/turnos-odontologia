package ar.turnosodontologia.consultorio.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ar.turnosodontologia.consultorio.shared.api.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Ventana fija en memoria: 10 intentos por clave y ventana, sin guardar la clave en claro (design D5). */
class InMemoryRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Instant START = Instant.parse("2026-03-10T12:00:00Z");

    private static Clock clock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static Optional<Instant> acquire(RateLimiter limiter, String key, Instant at) {
        return limiter.tryAcquire(key, WINDOW, clock(at));
    }

    @Test
    void primerIntentoEsPermitido() {
        RateLimiter limiter = new InMemoryRateLimiter();

        assertThat(acquire(limiter, "ip|email", START)).isEmpty();
    }

    @Test
    void decimoIntentoEsPermitidoYUndecimoDevuelveInstanteDeReintento() {
        RateLimiter limiter = new InMemoryRateLimiter();
        for (int attempt = 1; attempt <= 10; attempt++) {
            assertThat(acquire(limiter, "ip|email", START.plusSeconds(attempt))).isEmpty();
        }

        Optional<Instant> retryAt = acquire(limiter, "ip|email", START.plusSeconds(11));

        assertThat(retryAt).contains(START.plusSeconds(1).plus(WINDOW));
        assertThat(Duration.between(START.plusSeconds(11), retryAt.orElseThrow()).getSeconds()).isPositive();
    }

    @Test
    void otraClaveNoComparteLaVentana() {
        RateLimiter limiter = new InMemoryRateLimiter();
        for (int attempt = 0; attempt < 10; attempt++) {
            acquire(limiter, "ip|primero@example.com", START);
        }

        assertThat(acquire(limiter, "ip|segundo@example.com", START)).isEmpty();
    }

    @Test
    void laVentanaExpiraAlAvanzarElReloj() {
        RateLimiter limiter = new InMemoryRateLimiter();
        for (int attempt = 0; attempt < 10; attempt++) {
            acquire(limiter, "ip|email", START);
        }
        assertThat(acquire(limiter, "ip|email", START.plusSeconds(1))).isPresent();

        assertThat(acquire(limiter, "ip|email", START.plus(WINDOW))).isEmpty();
    }

    @Test
    void niLaClaveNiElEmailAparecenEnMensajesONoSeGuardanEnClaro() {
        RateLimiter limiter = new InMemoryRateLimiter();
        String secret = "203.0.113.7|dr.perez@ejemplo.com";

        assertThatCode(() -> {
            for (int attempt = 0; attempt < 11; attempt++) {
                acquire(limiter, secret, START);
            }
        }).doesNotThrowAnyException();
        Optional<Instant> retryAt = acquire(limiter, secret, START);

        assertThat(retryAt.orElseThrow().toString()).doesNotContain("203.0.113.7", "dr.perez@ejemplo.com");
        assertThat(limiter.toString()).doesNotContain("203.0.113.7", "dr.perez@ejemplo.com");
    }

    @Test
    void clavesQueSoloCambianPorIpNoSeMezclan() {
        RateLimiter limiter = new InMemoryRateLimiter();
        for (int attempt = 0; attempt < 10; attempt++) {
            acquire(limiter, "203.0.113.7|dr@ejemplo.com", START);
        }

        assertThat(acquire(limiter, "203.0.113.8|dr@ejemplo.com", START)).isEmpty();
    }

    @Test
    void enElLimiteExactoLaVentanaSeRenueva() {
        RateLimiter limiter = new InMemoryRateLimiter();
        for (int attempt = 0; attempt < 10; attempt++) {
            acquire(limiter, "ip|email", START);
        }

        assertThat(acquire(limiter, "ip|email", START.plus(WINDOW))).isEmpty();
        assertThat(acquire(limiter, "ip|email", START.plus(WINDOW).plusNanos(1))).isEmpty();
    }

    @Test
    void retryAfterEsUnCalculoPuroYNuncaDevuelveCero() {
        assertThat(RateLimiter.retryAfterSeconds(START, START.plusSeconds(42))).isEqualTo(42);
        assertThat(RateLimiter.retryAfterSeconds(START, START.plusNanos(1))).isEqualTo(1);
        assertThat(RateLimiter.retryAfterSeconds(START, START)).isEqualTo(1);
    }
}
