package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.reservas.domain.BookingToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataBookingTokenRepository extends JpaRepository<BookingToken, UUID> {

    Optional<BookingToken> findByTokenHash(String tokenHash);

    List<BookingToken> findByAppointmentId(UUID appointmentId);

    @Modifying
    @Query("""
            update BookingToken t set t.revokedAt = :now
            where t.appointmentId = :appointmentId and t.revokedAt is null
            """)
    int revokeAllForAppointment(@Param("appointmentId") UUID appointmentId, @Param("now") Instant now);
}
