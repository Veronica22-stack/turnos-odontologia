package ar.turnosodontologia.consultorio.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class AuthEndpointsIT extends AbstractIntegrationTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String ME_PATH = "/api/v1/auth/me";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";
    private static final String CHANGE_PASSWORD_PATH = "/api/v1/auth/change-password";
    private static final String PRIVATE_PATH = "/api/v1/agenda/turnos";

    private final WebApplicationContext context;

    private MockMvc mockMvc;

    AuthEndpointsIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test
    void rutaPrivadaSinSesionDevuelve401EnFormatoProblema() throws Exception {
        MvcResult result = mockMvc.perform(get(PRIVATE_PATH)).andReturn();

        assertProblem(result, 401, "UNAUTHENTICATED");
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(result.getResponse().getRedirectedUrl()).isNull();
    }

    @Test
    void loginCorrectoEntregaSesionCookiesCsrfYRotaElIdentificador() throws Exception {
        MockHttpSession anonymous = new MockHttpSession();
        mockMvc.perform(get("/actuator/health").session(anonymous));

        MvcResult result = performLogin();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getRequest().getSession(false)).isNotNull();
        MockHttpSession authenticated = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(authenticated.getId()).isNotEqualTo(anonymous.getId());
        Cookie sessionCookie = result.getResponse().getCookie("SESSIONID");
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.getValue()).isEqualTo(authenticated.getId());
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        // Los IT fuerzan HTTP local (AUTH_SESSION_COOKIE_SECURE=false); el default seguro se cubre en SessionPropertiesTest.
        assertThat(sessionCookie.getSecure()).isFalse();
        assertThat(sessionCookie.getPath()).isEqualTo("/");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(value -> value.contains("SESSIONID=") && value.contains("SameSite=Strict"));
        String rotatedCsrf = csrfOf(result);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(value -> value.startsWith("XSRF-TOKEN=" + rotatedCsrf + ";"));
        assertThat(result.getResponse().getContentAsString()).contains("\"userId\"");
    }

    @Test
    void logoutSinCsrfEs403YNoInvalidaLaSesion() throws Exception {
        MvcResult login = performLogin();
        MockHttpSession session = sessionOf(login);

        MvcResult denied = mockMvc.perform(post(LOGOUT_PATH).session(session)).andReturn();

        assertProblem(denied, 403, "CSRF_REQUIRED");
        MvcResult stillAuthenticated = mockMvc.perform(get(ME_PATH).session(session)).andReturn();
        assertThat(stillAuthenticated.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void changePasswordSinCsrfEs403() throws Exception {
        MvcResult login = performLogin();

        MvcResult denied = mockMvc.perform(post(CHANGE_PASSWORD_PATH)
                .session(sessionOf(login))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"x\",\"newPassword\":\"y\"}")).andReturn();

        assertProblem(denied, 403, "CSRF_REQUIRED");
    }

    @Test
    void meNoExponeDatosPersonalesYDevuelveVencimientoUtc() throws Exception {
        MvcResult login = performLogin();

        MvcResult result = mockMvc.perform(get(ME_PATH).session(sessionOf(login))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"role\":\"DENTIST\"").contains("\"mustChangePassword\":true");
        assertThat(body).doesNotContain(BOOTSTRAP_EMAIL).doesNotContain("dni").doesNotContain("phone");
        Map<?, ?> json = new ObjectMapper().readValue(body, Map.class);
        assertThat(json.get("expiresAt").toString()).endsWith("Z");
    }

    @Test
    void rolDistintoDeDentistEs403() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        adminSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        adminSession.setAttribute(SessionTimeout.EXPIRES_AT, Instant.now().plusSeconds(3600).toEpochMilli());

        MvcResult result = mockMvc.perform(get(PRIVATE_PATH).session(adminSession)).andReturn();

        assertProblem(result, 403, "FORBIDDEN");
    }

    @Test
    void cambioPendienteSoloPermiteLasTresRutasDeAuth() throws Exception {
        MvcResult login = performLogin();
        MockHttpSession session = sessionOf(login);
        String csrf = csrfOf(login);

        assertProblem(mockMvc.perform(get(PRIVATE_PATH).session(session)).andReturn(), 403,
                "PASSWORD_CHANGE_REQUIRED");
        assertThat(mockMvc.perform(get(ME_PATH).session(session)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(post(LOGOUT_PATH).session(session)
                .cookie(new Cookie("XSRF-TOKEN", csrf))
                .header("X-XSRF-TOKEN", csrf))
                .andReturn().getResponse().getStatus()).isEqualTo(204);

        MvcResult secondLogin = performLogin();
        String secondCsrf = csrfOf(secondLogin);
        MvcResult wrongPassword = mockMvc.perform(post(CHANGE_PASSWORD_PATH)
                .session(sessionOf(secondLogin))
                .cookie(new Cookie("XSRF-TOKEN", secondCsrf))
                .header("X-XSRF-TOKEN", secondCsrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"incorrecta-123\",\"newPassword\":\"nueva-12345678\"}")).andReturn();
        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void respuestasIncluyenCabecerasDeSeguridad() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")).andReturn();

        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(result.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(result.getResponse().getHeader("Content-Security-Policy")).contains("default-src 'self'");
        assertThat(result.getResponse().getHeader("Strict-Transport-Security")).isNull();

        MvcResult secure = mockMvc.perform(get("/actuator/health").secure(true)).andReturn();
        assertThat(secure.getResponse().getHeader("Strict-Transport-Security")).contains("max-age=");
    }

    @Test
    void corsSoloAceptaOrigenesDeclarados() throws Exception {
        MvcResult allowed = mockMvc.perform(options(ME_PATH)
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andReturn();
        assertThat(allowed.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:5173");
        assertThat(allowed.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");

        MvcResult denied = mockMvc.perform(options(ME_PATH)
                .header(HttpHeaders.ORIGIN, "https://malicioso.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andReturn();
        assertThat(denied.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(denied.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNotEqualTo("*");
    }

    @Test
    void credencialesInvalidasYEInexistenteDevuelvenElMismoCuerpo() throws Exception {
        String emailInexistente = "no-existe-" + UUID.randomUUID() + "@ejemplo.test";
        MvcResult existente = mockMvc.perform(post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(BOOTSTRAP_EMAIL, "contrasena-incorrecta-123"))).andReturn();
        MvcResult inexistente = mockMvc.perform(post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(emailInexistente, "contrasena-incorrecta-123"))).andReturn();

        assertProblem(existente, 401, "INVALID_CREDENTIALS");
        assertThat(inexistente.getResponse().getContentAsString())
                .isEqualTo(existente.getResponse().getContentAsString())
                .doesNotContain(emailInexistente)
                .doesNotContain(BOOTSTRAP_EMAIL);
    }

    @Test
    void sesionVencidaPorInactividadDevuelve401YNoSeRenueva() throws Exception {
        MvcResult login = performLogin();
        MockHttpSession session = sessionOf(login);
        session.setAttribute(SessionTimeout.EXPIRES_AT, Instant.now().minusSeconds(1).toEpochMilli());

        MvcResult expired = mockMvc.perform(get(ME_PATH).session(session)).andReturn();
        MvcResult reused = mockMvc.perform(get(ME_PATH).session(session)).andReturn();

        assertProblem(expired, 401, "UNAUTHENTICATED");
        assertProblem(reused, 401, "UNAUTHENTICATED");
    }

    @Test
    void logoutSinSesionConCsrfValidoEs204() throws Exception {
        MvcResult health = mockMvc.perform(get("/actuator/health")).andReturn();
        Cookie csrf = health.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();

        MvcResult result = mockMvc.perform(post(LOGOUT_PATH)
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void limiteDeIntentosExcedidoDevuelve429ConRetryAfter() throws Exception {
        String email = "rate-" + UUID.randomUUID() + "@ejemplo.test";
        MvcResult last = null;
        for (int attempt = 0; attempt < 11; attempt++) {
            last = mockMvc.perform(post(LOGIN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(email, "contrasena-incorrecta-123"))).andReturn();
        }

        assertProblem(last, 429, "RATE_LIMITED");
        assertThat(last.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(last.getResponse().getContentAsString()).doesNotContain(email);
    }

    private MvcResult performLogin() throws Exception {
        return mockMvc.perform(post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(BOOTSTRAP_EMAIL, BOOTSTRAP_PASSWORD))).andReturn();
    }

    private static String loginJson(String email, String password) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("email", email, "password", password));
    }

    private static MockHttpSession sessionOf(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String csrfOf(MvcResult result) {
        return result.getResponse().getHeader("X-CSRF-TOKEN");
    }

    private static void assertProblem(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"" + code + "\"");
    }
}
