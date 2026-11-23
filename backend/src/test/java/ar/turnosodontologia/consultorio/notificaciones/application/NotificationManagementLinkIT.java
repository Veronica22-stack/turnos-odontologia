package ar.turnosodontologia.consultorio.notificaciones.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig.ControllableEmailSender;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig.MutableClock;
import ar.turnosodontologia.consultorio.notificaciones.api.ManagementLinkProvider;
import ar.turnosodontologia.consultorio.notificaciones.api.NotificationTypeView;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import ar.turnosodontologia.consultorio.notificaciones.domain.OutgoingEmail;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Con un emisor de tokens (C-14) el link se arma al enviar y nunca se persiste ni se loguea (design D11). */
@ExtendWith(OutputCaptureExtension.class)
@Import({NotificationTestConfig.class, NotificationManagementLinkIT.ProviderConfig.class})
class NotificationManagementLinkIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final String TOKEN = "abc123";

    /** Emisor de prueba: devuelve siempre el mismo token y registra para que turnos y tipos se le pidio. */
    static class RecordingProvider implements ManagementLinkProvider {
        final List<String> calls = new CopyOnWriteArrayList<>();
        volatile boolean hasToken = true;

        @Override
        public Optional<String> tokenFor(UUID appointmentId, NotificationTypeView type) {
            calls.add(appointmentId + ":" + type);
            return hasToken ? Optional.of(TOKEN) : Optional.empty();
        }
    }

    @TestConfiguration
    static class ProviderConfig {
        @Bean
        @org.springframework.context.annotation.Primary
        RecordingProvider recordingProvider() {
            return new RecordingProvider();
        }
    }

    private final NotificationDispatcher dispatcher;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final ControllableEmailSender sender;
    private final RecordingProvider provider;
    private UUID patient;
    private UUID serviceId;

    NotificationManagementLinkIT(NotificationDispatcher dispatcher, JdbcTemplate jdbc, MutableClock clock,
            ControllableEmailSender sender, RecordingProvider provider) {
        this.dispatcher = dispatcher;
        this.jdbc = jdbc;
        this.clock = clock;
        this.sender = sender;
        this.provider = provider;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        clock.set(NOW);
        sender.reset();
        provider.calls.clear();
        provider.hasToken = true;
        patient = AppointmentFixtures.patient(true);
        AppointmentFixtures.execute("UPDATE patient SET email = 'ana.link@ejemplo.com' WHERE id = '" + patient + "'");
        serviceId = AppointmentFixtures.service(30, true, true);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        sender.reset();
        AppointmentFixtures.cleanAll();
    }

    private UUID dueNotification(NotificationType type) {
        UUID appointment = AppointmentFixtures.appointment(patient, serviceId, "2026-12-02T13:00:00Z",
                "2026-12-02T13:30:00Z", "CONFIRMED");
        AppointmentFixtures.execute("INSERT INTO notification (id, practice_id, appointment_id, type, scheduled_for) "
                + "VALUES ('" + UUID.randomUUID() + "', '" + AppointmentFixtures.PRACTICE_ID + "', '" + appointment
                + "', '" + type.name() + "', '" + NOW.minusSeconds(60) + "')");
        return appointment;
    }

    @Test
    void conEmisorDeTokensElCuerpoLlevaElLinkYNiLaFilaNiElLogLoContienen(CapturedOutput output) {
        UUID appointment = dueNotification(NotificationType.CONFIRMATION);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).hasSize(1);
        assertThat(sender.sent().get(0).body()).contains("https://turnos.ejemplo.com/turno/" + TOKEN);
        assertThat(provider.calls).containsExactly(appointment + ":CONFIRMATION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE last_error LIKE ?", Integer.class,
                "%" + TOKEN + "%")).isZero();
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void siElEmisorNoTieneTokenParaElTurnoElCuerpoOmiteElLink() {
        provider.hasToken = false;
        dueNotification(NotificationType.REMINDER);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).hasSize(1);
        OutgoingEmail email = sender.sent().get(0);
        assertThat(email.body()).doesNotContain("/turno/").contains("comunicate con el consultorio");
    }

    @Test
    void lasCancelacionesNoPidenTokenAlEmisor() {
        dueNotification(NotificationType.CANCELLATION);

        dispatcher.dispatchDue();

        assertThat(sender.sent()).hasSize(1);
        assertThat(provider.calls).isEmpty();
        assertThat(sender.sent().get(0).body()).doesNotContain("/turno/");
    }
}
