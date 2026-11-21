package ar.turnosodontologia.consultorio.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.turnosodontologia.consultorio.auth.domain.AppUser;
import ar.turnosodontologia.consultorio.auth.domain.AppUserRepository;
import ar.turnosodontologia.consultorio.auth.domain.PasswordHasher;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Sesiones, gate de cambio obligatorio e invalidación global (C-04, D6/D8). */
class ForcedPasswordChangeIT extends AbstractIntegrationTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String ME_PATH = "/api/v1/auth/me";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";
    private static final String CHANGE_PASSWORD_PATH = "/api/v1/auth/change-password";
    private static final String PRIVATE_PATH = "/api/v1/agenda/turnos";
    private static final String INITIAL_PASSWORD = "inicial-123456";
    private static final String NEW_PASSWORD = "nueva-contrasena-123";

    private final WebApplicationContext context;
    private final AppUserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;
    private final SessionRegistry registry;

    private MockMvc mockMvc;
    private String email;
    private UUID userId;

    ForcedPasswordChangeIT(WebApplicationContext context, AppUserRepository users,
            PasswordHasher hasher, Clock clock, SessionRegistry registry) {
        this.context = context;
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
        this.registry = registry;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        email = "forced-" + UUID.randomUUID() + "@ejemplo.test";
        UUID practiceId = users.findByEmail(BOOTSTRAP_EMAIL).orElseThrow().practiceId();
        AppUser user = users.save(AppUser.newDentist(
                practiceId, email, hasher.hash(INITIAL_PASSWORD), clock));
        userId = user.id();
    }

    @Test
    void cambioPendientePermiteSoloTresRutasYTrasCambiarLaSesionPreviaDejaDeServir() throws Exception {
        MvcResult login = performLogin();
        MockHttpSession session = sessionOf(login);
        String csrf = csrfOf(login);

        assertThat(mockMvc.perform(get(ME_PATH).session(session)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(post(LOGOUT_PATH).session(session)
                .cookie(new Cookie("XSRF-TOKEN", csrf))
                .header("X-XSRF-TOKEN", csrf))
                .andReturn().getResponse().getStatus()).isEqualTo(204);

        MvcResult secondLogin = performLogin();
        assertProblem(mockMvc.perform(get(PRIVATE_PATH).session(sessionOf(secondLogin))).andReturn(),
                403, "PASSWORD_CHANGE_REQUIRED");
        String oldSessionId = sessionOf(secondLogin).getId();
        MvcResult changed = performChangePassword(secondLogin);
        assertThat(changed.getResponse().getStatus()).isEqualTo(200);

        assertProblem(mockMvc.perform(get(ME_PATH).session(new MockHttpSession(null, oldSessionId))).andReturn(),
                401, "UNAUTHENTICATED");
    }

    @Test
    void cambioDeContrasenaInvalidaTodasLasSesionesDelUsuario() throws Exception {
        MvcResult first = performLogin();
        MvcResult second = performLogin();
        MockHttpSession firstReplay = replayOf(first);
        MockHttpSession secondReplay = replayOf(second);

        MvcResult changed = performChangePassword(second);

        assertThat(changed.getResponse().getStatus()).isEqualTo(200);
        assertProblem(mockMvc.perform(get(ME_PATH).session(firstReplay)).andReturn(), 401, "UNAUTHENTICATED");
        assertProblem(mockMvc.perform(get(ME_PATH).session(secondReplay)).andReturn(), 401, "UNAUTHENTICATED");
    }

    @Test
    void sesionExpiradaPorCambioNoSeRenueva() throws Exception {
        MvcResult first = performLogin();
        MvcResult second = performLogin();
        MockHttpSession expiredReplay = replayOf(first);
        String expiredSessionId = sessionOf(first).getId();
        performChangePassword(second);

        assertProblem(mockMvc.perform(get(ME_PATH).session(expiredReplay)).andReturn(), 401, "UNAUTHENTICATED");
        assertProblem(mockMvc.perform(get(ME_PATH).session(new MockHttpSession(null, expiredSessionId))).andReturn(),
                401, "UNAUTHENTICATED");
        assertThat(registry.getSessionInformation(expiredSessionId).isExpired()).isTrue();
    }

    @Test
    void logoutInvalidaSoloLaSesionActual() throws Exception {
        MvcResult first = performLogin();
        MvcResult second = performLogin();
        String csrf = csrfOf(first);
        String oldSessionId = sessionOf(first).getId();

        MvcResult logout = mockMvc.perform(post(LOGOUT_PATH).session(sessionOf(first))
                .cookie(new Cookie("XSRF-TOKEN", csrf))
                .header("X-XSRF-TOKEN", csrf)).andReturn();

        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        assertProblem(mockMvc.perform(get(ME_PATH).session(new MockHttpSession(null, oldSessionId))).andReturn(),
                401, "UNAUTHENTICATED");
        assertThat(mockMvc.perform(get(ME_PATH).session(sessionOf(second))).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void registroDeSesionesNoGuardaDatosPersonales() throws Exception {
        performLogin();

        boolean registered = registry.getAllPrincipals().stream()
                .filter(principal -> userId.toString().equals(principalName(principal)))
                .findFirst()
                .map(principal -> {
                    String description = principal.toString();
                    assertThat(description).doesNotContain(email).doesNotContain("@")
                            .doesNotContain("dni").doesNotContain("phone").doesNotContain("diagnostico");
                    return true;
                })
                .orElse(false);

        assertThat(registered).isTrue();
    }

    private MvcResult performLogin() throws Exception {
        return mockMvc.perform(post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(Map.of("email", email, "password", INITIAL_PASSWORD))))
                .andReturn();
    }

    private MvcResult performChangePassword(MvcResult login) throws Exception {
        String csrf = csrfOf(login);
        return mockMvc.perform(post(CHANGE_PASSWORD_PATH).session(sessionOf(login))
                .cookie(new Cookie("XSRF-TOKEN", csrf))
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(Map.of(
                        "currentPassword", INITIAL_PASSWORD, "newPassword", NEW_PASSWORD))))
                .andReturn();
    }

    private static MockHttpSession sessionOf(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String csrfOf(MvcResult result) {
        return result.getResponse().getHeader("X-CSRF-TOKEN");
    }

    /** Copia el estado de la sesión a un objeto nuevo con el mismo id: simula el cookie reenviado por el cliente. */
    private static MockHttpSession replayOf(MvcResult result) {
        MockHttpSession original = sessionOf(result);
        MockHttpSession replay = new MockHttpSession(null, original.getId());
        java.util.Enumeration<String> names = original.getAttributeNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            replay.setAttribute(name, original.getAttribute(name));
        }
        return replay;
    }

    private static String principalName(Object principal) {
        return principal instanceof java.security.Principal asPrincipal
                ? asPrincipal.getName()
                : principal.toString();
    }

    private static void assertProblem(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"" + code + "\"");
    }
}
