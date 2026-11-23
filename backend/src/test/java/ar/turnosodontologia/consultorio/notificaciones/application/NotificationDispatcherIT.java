package ar.turnosodontologia.consultorio.notificaciones.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig.ControllableEmailSender;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig.MutableClock;
import ar.turnosodontologia.consultorio.notificaciones.domain.EmailSendException;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationErrorCode;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import ar.turnosodontologia.consultorio.notificaciones.domain.OutgoingEmail;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Despacho, revalidacion, reintentos y privacidad con un emisor falso controlable (design D5-D7, D16). */
@ExtendWith(OutputCaptureExtension.class)
@Import(NotificationTestConfig.class)
class NotificationDispatcherIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final String PATIENT_EMAIL = "ana.paciente@ejemplo.com";
    private static final String DENTIST_EMAIL = "consultorio@ejemplo.com";
    private static final String PRACTICE = AppointmentFixtures.PRACTICE_ID;

    private final NotificationDispatcher dispatcher;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final ControllableEmailSender sender;
    private UUID patient;
    private UUID serviceId;

    NotificationDispatcherIT(NotificationDispatcher dispatcher, JdbcTemplate jdbc, MutableClock clock,
            ControllableEmailSender sender) {
        this.dispatcher = dispatcher;
        this.jdbc = jdbc;
        this.clock = clock;
        this.sender = sender;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        clock.set(NOW);
        sender.reset();
        patient = AppointmentFixtures.patient(true);
        AppointmentFixtures.execute("UPDATE patient SET email = '" + PATIENT_EMAIL + "' WHERE id = '" + patient + "'");
        serviceId = AppointmentFixtures.service(30, true, true);
        AppointmentFixtures.execute("UPDATE practice SET contact_email = '" + DENTIST_EMAIL
                + "', contact_phone = '351-5551234', address = 'Av. Siempre Viva 742' WHERE id = '" + PRACTICE + "'");
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        sender.reset();
        AppointmentFixtures.cleanAll();
        AppointmentFixtures.execute("DELETE FROM patient WHERE id = '" + patient + "'");
        AppointmentFixtures.execute("UPDATE practice SET contact_email = NULL, contact_phone = NULL, address = NULL "
                + "WHERE id = '" + PRACTICE + "'");
    }

    // ---- helpers ----

    private UUID appointment(String startsAt, String status) {
        Instant start = Instant.parse(startsAt);
        return AppointmentFixtures.appointment(patient, serviceId, startsAt, start.plusSeconds(1800).toString(),
                status);
    }

    private UUID notification(UUID appointment, NotificationType type, Instant scheduledFor, int attempts) {
        UUID id = UUID.randomUUID();
        AppointmentFixtures.execute("INSERT INTO notification (id, practice_id, appointment_id, type, scheduled_for, "
                + "attempts) VALUES ('" + id + "', '" + PRACTICE + "', '" + appointment + "', '" + type.name()
                + "', '" + scheduledFor + "', " + attempts + ")");
        return id;
    }

    private Map<String, Object> row(UUID notification) {
        return jdbc.queryForMap("SELECT status, attempts, scheduled_for, sent_at, last_error FROM notification "
                + "WHERE id = ?", notification);
    }

    private static Instant instant(Object timestamp) {
        return ((Timestamp) timestamp).toInstant();
    }

    // ---- despacho ----

    @Test
    void recordatorioVencidoSeEnviaAlPacienteYQuedaSentConElInstanteDelServidor() {
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID reminder = notification(appointment, NotificationType.REMINDER, NOW.minusSeconds(3600), 0);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).hasSize(1);
        OutgoingEmail email = sender.sent().get(0);
        assertThat(email.to()).isEqualTo(PATIENT_EMAIL);
        assertThat(email.notificationId()).isEqualTo(reminder);
        assertThat(email.appointmentId()).isEqualTo(appointment);
        assertThat(email.type()).isEqualTo(NotificationType.REMINDER);
        assertThat(email.templateName()).isEqualTo("reminder");
        assertThat(email.body()).contains("Fecha: miércoles 02/12/2026").contains("Hora: 10:00");
        Map<String, Object> row = row(reminder);
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(instant(row.get("sent_at"))).isEqualTo(NOW);
    }

    @Test
    void recordatorioTodaviaNoVencidoNoSeEnvia() {
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID reminder = notification(appointment, NotificationType.REMINDER, NOW.plusSeconds(3600), 0);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).isEmpty();
        assertThat(row(reminder).get("status")).isEqualTo("PENDING");
    }

    @Test
    void despachaEnOrdenDeScheduledFor() {
        UUID late = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID early = appointment("2026-12-03T13:00:00Z", "CONFIRMED");
        UUID middle = appointment("2026-12-04T13:00:00Z", "CONFIRMED");
        notification(late, NotificationType.CONFIRMATION, NOW.minusSeconds(60), 0);
        notification(early, NotificationType.CONFIRMATION, NOW.minusSeconds(300), 0);
        notification(middle, NotificationType.CONFIRMATION, NOW.minusSeconds(120), 0);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).extracting(OutgoingEmail::appointmentId).containsExactly(early, middle, late);
    }

    @Test
    void unDespachoProcesaComoMaximoElTopeDeLote() {
        for (int i = 0; i < 25; i++) {
            UUID appointment = appointment(Instant.parse("2026-12-02T09:00:00Z").plus(Duration.ofHours(i)).toString(),
                    "CONFIRMED");
            notification(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(600 - i), 0);
        }

        dispatcher.dispatchDue();

        assertThat(sender.sent()).hasSize(NotificationDispatcher.BATCH_SIZE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE status = 'PENDING'", Integer.class))
                .isEqualTo(25 - NotificationDispatcher.BATCH_SIZE);
    }

    // ---- revalidacion ----

    @Test
    void recordatorioDeTurnoCanceladoEntreMedioSeDescartaSinEnviar() {
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CANCELLED");
        UUID reminder = notification(appointment, NotificationType.REMINDER, NOW.minusSeconds(60), 0);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).isEmpty();
        assertThat(row(reminder).get("status")).isEqualTo("DISCARDED");
        assertThat(row(reminder).get("last_error")).isEqualTo(NotificationErrorCode.APPOINTMENT_NOT_CONFIRMED.name());
    }

    @Test
    void recordatorioTardioDeUnTurnoYaIniciadoSeDescartaPorExpirado() {
        UUID appointment = appointment("2026-12-01T11:00:00Z", "CONFIRMED");
        UUID reminder = notification(appointment, NotificationType.REMINDER, NOW.minusSeconds(86_400), 0);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).isEmpty();
        assertThat(row(reminder).get("status")).isEqualTo("DISCARDED");
        assertThat(row(reminder).get("last_error")).isEqualTo("EXPIRED");
    }

    @Test
    void pacienteSinEmailDescartaSinIntentarNiReintentar() {
        AppointmentFixtures.execute("UPDATE patient SET email = NULL WHERE id = '" + patient + "'");
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID confirmation = notification(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(60), 0);

        dispatcher.dispatchDue();
        dispatcher.dispatchDue();

        assertThat(sender.sent()).isEmpty();
        Map<String, Object> row = row(confirmation);
        assertThat(row.get("status")).isEqualTo("DISCARDED");
        assertThat(row.get("last_error")).isEqualTo("NO_RECIPIENT");
        assertThat(row.get("attempts")).isEqualTo(0);
    }

    @Test
    void laCancelacionDeUnTurnoCanceladoSiSeEnviaAunqueYaHayaEmpezado() {
        UUID appointment = appointment("2026-12-01T11:00:00Z", "CANCELLED");
        UUID cancellation = notification(appointment, NotificationType.CANCELLATION, NOW.minusSeconds(60), 0);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).hasSize(1);
        assertThat(sender.sent().get(0).type()).isEqualTo(NotificationType.CANCELLATION);
        assertThat(row(cancellation).get("status")).isEqualTo("SENT");
    }

    @Test
    void elAvisoAlOdontologoVaAlEmailDeContactoDelConsultorioSinDatosDelPaciente() {
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CANCELLED");
        UUID notice = notification(appointment, NotificationType.DENTIST_CANCELLATION, NOW.minusSeconds(60), 0);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).hasSize(1);
        OutgoingEmail email = sender.sent().get(0);
        assertThat(email.to()).isEqualTo(DENTIST_EMAIL);
        assertThat(email.body()).contains("Un paciente canceló su turno del miércoles 02/12/2026 10:00");
        assertThat(email.body()).doesNotContain(PATIENT_EMAIL);
        assertThat(row(notice).get("status")).isEqualTo("SENT");
    }

    @Test
    void elAvisoAlOdontologoSinEmailDeContactoSeDescartaPorSinDestinatario() {
        AppointmentFixtures.execute("UPDATE practice SET contact_email = NULL WHERE id = '" + PRACTICE + "'");
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CANCELLED");
        UUID notice = notification(appointment, NotificationType.DENTIST_CANCELLATION, NOW.minusSeconds(60), 0);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).isEmpty();
        assertThat(row(notice).get("status")).isEqualTo("DISCARDED");
        assertThat(row(notice).get("last_error")).isEqualTo("NO_RECIPIENT");
    }

    // ---- reintentos ----

    @Test
    void primerFalloTransitorioReprogramaACincoMinutos() {
        sender.failWith(new EmailSendException(NotificationErrorCode.SMTP_UNAVAILABLE, true));
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID confirmation = notification(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(1), 0);

        dispatcher.dispatchDue();

        Map<String, Object> row = row(confirmation);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(instant(row.get("scheduled_for"))).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(row.get("last_error")).isEqualTo("SMTP_UNAVAILABLE");
    }

    @Test
    void terceroFalloTransitorioReprogramaAVeinteMinutos() {
        sender.failWith(new EmailSendException(NotificationErrorCode.SMTP_UNAVAILABLE, true));
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID confirmation = notification(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(1), 2);

        dispatcher.dispatchDue();

        Map<String, Object> row = row(confirmation);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(3);
        assertThat(instant(row.get("scheduled_for"))).isEqualTo(NOW.plus(Duration.ofMinutes(20)));
    }

    @Test
    void agotadosLosIntentosQuedaFailedYSeRegistraUnaAdvertenciaConElId(CapturedOutput output) {
        sender.failWith(new EmailSendException(NotificationErrorCode.SMTP_UNAVAILABLE, true));
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID confirmation = notification(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(1), 4);

        dispatcher.dispatchDue();
        dispatcher.dispatchDue();

        Map<String, Object> row = row(confirmation);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("attempts")).isEqualTo(5);
        assertThat(output.getAll()).contains("WARN").contains(confirmation.toString()).contains("SMTP_UNAVAILABLE");
    }

    @Test
    void proveedorRecuperadoEnviaYQuedaSent() {
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID confirmation = notification(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(1), 2);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).hasSize(1);
        assertThat(row(confirmation).get("status")).isEqualTo("SENT");
    }

    @Test
    void destinatarioRechazadoQuedaFailedEnElPrimerIntentoSinReprogramar() {
        sender.failWith(new EmailSendException(NotificationErrorCode.SMTP_REJECTED, false));
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID confirmation = notification(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(1), 0);

        dispatcher.dispatchDue();
        dispatcher.dispatchDue();

        Map<String, Object> row = row(confirmation);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("last_error")).isEqualTo("SMTP_REJECTED");
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void unaExcepcionInesperadaEsUnFalloTransitorioConCodigoUnexpected() {
        sender.failWith(new IllegalStateException("fallo raro"));
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID confirmation = notification(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(1), 0);

        dispatcher.dispatchDue();

        Map<String, Object> row = row(confirmation);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("last_error")).isEqualTo("UNEXPECTED");
    }

    @Test
    void unaNotificacionQueFallaNoImpideDespacharLasSiguientes() {
        UUID first = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID second = appointment("2026-12-03T13:00:00Z", "CONFIRMED");
        UUID failing = notification(first, NotificationType.CONFIRMATION, NOW.minusSeconds(120), 0);
        UUID ok = notification(second, NotificationType.CONFIRMATION, NOW.minusSeconds(60), 0);
        sender.beforeSend(email -> {
            if (email.appointmentId().equals(first)) {
                throw new EmailSendException(NotificationErrorCode.SMTP_UNAVAILABLE, true);
            }
        });

        dispatcher.dispatchDue();

        assertThat(row(failing).get("status")).isEqualTo("PENDING");
        assertThat(row(ok).get("status")).isEqualTo("SENT");
    }

    // ---- concurrencia ----

    @Test
    void dosDespachadoresEnParaleloEnvianCadaNotificacionUnaSolaVez() throws Exception {
        List<UUID> notifications = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID appointment = appointment(Instant.parse("2026-12-02T09:00:00Z").plus(Duration.ofHours(i)).toString(),
                    "CONFIRMED");
            notifications.add(notification(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(600 - i), 0));
        }
        sender.beforeSend(email -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    dispatcher.dispatchDue();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Set<UUID> sentIds = new HashSet<>();
        sender.sent().forEach(email -> sentIds.add(email.notificationId()));
        assertThat(sender.sent()).hasSize(10);
        assertThat(sentIds).hasSize(10).containsAll(notifications);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE status = 'SENT'", Integer.class))
                .isEqualTo(10);
    }

    // ---- privacidad ----

    @Test
    void elErrorDelProveedorConLaDireccionNoLlegaALastErrorNiAlLog(CapturedOutput output) {
        sender.failWith(new IllegalStateException("550 5.1.1 <" + PATIENT_EMAIL + "> no existe"));
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        UUID confirmation = notification(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(1), 0);

        dispatcher.dispatchDue();

        assertThat(row(confirmation).get("last_error")).isEqualTo("UNEXPECTED");
        assertThat(output.getAll()).doesNotContain(PATIENT_EMAIL).doesNotContain("no existe");
    }

    @Test
    void elEmailNoIncluyeNotasNiNombreDniOTelefonoDelPaciente(CapturedOutput output) {
        AppointmentFixtures.execute("UPDATE patient SET first_name = 'Rodolfa', last_name = 'Zuberbuhler' "
                + "WHERE id = '" + patient + "'");
        UUID appointment = appointment("2026-12-02T13:00:00Z", "CONFIRMED");
        AppointmentFixtures.execute("UPDATE appointment SET notes = 'nota clinica reservada' WHERE id = '"
                + appointment + "'");
        Map<String, Object> person = jdbc.queryForMap("SELECT first_name, last_name, dni, phone FROM patient "
                + "WHERE id = ?", patient);
        notification(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(1), 0);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).hasSize(1);
        OutgoingEmail email = sender.sent().get(0);
        String text = email.subject() + "\n" + email.body();
        assertThat(text)
                .doesNotContain("nota clinica reservada")
                .doesNotContain((String) person.get("first_name"))
                .doesNotContain((String) person.get("last_name"))
                .doesNotContain((String) person.get("dni"))
                .doesNotContain((String) person.get("phone"))
                .doesNotContain(PATIENT_EMAIL);
        assertThat(output.getAll()).doesNotContain(PATIENT_EMAIL).doesNotContain(email.body());
        assertThat(email.body()).contains("Para cancelar o reprogramar, comunicate con el consultorio al 351-5551234")
                .doesNotContain("/turno/");
    }
}
