package ar.turnosodontologia.consultorio.reservas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentOperations;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentOriginView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentServiceLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentStatusView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentView;
import ar.turnosodontologia.consultorio.agenda.api.CancelledByView;
import ar.turnosodontologia.consultorio.agenda.api.PublicBookingCommand;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.reservas.domain.BookingToken;
import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenRepository;
import ar.turnosodontologia.consultorio.reservas.domain.TokenHasher;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests del caso de uso de gestión por token con fakes manuales (Flujo 4). */
class BookingManagementUseCaseTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final Instant START = Instant.parse("2026-12-10T13:00:00Z");
    private static final String RAW_TOKEN = "token-en-claro-abc123-def456_GHI789";

    @Test
    void vistaConTokenValidoEsAcotadaYAudita() {
        Fixture fixture = new Fixture();
        BookingDetails details = fixture.useCase().view(RAW_TOKEN, "127.0.0.1");

        assertThat(details.bookingId()).isEqualTo(fixture.appointmentId);
        assertThat(details.serviceName()).isEqualTo("Limpieza");
        assertThat(details.practiceName()).isEqualTo("Consultorio");
        assertThat(details.cancellable()).isTrue();
        assertThat(fixture.audits).hasSize(1);
        assertThat(fixture.audits.get(0).action().name()).isEqualTo("BOOKING_TOKEN_USE");
        assertThat(fixture.audits.get(0).userId()).isNull();
        assertThat(fixture.audits.get(0).details())
                .containsEntry("outcome", "VIEW")
                .containsEntry("reason_code", "TOKEN_VIEW")
                .doesNotContainKey("token");
    }

    @Test
    void falloDeAuditoriaRevierteElUsoDelToken() {
        Fixture fixture = new Fixture();
        fixture.failAudit = true;
        assertThatThrownBy(() -> fixture.useCase().view(RAW_TOKEN, "127.0.0.1"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("AUDIT_WRITE_FAILED");
    }

    @Test
    void cancelarRevocaTodosLosTokensYRegistraPatient() {
        Fixture fixture = new Fixture();
        fixture.useCase().cancel(RAW_TOKEN, "127.0.0.1");

        assertThat(fixture.cancelledBy).isEqualTo(CancelledByView.PATIENT);
        assertThat(fixture.revoked).isEqualTo(1);
        assertThat(fixture.audits).hasSize(1);
    }

    @Test
    void segundaCancelacionNoPublicaOtroEvento() {
        Fixture fixture = new Fixture();
        fixture.useCase().cancel(RAW_TOKEN, "127.0.0.1");
        assertThat(fixture.audits).hasSize(1);

        assertThatThrownBy(() -> fixture.useCase().cancel(RAW_TOKEN, "127.0.0.1"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("INVALID_OR_EXPIRED_TOKEN");
        assertThat(fixture.audits).hasSize(1);
    }

    @Test
    void reprogramacionConConflictoConservaElTurnoOriginal() {
        Fixture fixture = new Fixture();
        fixture.conflict = true;
        assertThatThrownBy(() -> fixture.useCase().reschedule(RAW_TOKEN, START.plusSeconds(3600), "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SLOT_TAKEN");
        assertThat(fixture.rescheduledTo).isNull();
    }

    @Test
    void reprogramacionCorrectaConservaTokenYAjustaVigencia() {
        Fixture fixture = new Fixture();
        Instant newStart = START.plusSeconds(3600);
        BookingDetails details = fixture.useCase().reschedule(RAW_TOKEN, newStart, "127.0.0.1");

        assertThat(fixture.rescheduledTo).isEqualTo(newStart);
        assertThat(fixture.token.expiresAt()).isAfter(newStart);
        assertThat(details.bookingId()).isEqualTo(fixture.appointmentId);
    }

    @Test
    void tokenInvalidoOExpiradoDaSiempreElMismoErrorGenerico() {
        Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.useCase().view("otro-token", "127.0.0.1"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("INVALID_OR_EXPIRED_TOKEN");
        fixture.token.revoke(NOW);
        assertThatThrownBy(() -> fixture.useCase().view(RAW_TOKEN, "127.0.0.1"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("INVALID_OR_EXPIRED_TOKEN");
    }

    @Test
    void turnoPasadoEsSoloLectura() {
        Fixture fixture = new Fixture();
        fixture.appointment = new AppointmentNotificationView(fixture.appointmentId, UUID.randomUUID(), "Limpieza",
                NOW.minusSeconds(3600), NOW.minusSeconds(1800), AppointmentStatusView.CONFIRMED);
        assertThatThrownBy(() -> fixture.useCase().cancel(RAW_TOKEN, "127.0.0.1"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("BOOKING_MODIFICATION_NOT_ALLOWED");
    }

    private static final class Fixture {
        final UUID serviceId = UUID.randomUUID();
        final UUID appointmentId = UUID.randomUUID();
        final BookingToken token = BookingToken.forEmail(appointmentId, TokenHasher.hash(RAW_TOKEN),
                START.plusSeconds(1800).plusSeconds(86400), NOW);
        final List<AuditEvent> audits = new ArrayList<>();
        AppointmentNotificationView appointment = new AppointmentNotificationView(appointmentId, UUID.randomUUID(),
                "Limpieza", START, START.plusSeconds(1800), AppointmentStatusView.CONFIRMED);
        CancelledByView cancelledBy;
        Instant rescheduledTo;
        int revoked;
        boolean conflict;
        boolean failAudit;

        BookingManagementUseCase useCase() {
            PracticeRepository practices = new PracticeRepository() {
                @Override
                public List<Practice> findAll() {
                    return List.of(Practice.existing(PRACTICE_ID, "Consultorio", "UTC"));
                }
            };
            return new BookingManagementUseCase(
                    new BookingTokenRepository() {
                        @Override
                        public BookingToken save(BookingToken value) {
                            return value;
                        }

                        @Override
                        public Optional<BookingToken> findByTokenHash(String hash) {
                            return hash.equals(token.tokenHash()) ? Optional.of(token) : Optional.empty();
                        }

                        @Override
                        public List<BookingToken> findByAppointmentId(UUID id) {
                            return List.of(token);
                        }

                        @Override
                        public int revokeAllForAppointment(UUID id, Instant now) {
                            revoked++;
                            token.revoke(now);
                            return 1;
                        }
                    },
                    id -> Optional.ofNullable(appointment),
                    new AppointmentOperations() {
                        @Override
                        public AppointmentView book(PublicBookingCommand command) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public AppointmentView reschedule(UUID id, Instant startsAt, boolean overbooking) {
                            if (conflict) {
                                throw new ConflictException("APPOINTMENT_SLOT_TAKEN");
                            }
                            rescheduledTo = startsAt;
                            return new AppointmentView(id, UUID.randomUUID(), serviceId, startsAt,
                                    startsAt.plusSeconds(1800), 30, AppointmentStatusView.CONFIRMED,
                                    AppointmentOriginView.PUBLIC, false);
                        }

                        @Override
                        public AppointmentView cancel(UUID id, CancelledByView by) {
                            cancelledBy = by;
                            appointment = new AppointmentNotificationView(id, appointment.patientId(), "Limpieza",
                                    appointment.startsAt(), appointment.endsAt(), AppointmentStatusView.CANCELLED);
                            return new AppointmentView(id, appointment.patientId(), serviceId, appointment.startsAt(),
                                    appointment.endsAt(), 30, AppointmentStatusView.CANCELLED,
                                    AppointmentOriginView.PUBLIC, false);
                        }
                    },
                    id -> Optional.of(serviceId),
                    (practiceId, sid, date, startsAt) -> !conflict,
                    (key, clock) -> Optional.empty(),
                    new AuditLogger() {
                        @Override
                        public void log(AuditEvent event) {
                            if (failAudit) {
                                throw new BusinessRuleException("AUDIT_WRITE_FAILED");
                            }
                            audits.add(event);
                        }
                    },
                    practices,
                    new InstallationPractice(practices),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    1);
        }
    }
}
