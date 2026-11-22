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
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;

class ScheduleBlocksEndpointsIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/schedule-blocks";
    private static final String NINE = "2026-12-10T09:00:00Z";
    private static final String ELEVEN = "2026-12-10T11:00:00Z";
    private static final String TWELVE = "2026-12-10T12:00:00Z";
    private static final String FOURTEEN = "2026-12-10T14:00:00Z";
    private static final String SIXTEEN = "2026-12-10T16:00:00Z";
    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private static final String EXPIRES_AT = "ar.turnosodontologia.consultorio.auth.infrastructure.SessionTimeout.expiresAt";

    private final WebApplicationContext context;
    private final JdbcTemplate jdbcTemplate;
    private MockMvc mockMvc;

    ScheduleBlocksEndpointsIT(WebApplicationContext context, JdbcTemplate jdbcTemplate) {
        this.context = context;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM schedule_block");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void getSinBloqueosDevuelveListaVaciaSinCsrf() throws Exception {
        mockMvc.perform(get(BASE).session(session("ROLE_DENTIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void crearYConsultarBloqueoDevuelveDatosGeneradosPorServidor() throws Exception {
        MockHttpSession session = session("ROLE_DENTIST");
        String csrf = injectCsrf(session);

        MvcResult created = create(session, csrf, NINE, ELEVEN, "Vacaciones");
        created.getResponse().getContentAsString();

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        UUID id = idOf(created);
        assertThat(created.getResponse().getHeader("Location")).endsWith(BASE + "/" + id);
        String startsAt = content(created, "$.startsAt");
        String endsAt = content(created, "$.endsAt");
        String reason = content(created, "$.reason");
        String createdAt = content(created, "$.createdAt");
        String updatedAt = content(created, "$.updatedAt");
        Integer affectedCount = content(created, "$.affectedAppointments.length()");
        assertThat(startsAt).isEqualTo(NINE);
        assertThat(endsAt).isEqualTo(ELEVEN);
        assertThat(reason).isEqualTo("Vacaciones");
        assertThat(createdAt).isNotBlank();
        assertThat(updatedAt).isNotBlank();
        assertThat(affectedCount).isEqualTo(0);

        mockMvc.perform(get(BASE + "/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.reason").value("Vacaciones"));
    }

    @Test
    void listarOrdenadoYFiltradoPorRango() throws Exception {
        MockHttpSession session = session("ROLE_DENTIST");
        String csrf = injectCsrf(session);
        UUID first = idOf(create(session, csrf, NINE, ELEVEN, "Vacaciones"));
        UUID second = idOf(create(session, csrf, FOURTEEN, SIXTEEN, "Trámite"));
        UUID third = idOf(create(session, csrf, "2026-12-10T18:00:00Z", "2026-12-10T20:00:00Z", "Mantenimiento"));

        MvcResult all = mockMvc.perform(get(BASE).session(session)).andExpect(status().isOk()).andReturn();
        assertThat(JsonPath.<List<String>>read(all.getResponse().getContentAsString(), "$[*].id"))
                .containsExactly(first.toString(), second.toString(), third.toString());

        MvcResult filtered = mockMvc.perform(get(BASE)
                        .session(session)
                        .param("from", "2026-12-10T10:30:00Z")
                        .param("to", "2026-12-10T15:30:00Z"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(JsonPath.<List<String>>read(filtered.getResponse().getContentAsString(), "$[*].id"))
                .containsExactly(first.toString(), second.toString());
    }

    @Test
    void editarYEliminarBloqueo() throws Exception {
        MockHttpSession session = session("ROLE_DENTIST");
        String csrf = injectCsrf(session);
        UUID id = idOf(create(session, csrf, NINE, ELEVEN, "Vacaciones"));

        MvcResult updated = mockMvc.perform(put(BASE + "/" + id)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrf)
                        .cookie(new Cookie("XSRF-TOKEN", csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(NINE, TWELVE, "Reprogramado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endsAt").value(TWELVE))
                .andExpect(jsonPath("$.reason").value("Reprogramado"))
                .andReturn();
        Integer updatedAffectedCount = content(updated, "$.affectedAppointments.length()");
        assertThat(updatedAffectedCount).isEqualTo(0);

        mockMvc.perform(delete(BASE + "/" + id)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrf)
                        .cookie(new Cookie("XSRF-TOKEN", csrf)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + id).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_BLOCK_NOT_FOUND"));
    }

    @Test
    void rangoInvertidoDevuelve400InvalidRangeEnCreacionYEdicion() throws Exception {
        MockHttpSession session = session("ROLE_DENTIST");
        String csrf = injectCsrf(session);
        UUID id = idOf(create(session, csrf, NINE, ELEVEN, "Vacaciones"));

        mockMvc.perform(post(BASE)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrf)
                        .cookie(new Cookie("XSRF-TOKEN", csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(ELEVEN, NINE, "Vacaciones")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));

        mockMvc.perform(put(BASE + "/" + id)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrf)
                        .cookie(new Cookie("XSRF-TOKEN", csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(NINE, NINE, "Vacaciones")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    }

    @Test
    void motivoInvalidoDevuelve400ValidationFailedSinDevolverElValor() throws Exception {
        MockHttpSession session = session("ROLE_DENTIST");
        String csrf = injectCsrf(session);

        MvcResult blank = mockMvc.perform(post(BASE)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrf)
                        .cookie(new Cookie("XSRF-TOKEN", csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(NINE, ELEVEN, "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();

        String personal = "Juan Pérez DNI 12345678 " + "x".repeat(500);
        MvcResult tooLong = mockMvc.perform(post(BASE)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrf)
                        .cookie(new Cookie("XSRF-TOKEN", csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(NINE, ELEVEN, personal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();

        assertThat(blank.getResponse().getContentAsString()).doesNotContain("   ");
        assertThat(tooLong.getResponse().getContentAsString()).doesNotContain("Juan Pérez", "12345678", "xxxxx");
    }

    @Test
    void filtroInvalidoDevuelve400InvalidRange() throws Exception {
        MockHttpSession session = session("ROLE_DENTIST");

        mockMvc.perform(get(BASE).session(session)
                        .param("from", ELEVEN)
                        .param("to", NINE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));

        mockMvc.perform(get(BASE).session(session)
                        .param("from", "no-es-instante")
                        .param("to", TWELVE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    }

    @Test
    void bloqueoInexistenteDevuelve404EnDetalleEdicionYEliminacion() throws Exception {
        MockHttpSession session = session("ROLE_DENTIST");
        String csrf = injectCsrf(session);
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get(BASE + "/" + unknown).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_BLOCK_NOT_FOUND"));
        mockMvc.perform(put(BASE + "/" + unknown)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrf)
                        .cookie(new Cookie("XSRF-TOKEN", csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(NINE, ELEVEN, "Vacaciones")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_BLOCK_NOT_FOUND"));
        mockMvc.perform(delete(BASE + "/" + unknown)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrf)
                        .cookie(new Cookie("XSRF-TOKEN", csrf)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_BLOCK_NOT_FOUND"));
    }

    @Test
    void solapamientoParcialContenidoEIdenticoDevuelve409SinTextoDePostgres() throws Exception {
        MockHttpSession session = session("ROLE_DENTIST");
        String csrf = injectCsrf(session);
        create(session, csrf, NINE, ELEVEN, "Vacaciones");

        String[][] attempts = {
                {Instant.parse("2026-12-10T10:00:00Z").toString(), TWELVE},
                {"2026-12-10T09:30:00Z", "2026-12-10T10:30:00Z"},
                {NINE, ELEVEN}
        };
        for (String[] attempt : attempts) {
            MvcResult conflict = mockMvc.perform(post(BASE)
                            .session(session)
                            .header("X-XSRF-TOKEN", csrf)
                            .cookie(new Cookie("XSRF-TOKEN", csrf))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson(attempt[0], attempt[1], "Otro")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE_BLOCK_OVERLAP"))
                    .andReturn();
            assertThat(conflict.getResponse().getContentAsString())
                    .doesNotContain("schedule_block", "CONFLICT", "exclusion", "SQL");
        }
    }

    @Test
    void bloquesAdyacentesSeAceptan() throws Exception {
        MockHttpSession session = session("ROLE_DENTIST");
        String csrf = injectCsrf(session);
        create(session, csrf, NINE, ELEVEN, "Vacaciones");

        MvcResult adjacent = create(session, csrf, ELEVEN, TWELVE, "Trámite");

        assertThat(adjacent.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void peticionesAnonimasDevuelven401EnGetYPost() throws Exception {
        MockHttpSession anonymous = new MockHttpSession();
        String csrf = injectCsrf(anonymous);

        mockMvc.perform(get(BASE).session(anonymous)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE)
                        .session(anonymous)
                        .header("X-XSRF-TOKEN", csrf)
                        .cookie(new Cookie("XSRF-TOKEN", csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(NINE, ELEVEN, "Vacaciones")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolInsuficienteDevuelve403() throws Exception {
        mockMvc.perform(get(BASE).session(session("ROLE_PATIENT"))).andExpect(status().isForbidden());
    }

    @Test
    void mutacionesSinCsrfDevuelven403CsrfRequired() throws Exception {
        MockHttpSession session = session("ROLE_DENTIST");
        UUID id = idOf(create(session, injectCsrf(session), NINE, ELEVEN, "Vacaciones"));

        mockMvc.perform(post(BASE)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(NINE, TWELVE, "Otro")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));
        mockMvc.perform(put(BASE + "/" + id)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(NINE, TWELVE, "Otro")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));
        mockMvc.perform(delete(BASE + "/" + id).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));
    }

    @Test
    void motivoConDatosPersonalesNoApareceEnRespuestaNiEnLogs() throws Exception {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
        try {
            MockHttpSession session = session("ROLE_DENTIST");
            String personal = "Juan Pérez DNI 12345678 " + "x".repeat(500);
            MvcResult rejected = create(session, injectCsrf(session), NINE, ELEVEN, personal);

            assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
            assertThat(rejected.getResponse().getContentAsString()).doesNotContain("Juan Pérez", "12345678", "xxxxx");
            assertThat(appender.list)
                    .noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains("Juan Pérez", "12345678"));
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_block", Integer.class)).isZero();
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }
    }

    private MvcResult create(MockHttpSession session, String csrf, String startsAt, String endsAt, String reason)
            throws Exception {
        return mockMvc.perform(post(BASE)
                        .session(session)
                        .header("X-XSRF-TOKEN", csrf)
                        .cookie(new Cookie("XSRF-TOKEN", csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(startsAt, endsAt, reason)))
                .andReturn();
    }

    private static String requestJson(String startsAt, String endsAt, String reason) {
        return """
                {"startsAt":"%s","endsAt":"%s","reason":"%s"}
                """.formatted(startsAt, endsAt, reason);
    }

    private static UUID idOf(MvcResult result) throws Exception {
        return UUID.fromString(content(result, "$.id"));
    }

    private static <T> T content(MvcResult result, String expression) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), expression);
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
