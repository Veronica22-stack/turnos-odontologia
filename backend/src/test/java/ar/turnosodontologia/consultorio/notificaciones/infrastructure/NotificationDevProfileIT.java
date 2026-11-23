package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ar.turnosodontologia.consultorio.ConsultorioApplication;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentResult;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentService;
import ar.turnosodontologia.consultorio.agenda.application.BookAppointmentCommand;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import ar.turnosodontologia.consultorio.support.EmbeddedPostgresSupport;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * Arranque real con el perfil {@code dev} (sin definir {@code MAIL_PROVIDER}), sin dobles de prueba: el perfil aporta
 * {@code log}, el despacho inmediato "envia" y el log muestra solo identificadores, tipo y plantilla (runtime-configuration
 * "Proveedor por defecto en dev", appointment-notifications "Envio en desarrollo").
 */
@SpringBootTest(classes = ConsultorioApplication.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationDevProfileIT {

    private static final EmbeddedPostgresSupport.Database DATABASE = EmbeddedPostgresSupport.shared();
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate DAY = LocalDate.of(2099, 12, 10);
    private static final String PATIENT_EMAIL = "ana.dev@ejemplo.com";
    private static final Set<String> NOT_SET_BY_DEV_PROFILE = Set.of("MAIL_PROVIDER", "NOTIFICATION_DISPATCH_ENABLED");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        DATABASE.properties().forEach((name, value) -> {
            if (!NOT_SET_BY_DEV_PROFILE.contains(name)) {
                registry.add(name, () -> value);
            }
        });
        registry.add("NOTIFICATION_POLL_INTERVAL", () -> "PT1H");
    }

    private final AppointmentService service;
    private final JdbcTemplate jdbc;
    private UUID patient;
    private UUID serviceId;

    NotificationDevProfileIT(AppointmentService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        patient = AppointmentFixtures.patient(true);
        AppointmentFixtures.execute("UPDATE patient SET email = '" + PATIENT_EMAIL + "' WHERE id = '" + patient + "'");
        serviceId = AppointmentFixtures.service(30, true, true);
        AppointmentFixtures.workingHours(DAY.getDayOfWeek().getValue(), "09:00", "17:00");
    }

    @AfterEach
    void tearDown() {
        AppointmentFixtures.cleanAll();
        AppointmentFixtures.execute("DELETE FROM patient WHERE id = '" + patient + "'");
    }

    @Test
    void conElPerfilDevLaConfirmacionSeSimulaYElLogSoloTieneMetadatos(CapturedOutput output) {
        Instant startsAt = DAY.atTime(LocalTime.of(10, 0)).atZone(ZONE).toInstant();

        AppointmentResult created = service.book(new BookAppointmentCommand(patient, serviceId, startsAt, false, null));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT status FROM notification WHERE appointment_id = ? AND type = 'CONFIRMATION'", String.class,
                created.id())).isEqualTo("SENT"));
        String log = output.getAll();
        assertThat(log).contains("email simulado").contains(created.id().toString()).contains("type=CONFIRMATION")
                .contains("template=confirmation");
        assertThat(log).doesNotContain(PATIENT_EMAIL).doesNotContain("Tu turno quedó confirmado");
    }
}
