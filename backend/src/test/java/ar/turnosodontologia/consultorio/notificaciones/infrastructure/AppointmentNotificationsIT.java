package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentCancelled;
import ar.turnosodontologia.consultorio.agenda.api.CancelledByView;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentResult;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentService;
import ar.turnosodontologia.consultorio.agenda.application.BookAppointmentCommand;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig.ControllableEmailSender;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig.MutableClock;
import ar.turnosodontologia.consultorio.shared.domain.DomainException;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Encolado por eventos con turnos reales y despacho desactivado (appointment-notifications, D3, D4). */
@Import(NotificationTestConfig.class)
class AppointmentNotificationsIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final Instant THURSDAY_10_LOCAL = Instant.parse("2026-12-10T13:00:00Z");

    private final AppointmentService service;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final ControllableEmailSender sender;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transactions;
    private UUID patient;
    private UUID serviceId;

    AppointmentNotificationsIT(AppointmentService service, JdbcTemplate jdbc, MutableClock clock,
            ControllableEmailSender sender, ApplicationEventPublisher publisher, TransactionTemplate transactions) {
        this.service = service;
        this.jdbc = jdbc;
        this.clock = clock;
        this.sender = sender;
        this.publisher = publisher;
        this.transactions = transactions;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        clock.set(NOW);
        sender.reset();
        patient = AppointmentFixtures.patient(true);
        serviceId = AppointmentFixtures.service(30, true, true);
        for (int weekday = 1; weekday <= 7; weekday++) {
            AppointmentFixtures.workingHours(weekday, "09:00", "17:00");
        }
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        sender.reset();
        AppointmentFixtures.cleanAll();
    }

    private AppointmentResult book(Instant startsAt) {
        return service.book(new BookAppointmentCommand(patient, serviceId, startsAt, false, "nota privada"));
    }

    private Map<String, Object> row(UUID appointment, String type) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status, attempts, scheduled_for, sent_at, last_error FROM notification "
                        + "WHERE appointment_id = ? AND type = ?", appointment, type);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int count(UUID appointment) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE appointment_id = ?", Integer.class,
                appointment);
    }

    private static Instant scheduledFor(Map<String, Object> row) {
        return ((java.sql.Timestamp) row.get("scheduled_for")).toInstant();
    }

    @Test
    void turnoConfirmadoEncolaConfirmacionPendienteConElInstanteDelServidor() {
        AppointmentResult created = book(THURSDAY_10_LOCAL);

        Map<String, Object> confirmation = row(created.id(), "CONFIRMATION");
        assertThat(confirmation).isNotNull();
        assertThat(confirmation.get("status")).isEqualTo("PENDING");
        assertThat(confirmation.get("attempts")).isEqualTo(0);
        assertThat(scheduledFor(confirmation)).isEqualTo(NOW);
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void altaRechazadaNoEncolaNada() {
        UUID inactive = AppointmentFixtures.patient(false);

        assertThatThrownBy(() -> service.book(
                new BookAppointmentCommand(inactive, serviceId, THURSDAY_10_LOCAL, false, null)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.book(
                new BookAppointmentCommand(patient, serviceId, Instant.parse("2026-12-10T05:00:00Z"), false, null)))
                .isInstanceOf(DomainException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isZero();
    }

    @Test
    void recordatorioVeinticuatroHorasAntesDelInicio() {
        AppointmentResult created = book(THURSDAY_10_LOCAL);

        Map<String, Object> reminder = row(created.id(), "REMINDER");
        assertThat(reminder.get("status")).isEqualTo("PENDING");
        assertThat(scheduledFor(reminder)).isEqualTo(Instant.parse("2026-12-09T13:00:00Z"));
    }

    @Test
    void turnoDentroDeLaVentanaNoTieneRecordatorio() {
        AppointmentResult created = book(Instant.parse("2026-12-01T15:00:00Z"));

        assertThat(row(created.id(), "CONFIRMATION")).isNotNull();
        assertThat(row(created.id(), "REMINDER")).isNull();
    }

    @Test
    void reprogramarRecalculaElRecordatorioYEncolaLaReprogramacion() {
        AppointmentResult created = book(THURSDAY_10_LOCAL);

        service.reschedule(created.id(), Instant.parse("2026-12-15T13:00:00Z"), false);

        assertThat(count(created.id())).isEqualTo(3);
        Map<String, Object> reminder = row(created.id(), "REMINDER");
        assertThat(reminder.get("status")).isEqualTo("PENDING");
        assertThat(reminder.get("attempts")).isEqualTo(0);
        assertThat(scheduledFor(reminder)).isEqualTo(Instant.parse("2026-12-14T13:00:00Z"));
        assertThat(row(created.id(), "RESCHEDULE").get("status")).isEqualTo("PENDING");
    }

    @Test
    void recordatorioYaEnviadoVuelveAPendienteSiElTurnoSeMueveMasAdelante() {
        AppointmentResult created = book(THURSDAY_10_LOCAL);
        jdbc.update("UPDATE notification SET status = 'SENT', sent_at = now(), attempts = 1 "
                + "WHERE appointment_id = ? AND type = 'REMINDER'", created.id());

        service.reschedule(created.id(), Instant.parse("2026-12-15T13:00:00Z"), false);

        Map<String, Object> reminder = row(created.id(), "REMINDER");
        assertThat(reminder.get("status")).isEqualTo("PENDING");
        assertThat(reminder.get("attempts")).isEqualTo(0);
        assertThat(reminder.get("sent_at")).isNull();
        assertThat(scheduledFor(reminder)).isEqualTo(Instant.parse("2026-12-14T13:00:00Z"));
    }

    @Test
    void segundaReprogramacionReutilizaLaFilaDeReprogramacion() {
        AppointmentResult created = book(THURSDAY_10_LOCAL);
        service.reschedule(created.id(), Instant.parse("2026-12-15T13:00:00Z"), false);
        jdbc.update("UPDATE notification SET status = 'SENT', sent_at = now(), attempts = 1 "
                + "WHERE appointment_id = ? AND type = 'RESCHEDULE'", created.id());

        service.reschedule(created.id(), Instant.parse("2026-12-16T13:00:00Z"), false);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE appointment_id = ? "
                + "AND type = 'RESCHEDULE'", Integer.class, created.id())).isEqualTo(1);
        assertThat(row(created.id(), "RESCHEDULE").get("status")).isEqualTo("PENDING");
    }

    @Test
    void reprogramadoDentroDeLaVentanaDescartaElRecordatorio() {
        AppointmentResult created = book(THURSDAY_10_LOCAL);

        service.reschedule(created.id(), Instant.parse("2026-12-01T15:00:00Z"), false);

        Map<String, Object> reminder = row(created.id(), "REMINDER");
        assertThat(reminder.get("status")).isEqualTo("DISCARDED");
        assertThat(reminder.get("last_error")).isEqualTo("REMINDER_WINDOW_PASSED");
        assertThat(row(created.id(), "RESCHEDULE").get("status")).isEqualTo("PENDING");
    }

    @Test
    void cancelarAnulaElRecordatorioYEncolaLaCancelacion() {
        AppointmentResult created = book(THURSDAY_10_LOCAL);

        service.cancel(created.id());

        assertThat(row(created.id(), "REMINDER").get("status")).isEqualTo("DISCARDED");
        assertThat(row(created.id(), "REMINDER").get("last_error")).isEqualTo("APPOINTMENT_CANCELLED");
        assertThat(row(created.id(), "CANCELLATION").get("status")).isEqualTo("PENDING");
        assertThat(row(created.id(), "DENTIST_CANCELLATION")).isNull();
    }

    @Test
    void laConfirmacionAunPendienteQuedaDescartadaAlCancelar() {
        AppointmentResult created = book(THURSDAY_10_LOCAL);
        assertThat(row(created.id(), "CONFIRMATION").get("status")).isEqualTo("PENDING");

        service.cancel(created.id());

        assertThat(row(created.id(), "CONFIRMATION").get("status")).isEqualTo("DISCARDED");
        assertThat(row(created.id(), "CANCELLATION").get("status")).isEqualTo("PENDING");
    }

    @Test
    void cancelacionPorElPacienteEncolaTambienElAvisoAlOdontologo() {
        AppointmentResult created = book(THURSDAY_10_LOCAL);
        AppointmentFixtures.execute("UPDATE appointment SET status = 'CANCELLED', cancelled_by = 'PATIENT', "
                + "cancelled_at = now() WHERE id = '" + created.id() + "'");

        transactions.executeWithoutResult(status -> publisher.publishEvent(new AppointmentCancelled(created.id(),
                UUID.fromString(AppointmentFixtures.PRACTICE_ID), patient, THURSDAY_10_LOCAL,
                THURSDAY_10_LOCAL.plusSeconds(1800), CancelledByView.PATIENT)));

        assertThat(row(created.id(), "CANCELLATION").get("status")).isEqualTo("PENDING");
        assertThat(row(created.id(), "DENTIST_CANCELLATION").get("status")).isEqualTo("PENDING");
    }

    @Test
    void procesarDosVecesElEventoDeConfirmacionNoReiniciaUnaConfirmacionEnviada() {
        AppointmentResult created = book(THURSDAY_10_LOCAL);
        jdbc.update("UPDATE notification SET status = 'SENT', sent_at = now(), attempts = 1 "
                + "WHERE appointment_id = ? AND type = 'CONFIRMATION'", created.id());

        transactions.executeWithoutResult(status -> publisher.publishEvent(
                new ar.turnosodontologia.consultorio.agenda.api.AppointmentConfirmed(created.id(),
                        UUID.fromString(AppointmentFixtures.PRACTICE_ID), patient, serviceId, THURSDAY_10_LOCAL,
                        THURSDAY_10_LOCAL.plusSeconds(1800),
                        ar.turnosodontologia.consultorio.agenda.api.AppointmentOriginView.INTERNAL)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE appointment_id = ? "
                + "AND type = 'CONFIRMATION'", Integer.class, created.id())).isEqualTo(1);
        assertThat(row(created.id(), "CONFIRMATION").get("status")).isEqualTo("SENT");
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void unaTransaccionRevertidaNoEncolaNada() {
        UUID appointment = AppointmentFixtures.appointment(patient, serviceId, "2026-12-10T13:00:00Z",
                "2026-12-10T13:30:00Z", "CONFIRMED");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            publisher.publishEvent(new ar.turnosodontologia.consultorio.agenda.api.AppointmentConfirmed(appointment,
                    UUID.fromString(AppointmentFixtures.PRACTICE_ID), patient, serviceId, THURSDAY_10_LOCAL,
                    THURSDAY_10_LOCAL.plusSeconds(1800),
                    ar.turnosodontologia.consultorio.agenda.api.AppointmentOriginView.INTERNAL));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isZero();
    }
}
