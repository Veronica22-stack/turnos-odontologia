package ar.turnosodontologia.consultorio.reservas.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentStatusView;
import ar.turnosodontologia.consultorio.notificaciones.api.NotificationTypeView;
import ar.turnosodontologia.consultorio.reservas.domain.BookingToken;
import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenGenerator;
import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenRepository;
import ar.turnosodontologia.consultorio.reservas.domain.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El enlace de un recordatorio días después usa un token nuevo guardado solo hasheado (C-13 D11). */
class BookingManagementLinkProviderTest {

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-12-10T13:30:00Z");

    @Test
    void emiteTokenNuevoHasheadoConVigenciaPosteriorAlTurno() {
        UUID appointmentId = UUID.randomUUID();
        List<BookingToken> saved = new ArrayList<>();
        BookingManagementLinkProvider provider = provider(appointmentId, saved);

        Optional<String> raw = provider.tokenFor(appointmentId, NotificationTypeView.REMINDER);

        assertThat(raw).isPresent();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).tokenHash()).isEqualTo(TokenHasher.hash(raw.orElseThrow()));
        assertThat(saved.get(0).expiresAt()).isEqualTo(ENDS.plus(Duration.ofDays(1)));
        assertThat(raw.orElseThrow()).doesNotContain(saved.get(0).tokenHash());
    }

    @Test
    void cancelacionesNoPidenToken() {
        UUID appointmentId = UUID.randomUUID();
        List<BookingToken> saved = new ArrayList<>();
        BookingManagementLinkProvider provider = provider(appointmentId, saved);

        assertThat(provider.tokenFor(appointmentId, NotificationTypeView.CANCELLATION)).isEmpty();
        assertThat(provider.tokenFor(appointmentId, NotificationTypeView.DENTIST_CANCELLATION)).isEmpty();
        assertThat(saved).isEmpty();
    }

    private static BookingManagementLinkProvider provider(UUID appointmentId, List<BookingToken> saved) {
        AppointmentNotificationLookup lookup = id -> Optional.of(new AppointmentNotificationView(
                appointmentId, UUID.randomUUID(), "Limpieza", ENDS.minusSeconds(1800), ENDS,
                AppointmentStatusView.CONFIRMED));
        BookingTokenRepository repository = new BookingTokenRepository() {
            @Override
            public BookingToken save(BookingToken token) {
                saved.add(token);
                return token;
            }

            @Override
            public Optional<BookingToken> findByTokenHash(String hash) {
                return saved.stream().filter(t -> t.tokenHash().equals(hash)).findFirst();
            }

            @Override
            public List<BookingToken> findByAppointmentId(UUID id) {
                return List.copyOf(saved);
            }

            @Override
            public int revokeAllForAppointment(UUID id, Instant now) {
                return 0;
            }
        };
        return new BookingManagementLinkProvider(lookup, repository, new BookingTokenGenerator(),
                Clock.fixed(NOW, ZoneOffset.UTC), 1);
    }
}
