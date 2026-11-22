package ar.turnosodontologia.consultorio.pacientes.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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

class PatientsEndpointsIT extends AbstractIntegrationTest {

    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");

    private final WebApplicationContext context;
    private MockMvc mockMvc;

    PatientsEndpointsIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM patient");
        }
    }

    private MockHttpSession authenticatedDentistSession() throws SQLException {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        AuthenticatedUser user = new AuthenticatedUser(
                bootstrapUserId(), UUID.randomUUID(), Role.DENTIST, false);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_DENTIST")));
        securityContext.setAuthentication(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.setAttribute("ar.turnosodontologia.consultorio.auth.infrastructure.SessionTimeout.expiresAt",
                Instant.now().plusSeconds(3600).toEpochMilli());
        return session;
    }

    private String injectCsrf(MockHttpSession session) {
        String tokenValue = UUID.randomUUID().toString();
        CsrfToken csrfToken = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", tokenValue);
        session.setAttribute(CSRF_ATTR, csrfToken);
        return tokenValue;
    }

    @Test
    void getPatientsSinAutenticacionDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void postPatientSinCsrfDevuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .session(authenticatedDentistSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(uniqueDni())))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutacionesSinAutenticacionSonRechazadas() throws Exception {
        UUID id = UUID.randomUUID();
        for (var request : List.of(
                post("/api/v1/patients").contentType(MediaType.APPLICATION_JSON).content(createPayload(uniqueDni())),
                put("/api/v1/patients/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload()),
                delete("/api/v1/patients/" + id))) {
            int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
            assertThat(status).isIn(401, 403);
        }
    }

    @Test
    void cicloCompletoDeFichaConBajaLogica() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);
        String dni = uniqueDni();

        MvcResult createdResult = mockMvc.perform(post("/api/v1/patients")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(dni)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dni").value(dni))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        String patientId = createdResult.getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/patients").param("q", dni).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(patientId));

        mockMvc.perform(get("/api/v1/patients/" + patientId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Juan"))
                .andExpect(jsonPath("$.phone").value("11223344"));

        mockMvc.perform(put("/api/v1/patients/" + patientId)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Juan Carlos"))
                .andExpect(jsonPath("$.phone").value("99887766"));

        mockMvc.perform(delete("/api/v1/patients/" + patientId)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/patients").param("q", dni).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/patients/" + patientId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void crearConDniDuplicadoDevuelve409() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);
        String dni = uniqueDni();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/patients")
                            .session(session)
                            .header("X-XSRF-TOKEN", csrfToken)
                            .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createPayload(dni)))
                    .andExpect(attempt == 0 ? status().isCreated() : status().isConflict());
        }
    }

    @Test
    void pacienteInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/patients/" + UUID.randomUUID())
                        .session(authenticatedDentistSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    }

    @Test
    void crearConCamposInvalidosDevuelve400() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        String invalidPayload = """
                {
                    "firstName": "  ",
                    "lastName": "Perez",
                    "dni": "%s",
                    "phone": "11223344"
                }
                """.formatted(uniqueDni());

        mockMvc.perform(post("/api/v1/patients")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void crearConFechaDeNacimientoInvalidaDevuelve400() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        String invalidPayload = """
                {
                    "firstName": "Juan",
                    "lastName": "Perez",
                    "dni": "%s",
                    "phone": "11223344",
                    "birthDate": "20-05-1990"
                }
                """.formatted(uniqueDni());

        mockMvc.perform(post("/api/v1/patients")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /** La auditoria exige un user_id existente en app_user (FK de audit_log). */
    private static UUID bootstrapUserId() throws SQLException {
        try (Connection connection = DATABASE.appConnection();
                PreparedStatement query = connection.prepareStatement("SELECT id FROM app_user WHERE email = ?")) {
            query.setString(1, BOOTSTRAP_EMAIL);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("No existe el usuario bootstrap de pruebas");
                }
                return UUID.fromString(rows.getString(1));
            }
        }
    }

    private static String createPayload(String dni) {
        return """
                {
                    "firstName": "Juan",
                    "lastName": "Perez",
                    "dni": "%s",
                    "phone": "11223344",
                    "email": "juan@test.com",
                    "birthDate": "1990-05-20",
                    "address": "Calle 1"
                }
                """.formatted(dni);
    }

    private static String updatePayload() {
        return """
                {
                    "firstName": "Juan Carlos",
                    "lastName": "Perez",
                    "phone": "99887766",
                    "email": "nuevo@test.com",
                    "birthDate": "1985-01-01",
                    "address": "Calle 2"
                }
                """;
    }

    private static String uniqueDni() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
    }
}
