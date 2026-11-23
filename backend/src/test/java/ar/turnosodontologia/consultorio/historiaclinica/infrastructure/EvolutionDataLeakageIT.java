package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import ar.turnosodontologia.consultorio.support.ClinicalFixtures;
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
 * Ni la descripcion ni el motivo de correccion llegan a los logs, a las respuestas de error ni a los detalles de
 * auditoria, y el identificador del turno asociado tampoco (regla dura 6, patron de OdontogramDataLeakageIT).
 */
@ExtendWith(OutputCaptureExtension.class)
class EvolutionDataLeakageIT extends AbstractIntegrationTest {

    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private static final String PRACTICE = "01900000-0000-7000-8000-000000000001";
    private static final String DESCRIPTION_MARKER = "MARCADOR-DESCRIPCION-5b3a91";
    private static final String REASON_MARKER = "MARCADOR-MOTIVO-77e0c4";

    private final WebApplicationContext context;
    private MockMvc mockMvc;
    private UUID userId;
    private UUID patientId;
    private UUID otherPatientId;

    EvolutionDataLeakageIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() throws SQLException {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanRows();
        patientId = UUID.randomUUID();
        otherPatientId = UUID.randomUUID();
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT id FROM app_user WHERE email = '" + BOOTSTRAP_EMAIL + "'")) {
                rows.next();
                userId = UUID.fromString(rows.getString(1));
            }
            statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) VALUES ('"
                    + patientId + "', '" + PRACTICE + "', 'Ana', 'Leak', '80100001', '1111'), ('"
                    + otherPatientId + "', '" + PRACTICE + "', 'Beto', 'Leak', '80100002', '1111')");
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        ClinicalFixtures.deleteEvolutions();
        AppointmentFixtures.cleanAll();
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM audit_log WHERE action IN ('HC_READ', 'HC_CREATE', 'HC_CORRECT')");
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('80100001', '80100002')");
        }
    }

    @Test
    void descripcionMotivoYTurnoNoLleganALogsRespuestasDeErrorNiAuditoria(CapturedOutput output) throws Exception {
        MockHttpSession session = session();
        List<String> errorBodies = new ArrayList<>();
        String base = "/api/v1/patients/" + patientId + "/clinical-record/evolutions";
        UUID service = AppointmentFixtures.service(30, true, true);
        UUID valid = AppointmentFixtures.appointment(patientId, service, hours(-48), hours(-47), "COMPLETED");
        UUID future = AppointmentFixtures.appointment(patientId, service, hours(24), hours(25), "CONFIRMED");
        UUID foreign = AppointmentFixtures.appointment(otherPatientId, service, hours(-30), hours(-29), "COMPLETED");
        UUID unknown = UUID.randomUUID();
        UUID malformedLike = UUID.randomUUID();

        // Alta con turno, lectura y correccion validas que llevan los marcadores.
        MvcResult created = mockMvc.perform(postJson(base, session, create(DESCRIPTION_MARKER, valid.toString())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        String entryId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get(base).session(session)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(postJson(base + "/" + entryId + "/corrections", session,
                        correction(REASON_MARKER, DESCRIPTION_MARKER + "2")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"));

        // Errores de validacion y de negocio con los marcadores (y ids de turno) en el cuerpo enviado.
        errorBodies.add(body(mockMvc.perform(postJson(base, session,
                create(DESCRIPTION_MARKER + "x".repeat(5000), null))).andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(base, session,
                create(DESCRIPTION_MARKER, malformedLike + "-zz"))).andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(base, session,
                create(DESCRIPTION_MARKER, future.toString()))).andExpect(status().isUnprocessableEntity())));
        errorBodies.add(body(mockMvc.perform(postJson(base, session,
                create(DESCRIPTION_MARKER, foreign.toString()))).andExpect(status().isUnprocessableEntity())));
        errorBodies.add(body(mockMvc.perform(postJson(base, session,
                create(DESCRIPTION_MARKER, unknown.toString()))).andExpect(status().isUnprocessableEntity())));
        errorBodies.add(body(mockMvc.perform(postJson(base + "/" + entryId + "/corrections", session,
                correction(REASON_MARKER + "y".repeat(500), DESCRIPTION_MARKER))).andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(base + "/" + entryId + "/corrections", session,
                correction(REASON_MARKER, DESCRIPTION_MARKER))).andExpect(status().isConflict())));
        errorBodies.add(body(mockMvc.perform(postJson(base, session,
                "{\"description\":\"" + DESCRIPTION_MARKER + "\"")).andExpect(status().isBadRequest())));
        errorBodies.add(body(mockMvc.perform(postJson(
                        "/api/v1/patients/" + UUID.randomUUID() + "/clinical-record/evolutions", session,
                        create(DESCRIPTION_MARKER, valid.toString())))
                .andExpect(status().isNotFound())));

        String[] secrets = {DESCRIPTION_MARKER, REASON_MARKER};
        String[] appointmentIds = {valid.toString(), future.toString(), foreign.toString(), unknown.toString(),
                malformedLike.toString()};
        assertThat(errorBodies).noneMatch(text -> text.contains(DESCRIPTION_MARKER) || text.contains(REASON_MARKER));
        assertThat(errorBodies).noneMatch(text -> List.of(appointmentIds).stream().anyMatch(text::contains));
        assertThat(output.toString()).doesNotContain(secrets).doesNotContain(appointmentIds);
        String audit = auditDetailsAndIds();
        assertThat(audit).isNotBlank().contains("evolution_entry", "EVOLUTION");
        assertThat(audit).doesNotContain(secrets).doesNotContain(appointmentIds);
        assertThat(audit).doesNotContain("description", "appointment");
    }

    private static String hours(int offset) {
        return Instant.now().plusSeconds(offset * 3600L).toString();
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

    private static String create(String description, String appointmentId) {
        return "{\"description\":\"" + description + "\",\"appointmentId\":"
                + (appointmentId == null ? "null" : "\"" + appointmentId + "\"") + "}";
    }

    private static String correction(String reason, String description) {
        return "{\"reason\":\"" + reason + "\",\"description\":\"" + description + "\"}";
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
