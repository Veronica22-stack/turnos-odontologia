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

/** API de evoluciones de punta a punta: contratos HTTP, turno asociado, seguridad y filas de auditoria. */
class EvolutionEndpointsIT extends AbstractIntegrationTest {

    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private static final String PRACTICE = "01900000-0000-7000-8000-000000000001";

    private final WebApplicationContext context;
    private MockMvc mockMvc;
    private UUID userId;
    private UUID patientId;
    private UUID otherPatientId;
    private UUID inactivePatientId;
    private UUID serviceId;

    EvolutionEndpointsIT(WebApplicationContext context) {
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
            patientId = insertPatient(statement, "79100001", true);
            otherPatientId = insertPatient(statement, "79100002", true);
            inactivePatientId = insertPatient(statement, "79100003", false);
        }
        serviceId = AppointmentFixtures.service(30, true, true);
    }

    @AfterEach
    void cleanRows() throws SQLException {
        ClinicalFixtures.deleteEvolutions();
        AppointmentFixtures.cleanAll();
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM odontogram_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM odontogram_entry");
            statement.executeUpdate("DELETE FROM anamnesis_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM anamnesis_entry");
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM audit_log WHERE action IN ('HC_READ', 'HC_CREATE', 'HC_CORRECT')");
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('79100001', '79100002', '79100003')");
        }
    }

    // ---------------------------------------------------------------- contratos

    @Test
    void altaValidaSinTurnoDevuelve201ConAutorDeLaSesionYFechaDelServidor() throws Exception {
        Instant before = Instant.now().minusSeconds(5);

        MvcResult result = mockMvc.perform(postJson(evolutionsUrl(patientId), session(false), body("Control de rutina", null)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.description").value("Control de rutina"))
                .andExpect(jsonPath("$.appointmentId").doesNotExist())
                .andExpect(jsonPath("$.authorId").value(userId.toString()))
                .andExpect(jsonPath("$.correctsEntryId").doesNotExist())
                .andExpect(jsonPath("$.correctedByEntryId").doesNotExist())
                .andReturn();

        Instant createdAt = Instant.parse(JsonPath.read(result.getResponse().getContentAsString(), "$.createdAt"));
        assertThat(createdAt).isAfter(before);
        assertThat(count("clinical_record")).isEqualTo(1);
        assertThat(count("evolution_entry")).isEqualTo(1);
    }

    @Test
    void elClienteNoPuedeFijarIdAutorNiFechaYLosCamposDesconocidosSeIgnoran() throws Exception {
        UUID fakeAuthor = UUID.randomUUID();
        String body = """
                {"description":"texto","id":"%s","authorId":"%s","createdAt":"2001-01-01T00:00:00Z",
                 "clinicalRecordId":"%s","ssn":"123-45-6789"}
                """.formatted(UUID.randomUUID(), fakeAuthor, UUID.randomUUID());

        MvcResult result = mockMvc.perform(postJson(evolutionsUrl(patientId), session(false), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(userId.toString()))
                .andReturn();

        Instant createdAt = Instant.parse(JsonPath.read(result.getResponse().getContentAsString(), "$.createdAt"));
        assertThat(createdAt).isAfter(Instant.now().minusSeconds(10));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("ssn", fakeAuthor.toString());
    }

    @Test
    void sinHistoriaClinicaLaConsultaDevuelve200VacioYNoLaAbre() throws Exception {
        mockMvc.perform(get(evolutionsUrl(patientId)).session(session(false)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.entries.length()").value(0));

        assertThat(count("clinical_record")).isZero();
    }

    @Test
    void elHistorialVaDeLaMasRecienteALaMasAntiguaYMarcaLaCorreccion() throws Exception {
        MockHttpSession session = session(false);
        String first = create(session, "primera", null);
        String second = create(session, "segunda", null);
        String third = create(session, "tercera", null);

        mockMvc.perform(get(evolutionsUrl(patientId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[0].id").value(third))
                .andExpect(jsonPath("$.entries[1].id").value(second))
                .andExpect(jsonPath("$.entries[2].id").value(first));

        MvcResult correction = mockMvc.perform(postJson(correctionUrl(patientId, first), session,
                        correctionBody("error de carga", "primera corregida")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("primera corregida"))
                .andExpect(jsonPath("$.correctsEntryId").value(first))
                .andExpect(jsonPath("$.correctionReason").value("error de carga"))
                .andReturn();
        String corrected = JsonPath.read(correction.getResponse().getContentAsString(), "$.id");

        MvcResult read = mockMvc.perform(get(evolutionsUrl(patientId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(4))
                .andExpect(jsonPath("$.entries[0].id").value(corrected))
                .andExpect(jsonPath("$.entries[3].id").value(first))
                .andReturn();
        String json = read.getResponse().getContentAsString();
        List<String> correctedByFirst = JsonPath.read(json, "$.entries[?(@.id=='" + first + "')].correctedByEntryId");
        List<String> correctedBySecond = JsonPath.read(json, "$.entries[?(@.id=='" + second + "')].correctedByEntryId");
        assertThat(correctedByFirst).containsExactly(corrected);
        assertThat(correctedBySecond).containsOnlyNulls();
    }

    // ---------------------------------------------------------------- turno asociado

    @Test
    void asociaTurnosCompletadosYConfirmadosYaComenzadosSinCambiarSuEstadoYAdmiteVariasPorTurno() throws Exception {
        MockHttpSession session = session(false);
        UUID completed = appointment(patientId, hoursFromNow(-48), "COMPLETED");
        UUID started = appointment(patientId, hoursFromNow(-1), "CONFIRMED");

        mockMvc.perform(postJson(evolutionsUrl(patientId), session, body("atencion completada", completed.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(completed.toString()));
        mockMvc.perform(postJson(evolutionsUrl(patientId), session, body("atencion en curso", started.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(started.toString()));
        mockMvc.perform(postJson(evolutionsUrl(patientId), session, body("otra del mismo turno", started.toString())))
                .andExpect(status().isCreated());

        assertThat(AppointmentFixtures.statusOf(started)).isEqualTo("CONFIRMED");
        assertThat(AppointmentFixtures.statusOf(completed)).isEqualTo("COMPLETED");
        assertThat(count("evolution_entry WHERE appointment_id = '" + started + "'")).isEqualTo(2);
    }

    @Test
    void unTurnoInvalidoDa422ConSuCodigoSinCrearHistoriaNiAuditar() throws Exception {
        MockHttpSession session = session(false);
        UUID foreign = appointment(otherPatientId, hoursFromNow(-48), "COMPLETED");
        UUID cancelled = appointment(patientId, hoursFromNow(-49), "CANCELLED");
        UUID noShow = appointment(patientId, hoursFromNow(-50), "NO_SHOW");
        UUID future = appointment(patientId, hoursFromNow(24), "CONFIRMED");

        assertRule(session, foreign.toString(), "EVOLUTION_APPOINTMENT_NOT_FOUND");
        assertRule(session, UUID.randomUUID().toString(), "EVOLUTION_APPOINTMENT_NOT_FOUND");
        assertRule(session, cancelled.toString(), "EVOLUTION_APPOINTMENT_NOT_ATTENDED");
        assertRule(session, noShow.toString(), "EVOLUTION_APPOINTMENT_NOT_ATTENDED");
        assertRule(session, future.toString(), "EVOLUTION_APPOINTMENT_NOT_STARTED");

        assertThat(count("clinical_record")).isZero();
        assertThat(count("evolution_entry")).isZero();
        assertThat(count("audit_log WHERE action IN ('HC_CREATE', 'HC_CORRECT')")).isZero();
    }

    @Test
    void laCorreccionConservaElTurnoDeLaOriginalEIgnoraUnTurnoEnviadoPorElCliente() throws Exception {
        MockHttpSession session = session(false);
        UUID appointment = appointment(patientId, hoursFromNow(-48), "COMPLETED");
        UUID another = appointment(patientId, hoursFromNow(-72), "COMPLETED");
        String original = create(session, "original", appointment.toString());

        mockMvc.perform(postJson(correctionUrl(patientId, original), session,
                        "{\"reason\":\"error\",\"description\":\"corregida\",\"appointmentId\":\"" + another + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(appointment.toString()));
    }

    // ---------------------------------------------------------------- errores

    @Test
    void erroresDeCorreccionUsanLosCodigosDeLaEspecificacion() throws Exception {
        MockHttpSession session = session(false);
        String original = create(session, "original", null);
        String foreign = createFor(otherPatientId, session, "ajena");

        MvcResult first = mockMvc.perform(postJson(correctionUrl(patientId, original), session,
                        correctionBody("motivo", "B")))
                .andExpect(status().isCreated()).andReturn();
        String b = JsonPath.read(first.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(postJson(correctionUrl(patientId, original), session, correctionBody("otra vez", "X")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_ALREADY_CORRECTED"));
        // Una correccion tambien se corrige (cadena A <- B <- C).
        mockMvc.perform(postJson(correctionUrl(patientId, b), session, correctionBody("corrige B", "C")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correctsEntryId").value(b));
        mockMvc.perform(postJson(correctionUrl(patientId, UUID.randomUUID().toString()), session,
                        correctionBody("m", "x")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_NOT_FOUND"));
        mockMvc.perform(postJson(correctionUrl(patientId, foreign), session, correctionBody("m", "x")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_NOT_FOUND"));

        assertThat(count("evolution_entry")).isEqualTo(4);
    }

    @Test
    void entradasDeAnamnesisYOdontogramaPorLaRutaDeEvolucionesDan404() throws Exception {
        MockHttpSession session = session(false);
        MvcResult anamnesis = mockMvc.perform(postJson(
                        "/api/v1/patients/" + patientId + "/clinical-record/anamnesis", session,
                        "{\"allergies\":\"penicilina\"}"))
                .andExpect(status().isCreated()).andReturn();
        String anamnesisId = JsonPath.read(anamnesis.getResponse().getContentAsString(), "$.id");
        MvcResult odontogram = mockMvc.perform(postJson(
                        "/api/v1/patients/" + patientId + "/clinical-record/odontogram", session,
                        "{\"entries\":[{\"toothCode\":\"11\",\"condition\":\"CARIES\"}]}"))
                .andExpect(status().isCreated()).andReturn();
        String odontogramId = JsonPath.read(odontogram.getResponse().getContentAsString(), "$.entries[0].id");

        for (String id : new String[] {anamnesisId, odontogramId}) {
            mockMvc.perform(postJson(correctionUrl(patientId, id), session, correctionBody("m", "x")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLINICAL_ENTRY_NOT_FOUND"));
        }
        assertThat(count("evolution_entry")).isZero();
    }

    @Test
    void validacionDevuelve400ConElCampoSinEcoDelValorYNoCreaNada() throws Exception {
        MockHttpSession session = session(false);
        String original = create(session, "original", null);

        for (String description : new String[] {"", "   ", "ZZZ" + "a".repeat(5000)}) {
            MvcResult result = mockMvc.perform(postJson(evolutionsUrl(patientId), session, body(description, null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("description"))
                    .andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("ZZZ");
        }
        mockMvc.perform(postJson(evolutionsUrl(patientId), session, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        MvcResult malformed = mockMvc.perform(postJson(evolutionsUrl(patientId), session, body("texto", "no-es-un-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("appointmentId"))
                .andReturn();
        assertThat(malformed.getResponse().getContentAsString()).doesNotContain("no-es-un-uuid");

        for (String reason : new String[] {"", "   ", "x".repeat(501)}) {
            mockMvc.perform(postJson(correctionUrl(patientId, original), session, correctionBody(reason, "nueva")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("reason"));
        }
        mockMvc.perform(postJson(correctionUrl(patientId, original), session, "{\"reason\":\"m\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));

        assertThat(count("evolution_entry")).isEqualTo(1);
    }

    @Test
    void putPatchYDeleteNoEstanDisponiblesYNoCambianNada() throws Exception {
        MockHttpSession session = session(false);
        String original = create(session, "original", null);
        List<MockHttpServletRequestBuilder> forbidden = new ArrayList<>();
        for (String url : new String[] {evolutionsUrl(patientId), evolutionsUrl(patientId) + "/" + original,
                correctionUrl(patientId, original)}) {
            forbidden.add(put(url).contentType(MediaType.APPLICATION_JSON).content(body("alterada", null)));
            forbidden.add(patch(url).contentType(MediaType.APPLICATION_JSON).content(body("alterada", null)));
            forbidden.add(delete(url));
        }

        for (MockHttpServletRequestBuilder request : forbidden) {
            MvcResult result = mockMvc.perform(withCsrf(request, session)).andReturn();
            assertThat(result.getResponse().getStatus()).isIn(404, 405);
            assertThat(result.getResponse().getContentType()).contains("problem+json");
        }

        assertThat(count("evolution_entry")).isEqualTo(1);
        assertThat(count("evolution_entry WHERE description = 'original'")).isEqualTo(1);
    }

    @Test
    void pacienteInexistenteDa404EnLecturaYEscrituraSinAuditoria() throws Exception {
        MockHttpSession session = session(false);
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get(evolutionsUrl(unknown)).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        mockMvc.perform(postJson(evolutionsUrl(unknown), session, body("texto", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));

        assertThat(count("audit_log WHERE action IN ('HC_READ', 'HC_CREATE')")).isZero();
    }

    @Test
    void pacienteDeBajaPermiteLeerEvolucionesPeroNoEscribirNiCorregir() throws Exception {
        MockHttpSession session = session(false);
        String original = create(session, "original", null);
        deactivate(patientId);

        mockMvc.perform(get(evolutionsUrl(patientId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1));
        mockMvc.perform(postJson(evolutionsUrl(inactivePatientId), session, body("texto", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_INACTIVE"));
        mockMvc.perform(postJson(evolutionsUrl(patientId), session, body("texto", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_INACTIVE"));
        mockMvc.perform(postJson(correctionUrl(patientId, original), session, correctionBody("m", "nueva")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_INACTIVE"));

        assertThat(count("evolution_entry")).isEqualTo(1);
        assertThat(count("clinical_record")).isEqualTo(1);
    }

    // ------------------------------------------------------------- seguridad

    @Test
    void sinSesionLaLecturaDa401YLasMutacionesSeCortanSinCrearNiAuditar() throws Exception {
        mockMvc.perform(get(evolutionsUrl(patientId))).andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("problem+json")));
        for (MockHttpServletRequestBuilder request : List.of(
                post(evolutionsUrl(patientId)).contentType(MediaType.APPLICATION_JSON).content(body("texto", null)),
                post(correctionUrl(patientId, UUID.randomUUID().toString())).contentType(MediaType.APPLICATION_JSON)
                        .content(correctionBody("m", "nueva")))) {
            assertThat(mockMvc.perform(request).andReturn().getResponse().getStatus()).isIn(401, 403);
        }

        assertThat(count("audit_log WHERE action IN ('HC_READ', 'HC_CREATE', 'HC_CORRECT')")).isZero();
        assertThat(count("evolution_entry")).isZero();
    }

    @Test
    void altaSinCsrfDevuelve403CsrfRequiredYNoCreaNada() throws Exception {
        mockMvc.perform(post(evolutionsUrl(patientId))
                        .session(session(false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("texto", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));

        assertThat(count("evolution_entry")).isZero();
        assertThat(count("clinical_record")).isZero();
    }

    @Test
    void conCambioDeContrasenaPendienteDevuelve403YNoAudita() throws Exception {
        mockMvc.perform(get(evolutionsUrl(patientId)).session(session(true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(postJson(evolutionsUrl(patientId), session(true), body("texto", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        assertThat(count("audit_log WHERE action IN ('HC_READ', 'HC_CREATE')")).isZero();
        assertThat(count("evolution_entry")).isZero();
    }

    @Test
    void auditaHcReadHcCreateYHcCorrectConUsuarioPacienteIpEIdentificador() throws Exception {
        MockHttpSession session = session(false);

        mockMvc.perform(get(evolutionsUrl(patientId)).session(session)).andExpect(status().isOk());
        String first = create(session, "nota clinica uno", null);
        String second = create(session, "nota clinica dos", null);
        MvcResult correction = mockMvc.perform(postJson(correctionUrl(patientId, first), session,
                        correctionBody("motivo clinico", "texto nuevo")))
                .andExpect(status().isCreated()).andReturn();
        String corrected = JsonPath.read(correction.getResponse().getContentAsString(), "$.id");

        List<String[]> rows = auditRows();
        assertThat(rows).extracting(row -> row[0])
                .containsExactly("HC_READ", "HC_CREATE", "HC_CREATE", "HC_CORRECT");
        for (String[] row : rows) {
            assertThat(row[1]).isEqualTo(userId.toString());
            assertThat(row[2]).isEqualTo(patientId.toString());
            assertThat(row[3]).isEqualTo("evolution_entry");
            assertThat(row[4]).isEqualTo("127.0.0.1");
        }
        assertThat(rows.get(0)[5]).isNull();
        assertThat(rows.get(1)[5]).isEqualTo(first);
        assertThat(rows.get(2)[5]).isEqualTo(second);
        assertThat(rows.get(3)[5]).isEqualTo(corrected);
        assertThat(rows.get(3)[6]).contains(first).contains("EVOLUTION")
                .doesNotContain("motivo clinico", "texto nuevo", "nota clinica");
        assertThat(rows.get(1)[6]).contains("EVOLUTION").doesNotContain("nota clinica");
    }

    @Test
    void unaSolicitudRechazadaPorValidacionNoRegistraAuditoriaDeEscritura() throws Exception {
        MockHttpSession session = session(false);

        mockMvc.perform(postJson(evolutionsUrl(patientId), session, "{}")).andExpect(status().isBadRequest());
        mockMvc.perform(postJson(evolutionsUrl(patientId), session, body("texto", "mal")))
                .andExpect(status().isBadRequest());

        assertThat(count("audit_log WHERE action IN ('HC_CREATE', 'HC_CORRECT')")).isZero();
    }

    // ---------------------------------------------------------------- helpers

    private void assertRule(MockHttpSession session, String appointmentId, String code) throws Exception {
        mockMvc.perform(postJson(evolutionsUrl(patientId), session, body("texto", appointmentId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(code));
    }

    private UUID appointment(UUID patient, Instant startsAt, String status) {
        return AppointmentFixtures.appointment(
                patient, serviceId, startsAt.toString(), startsAt.plusSeconds(1800).toString(), status);
    }

    private static Instant hoursFromNow(int hours) {
        return Instant.now().plusSeconds(hours * 3600L);
    }

    private String create(MockHttpSession session, String description, String appointmentId) throws Exception {
        MvcResult result = mockMvc.perform(postJson(evolutionsUrl(patientId), session, body(description, appointmentId)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createFor(UUID patient, MockHttpSession session, String description) throws Exception {
        MvcResult result = mockMvc.perform(postJson(evolutionsUrl(patient), session, body(description, null)))
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

    private static String evolutionsUrl(UUID patient) {
        return "/api/v1/patients/" + patient + "/clinical-record/evolutions";
    }

    private static String correctionUrl(UUID patient, String entryId) {
        return evolutionsUrl(patient) + "/" + entryId + "/corrections";
    }

    private static String body(String description, String appointmentId) {
        return "{\"description\":" + quote(description) + ",\"appointmentId\":" + quote(appointmentId) + "}";
    }

    private static String correctionBody(String reason, String description) {
        return "{\"reason\":" + quote(reason) + ",\"description\":" + quote(description) + "}";
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
