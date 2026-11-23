package ar.turnosodontologia.consultorio.reservas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.agenda.api.ActiveBookingsLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentOperations;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentOriginView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentStatusView;
import ar.turnosodontologia.consultorio.agenda.api.CancelledByView;
import ar.turnosodontologia.consultorio.agenda.api.PublicServiceNameLookup;
import ar.turnosodontologia.consultorio.pacientes.api.PatientBookingOperations;
import ar.turnosodontologia.consultorio.pacientes.api.PatientBookingResult;
import ar.turnosodontologia.consultorio.reservas.domain.BookingToken;
import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenGenerator;
import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenRepository;
import ar.turnosodontologia.consultorio.reservas.domain.TokenHasher;
import ar.turnosodontologia.consultorio.shared.api.PracticeBookingSettings;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import ar.turnosodontologia.consultorio.shared.domain.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests del caso de uso de reserva pública con fakes manuales (sin Spring ni base). */
class PublicBookingUseCaseTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final Instant START = Instant.parse("2026-12-10T13:00:00Z");

    @Test
    void configuracionDeTtlNoPositivaFallaAlArrancar() {
        Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.useCase(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOKING_TOKEN_TTL_DAYS");
        assertThatThrownBy(() -> fixture.useCase(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservaCorrectaCreaTurnoYTokenHasheado() {
        Fixture fixture = new Fixture();
        PublicBookingResult result = fixture.useCase().book(fixture.request(), "127.0.0.1");

        assertThat(result.bookingId()).isEqualTo(fixture.appointmentId);
        assertThat(result.serviceName()).isEqualTo("Limpieza");
        assertThat(result.practiceName()).isEqualTo("Consultorio");
        assertThat(fixture.tokens).hasSize(1);
        BookingToken token = fixture.tokens.get(0);
        assertThat(token.tokenHash()).matches("[0-9a-f]{64}");
        assertThat(token.tokenHash()).isNotEqualTo(fixture.rawToken);
        assertThat(token.expiresAt()).isEqualTo(fixture.appointmentEnd.plusSeconds(86400));
        assertThat(fixture.appointments.bookings).hasSize(1);
    }

    @Test
    void slotNoOfrecidoDevuelve409SinCrearNada() {
        Fixture fixture = new Fixture();
        fixture.offered = false;
        assertThatThrownBy(() -> fixture.useCase().book(fixture.request(), "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SLOT_TAKEN");
        assertThat(fixture.appointments.bookings).isEmpty();
        assertThat(fixture.tokens).isEmpty();
    }

    @Test
    void inicioDentroDeLaAnticipacionMinimaEs422() {
        Fixture fixture = new Fixture();
        PublicBookingCommand tooSoon = new PublicBookingCommand(fixture.serviceId, NOW.plusSeconds(3600),
                "Ana", "Pérez", "12345678", "1133333333", "ana@example.com");
        assertThatThrownBy(() -> fixture.useCase().book(tooSoon, "127.0.0.1"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("BOOKING_TOO_SOON");
        assertThat(fixture.appointments.bookings).isEmpty();
    }

    @Test
    void inicioFueraDelHorizonteEs422() {
        Fixture fixture = new Fixture();
        PublicBookingCommand tooFar = new PublicBookingCommand(fixture.serviceId, NOW.plus(Duration.ofDays(31)),
                "Ana", "Pérez", "12345678", "1133333333", "ana@example.com");
        assertThatThrownBy(() -> fixture.useCase().book(tooFar, "127.0.0.1"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("BOOKING_BEYOND_HORIZON");
    }

    @Test
    void limitePorContactoRechazaLaReserva() {
        Fixture fixture = new Fixture();
        fixture.activeCount = PublicBookingUseCase.MAX_ACTIVE_FUTURE_BOOKINGS_PER_CONTACT;
        assertThatThrownBy(() -> fixture.useCase().book(fixture.request(), "127.0.0.1"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("BOOKING_CONTACT_LIMIT");
        assertThat(fixture.appointments.bookings).isEmpty();
    }

    @Test
    void rateLimitRechazaAntesDeCrearNada() {
        Fixture fixture = new Fixture();
        fixture.retryAt = NOW.plusSeconds(45);
        assertThatThrownBy(() -> fixture.useCase().book(fixture.request(), "127.0.0.1"))
                .isInstanceOf(RateLimitedException.class);
        assertThat(fixture.appointments.bookings).isEmpty();
    }

    private static final class Fixture {
        final UUID serviceId = UUID.randomUUID();
        final UUID appointmentId = UUID.randomUUID();
        final Instant appointmentEnd = START.plusSeconds(1800);
        final List<BookingToken> tokens = new ArrayList<>();
        final FakeAppointments appointments = new FakeAppointments();
        boolean offered = true;
        long activeCount = 0;
        Instant retryAt = null;
        String rawToken = "raw-token-not-stored";

        PublicBookingUseCase useCase() {
            return useCase(1);
        }

        PublicBookingUseCase useCase(long ttlDays) {
            PracticeRepository practices = new PracticeRepository() {
                @Override
                public List<Practice> findAll() {
                    return List.of(Practice.existing(PRACTICE_ID, "Consultorio", "UTC"));
                }
            };
            return new PublicBookingUseCase(
                    (practiceId, sid, date, startsAt) -> offered,
                    id -> new PracticeBookingSettings("UTC", 15, 120, 30),
                    new FakePatients(),
                    (practiceId, ids, from) -> activeCount,
                    appointments,
                    (practiceId, sid) -> Optional.of("Limpieza"),
                    new FakeTokens(),
                    new BookingTokenGenerator(),
                    (key, clock) -> retryAt == null ? Optional.empty() : Optional.of(retryAt),
                    new InstallationPractice(practices),
                    practices,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    ttlDays);
        }

        PublicBookingCommand request() {
            return new PublicBookingCommand(serviceId, START, "Ana", "Pérez", "12345678", "1133333333",
                    "ana@example.com");
        }

        private final class FakePatients implements PatientBookingOperations {
            @Override
            public PatientBookingResult findOrCreate(UUID practiceId, String dni, String firstName, String lastName,
                    String phone, String email) {
                return new PatientBookingResult(UUID.randomUUID(), true, false);
            }

            @Override
            public List<UUID> findIdsByContact(UUID practiceId, String dni, String email, String phone) {
                return List.of(UUID.randomUUID());
            }
        }

        private final class FakeTokens implements BookingTokenRepository {
            @Override
            public BookingToken save(BookingToken token) {
                tokens.add(token);
                return token;
            }

            @Override
            public Optional<BookingToken> findByTokenHash(String tokenHash) {
                return tokens.stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
            }

            @Override
            public List<BookingToken> findByAppointmentId(UUID appointmentId) {
                return List.copyOf(tokens);
            }

            @Override
            public int revokeAllForAppointment(UUID appointmentId, Instant now) {
                return 0;
            }
        }

        private final class FakeAppointments implements AppointmentOperations {
            final List<ar.turnosodontologia.consultorio.agenda.api.PublicBookingCommand> bookings = new ArrayList<>();

            @Override
            public AppointmentView book(ar.turnosodontologia.consultorio.agenda.api.PublicBookingCommand command) {
                bookings.add(command);
                return new AppointmentView(appointmentId, UUID.randomUUID(), command.serviceId(),
                        command.startsAt(), appointmentEnd, 30, AppointmentStatusView.CONFIRMED,
                        AppointmentOriginView.PUBLIC, false);
            }

            @Override
            public AppointmentView reschedule(UUID id, Instant newStartsAt, boolean overbooking) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AppointmentView cancel(UUID id, CancelledByView by) {
                throw new UnsupportedOperationException();
            }
        }
    }
}
