package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

class WorkingHoursEndpointsIT extends AbstractIntegrationTest {

    private final WebApplicationContext context;
    private MockMvc mockMvc;

    WorkingHoursEndpointsIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private MockHttpSession authenticatedDentistSession() {
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

    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");

    private String injectCsrf(MockHttpSession session) {
        String tokenValue = UUID.randomUUID().toString();
        CsrfToken csrfToken = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", tokenValue);
        session.setAttribute(CSRF_ATTR, csrfToken);
        return tokenValue;
    }

    @Test
    void getWorkingHoursSinAutenticacionDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/working-hours")).andExpect(status().isUnauthorized());
    }

    @Test
    void putWorkingHoursSinCsrfDevuelve403() throws Exception {
        MockHttpSession session = authenticatedDentistSession();

        String jsonPayload = """
                {
                    "workingHours": []
                }
                """;

        mockMvc.perform(put("/api/v1/working-hours")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void getYPutWorkingHoursConAutenticacionYCsrfFuncionaCorrectamente() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        String jsonPayload = """
                {
                    "workingHours": [
                        { "weekday": 1, "startTime": "09:00:00", "endTime": "13:00:00" },
                        { "weekday": 1, "startTime": "14:00:00", "endTime": "18:00:00" }
                    ]
                }
                """;

        MvcResult result = mockMvc.perform(put("/api/v1/working-hours")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("09:00:00");
        assertThat(result.getResponse().getContentAsString()).contains("18:00:00");

        MvcResult getResult = mockMvc.perform(get("/api/v1/working-hours")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(getResult.getResponse().getContentAsString()).contains("09:00:00");
        assertThat(getResult.getResponse().getContentAsString()).contains("14:00:00");
    }

    @Test
    void getWorkingHoursSinTramosDevuelveListaVacia() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        mockMvc.perform(put("/api/v1/working-hours")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workingHours\": []}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/working-hours").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void putWorkingHoursSinAutenticacionEsRechazado() throws Exception {
        int status = mockMvc.perform(put("/api/v1/working-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workingHours\": []}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    void putWorkingHoursConHoraFinAnteriorAInicioDevuelve400() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        String jsonPayload = """
                {
                    "workingHours": [
                        { "weekday": 1, "startTime": "13:00:00", "endTime": "09:00:00" }
                    ]
                }
                """;

        mockMvc.perform(put("/api/v1/working-hours")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void putWorkingHoursConSolapamientoDevuelve400() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        String jsonPayload = """
                {
                    "workingHours": [
                        { "weekday": 1, "startTime": "09:00:00", "endTime": "13:00:00" },
                        { "weekday": 1, "startTime": "12:00:00", "endTime": "16:00:00" }
                    ]
                }
                """;

        mockMvc.perform(put("/api/v1/working-hours")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
