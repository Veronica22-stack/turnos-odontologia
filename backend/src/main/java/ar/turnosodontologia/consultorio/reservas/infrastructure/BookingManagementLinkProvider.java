package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationLookup;
import ar.turnosodontologia.consultorio.notificaciones.api.ManagementLinkProvider;
import ar.turnosodontologia.consultorio.notificaciones.api.NotificationTypeView;
import ar.turnosodontologia.consultorio.reservas.domain.BookingToken;
import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenGenerator;
import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenRepository;
import ar.turnosodontologia.consultorio.reservas.domain.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SPI que C-13 dejó preparada: emite un token nuevo por correo (el claro no se puede recuperar del hash) y lo guarda
 * solo hasheado. El enlace se arma en memoria en el despachador; nunca se persiste ni se loguea (design C-13 D11).
 */
@Component
public class BookingManagementLinkProvider implements ManagementLinkProvider {

    private final AppointmentNotificationLookup appointments;
    private final BookingTokenRepository tokens;
    private final BookingTokenGenerator generator;
    private final Clock clock;
    private final Duration ttl;

    public BookingManagementLinkProvider(
            AppointmentNotificationLookup appointments,
            BookingTokenRepository tokens,
            BookingTokenGenerator generator,
            Clock clock,
            @Value("${BOOKING_TOKEN_TTL_DAYS:1}") long tokenTtlDays) {
        if (tokenTtlDays <= 0) {
            throw new IllegalArgumentException("BOOKING_TOKEN_TTL_DAYS debe ser mayor que 0");
        }
        this.appointments = appointments;
        this.tokens = tokens;
        this.generator = generator;
        this.clock = clock;
        this.ttl = Duration.ofDays(tokenTtlDays);
    }

    @Override
    @Transactional
    public Optional<String> tokenFor(UUID appointmentId, NotificationTypeView type) {
        if (appointmentId == null || type == null) {
            return Optional.empty();
        }
        if (type == NotificationTypeView.CANCELLATION || type == NotificationTypeView.DENTIST_CANCELLATION) {
            return Optional.empty();
        }
        Optional<ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationView> found =
                appointments.find(appointmentId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        String raw = generator.generate();
        tokens.save(BookingToken.forEmail(appointmentId, TokenHasher.hash(raw),
                found.get().endsAt().plus(ttl), now));
        return Optional.of(raw);
    }
}
