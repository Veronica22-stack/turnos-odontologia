package ar.turnosodontologia.consultorio.notificaciones.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Backoff exponencial {@code min(base x 2^(n-1), tope)} tras el fallo n (design D7): con base 5 min y tope 1 h son
 * 5, 10, 20, 40, 60 min. Al llegar a {@code maxAttempts} o ante un fallo permanente la notificacion queda fallida.
 */
public final class NotificationRetryPolicy {

    public static final Duration DEFAULT_BASE = Duration.ofMinutes(5);
    public static final Duration DEFAULT_CAP = Duration.ofHours(1);

    private static final int MAX_DOUBLINGS = 30;

    private final Duration base;
    private final Duration cap;
    private final int maxAttempts;

    public NotificationRetryPolicy(Duration base, Duration cap, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts debe ser al menos 1");
        }
        if (base.isNegative() || base.isZero() || cap.compareTo(base) < 0) {
            throw new IllegalArgumentException("base y tope invalidos");
        }
        this.base = base;
        this.cap = cap;
        this.maxAttempts = maxAttempts;
    }

    /** Politica con la base (5 min) y el tope (1 h) por defecto; solo el maximo de intentos es configurable. */
    public static NotificationRetryPolicy standard(int maxAttempts) {
        return new NotificationRetryPolicy(DEFAULT_BASE, DEFAULT_CAP, maxAttempts);
    }

    /**
     * @param attempts intentos ya realizados, incluido el que acaba de fallar (>= 1)
     * @param retryable {@code false} si el proveedor informo un fallo permanente
     */
    public RetryDecision decide(int attempts, boolean retryable, Instant now) {
        if (!retryable || attempts >= maxAttempts) {
            return RetryDecision.giveUp();
        }
        return RetryDecision.retryAt(now.plus(delayAfter(attempts)));
    }

    private Duration delayAfter(int failureNumber) {
        int doublings = Math.min(Math.max(failureNumber - 1, 0), MAX_DOUBLINGS);
        Duration delay = base.multipliedBy(1L << doublings);
        return delay.compareTo(cap) > 0 ? cap : delay;
    }
}
