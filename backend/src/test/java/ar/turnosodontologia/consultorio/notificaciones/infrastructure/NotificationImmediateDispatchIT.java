package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ar.turnosodontologia.consultorio.ConsultorioApplication;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentResult;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentService;
import ar.turnosodontologia.consultorio.agenda.application.BookAppointmentCommand;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig.ControllableEmailSender;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig.MutableClock;
import ar.turnosodontologia.consultorio.notificaciones.domain.EmailSendException;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationErrorCode;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import ar.turnosodontologia.consultorio.support.EmbeddedPostgresSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * Con el despacho ACTIVO (a diferencia del resto de los ITs): el disparo inmediato tras el commit envia la
 * confirmacion sin esperar el poll, y un proveedor caido no afecta al turno (appointment-notifications, D14).
 */
@SpringBootTest(classes = ConsultorioApplication.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
@Import(NotificationTestConfig.class)
// Contexto propio y descartable: su poll periodico no debe sobrevivir y despachar filas de otros tests.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationImmediateDispatchIT {

    private static final EmbeddedPostgresSupport.Database DATABASE = EmbeddedPostgresSupport.shared();
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate DAY = LocalDate.of(2099, 12, 10);
    private static final String PATIENT_EMAIL = "ana.inmediato@ejemplo.com";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        DATABASE.properties().forEach((name, value) -> {
            if (!"NOTIFICATION_DISPATCH_ENABLED".equals(name)) {
                registry.add(name, () -> value);
            }
        });
        registry.add("NOTIFICATION_DISPATCH_ENABLED", () -> "true");
        // El poll queda muy lejos: lo que se envie dentro del test lo envia el disparo inmediato.
        registry.add("NOTIFICATION_POLL_INTERVAL", () -> "PT1H");
    }

    private final AppointmentService service;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final ControllableEmailSender sender;
    private UUID patient;
    private UUID serviceId;

    NotificationImmediateDispatchIT(AppointmentService service, JdbcTemplate jdbc, MutableClock clock,
            ControllableEmailSender sender) {
        this.service = service;
        this.jdbc = jdbc;
        this.clock = clock;
        this.sender = sender;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        clock.reset();
        sender.reset();
        patient = AppointmentFixtures.patient(true);
        AppointmentFixtures.execute("UPDATE patient SET email = '" + PATIENT_EMAIL + "' WHERE id = '" + patient + "'");
        serviceId = AppointmentFixtures.service(30, true, true);
        AppointmentFixtures.workingHours(DAY.getDayOfWeek().getValue(), "09:00", "17:00");
    }

    @AfterEach
    void tearDown() {
        sender.reset();
        AppointmentFixtures.cleanAll();
        AppointmentFixtures.execute("DELETE FROM patient WHERE id = '" + patient + "'");
    }

    private AppointmentResult book() {
        Instant startsAt = DAY.atTime(LocalTime.of(10, 0)).atZone(ZONE).toInstant();
        return service.book(new BookAppointmentCommand(patient, serviceId, startsAt, false, null));
    }

    private Map<String, Object> confirmation(UUID appointment) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status, attempts, scheduled_for, last_error FROM "
                + "notification WHERE appointment_id = ? AND type = 'CONFIRMATION'", appointment);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Test
    void laConfirmacionSeEnviaSinEsperarElPollDespuesDeConfirmarElTurno() {
        AppointmentResult created = book();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(sender.sent()).hasSize(1);
            assertThat(confirmation(created.id()).get("status")).isEqualTo("SENT");
        });
        assertThat(sender.sent().get(0).to()).isEqualTo(PATIENT_EMAIL);
        assertThat(sender.sent().get(0).appointmentId()).isEqualTo(created.id());
    }

    @Test
    void conElProveedorCaidoLaApiConfirmaElTurnoYLaConfirmacionQuedaPendienteConUnIntentoFallido() {
        sender.failWith(new EmailSendException(NotificationErrorCode.SMTP_UNAVAILABLE, true));
        Instant before = Instant.now();

        AppointmentResult created = book();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(
                () -> assertThat(confirmation(created.id()).get("attempts")).isEqualTo(1));
        Map<String, Object> row = confirmation(created.id());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("last_error")).isEqualTo("SMTP_UNAVAILABLE");
        Instant next = ((Timestamp) row.get("scheduled_for")).toInstant();
        assertThat(next).isAfter(before.plus(Duration.ofMinutes(4))).isBefore(Instant.now().plus(Duration.ofMinutes(6)));
        assertThat(jdbc.queryForObject("SELECT status FROM appointment WHERE id = ?", String.class, created.id()))
                .isEqualTo("CONFIRMED");
        assertThat(sender.sent()).isEmpty();
    }
}
