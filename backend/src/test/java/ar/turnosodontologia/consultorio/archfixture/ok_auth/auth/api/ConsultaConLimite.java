package ar.turnosodontologia.consultorio.archfixture.ok_auth.auth.api;

import ar.turnosodontologia.consultorio.archfixture.ok_auth.shared.api.RateLimiter;

/** Fixture positivo: auth.api -> shared.api esta permitido (D11). */
public final class ConsultaConLimite {

    private final RateLimiter rateLimiter;

    public ConsultaConLimite(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public boolean puedeConsultar() {
        return rateLimiter.tryAcquire("fixture");
    }
}
