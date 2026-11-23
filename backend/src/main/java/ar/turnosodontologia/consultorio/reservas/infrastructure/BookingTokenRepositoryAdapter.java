package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.reservas.domain.BookingToken;
import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA del repositorio de tokens; el hash es la única representación persistida. */
@Component
class BookingTokenRepositoryAdapter implements BookingTokenRepository {

    private final SpringDataBookingTokenRepository repository;

    BookingTokenRepositoryAdapter(SpringDataBookingTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public BookingToken save(BookingToken token) {
        return repository.save(token);
    }

    @Override
    public Optional<BookingToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash);
    }

    @Override
    public List<BookingToken> findByAppointmentId(UUID appointmentId) {
        return repository.findByAppointmentId(appointmentId);
    }

    @Override
    @Transactional
    public int revokeAllForAppointment(UUID appointmentId, Instant now) {
        return repository.revokeAllForAppointment(appointmentId, now);
    }
}
