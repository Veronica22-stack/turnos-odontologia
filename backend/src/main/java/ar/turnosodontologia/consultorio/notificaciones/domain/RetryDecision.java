package ar.turnosodontologia.consultorio.notificaciones.domain;

import java.time.Instant;

/** Resultado de {@link NotificationRetryPolicy}: reintentar en {@code nextScheduledFor} o dejar la fila {@code FAILED}. */
public record RetryDecision(boolean retry, Instant nextScheduledFor) {

    public static RetryDecision retryAt(Instant nextScheduledFor) {
        return new RetryDecision(true, nextScheduledFor);
    }

    public static RetryDecision giveUp() {
        return new RetryDecision(false, null);
    }
}
