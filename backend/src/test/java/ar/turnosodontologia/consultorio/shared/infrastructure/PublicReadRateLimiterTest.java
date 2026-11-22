package ar.turnosodontologia.consultorio.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PublicReadRateLimiterTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void permiteNySuperaElLimiteDevuelveElInstantDeReintento() {
        PublicReadRateLimiter limiter = new PublicReadRateLimiter(2, 60);
        MutableClock clock = new MutableClock(START);

        assertThat(limiter.tryAcquire("cliente-A", clock)).isEmpty();
        assertThat(limiter.tryAcquire("cliente-A", clock)).isEmpty();

        assertThat(limiter.tryAcquire("cliente-A", clock)).contains(START.plusSeconds(60));
    }

    @Test
    void noExpeLaClaveOriginal() {
        PublicReadRateLimiter limiter = new PublicReadRateLimiter(1, 60);
        MutableClock clock = new MutableClock(START);
        limiter.tryAcquire("cliente-A", clock);

        Optional<Instant> blocked = limiter.tryAcquire("cliente-A", clock);

        assertThat(blocked).isPresent();
        assertThat(blocked.get()).isEqualTo(START.plusSeconds(60));
    }

    @Test
    void dosClavesDistintasNoSeAfectan() {
        PublicReadRateLimiter limiter = new PublicReadRateLimiter(1, 60);
        MutableClock clock = new MutableClock(START);

        assertThat(limiter.tryAcquire("cliente-A", clock)).isEmpty();
        assertThat(limiter.tryAcquire("cliente-B", clock)).isEmpty();
        assertThat(limiter.tryAcquire("cliente-A", clock)).isPresent();
    }

    @Test
    void unLimiteDeUnoBloqueaLaSegundaSolicitud() {
        PublicReadRateLimiter limiter = new PublicReadRateLimiter(1, 60);
        MutableClock clock = new MutableClock(START);

        assertThat(limiter.tryAcquire("cliente-A", clock)).isEmpty();
        assertThat(limiter.tryAcquire("cliente-A", clock)).isPresent();
    }

    @Test
    void laVentanaSeReiniciaAlAvanzarElClock() {
        PublicReadRateLimiter limiter = new PublicReadRateLimiter(1, 60);
        MutableClock clock = new MutableClock(START);

        assertThat(limiter.tryAcquire("cliente-A", clock)).isEmpty();
        assertThat(limiter.tryAcquire("cliente-A", clock)).isPresent();

        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.tryAcquire("cliente-A", clock)).isEmpty();
    }

    @Test
    void unLimiteNoPositivoSeRechazaAlConstruir() {
        assertThatThrownBy(() -> new PublicReadRateLimiter(0, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicReadRateLimiter(-1, 60)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaVentanaNoPositivaSeRechazaAlConstruir() {
        assertThatThrownBy(() -> new PublicReadRateLimiter(10, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
