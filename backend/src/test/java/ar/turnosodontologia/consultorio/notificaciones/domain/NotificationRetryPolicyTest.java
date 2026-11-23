package ar.turnosodontologia.consultorio.notificaciones.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Backoff exponencial 5/10/20/40 min con tope de 1 h y maximo de intentos (design D7). */
class NotificationRetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");

    @ParameterizedTest
    @CsvSource({"1,5", "2,10", "3,20", "4,40"})
    void losFallosTransitoriosReprogramanConBackoffExponencial(int attempts, long expectedMinutes) {
        NotificationRetryPolicy policy = NotificationRetryPolicy.standard(5);

        RetryDecision decision = policy.decide(attempts, true, NOW);

        assertThat(decision.retry()).isTrue();
        assertThat(decision.nextScheduledFor()).isEqualTo(NOW.plus(Duration.ofMinutes(expectedMinutes)));
    }

    @ParameterizedTest
    @CsvSource({"1,30", "2,60", "3,60", "6,60"})
    void elRetrasoNuncaSuperaElTope(int attempts, long expectedMinutes) {
        NotificationRetryPolicy policy =
                new NotificationRetryPolicy(Duration.ofMinutes(30), Duration.ofMinutes(60), 10);

        RetryDecision decision = policy.decide(attempts, true, NOW);

        assertThat(decision.nextScheduledFor()).isEqualTo(NOW.plus(Duration.ofMinutes(expectedMinutes)));
    }

    @Test
    void conMuchosIntentosElTopeEvitaDesbordamientoDelRetraso() {
        NotificationRetryPolicy policy = NotificationRetryPolicy.standard(500);

        assertThat(policy.decide(400, true, NOW).nextScheduledFor()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
    }

    @ParameterizedTest
    @CsvSource({"5,5", "6,5", "3,3", "4,3"})
    void alLlegarAlMaximoDeIntentosQuedaFallida(int attempts, int maxAttempts) {
        RetryDecision decision = NotificationRetryPolicy.standard(maxAttempts).decide(attempts, true, NOW);

        assertThat(decision.retry()).isFalse();
        assertThat(decision.nextScheduledFor()).isNull();
    }

    @Test
    void unIntentoPorDebajoDelMaximoTodaviaReintenta() {
        assertThat(NotificationRetryPolicy.standard(3).decide(2, true, NOW).retry()).isTrue();
        assertThat(NotificationRetryPolicy.standard(5).decide(4, true, NOW).retry()).isTrue();
    }

    @Test
    void unFalloPermanenteFallaEnElPrimerIntentoSinReprogramar() {
        RetryDecision decision = NotificationRetryPolicy.standard(5).decide(1, false, NOW);

        assertThat(decision.retry()).isFalse();
        assertThat(decision.nextScheduledFor()).isNull();
    }

    @Test
    void rechazaUnMaximoDeIntentosMenorQueUno() {
        assertThatThrownBy(() -> NotificationRetryPolicy.standard(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
