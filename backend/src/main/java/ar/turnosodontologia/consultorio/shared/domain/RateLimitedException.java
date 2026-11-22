package ar.turnosodontologia.consultorio.shared.domain;

/** Error 429 con el plazo de reintento; la clave del limitador nunca viaja en el mensaje. */
public final class RateLimitedException extends DomainException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super("RATE_LIMITED");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
