package ar.turnosodontologia.consultorio.shared.infrastructure;

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

class PracticeSettingsEndpointsIT extends AbstractIntegrationTest {

    private final WebApplicationContext context;
    private MockMvc mockMvc;

    PracticeSettingsEndpointsIT(WebApplicationContext context) {
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
    void getSettingsSinAutenticacionDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/practice/settings")).andExpect(status().isUnauthorized());
    }

    @Test
    void putSettingsSinCsrfDevuelve403() throws Exception {
        MockHttpSession session = authenticatedDentistSession();

        String jsonPayload = """
                {
                    "name": "Consultorio",
                    "timezone": "America/Argentina/Buenos_Aires",
                    "slotGranularityMin": 15,
                    "minNoticeMin": 120,
                    "maxAdvanceDays": 30,
                    "reminderHoursBefore": 24
                }
                """;

        mockMvc.perform(put("/api/v1/practice/settings")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void getYPutSettingsConAutenticacionYCsrfFuncionaCorrectamente() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        String jsonPayload = """
                {
                    "name": "Consultorio Modificado",
                    "timezone": "America/Argentina/Buenos_Aires",
                    "contactEmail": "contacto@modificado.com",
                    "contactPhone": "123456",
                    "address": "Calle 1",
                    "slotGranularityMin": 20,
                    "minNoticeMin": 90,
                    "maxAdvanceDays": 45,
                    "reminderHoursBefore": 12
                }
                """;

        MvcResult result = mockMvc.perform(put("/api/v1/practice/settings")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("Consultorio Modificado");
        assertThat(result.getResponse().getContentAsString()).contains("contacto@modificado.com");

        MvcResult getResult = mockMvc.perform(get("/api/v1/practice/settings")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(getResult.getResponse().getContentAsString()).contains("Consultorio Modificado");
        assertThat(getResult.getResponse().getContentAsString()).contains("45");
    }

    @Test
    void putSettingsSinAutenticacionEsRechazado() throws Exception {
        int status = mockMvc.perform(put("/api/v1/practice/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsPayload(15, 120, 30, 24)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    void putSettingsConGranularidadNoPermitidaDevuelve400() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        assertInvalidPut(session, csrfToken, settingsPayload(25, 120, 30, 24));
    }

    @Test
    void putSettingsConAnticipacionMinimaNegativaDevuelve400() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        assertInvalidPut(session, csrfToken, settingsPayload(15, -1, 30, 24));
    }

    @Test
    void putSettingsConHorizonteMaximoFueraDeRangoDevuelve400() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        assertInvalidPut(session, csrfToken, settingsPayload(15, 120, 400, 24));
        assertInvalidPut(session, csrfToken, settingsPayload(15, 120, 0, 24));
    }

    @Test
    void putSettingsConRecordatorioFueraDeRangoDevuelve400() throws Exception {
        MockHttpSession session = authenticatedDentistSession();
        String csrfToken = injectCsrf(session);

        assertInvalidPut(session, csrfToken, settingsPayload(15, 120, 30, 200));
        assertInvalidPut(session, csrfToken, settingsPayload(15, 120, 30, 0));
    }

    private void assertInvalidPut(MockHttpSession session, String csrfToken, String payload) throws Exception {
        mockMvc.perform(put("/api/v1/practice/settings")
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private static String settingsPayload(int slotGranularityMin, int minNoticeMin,
                                          int maxAdvanceDays, int reminderHoursBefore) {
        return """
                {
                    "name": "Consultorio",
                    "timezone": "America/Argentina/Buenos_Aires",
                    "slotGranularityMin": %d,
                    "minNoticeMin": %d,
                    "maxAdvanceDays": %d,
                    "reminderHoursBefore": %d
                }
                """.formatted(slotGranularityMin, minNoticeMin, maxAdvanceDays, reminderHoursBefore);
    }
}
