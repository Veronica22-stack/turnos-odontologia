package ar.turnosodontologia.consultorio.agenda.infrastructure;

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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

class ServicesEndpointsIT extends AbstractIntegrationTest {

    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");

    private final WebApplicationContext context;
    private MockMvc mockMvc;

    ServicesEndpointsIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM service");
        }
    }

    @Test
    void postResponde201ConLaPrestacionCreada() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        MvcResult result = mockMvc.perform(post("/api/v1/services")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Dolor dental", 45, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Dolor dental"))
                .andExpect(jsonPath("$.description").value("Descripcion de Dolor dental"))
                .andExpect(jsonPath("$.durationMin").value(45))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.publicVisible").value(true))
                .andReturn();

        assertThat(result.getResponse().getHeader("Location")).contains("/api/v1/services/");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("practice_id");
    }

    @Test
    void cicloCompletoDePrestacionConBajaLogicaIdempotenteYListadoConInactivas() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);
        String id = createService("Dolor dental", 45, false, session, csrfToken);

        mockMvc.perform(get("/api/v1/services/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dolor dental"));

        mockMvc.perform(get("/api/v1/services").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].active").value(true));

        mockMvc.perform(put("/api/v1/services/" + id)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("Dolor dental", 60, false, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMin").value(60));

        mockMvc.perform(delete("/api/v1/services/" + id)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken)))
                .andExpect(status().isNoContent());

        // Baja repetida: idempotente.
        mockMvc.perform(delete("/api/v1/services/" + id)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/services/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/services").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void putConActiveTrueReactiva() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);
        String id = createService("Dolor dental", 45, false, session, csrfToken);

        mockMvc.perform(delete("/api/v1/services/" + id)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/v1/services/" + id)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("Dolor dental", 45, false, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/v1/services/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void crearConNombreVacioDevuelve400() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        mockMvc.perform(post("/api/v1/services")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("   ", 30, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void crearConDuracionCeroDevuelve400() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        mockMvc.perform(post("/api/v1/services")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Dolor", 0, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void duracionNoMultiploEnAltaDevuelve422() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        mockMvc.perform(post("/api/v1/services")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Dolor", 20, false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DURATION_NOT_MULTIPLE_OF_GRANULARITY"));
    }

    @Test
    void duracionNoMultiploEnEdicionDevuelve422YConservaElValorAnterior() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);
        String id = createService("Dolor dental", 45, false, session, csrfToken);

        mockMvc.perform(put("/api/v1/services/" + id)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("Dolor dental", 20, false, true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DURATION_NOT_MULTIPLE_OF_GRANULARITY"));

        mockMvc.perform(get("/api/v1/services/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMin").value(45));
    }

    @Test
    void idInexistenteDevuelve404() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);
        String missing = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/services/" + missing).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/services/" + missing)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("Dolor", 45, false, true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/services/" + missing)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
    }

    @Test
    void sinAutenticacionResponde401O403() throws Exception {
        UUID missing = UUID.randomUUID();
        for (var request : List.of(
                get("/api/v1/services"),
                get("/api/v1/services/" + missing),
                post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Dolor", 45, false)),
                put("/api/v1/services/" + missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("Dolor", 45, false, true)),
                delete("/api/v1/services/" + missing))) {
            int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
            assertThat(status).isIn(401, 403);
        }
    }

    @Test
    void mutacionesSinCsrfDevuelven403() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String id = createService("Dolor dental", 45, false, session, injectCsrf(session));

        mockMvc.perform(post("/api/v1/services")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Otro", 30, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));

        mockMvc.perform(put("/api/v1/services/" + id)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("Otro", 30, false, true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));

        mockMvc.perform(delete("/api/v1/services/" + id).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));
    }

    private String createService(
            String name, int durationMin, boolean publicVisible, MockHttpSession session, String csrfToken)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/services")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(name, durationMin, publicVisible)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private static MockHttpSession authenticatedDentistSession() {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), Role.DENTIST, false);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_DENTIST")));
        securityContext.setAuthentication(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.setAttribute("ar.turnosodontologia.consultorio.auth.infrastructure.SessionTimeout.expiresAt",
                Instant.now().plusSeconds(3600).toEpochMilli());
        return session;
    }

    private static String injectCsrf(MockHttpSession session) {
        String tokenValue = UUID.randomUUID().toString();
        CsrfToken csrfToken = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", tokenValue);
        session.setAttribute(CSRF_ATTR, csrfToken);
        return tokenValue;
    }

    private static String createPayload(String name, int durationMin, boolean publicVisible) {
        return """
                {
                    "name": "%s",
                    "description": "Descripcion de %s",
                    "durationMin": %d,
                    "publicVisible": %s
                }
                """.formatted(name, name, durationMin, publicVisible);
    }

    private static String updatePayload(String name, int durationMin, boolean publicVisible, boolean active) {
        return """
                {
                    "name": "%s",
                    "description": "Descripcion actualizada",
                    "durationMin": %d,
                    "publicVisible": %s,
                    "active": %s
                }
                """.formatted(name, durationMin, publicVisible, active);
    }
}
