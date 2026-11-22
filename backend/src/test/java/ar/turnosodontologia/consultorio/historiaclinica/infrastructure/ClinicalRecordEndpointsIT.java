package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

/** API de historia clinica de punta a punta: contratos HTTP, seguridad y filas de auditoria. */
class ClinicalRecordEndpointsIT extends AbstractIntegrationTest {

    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private static final String PRACTICE = "01900000-0000-7000-8000-000000000001";

    private final WebApplicationContext context;
    private MockMvc mockMvc;
    private UUID userId;
    private UUID patientId;
    private UUID otherPatientId;
    private UUID inactivePatientId;

    ClinicalRecordEndpointsIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() throws SQLException {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanRows();
        try (Connection connection = DATABASE.migratorConnection()) {
            try (PreparedStatement query = connection.prepareStatement("SELECT id FROM app_user WHERE email = ?")) {
                query.setString(1, BOOTSTRAP_EMAIL);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    userId = UUID.fromString(rows.getString(1));
                }
            }
            patientId = insertPatient(connection, "74000001", true);
            otherPatientId = insertPatient(connection, "74000002", true);
            inactivePatientId = insertPatient(connection, "74000003", false);
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM anamnesis_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM anamnesis_entry");
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM audit_log WHERE action IN ('HC_READ', 'HC_CREATE', 'HC_CORRECT')");
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('74000001', '74000002', '74000003')");
        }
    }

    // ---------------------------------------------------------------- contratos

    @Test
    void historiaClinicaSinAbrirDevuelve200ConOpenedFalseYNoLaCrea() throws Exception {
        mockMvc.perform(get(recordUrl(patientId)).session(session(false)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.opened").value(false))
                .andExpect(jsonPath("$.currentAnamnesis").doesNotExist());

        assertThat(count("clinical_record")).isZero();
    }

    @Test
    void primeraAnamnesisDevuelve201ConVersionUnoYAbreLaHistoria() throws Exception {
        MockHttpSession session = session(false);

        mockMvc.perform(postJson(anamnesisUrl(patientId), session, anamnesisBody("penicilina", "ibuprofeno")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.authorId").value(userId.toString()))
                .andExpect(jsonPath("$.content.allergies").value("penicilina"))
                .andExpect(jsonPath("$.content.medications").value("ibuprofeno"))
                .andExpect(jsonPath("$.correctsEntryId").doesNotExist());

        assertThat(count("clinical_record")).isEqualTo(1);
        mockMvc.perform(get(recordUrl(patientId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opened").value(true))
                .andExpect(jsonPath("$.currentAnamnesis.version").value(1));
    }

    @Test
    void elClienteNoPuedeFijarAutorFechaVersionNiIdYLosCamposDesconocidosNoSePersisten() throws Exception {
        UUID fakeAuthor = UUID.randomUUID();
        String body = """
                {"allergies":"penicilina","authorId":"%s","createdAt":"2001-01-01T00:00:00Z","version":99,
                 "id":"%s","clinicalRecordId":"%s","ssn":"123-45-6789"}
                """.formatted(fakeAuthor, UUID.randomUUID(), UUID.randomUUID());
        Instant before = Instant.now().minusSeconds(5);

        MvcResult result = mockMvc.perform(postJson(anamnesisUrl(patientId), session(false), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.authorId").value(userId.toString()))
                .andReturn();

        Instant createdAt = Instant.parse(JsonPath.read(result.getResponse().getContentAsString(), "$.createdAt"));
        assertThat(createdAt).isAfter(before);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("ssn", "123-45-6789");
        assertThat(storedContent()).doesNotContain("ssn", "123-45-6789");
    }

    @Test
    void nuevaVersionHistorialYCorreccionDeLaVigenteConRutaUniforme() throws Exception {
        MockHttpSession session = session(false);
        String v1 = createAnamnesis(session, "penicilina");
        String v2 = createAnamnesis(session, "latex");

        MvcResult correction = mockMvc.perform(postJson(correctionUrl(patientId, v2), session,
                        correctionBody("alergia mal cargada", "latex y polen")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.correctsEntryId").value(v2))
                .andExpect(jsonPath("$.correctionReason").value("alergia mal cargada"))
                .andExpect(jsonPath("$.content.allergies").value("latex y polen"))
                .andReturn();
        String v3 = JsonPath.read(correction.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get(anamnesisUrl(patientId)).session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.current.id").value(v3))
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.history[0].version").value(3))
                .andExpect(jsonPath("$.history[1].id").value(v2))
                .andExpect(jsonPath("$.history[1].correctedByEntryId").value(v3))
                .andExpect(jsonPath("$.history[2].id").value(v1))
                .andExpect(jsonPath("$.history[2].correctedByEntryId").doesNotExist())
                .andExpect(jsonPath("$.history[2].content.allergies").value("penicilina"));
    }

    @Test
    void sinAnamnesisLaConsultaDevuelve200SinVigenteNiHistorial() throws Exception {
        mockMvc.perform(get(anamnesisUrl(patientId)).session(session(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").doesNotExist())
                .andExpect(jsonPath("$.history.length()").value(0));
    }

    @Test
    void erroresDeCorreccionUsanLosCodigosDeLaEspecificacion() throws Exception {
        MockHttpSession session = session(false);
        String v1 = createAnamnesis(session, "penicilina");
        String v2 = createAnamnesis(session, "latex");
        String foreign = createAnamnesisFor(otherPatientId, session, "ajena");

        mockMvc.perform(postJson(correctionUrl(patientId, v1), session, correctionBody("motivo", "x")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_NOT_CURRENT"));
        mockMvc.perform(postJson(correctionUrl(patientId, UUID.randomUUID().toString()), session,
                        correctionBody("motivo", "x")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_NOT_FOUND"));
        mockMvc.perform(postJson(correctionUrl(patientId, foreign), session, correctionBody("motivo", "x")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_NOT_FOUND"));
        mockMvc.perform(postJson(correctionUrl(patientId, v2), session, correctionBody("motivo", "x")))
                .andExpect(status().isCreated());
        mockMvc.perform(postJson(correctionUrl(patientId, v2), session, correctionBody("otra", "y")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_ALREADY_CORRECTED"));

        assertThat(count("anamnesis_entry")).isEqualTo(4);
    }

    @Test
    void correccionSinMotivoOConMotivoLargoDevuelve400SobreElCampoReason() throws Exception {
        MockHttpSession session = session(false);
        String v1 = createAnamnesis(session, "penicilina");

        for (String reason : new String[] {"", "   ", "x".repeat(501)}) {
            mockMvc.perform(postJson(correctionUrl(patientId, v1), session, correctionBody(reason, "x")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("reason"));
        }
        mockMvc.perform(postJson(correctionUrl(patientId, v1), session, "{\"content\":{\"allergies\":\"x\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        assertThat(count("anamnesis_entry")).isEqualTo(1);
    }

    @Test
    void anamnesisVaciaOConCampoExcedidoDevuelve400SinEcoDelValor() throws Exception {
        MockHttpSession session = session(false);
        String tooLong = "ZZZ" + "a".repeat(2000);

        mockMvc.perform(postJson(anamnesisUrl(patientId), session, "{\"allergies\":\"  \",\"notes\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        MvcResult excess = mockMvc.perform(postJson(anamnesisUrl(patientId), session,
                        "{\"allergies\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("allergies"))
                .andReturn();

        assertThat(excess.getResponse().getContentAsString()).doesNotContain("ZZZ");
        assertThat(count("clinical_record")).isZero();
        assertThat(count("anamnesis_entry")).isZero();
    }

    @Test
    void putPatchYDeleteNoEstanDisponiblesYNoCambianNada() throws Exception {
        MockHttpSession session = session(false);
        String v1 = createAnamnesis(session, "penicilina");
        List<MockHttpServletRequestBuilder> forbidden = new ArrayList<>();
        for (String url : new String[] {recordUrl(patientId), anamnesisUrl(patientId),
                anamnesisUrl(patientId) + "/" + v1, correctionUrl(patientId, v1)}) {
            forbidden.add(put(url).contentType(MediaType.APPLICATION_JSON).content(anamnesisBody("x", null)));
            forbidden.add(patch(url).contentType(MediaType.APPLICATION_JSON).content(anamnesisBody("x", null)));
            forbidden.add(delete(url));
        }

        for (MockHttpServletRequestBuilder request : forbidden) {
            int httpStatus = mockMvc.perform(withCsrf(request, session)).andReturn().getResponse().getStatus();
            assertThat(httpStatus).isIn(404, 405);
        }

        assertThat(count("anamnesis_entry")).isEqualTo(1);
        assertThat(storedContent()).contains("penicilina");
    }

    @Test
    void pacienteInexistenteDevuelve404EnLecturaYEscritura() throws Exception {
        MockHttpSession session = session(false);
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get(recordUrl(unknown)).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        mockMvc.perform(get(anamnesisUrl(unknown)).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(postJson(anamnesisUrl(unknown), session, anamnesisBody("x", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        assertThat(count("audit_log WHERE action IN ('HC_READ', 'HC_CREATE')")).isZero();
    }

    @Test
    void pacienteInactivoPermiteLeerPeroNoEscribir() throws Exception {
        MockHttpSession session = session(false);

        mockMvc.perform(get(recordUrl(inactivePatientId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opened").value(false));
        mockMvc.perform(postJson(anamnesisUrl(inactivePatientId), session, anamnesisBody("x", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_INACTIVE"));

        assertThat(count("clinical_record")).isZero();
        assertThat(count("anamnesis_entry")).isZero();
    }

    // ------------------------------------------------------------- seguridad

    @Test
    void sinSesionDevuelve401EnTodasLasRutasYNoAuditaLecturaClinica() throws Exception {
        UUID entry = UUID.randomUUID();

        for (MockHttpServletRequestBuilder request : List.of(get(recordUrl(patientId)), get(anamnesisUrl(patientId)))) {
            mockMvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("problem+json")));
        }
        // Las mutaciones sin sesion ni token CSRF las corta primero el filtro CSRF (403), igual que en el resto de la API.
        for (MockHttpServletRequestBuilder request : List.of(
                post(anamnesisUrl(patientId)).contentType(MediaType.APPLICATION_JSON).content(anamnesisBody("x", null)),
                post(correctionUrl(patientId, entry.toString())).contentType(MediaType.APPLICATION_JSON)
                        .content(correctionBody("m", "x")))) {
            assertThat(mockMvc.perform(request).andReturn().getResponse().getStatus()).isIn(401, 403);
        }

        assertThat(count("audit_log WHERE action IN ('HC_READ', 'HC_CREATE', 'HC_CORRECT')")).isZero();
        assertThat(count("anamnesis_entry")).isZero();
    }

    @Test
    void altaSinCsrfDevuelve403CsrfRequiredYNoCreaNada() throws Exception {
        mockMvc.perform(post(anamnesisUrl(patientId))
                        .session(session(false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anamnesisBody("penicilina", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));

        assertThat(count("anamnesis_entry")).isZero();
        assertThat(count("clinical_record")).isZero();
    }

    @Test
    void conCambioDeContrasenaPendienteDevuelve403YNoAuditaLectura() throws Exception {
        mockMvc.perform(get(recordUrl(patientId)).session(session(true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        assertThat(count("audit_log WHERE action = 'HC_READ'")).isZero();
    }

    @Test
    void auditaHcReadHcCreateYHcCorrectConUsuarioPacienteIpEIdentificador() throws Exception {
        MockHttpSession session = session(false);

        mockMvc.perform(get(anamnesisUrl(patientId)).session(session)).andExpect(status().isOk());
        String v1 = createAnamnesis(session, "penicilina");
        MvcResult correction = mockMvc.perform(postJson(correctionUrl(patientId, v1), session,
                        correctionBody("alergia mal cargada", "latex")))
                .andExpect(status().isCreated()).andReturn();
        String v2 = JsonPath.read(correction.getResponse().getContentAsString(), "$.id");

        List<String[]> rows = auditRows();
        assertThat(rows).extracting(row -> row[0]).containsExactly("HC_READ", "HC_CREATE", "HC_CORRECT");
        for (String[] row : rows) {
            assertThat(row[1]).isEqualTo(userId.toString());
            assertThat(row[2]).isEqualTo(patientId.toString());
            assertThat(row[3]).isEqualTo("anamnesis_entry");
            assertThat(row[4]).isEqualTo("127.0.0.1");
        }
        assertThat(rows.get(1)[5]).isEqualTo(v1);
        assertThat(rows.get(2)[5]).isEqualTo(v2);
        assertThat(rows.get(2)[6]).contains(v1).contains("ANAMNESIS").doesNotContain("alergia mal cargada", "latex");
        assertThat(rows.get(1)[6]).contains("ANAMNESIS").doesNotContain("penicilina");
    }

    @Test
    void unaSolicitudRechazadaPorValidacionNoRegistraAuditoriaDeEscritura() throws Exception {
        MockHttpSession session = session(false);

        mockMvc.perform(postJson(anamnesisUrl(patientId), session, "{}")).andExpect(status().isBadRequest());

        assertThat(count("audit_log WHERE action IN ('HC_CREATE', 'HC_CORRECT')")).isZero();
    }

    // ---------------------------------------------------------------- helpers

    private String createAnamnesis(MockHttpSession session, String allergies) throws Exception {
        return createAnamnesisFor(patientId, session, allergies);
    }

    private String createAnamnesisFor(UUID patient, MockHttpSession session, String allergies) throws Exception {
        MvcResult result = mockMvc.perform(postJson(anamnesisUrl(patient), session, anamnesisBody(allergies, null)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MockHttpServletRequestBuilder postJson(String url, MockHttpSession session, String body) {
        return withCsrf(post(url).contentType(MediaType.APPLICATION_JSON).content(body), session);
    }

    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, MockHttpSession session) {
        String token = UUID.randomUUID().toString();
        session.setAttribute(CSRF_ATTR, (CsrfToken) new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", token));
        return request.session(session).header("X-XSRF-TOKEN", token).cookie(new Cookie("XSRF-TOKEN", token));
    }

    private MockHttpSession session(boolean mustChangePassword) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        AuthenticatedUser user = new AuthenticatedUser(userId, UUID.randomUUID(), Role.DENTIST, mustChangePassword);
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_DENTIST"))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.setAttribute("ar.turnosodontologia.consultorio.auth.infrastructure.SessionTimeout.expiresAt",
                Instant.now().plusSeconds(3600).toEpochMilli());
        return session;
    }

    private static String recordUrl(UUID patient) {
        return "/api/v1/patients/" + patient + "/clinical-record";
    }

    private static String anamnesisUrl(UUID patient) {
        return recordUrl(patient) + "/anamnesis";
    }

    private static String correctionUrl(UUID patient, String entryId) {
        return anamnesisUrl(patient) + "/" + entryId + "/corrections";
    }

    private static String anamnesisBody(String allergies, String medications) {
        return "{\"allergies\":" + quote(allergies) + ",\"medications\":" + quote(medications) + "}";
    }

    private static String correctionBody(String reason, String allergies) {
        return "{\"reason\":" + quote(reason) + ",\"content\":{\"allergies\":" + quote(allergies) + "}}";
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private List<String[]> auditRows() throws SQLException {
        List<String[]> rows = new ArrayList<>();
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT action, user_id::text, patient_id::text, entity_type, host(ip), entity_id, details::text "
                                + "FROM audit_log WHERE action IN ('HC_READ', 'HC_CREATE', 'HC_CORRECT') ORDER BY id")) {
            while (result.next()) {
                rows.add(new String[] {result.getString(1), result.getString(2), result.getString(3),
                        result.getString(4), result.getString(5), result.getString(6), result.getString(7)});
            }
        }
        return rows;
    }

    private static String storedContent() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT string_agg(content::text, ' ') FROM anamnesis_entry")) {
            rows.next();
            return String.valueOf(rows.getString(1));
        }
    }

    private static int count(String tableAndFilter) throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + tableAndFilter)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static UUID insertPatient(Connection connection, String dni, boolean active) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone, active) VALUES ('"
                    + id + "', '" + PRACTICE + "', 'Ana', 'Endpoint', '" + dni + "', '1111', " + active + ")");
        }
        return id;
    }
}
