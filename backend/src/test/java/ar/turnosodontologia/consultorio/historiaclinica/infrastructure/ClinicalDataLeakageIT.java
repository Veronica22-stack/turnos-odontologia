package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Ni el contenido clinico ni el motivo de correccion llegan a los logs, a las respuestas de error ni a los detalles
 * de auditoria (regla dura 6, patron de AuditLogLeakageIT).
 */
@ExtendWith(OutputCaptureExtension.class)
class ClinicalDataLeakageIT extends AbstractIntegrationTest {

    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private static final String PRACTICE = "01900000-0000-7000-8000-000000000001";
    private static final String CONTENT_MARKER = "MARCADOR-CONTENIDO-7f3a9c";
    private static final String REASON_MARKER = "MARCADOR-MOTIVO-51be02";

    private final WebApplicationContext context;
    private MockMvc mockMvc;
    private UUID userId;
    private UUID patientId;

    ClinicalDataLeakageIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() throws SQLException {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanRows();
        patientId = UUID.randomUUID();
        try (Connection connection = DATABASE.migratorConnection()) {
            try (PreparedStatement query = connection.prepareStatement("SELECT id FROM app_user WHERE email = ?")) {
                query.setString(1, BOOTSTRAP_EMAIL);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    userId = UUID.fromString(rows.getString(1));
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) VALUES ('"
                        + patientId + "', '" + PRACTICE + "', 'Ana', 'Leak', '75000001', '1111')");
            }
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM anamnesis_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM anamnesis_entry");
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM audit_log WHERE action IN ('HC_READ', 'HC_CREATE', 'HC_CORRECT')");
            statement.executeUpdate("DELETE FROM patient WHERE dni = '75000001'");
        }
    }

    @Test
    void elContenidoYElMotivoNoLleganALogsRespuestasDeErrorNiAuditoria(CapturedOutput output) throws Exception {
        MockHttpSession session = session();
        List<String> errorBodies = new ArrayList<>();
        String base = "/api/v1/patients/" + patientId + "/clinical-record/anamnesis";

        // Alta, lectura y correccion validas que llevan los marcadores en el contenido y en el motivo.
        MvcResult created = mockMvc.perform(postJson(base, session,
                        "{\"allergies\":\"" + CONTENT_MARKER + "\",\"notes\":\"" + CONTENT_MARKER + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        String entryId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get(base).session(session)).andExpect(status().isOk());
        mockMvc.perform(postJson(base + "/" + entryId + "/corrections", session,
                        "{\"reason\":\"" + REASON_MARKER + "\",\"content\":{\"allergies\":\"" + CONTENT_MARKER + "2\"}}"))
                .andExpect(status().isCreated());

        // Errores de validacion y de negocio con los marcadores en el cuerpo enviado.
        errorBodies.add(body(mockMvc.perform(postJson(base, session,
                "{\"allergies\":\"" + CONTENT_MARKER + "x".repeat(2000) + "\"}")).andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(base + "/" + entryId + "/corrections", session,
                "{\"reason\":\"" + REASON_MARKER + "y".repeat(500) + "\",\"content\":{\"allergies\":\""
                        + CONTENT_MARKER + "\"}}")).andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(base + "/" + entryId + "/corrections", session,
                "{\"reason\":\"" + REASON_MARKER + "\",\"content\":{\"allergies\":\"" + CONTENT_MARKER + "\"}}"))
                .andExpect(status().isConflict())));
        errorBodies.add(body(mockMvc.perform(postJson(base, session, "{\"allergies\":\"" + CONTENT_MARKER + "\""))
                .andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(
                        "/api/v1/patients/" + UUID.randomUUID() + "/clinical-record/anamnesis", session,
                        "{\"allergies\":\"" + CONTENT_MARKER + "\"}"))
                .andExpect(status().isNotFound())));

        assertThat(errorBodies).noneMatch(text -> text.contains(CONTENT_MARKER) || text.contains(REASON_MARKER));
        assertThat(output.toString()).doesNotContain(CONTENT_MARKER, REASON_MARKER);
        assertThat(auditDetailsAndIds()).doesNotContain(CONTENT_MARKER, REASON_MARKER);
        assertThat(auditDetailsAndIds()).isNotBlank();
    }

    private static String body(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private String auditDetailsAndIds() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT coalesce(string_agg(details::text || ' ' || coalesce(entity_id, '') || ' '"
                                + " || coalesce(entity_type, '') || ' ' || coalesce(user_agent, ''), ' '), '') "
                                + "FROM audit_log WHERE action IN ('HC_READ', 'HC_CREATE', 'HC_CORRECT')")) {
            rows.next();
            return rows.getString(1);
        }
    }

    private MockHttpServletRequestBuilder postJson(String url, MockHttpSession session, String body) {
        String token = UUID.randomUUID().toString();
        session.setAttribute(CSRF_ATTR, (CsrfToken) new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", token));
        return post(url).session(session).header("X-XSRF-TOKEN", token).cookie(new Cookie("XSRF-TOKEN", token))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        AuthenticatedUser user = new AuthenticatedUser(userId, UUID.randomUUID(), Role.DENTIST, false);
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_DENTIST"))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.setAttribute("ar.turnosodontologia.consultorio.auth.infrastructure.SessionTimeout.expiresAt",
                Instant.now().plusSeconds(3600).toEpochMilli());
        return session;
    }
}
