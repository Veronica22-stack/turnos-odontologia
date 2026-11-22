package ar.turnosodontologia.consultorio.archfixture.ok_auth.shared.api;

/** Fixture: contrato compartido que auth.api puede consumir sin crear una arista de modulo. */
public interface RateLimiter {

    boolean tryAcquire(String key);
}
