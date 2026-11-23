package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.support.ClinicalFixtures;
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

/** API del odontograma de punta a punta: contratos HTTP, seguridad y filas de auditoria. */
class OdontogramEndpointsIT extends AbstractIntegrationTest {

    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private static final String PRACTICE = "01900000-0000-7000-8000-000000000001";

    private final WebApplicationContext context;
    private MockMvc mockMvc;
    private UUID userId;
    private UUID patientId;
    private UUID otherPatientId;
    private UUID inactivePatientId;

    OdontogramEndpointsIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() throws SQLException {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanRows();
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery(
                    "SELECT id FROM app_user WHERE email = '" + BOOTSTRAP_EMAIL + "'")) {
                rows.next();
                userId = UUID.fromString(rows.getString(1));
            }
            patientId = insertPatient(statement, "79000001", true);
            otherPatientId = insertPatient(statement, "79000002", true);
            inactivePatientId = insertPatient(statement, "79000003", false);
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM odontogram_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM odontogram_entry");
            statement.executeUpdate("DELETE FROM anamnesis_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM anamnesis_entry");
            ClinicalFixtures.deleteEvolutions();
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM audit_log WHERE action IN ('HC_READ', 'HC_CREATE', 'HC_CORRECT')");
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('79000001', '79000002', '79000003')");
        }
    }

    // ---------------------------------------------------------------- contratos

    @Test
    void registroValidoDevuelve201ConDosEntradasAutorDeLaSesionYFechaDelServidor() throws Exception {
        Instant before = Instant.now().minusSeconds(5);

        MvcResult result = mockMvc.perform(postJson(odontogramUrl(patientId), session(false), batch(
                        entry("16", "OCCLUSAL", "CARIES", "mancha"), entry("21", null, "CROWN", null))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].toothCode").value("16"))
                .andExpect(jsonPath("$.entries[0].surface").value("OCCLUSAL"))
                .andExpect(jsonPath("$.entries[0].condition").value("CARIES"))
                .andExpect(jsonPath("$.entries[0].notes").value("mancha"))
                .andExpect(jsonPath("$.entries[0].authorId").value(userId.toString()))
                .andExpect(jsonPath("$.entries[1].toothCode").value("21"))
                .andExpect(jsonPath("$.entries[1].surface").doesNotExist())
                .andExpect(jsonPath("$.entries[1].correctsEntryId").doesNotExist())
                .andReturn();

        Instant createdAt = Instant.parse(JsonPath.read(result.getResponse().getContentAsString(), "$.entries[0].createdAt"));
        assertThat(createdAt).isAfter(before);
        assertThat(count("clinical_record")).isEqualTo(1);
        assertThat(count("odontogram_entry")).isEqualTo(2);
    }

    @Test
    void elClienteNoPuedeFijarIdAutorNiFechaYLosCamposDesconocidosSeIgnoran() throws Exception {
        UUID fakeAuthor = UUID.randomUUID();
        String body = """
                {"entries":[{"toothCode":"11","condition":"CARIES","id":"%s","authorId":"%s",
                 "createdAt":"2001-01-01T00:00:00Z","clinicalRecordId":"%s","ssn":"123-45-6789"}]}
                """.formatted(UUID.randomUUID(), fakeAuthor, UUID.randomUUID());

        MvcResult result = mockMvc.perform(postJson(odontogramUrl(patientId), session(false), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entries[0].authorId").value(userId.toString()))
                .andReturn();

        Instant createdAt = Instant.parse(JsonPath.read(result.getResponse().getContentAsString(), "$.entries[0].createdAt"));
        assertThat(createdAt).isAfter(Instant.now().minusSeconds(10));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("ssn", fakeAuthor.toString());
    }

    @Test
    void elEstadoActualSeDerivaTrasAltaNuevaAltaYCorreccion() throws Exception {
        MockHttpSession session = session(false);
        String caries = createEntry(session, "36", "OCCLUSAL", "CARIES");
        String filled = createEntry(session, "36", "OCCLUSAL", "FILLED");
        String mesial = createEntry(session, "36", "MESIAL", "CARIES");
        String whole = createEntry(session, "11", null, "MISSING");

        mockMvc.perform(get(odontogramUrl(patientId)).session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.current.length()").value(3))
                .andExpect(jsonPath("$.current[0].id").value(whole))
                .andExpect(jsonPath("$.current[1].id").value(filled))
                .andExpect(jsonPath("$.current[2].id").value(mesial))
                .andExpect(jsonPath("$.history.length()").value(4))
                .andExpect(jsonPath("$.history[0].id").value(whole))
                .andExpect(jsonPath("$.history[3].id").value(caries));

        MvcResult correction = mockMvc.perform(postJson(correctionUrl(patientId, filled), session,
                        correctionBody("era resina", "FILLED", "resina compuesta")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toothCode").value("36"))
                .andExpect(jsonPath("$.surface").value("OCCLUSAL"))
                .andExpect(jsonPath("$.condition").value("FILLED"))
                .andExpect(jsonPath("$.correctsEntryId").value(filled))
                .andExpect(jsonPath("$.correctionReason").value("era resina"))
                .andReturn();
        String corrected = JsonPath.read(correction.getResponse().getContentAsString(), "$.id");

        MvcResult read = mockMvc.perform(get(odontogramUrl(patientId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current[1].id").value(corrected))
                .andExpect(jsonPath("$.history.length()").value(5))
                .andExpect(jsonPath("$.history[0].id").value(corrected))
                .andReturn();
        String json = read.getResponse().getContentAsString();
        List<String> correctedByFilled = JsonPath.read(json, "$.history[?(@.id=='" + filled + "')].correctedByEntryId");
        List<String> correctedByCaries = JsonPath.read(json, "$.history[?(@.id=='" + caries + "')].correctedByEntryId");
        assertThat(correctedByFilled).containsExactly(corrected);
        assertThat(correctedByCaries).containsOnlyNulls();
    }

    @Test
    void laCorreccionIgnoraPiezaYCaraEnviadasPorElCliente() throws Exception {
        MockHttpSession session = session(false);
        String original = createEntry(session, "46", null, "CARIES");

        mockMvc.perform(postJson(correctionUrl(patientId, original), session,
                        "{\"reason\":\"error\",\"condition\":\"HEALTHY\",\"toothCode\":\"11\",\"surface\":\"MESIAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toothCode").value("46"))
                .andExpect(jsonPath("$.surface").doesNotExist())
                .andExpect(jsonPath("$.condition").value("HEALTHY"));
    }

    @Test
    void sinHistoriaClinicaLaConsultaDevuelve200VacioYNoLaAbre() throws Exception {
        mockMvc.perform(get(odontogramUrl(patientId)).session(session(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.length()").value(0))
                .andExpect(jsonPath("$.history.length()").value(0));

        assertThat(count("clinical_record")).isZero();
    }

    @Test
    void erroresDeCorreccionUsanLosCodigosDeLaEspecificacion() throws Exception {
        MockHttpSession session = session(false);
        String superseded = createEntry(session, "46", null, "CARIES");
        String current = createEntry(session, "46", null, "CROWN");
        String surface = createEntry(session, "36", "OCCLUSAL", "CARIES");
        String foreign = createEntryFor(otherPatientId, session, "46", null, "CARIES");

        mockMvc.perform(postJson(correctionUrl(patientId, superseded), session, correctionBody("m", "HEALTHY", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_NOT_CURRENT"));
        mockMvc.perform(postJson(correctionUrl(patientId, UUID.randomUUID().toString()), session,
                        correctionBody("m", "HEALTHY", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_NOT_FOUND"));
        mockMvc.perform(postJson(correctionUrl(patientId, foreign), session, correctionBody("m", "HEALTHY", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_NOT_FOUND"));
        mockMvc.perform(postJson(correctionUrl(patientId, surface), session, correctionBody("m", "MISSING", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ODONTOGRAM_SURFACE_NOT_ALLOWED"));
        mockMvc.perform(postJson(correctionUrl(patientId, current), session, correctionBody("m", "HEALTHY", null)))
                .andExpect(status().isCreated());
        mockMvc.perform(postJson(correctionUrl(patientId, current), session, correctionBody("otra", "FILLED", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_ALREADY_CORRECTED"));

        assertThat(count("odontogram_entry")).isEqualTo(5);
    }

    @Test
    void unaEntradaDeAnamnesisPorLaRutaDelOdontogramaDa404() throws Exception {
        MockHttpSession session = session(false);
        MvcResult anamnesis = mockMvc.perform(postJson(
                        "/api/v1/patients/" + patientId + "/clinical-record/anamnesis", session,
                        "{\"allergies\":\"penicilina\"}"))
                .andExpect(status().isCreated()).andReturn();
        String anamnesisId = JsonPath.read(anamnesis.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(postJson(correctionUrl(patientId, anamnesisId), session, correctionBody("m", "HEALTHY", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_NOT_FOUND"));
    }

    @Test
    void correccionSinMotivoOConEstadoInvalidoDevuelve400SobreElCampo() throws Exception {
        MockHttpSession session = session(false);
        String original = createEntry(session, "46", null, "CARIES");

        for (String reason : new String[] {"", "   ", "x".repeat(501)}) {
            mockMvc.perform(postJson(correctionUrl(patientId, original), session, correctionBody(reason, "HEALTHY", null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("reason"));
        }
        mockMvc.perform(postJson(correctionUrl(patientId, original), session, "{\"condition\":\"HEALTHY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        mockMvc.perform(postJson(correctionUrl(patientId, original), session, correctionBody("m", "FRACTURED", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("condition"));
        assertThat(count("odontogram_entry")).isEqualTo(1);
    }

    @Test
    void validacionDelRegistroDevuelve400ConElCampoSinEcoDelValorYNoCreaNada() throws Exception {
        MockHttpSession session = session(false);

        for (String tooth : new String[] {"19", "56", "90", "00", "1", "111"}) {
            MvcResult result = mockMvc.perform(postJson(odontogramUrl(patientId), session,
                            batch(entry("11", null, "CARIES", null), entry(tooth, null, "CARIES", null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("entries[1].toothCode"))
                    .andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("\"" + tooth + "\"");
        }
        mockMvc.perform(postJson(odontogramUrl(patientId), session, batch(entry("11", null, "FRACTURED", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries[0].condition"));
        mockMvc.perform(postJson(odontogramUrl(patientId), session, batch(entry("11", "MESIAL", "MISSING", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries[0].surface"));
        mockMvc.perform(postJson(odontogramUrl(patientId), session,
                        batch(entry("11", null, "CARIES", "ZZZ" + "a".repeat(500)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries[0].notes"));
        mockMvc.perform(postJson(odontogramUrl(patientId), session, "{\"entries\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries"));
        mockMvc.perform(postJson(odontogramUrl(patientId), session, "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(postJson(odontogramUrl(patientId), session, batch(
                        entry("36", "OCCLUSAL", "CARIES", null), entry("36", "OCCLUSAL", "FILLED", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries"));

        assertThat(count("clinical_record")).isZero();
        assertThat(count("odontogram_entry")).isZero();
    }

    @Test
    void unLoteDeMasDe64EstadosDevuelve400() throws Exception {
        List<String> entries = new ArrayList<>();
        String[] surfaces = {"OCCLUSAL", "MESIAL"};
        for (int quadrant = 1; quadrant <= 4; quadrant++) {
            for (int tooth = 1; tooth <= 8; tooth++) {
                entries.add(entry(quadrant + "" + tooth, null, "HEALTHY", null));
                for (String surface : surfaces) {
                    entries.add(entry(quadrant + "" + tooth, surface, "CARIES", null));
                }
            }
        }
        assertThat(entries).hasSize(96);

        mockMvc.perform(postJson(odontogramUrl(patientId), session(false), batch(entries.toArray(String[]::new))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries"));
        assertThat(count("odontogram_entry")).isZero();
    }

    @Test
    void putPatchYDeleteNoEstanDisponiblesYNoCambianNada() throws Exception {
        MockHttpSession session = session(false);
        String original = createEntry(session, "46", null, "CARIES");
        List<MockHttpServletRequestBuilder> forbidden = new ArrayList<>();
        for (String url : new String[] {odontogramUrl(patientId), odontogramUrl(patientId) + "/" + original,
                correctionUrl(patientId, original)}) {
            forbidden.add(put(url).contentType(MediaType.APPLICATION_JSON).content(batch(entry("46", null, "HEALTHY", null))));
            forbidden.add(patch(url).contentType(MediaType.APPLICATION_JSON).content(batch(entry("46", null, "HEALTHY", null))));
            forbidden.add(delete(url));
        }

        for (MockHttpServletRequestBuilder request : forbidden) {
            MvcResult result = mockMvc.perform(withCsrf(request, session)).andReturn();
            assertThat(result.getResponse().getStatus()).isIn(404, 405);
            assertThat(result.getResponse().getContentType()).contains("problem+json");
        }

        assertThat(count("odontogram_entry")).isEqualTo(1);
        assertThat(count("odontogram_entry WHERE condition = 'CARIES'")).isEqualTo(1);
    }

    @Test
    void pacienteInexistenteDa404EnLecturaYEscrituraSinAuditoria() throws Exception {
        MockHttpSession session = session(false);
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get(odontogramUrl(unknown)).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        mockMvc.perform(postJson(odontogramUrl(unknown), session, batch(entry("11", null, "CARIES", null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));

        assertThat(count("audit_log WHERE action IN ('HC_READ', 'HC_CREATE')")).isZero();
    }

    @Test
    void pacienteDeBajaPermiteLeerElOdontogramaPeroNoEscribir() throws Exception {
        MockHttpSession session = session(false);
        createEntry(session, "11", null, "CARIES");
        deactivate(patientId);

        mockMvc.perform(get(odontogramUrl(patientId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.length()").value(1));
        mockMvc.perform(postJson(odontogramUrl(inactivePatientId), session, batch(entry("11", null, "CARIES", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_INACTIVE"));
        mockMvc.perform(postJson(odontogramUrl(patientId), session, batch(entry("12", null, "CARIES", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_INACTIVE"));

        assertThat(count("odontogram_entry")).isEqualTo(1);
        assertThat(count("clinical_record")).isEqualTo(1);
    }

    // ------------------------------------------------------------- seguridad

    @Test
    void sinSesionLaLecturaDa401YLasMutacionesSeCortanSinCrearNiAuditar() throws Exception {
        UUID entry = UUID.randomUUID();

        mockMvc.perform(get(odontogramUrl(patientId))).andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("problem+json")));
        for (MockHttpServletRequestBuilder request : List.of(
                post(odontogramUrl(patientId)).contentType(MediaType.APPLICATION_JSON)
                        .content(batch(entry("11", null, "CARIES", null))),
                post(correctionUrl(patientId, entry.toString())).contentType(MediaType.APPLICATION_JSON)
                        .content(correctionBody("m", "HEALTHY", null)))) {
            assertThat(mockMvc.perform(request).andReturn().getResponse().getStatus()).isIn(401, 403);
        }

        assertThat(count("audit_log WHERE action IN ('HC_READ', 'HC_CREATE', 'HC_CORRECT')")).isZero();
        assertThat(count("odontogram_entry")).isZero();
    }

    @Test
    void altaSinCsrfDevuelve403CsrfRequiredYNoCreaNada() throws Exception {
        mockMvc.perform(post(odontogramUrl(patientId))
                        .session(session(false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(entry("11", null, "CARIES", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));

        assertThat(count("odontogram_entry")).isZero();
        assertThat(count("clinical_record")).isZero();
    }

    @Test
    void conCambioDeContrasenaPendienteDevuelve403YNoAudita() throws Exception {
        mockMvc.perform(get(odontogramUrl(patientId)).session(session(true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(postJson(odontogramUrl(patientId), session(true), batch(entry("11", null, "CARIES", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        assertThat(count("audit_log WHERE action IN ('HC_READ', 'HC_CREATE')")).isZero();
        assertThat(count("odontogram_entry")).isZero();
    }

    @Test
    void auditaHcReadUnHcCreatePorEntradaYHcCorrectConUsuarioPacienteIpEIdentificador() throws Exception {
        MockHttpSession session = session(false);

        mockMvc.perform(get(odontogramUrl(patientId)).session(session)).andExpect(status().isOk());
        MvcResult created = mockMvc.perform(postJson(odontogramUrl(patientId), session, batch(
                        entry("16", "OCCLUSAL", "CARIES", "nota clinica"), entry("21", null, "CROWN", null),
                        entry("48", null, "MISSING", null))))
                .andExpect(status().isCreated()).andReturn();
        String first = JsonPath.read(created.getResponse().getContentAsString(), "$.entries[0].id");
        String second = JsonPath.read(created.getResponse().getContentAsString(), "$.entries[1].id");
        String third = JsonPath.read(created.getResponse().getContentAsString(), "$.entries[2].id");
        MvcResult correction = mockMvc.perform(postJson(correctionUrl(patientId, first), session,
                        correctionBody("motivo clinico", "FILLED", "otra nota")))
                .andExpect(status().isCreated()).andReturn();
        String corrected = JsonPath.read(correction.getResponse().getContentAsString(), "$.id");

        List<String[]> rows = auditRows();
        assertThat(rows).extracting(row -> row[0])
                .containsExactly("HC_READ", "HC_CREATE", "HC_CREATE", "HC_CREATE", "HC_CORRECT");
        for (String[] row : rows) {
            assertThat(row[1]).isEqualTo(userId.toString());
            assertThat(row[2]).isEqualTo(patientId.toString());
            assertThat(row[3]).isEqualTo("odontogram_entry");
            assertThat(row[4]).isEqualTo("127.0.0.1");
        }
        assertThat(rows.get(0)[5]).isNull();
        assertThat(rows.subList(1, 4)).extracting(row -> row[5]).containsExactlyInAnyOrder(first, second, third);
        assertThat(rows.get(4)[5]).isEqualTo(corrected);
        assertThat(rows.get(4)[6]).contains(first).contains("ODONTOGRAM")
                .doesNotContain("motivo clinico", "otra nota", "FILLED", "OCCLUSAL", "toothCode");
        assertThat(rows.get(1)[6]).contains("ODONTOGRAM").doesNotContain("nota clinica", "CARIES", "OCCLUSAL");
    }

    @Test
    void unaSolicitudRechazadaPorValidacionNoRegistraAuditoriaDeEscritura() throws Exception {
        MockHttpSession session = session(false);

        mockMvc.perform(postJson(odontogramUrl(patientId), session, "{}")).andExpect(status().isBadRequest());
        mockMvc.perform(postJson(odontogramUrl(patientId), session, batch(entry("19", null, "CARIES", null))))
                .andExpect(status().isBadRequest());

        assertThat(count("audit_log WHERE action IN ('HC_CREATE', 'HC_CORRECT')")).isZero();
    }

    // ---------------------------------------------------------------- helpers

    private String createEntry(MockHttpSession session, String tooth, String surface, String condition) throws Exception {
        return createEntryFor(patientId, session, tooth, surface, condition);
    }

    private String createEntryFor(UUID patient, MockHttpSession session, String tooth, String surface, String condition)
            throws Exception {
        MvcResult result = mockMvc.perform(postJson(odontogramUrl(patient), session,
                        batch(entry(tooth, surface, condition, null))))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.entries[0].id");
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

    private static String odontogramUrl(UUID patient) {
        return "/api/v1/patients/" + patient + "/clinical-record/odontogram";
    }

    private static String correctionUrl(UUID patient, String entryId) {
        return odontogramUrl(patient) + "/" + entryId + "/corrections";
    }

    private static String entry(String tooth, String surface, String condition, String notes) {
        return "{\"toothCode\":" + quote(tooth) + ",\"surface\":" + quote(surface) + ",\"condition\":" + quote(condition)
                + ",\"notes\":" + quote(notes) + "}";
    }

    private static String batch(String... entries) {
        return "{\"entries\":[" + String.join(",", entries) + "]}";
    }

    private static String correctionBody(String reason, String condition, String notes) {
        return "{\"reason\":" + quote(reason) + ",\"condition\":" + quote(condition) + ",\"notes\":" + quote(notes) + "}";
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

    private void deactivate(UUID patient) throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE patient SET active = false WHERE id = '" + patient + "'");
        }
    }

    private static int count(String tableAndFilter) throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + tableAndFilter)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static UUID insertPatient(Statement statement, String dni, boolean active) throws SQLException {
        UUID id = UUID.randomUUID();
        statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone, active) VALUES ('"
                + id + "', '" + PRACTICE + "', 'Ana', 'Endpoint', '" + dni + "', '1111', " + active + ")");
        return id;
    }
}
