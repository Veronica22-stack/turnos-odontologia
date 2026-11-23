package ar.turnosodontologia.consultorio.reservas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Puertos de limitación de tasa del caso de uso, implementado en infraestructura con clave hasheada. */
public interface BookingRateLimiter {

    Optional<Instant> tryAcquire(String key, Clock clock);
}
