package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.notificaciones.domain.Notification;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationErrorCode;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationRepository;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Un fallo del encolado jamas cambia la respuesta ni revierte el turno (RN-NO-05, D3). */
@ExtendWith(OutputCaptureExtension.class)
@Import(NotificationFailureIsolationIT.FailingRepositoryConfig.class)
class NotificationFailureIsolationIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/appointments";
    private static final String LEAKED_EMAIL = "paciente.secreto@ejemplo.com";
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate DAY = LocalDate.of(2099, 12, 10);
    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private static final String EXPIRES_AT =
            "ar.turnosodontologia.consultorio.auth.infrastructure.SessionTimeout.expiresAt";

    /** Repositorio que siempre falla, con un mensaje que incluye un email para comprobar que no llega al log. */
    static class FailingNotificationRepository implements NotificationRepository {

        private static RuntimeException boom() {
            return new IllegalStateException("la base fallo para " + LEAKED_EMAIL);
        }

        @Override
        public void enqueueIfAbsent(UUID practiceId, UUID appointmentId, NotificationType type, Instant scheduledFor,
                Instant now) {
            throw boom();
        }

        @Override
        public void upsertPending(UUID practiceId, UUID appointmentId, NotificationType type, Instant scheduledFor,
                Instant now) {
            throw boom();
        }

        @Override
        public int discardPendingForCancellation(UUID appointmentId, NotificationErrorCode code, Instant now) {
            throw boom();
        }

        @Override
        public int discardPendingOfType(UUID appointmentId, NotificationType type, NotificationErrorCode code,
                Instant now) {
            throw boom();
        }

        @Override
        public Optional<Notification> claimNextDue(Instant now) {
            throw boom();
        }

        @Override
        public Optional<Notification> findByAppointmentIdAndType(UUID appointmentId, NotificationType type) {
            throw boom();
        }

        @Override
        public <S extends Notification> S save(S notification) {
            throw boom();
        }
    }

    @TestConfiguration
    static class FailingRepositoryConfig {
        @Bean
        @Primary
        NotificationRepository failingNotificationRepository() {
            return new FailingNotificationRepository();
        }
    }

    private final WebApplicationContext context;
    private final JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private MockHttpSession session;
    private String csrf;
    private UUID patient;
    private UUID serviceId;

    NotificationFailureIsolationIT(WebApplicationContext context, JdbcTemplate jdbc) {
        this.context = context;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        session = session();
        csrf = injectCsrf(session);
        patient = AppointmentFixtures.patient(true);
        serviceId = AppointmentFixtures.service(30, true, true);
        AppointmentFixtures.workingHours(DAY.getDayOfWeek().getValue(), "09:00", "13:00");
    }

    @AfterEach
    void tearDown() {
        AppointmentFixtures.cleanAll();
    }

    private static String at(String time) {
        return LocalDateTime.of(DAY, LocalTime.parse(time)).atZone(ZONE).toInstant().toString();
    }

    private MvcResult postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).session(session).header("X-XSRF-TOKEN", csrf)
                .cookie(new Cookie("XSRF-TOKEN", csrf)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
    }

    private UUID book(String time) throws Exception {
        MvcResult result = postJson(BASE, "{\"patientId\":\"" + patient + "\",\"serviceId\":\"" + serviceId
                + "\",\"startsAt\":\"" + at(time) + "\"}");
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM appointment WHERE id = ?", String.class, id);
    }

    @Test
    void siElEncoladoFallaAlCrearElTurnoLaApiResponde201YElTurnoQuedaConfirmado(CapturedOutput output)
            throws Exception {
        UUID id = book("10:00");

        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
        assertThat(output.getAll())
                .contains("Error al encolar")
                .contains(id.toString())
                .doesNotContain(LEAKED_EMAIL);
    }

    @Test
    void siElEncoladoFallaAlCancelarLaApiResponde200YElTurnoQuedaCancelado(CapturedOutput output) throws Exception {
        UUID id = book("11:00");

        MvcResult cancelled = postJson(BASE + "/" + id + "/cancel", "{}");

        assertThat(cancelled.getResponse().getStatus()).as(cancelled.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(output.getAll())
                .contains("Error al encolar")
                .doesNotContain(LEAKED_EMAIL);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isZero();
    }

    @Test
    void elLogDelErrorMuestraElTipoDeEventoYLaClaseDeLaExcepcionPeroNoSuMensaje(CapturedOutput output)
            throws Exception {
        book("12:00");

        assertThat(output.getAll())
                .contains("AppointmentConfirmed")
                .contains("IllegalStateException")
                .doesNotContain("la base fallo para");
    }

    private static MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), Role.DENTIST, false);
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_DENTIST"))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.setAttribute(EXPIRES_AT, Instant.now().plusSeconds(3600).toEpochMilli());
        return session;
    }

    private static String injectCsrf(MockHttpSession session) {
        String tokenValue = UUID.randomUUID().toString();
        CsrfToken csrfToken = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", tokenValue);
        session.setAttribute(CSRF_ATTR, csrfToken);
        return tokenValue;
    }
}
