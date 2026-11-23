package ar.turnosodontologia.consultorio.reservas.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia de tokens de gestión: nunca guarda el token en claro. */
public interface BookingTokenRepository {

    BookingToken save(BookingToken token);

    Optional<BookingToken> findByTokenHash(String tokenHash);

    List<BookingToken> findByAppointmentId(UUID appointmentId);

    /** Revoca todos los tokens vigentes del turno y devuelve cuántos se revocaron. */
    int revokeAllForAppointment(UUID appointmentId, Instant now);
}
