package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Proteccion de {@code /api/v1/appointments/**}: sesion DENTIST, deny-by-default y CSRF en mutaciones. */
class AppointmentsSecurityIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/appointments";
    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private static final String EXPIRES_AT =
            "ar.turnosodontologia.consultorio.auth.infrastructure.SessionTimeout.expiresAt";
    private static final String FROM = "2099-12-10T00:00:00Z";
    private static final String TO = "2099-12-11T00:00:00Z";

    private final WebApplicationContext context;
    private final JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private UUID appointmentId;

    AppointmentsSecurityIT(WebApplicationContext context, JdbcTemplate jdbc) {
        this.context = context;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        UUID patient = AppointmentFixtures.patient(true);
        UUID service = AppointmentFixtures.service(30, true, true);
        appointmentId = AppointmentFixtures.appointment(patient, service, "2099-12-10T13:00:00Z",
                "2099-12-10T13:30:00Z", "CONFIRMED");
    }

    @AfterEach
    void tearDown() {
        AppointmentFixtures.cleanAll();
    }

    private static final String VALID_BODY = "{\"startsAt\":\"2099-12-10T15:00:00Z\"}";

    private List<String> postPaths() {
        return List.of(BASE, BASE + "/" + appointmentId + "/cancel", BASE + "/" + appointmentId + "/reschedule",
                BASE + "/" + appointmentId + "/complete", BASE + "/" + appointmentId + "/no-show");
    }

    @Test
    void consultasAnonimasDevuelven401SinRevelarSiElTurnoExiste() throws Exception {
        MockHttpSession anonymous = new MockHttpSession();

        mockMvc.perform(get(BASE + "?from=" + FROM + "&to=" + TO).session(anonymous))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(BASE + "/" + appointmentId).session(anonymous))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID()).session(anonymous))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void mutacionesAnonimasDevuelven401YNoCreanNiModificanTurnos() throws Exception {
        MockHttpSession anonymous = new MockHttpSession();
        String csrf = injectCsrf(anonymous);

        for (String path : postPaths()) {
            mockMvc.perform(post(path).session(anonymous).header("X-XSRF-TOKEN", csrf)
                            .cookie(new Cookie("XSRF-TOKEN", csrf)).contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM appointment", String.class)).isEqualTo("CONFIRMED");
    }

    @Test
    void rolDistintoDeDentistDevuelve403EnConsultasYMutaciones() throws Exception {
        MockHttpSession other = session("ROLE_PATIENT");
        String csrf = injectCsrf(other);

        mockMvc.perform(get(BASE + "?from=" + FROM + "&to=" + TO).session(other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        for (String path : postPaths()) {
            mockMvc.perform(post(path).session(other).header("X-XSRF-TOKEN", csrf)
                            .cookie(new Cookie("XSRF-TOKEN", csrf)).contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        assertThat(jdbc.queryForObject("SELECT status FROM appointment", String.class)).isEqualTo("CONFIRMED");
    }

    @Test
    void cadaMutacionSinTokenCsrfDevuelve403CsrfRequiredYElTurnoNoCambia() throws Exception {
        MockHttpSession dentist = session("ROLE_DENTIST");
        injectCsrf(dentist);

        for (String path : postPaths()) {
            mockMvc.perform(post(path).session(dentist).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM appointment", String.class)).isEqualTo("CONFIRMED");
    }

    @Test
    void consultasAutenticadasNoExigenCsrf() throws Exception {
        MockHttpSession dentist = session("ROLE_DENTIST");

        mockMvc.perform(get(BASE + "?from=" + FROM + "&to=" + TO).session(dentist))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + appointmentId).session(dentist))
                .andExpect(status().isOk());
    }

    private static MockHttpSession session(String authority) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), Role.DENTIST, false);
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority(authority))));
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
