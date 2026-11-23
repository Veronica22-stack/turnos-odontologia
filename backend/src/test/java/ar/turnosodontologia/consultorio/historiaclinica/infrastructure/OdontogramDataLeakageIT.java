package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.support.ClinicalFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Ni las notas ni el motivo de correccion llegan a los logs, a las respuestas de error ni a los detalles de
 * auditoria, y la auditoria no incluye pieza, cara ni estado (regla dura 6, patron de ClinicalDataLeakageIT).
 */
@ExtendWith(OutputCaptureExtension.class)
class OdontogramDataLeakageIT extends AbstractIntegrationTest {

    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private static final String PRACTICE = "01900000-0000-7000-8000-000000000001";
    private static final String NOTES_MARKER = "MARCADOR-NOTAS-8d41e6";
    private static final String REASON_MARKER = "MARCADOR-MOTIVO-2c97fa";

    private final WebApplicationContext context;
    private MockMvc mockMvc;
    private UUID userId;
    private UUID patientId;

    OdontogramDataLeakageIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() throws SQLException {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanRows();
        patientId = UUID.randomUUID();
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT id FROM app_user WHERE email = '" + BOOTSTRAP_EMAIL + "'")) {
                rows.next();
                userId = UUID.fromString(rows.getString(1));
            }
            statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) VALUES ('"
                    + patientId + "', '" + PRACTICE + "', 'Ana', 'Leak', '80000001', '1111')");
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM odontogram_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM odontogram_entry");
            ClinicalFixtures.deleteEvolutions();
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM audit_log WHERE action IN ('HC_READ', 'HC_CREATE', 'HC_CORRECT')");
            statement.executeUpdate("DELETE FROM patient WHERE dni = '80000001'");
        }
    }

    @Test
    void notasYMotivoNoLleganALogsRespuestasDeErrorNiAuditoria(CapturedOutput output) throws Exception {
        MockHttpSession session = session();
        List<String> errorBodies = new ArrayList<>();
        String base = "/api/v1/patients/" + patientId + "/clinical-record/odontogram";

        // Alta, lectura y correccion validas que llevan los marcadores en las notas y en el motivo.
        MvcResult created = mockMvc.perform(postJson(base, session, batch(entry("36", "OCCLUSAL", "CARIES", NOTES_MARKER))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        String entryId = JsonPath.read(created.getResponse().getContentAsString(), "$.entries[0].id");
        mockMvc.perform(get(base).session(session)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(postJson(base + "/" + entryId + "/corrections", session,
                        correction(REASON_MARKER, "FILLED", NOTES_MARKER + "2")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"));

        // Errores de validacion y de negocio con los marcadores en el cuerpo enviado.
        errorBodies.add(body(mockMvc.perform(postJson(base, session,
                batch(entry("36", null, "CARIES", NOTES_MARKER + "x".repeat(500)))))
                .andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(base, session,
                batch(entry("19", null, "CARIES", NOTES_MARKER)))).andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(base, session,
                batch(entry("11", "MESIAL", "MISSING", NOTES_MARKER)))).andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(base + "/" + entryId + "/corrections", session,
                correction(REASON_MARKER + "y".repeat(500), "HEALTHY", NOTES_MARKER)))
                .andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(base + "/" + entryId + "/corrections", session,
                correction(REASON_MARKER, "HEALTHY", NOTES_MARKER))).andExpect(status().isConflict())));
        errorBodies.add(body(mockMvc.perform(postJson(base, session, "{\"entries\":[{\"notes\":\"" + NOTES_MARKER + "\""))
                .andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(
                        "/api/v1/patients/" + UUID.randomUUID() + "/clinical-record/odontogram", session,
                        batch(entry("36", null, "CARIES", NOTES_MARKER))))
                .andExpect(status().isNotFound())));

        assertThat(errorBodies).noneMatch(text -> text.contains(NOTES_MARKER) || text.contains(REASON_MARKER));
        assertThat(output.toString()).doesNotContain(NOTES_MARKER, REASON_MARKER);
        String audit = auditDetailsAndIds();
        assertThat(audit).isNotBlank().contains("odontogram_entry", "ODONTOGRAM");
        assertThat(audit).doesNotContain(NOTES_MARKER, REASON_MARKER);
        assertThat(audit).doesNotContain("CARIES", "FILLED", "OCCLUSAL", "toothCode", "tooth_code", "condition");
        assertThat(output.toString()).doesNotContain("OCCLUSAL", "CARIES");
    }

    private static String body(ResultActions actions) throws Exception {
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

    private static String entry(String tooth, String surface, String condition, String notes) {
        return "{\"toothCode\":\"" + tooth + "\",\"surface\":" + (surface == null ? "null" : "\"" + surface + "\"")
                + ",\"condition\":\"" + condition + "\",\"notes\":\"" + notes + "\"}";
    }

    private static String batch(String... entries) {
        return "{\"entries\":[" + String.join(",", entries) + "]}";
    }

    private static String correction(String reason, String condition, String notes) {
        return "{\"reason\":\"" + reason + "\",\"condition\":\"" + condition + "\",\"notes\":\"" + notes + "\"}";
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
