package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.context.WebApplicationContext;

/** Escenarios HTTP de {@code specs/appointments}; reloj real, por eso las fechas son lejanas (2099) o pasadas (2020). */
class AppointmentsEndpointsIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/appointments";
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate DAY = LocalDate.of(2099, 12, 10);
    private static final int WEEKDAY = DAY.getDayOfWeek().getValue();
    private static final LocalDate PAST_DAY = LocalDate.of(2020, 3, 5);
    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private static final String EXPIRES_AT =
            "ar.turnosodontologia.consultorio.auth.infrastructure.SessionTimeout.expiresAt";

    private final WebApplicationContext context;
    private final JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private MockHttpSession session;
    private String csrf;
    private UUID patient;
    private UUID service30;

    AppointmentsEndpointsIT(WebApplicationContext context, JdbcTemplate jdbc) {
        this.context = context;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        session = session();
        csrf = injectCsrf(session);
        patient = AppointmentFixtures.patient(true);
        service30 = AppointmentFixtures.service(30, true, true);
        AppointmentFixtures.workingHours(WEEKDAY, "09:00", "13:00");
    }

    @AfterEach
    void tearDown() {
        AppointmentFixtures.cleanAll();
    }

    // ---- helpers ----

    private static String at(LocalDate day, String time) {
        return LocalDateTime.of(day, java.time.LocalTime.parse(time)).atZone(ZONE).toInstant().toString();
    }

    private static String at(String time) {
        return at(DAY, time);
    }

    private String body(UUID patientId, UUID serviceId, String startsAt, Boolean overbooking, String notes) {
        StringBuilder json = new StringBuilder("{");
        if (patientId != null) {
            json.append("\"patientId\":\"").append(patientId).append("\",");
        }
        if (serviceId != null) {
            json.append("\"serviceId\":\"").append(serviceId).append("\",");
        }
        if (startsAt != null) {
            json.append("\"startsAt\":\"").append(startsAt).append("\",");
        }
        if (overbooking != null) {
            json.append("\"overbooking\":").append(overbooking).append(",");
        }
        if (notes != null) {
            json.append("\"notes\":\"").append(notes).append("\",");
        }
        json.setLength(json.length() - 1);
        return json.append("}").toString();
    }

    private MvcResult book(String startsAt) throws Exception {
        return book(service30, startsAt, null);
    }

    private MvcResult book(UUID serviceId, String startsAt, Boolean overbooking) throws Exception {
        return postJson(BASE, body(patient, serviceId, startsAt, overbooking, null));
    }

    private MvcResult postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).session(session).header("X-XSRF-TOKEN", csrf)
                .cookie(new Cookie("XSRF-TOKEN", csrf)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
    }

    private MvcResult action(UUID id, String name) throws Exception {
        return postJson(BASE + "/" + id + "/" + name, "{}");
    }

    private MvcResult reschedule(UUID id, String startsAt, Boolean overbooking) throws Exception {
        String json = "{\"startsAt\":\"" + startsAt + "\""
                + (overbooking == null ? "" : ",\"overbooking\":" + overbooking) + "}";
        return postJson(BASE + "/" + id + "/reschedule", json);
    }

    private MvcResult getJson(String path) throws Exception {
        return mockMvc.perform(get(path).session(session)).andReturn();
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private static String code(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
    }

    private static <T> T field(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private static UUID idOf(MvcResult result) throws Exception {
        return UUID.fromString(field(result, "$.id"));
    }

    private UUID created(String startsAt) throws Exception {
        MvcResult result = book(startsAt);
        assertThat(status(result)).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return idOf(result);
    }

    private UUID pastConfirmed(String start, String end) {
        return AppointmentFixtures.appointment(patient, service30, at(PAST_DAY, start), at(PAST_DAY, end),
                "CONFIRMED");
    }

    private void assertRejected(MvcResult result, int expectedStatus, String expectedCode) throws Exception {
        assertThat(status(result)).as(result.getResponse().getContentAsString()).isEqualTo(expectedStatus);
        assertThat(code(result)).isEqualTo(expectedCode);
    }

    private int countAppointments() {
        return jdbc.queryForObject("SELECT count(*) FROM appointment", Integer.class);
    }

    // ---- alta ----

    @Test
    void altaExitosaDevuelve201ConLocationYValoresDelServidor() throws Exception {
        MvcResult result = book(at("10:00"));

        assertThat(status(result)).isEqualTo(201);
        UUID id = idOf(result);
        assertThat(result.getResponse().getHeader("Location")).endsWith(BASE + "/" + id);
        assertThat(field(result, "$.status").toString()).isEqualTo("CONFIRMED");
        assertThat(field(result, "$.origin").toString()).isEqualTo("INTERNAL");
        assertThat(field(result, "$.overbooking").toString()).isEqualTo("false");
        assertThat(Instant.parse(field(result, "$.endsAt"))).isEqualTo(Instant.parse(at("10:30")));
        assertThat(Instant.parse(field(result, "$.startsAt"))).isEqualTo(Instant.parse(at("10:00")));
        assertThat((Integer) field(result, "$.durationMin")).isEqualTo(30);
        assertThat(field(result, "$.patientId").toString()).isEqualTo(patient.toString());
        assertThat(field(result, "$.serviceId").toString()).isEqualTo(service30.toString());
    }

    @Test
    void inicioPasadoEs422AppointmentInPastYNoSeCreaNada() throws Exception {
        assertRejected(book(at(PAST_DAY, "10:00")), 422, "APPOINTMENT_IN_PAST");
        assertThat(countAppointments()).isZero();
    }

    @Test
    void camposObligatoriosAusentesONotasLargasDan400SinDevolverValores() throws Exception {
        String leaked = "x".repeat(1001);

        MvcResult noPatient = postJson(BASE, body(null, service30, at("10:00"), null, null));
        MvcResult noService = postJson(BASE, body(patient, null, at("10:00"), null, null));
        MvcResult noStart = postJson(BASE, body(patient, service30, null, null, null));
        MvcResult longNotes = postJson(BASE, body(patient, service30, at("10:00"), null, leaked));

        for (MvcResult rejected : List.of(noPatient, noService, noStart, longNotes)) {
            assertRejected(rejected, 400, "VALIDATION_FAILED");
            assertThat(rejected.getResponse().getContentAsString()).doesNotContain(leaked);
        }
        assertThat(countAppointments()).isZero();
    }

    @Test
    void elFinYLaDuracionEnviadosPorElClienteSeIgnoran() throws Exception {
        String json = "{\"patientId\":\"" + patient + "\",\"serviceId\":\"" + service30 + "\",\"startsAt\":\""
                + at("10:00") + "\",\"endsAt\":\"" + at("12:00") + "\",\"durationMin\":240}";

        MvcResult result = postJson(BASE, json);

        assertThat(status(result)).isEqualTo(201);
        assertThat(Instant.parse(field(result, "$.endsAt"))).isEqualTo(Instant.parse(at("10:30")));
        assertThat((Integer) field(result, "$.durationMin")).isEqualTo(30);
    }

    @Test
    void laDuracionSnapshotNoCambiaSiLaPrestacionSeEditaDespues() throws Exception {
        UUID service45 = AppointmentFixtures.service(45, true, true);
        MvcResult result = book(service45, at("10:00"), null);
        assertThat(status(result)).isEqualTo(201);
        assertThat((Integer) field(result, "$.durationMin")).isEqualTo(45);
        UUID id = idOf(result);

        AppointmentFixtures.updateServiceDuration(service45, 60, true);
        MvcResult read = getJson(BASE + "/" + id);

        assertThat((Integer) field(read, "$.durationMin")).isEqualTo(45);
        assertThat(Instant.parse(field(read, "$.endsAt"))).isEqualTo(Instant.parse(at("10:45")));
    }

    @Test
    void prestacionInexistenteInactivaYNoPublica() throws Exception {
        assertRejected(book(UUID.randomUUID(), at("10:00"), null), 404, "SERVICE_NOT_FOUND");

        UUID inactive = AppointmentFixtures.service(30, false, true);
        assertRejected(book(inactive, at("10:00"), null), 422, "SERVICE_INACTIVE");

        UUID notPublic = AppointmentFixtures.service(30, true, false);
        assertThat(status(book(notPublic, at("10:00"), null))).isEqualTo(201);
    }

    @Test
    void pacienteInexistenteEs404InactivoEs409SinDatosPersonales() throws Exception {
        MvcResult missing = postJson(BASE, body(UUID.randomUUID(), service30, at("10:00"), null, null));
        assertRejected(missing, 404, "PATIENT_NOT_FOUND");

        UUID inactive = AppointmentFixtures.patient(false);
        MvcResult rejected = postJson(BASE, body(inactive, service30, at("10:00"), null, null));
        assertRejected(rejected, 409, "PATIENT_INACTIVE");
        assertThat(rejected.getResponse().getContentAsString()).doesNotContain("Paciente", "IT-APPT");
        assertThat(countAppointments()).isZero();
    }

    // ---- solapamiento ----

    @Test
    void adyacenteSeAceptaYSolapadosSeRechazanAunConSobreturno() throws Exception {
        created(at("10:00"));

        assertThat(status(book(at("10:30")))).isEqualTo(201);
        for (String start : List.of("09:45", "10:00")) {
            assertRejected(book(at(start)), 409, "APPOINTMENT_SLOT_TAKEN");
        }
        assertRejected(book(service30, at("10:15"), true), 409, "APPOINTMENT_SLOT_TAKEN");
    }

    @Test
    void unTurnoMasLargoQueContieneAlExistenteEsSlotTaken() throws Exception {
        created(at("10:00"));
        UUID service75 = AppointmentFixtures.service(75, true, true);

        assertRejected(book(service75, at("09:45"), null), 409, "APPOINTMENT_SLOT_TAKEN");
    }

    @Test
    void elErrorDeSolapamientoNoRevelaDatosDelTurnoExistente() throws Exception {
        UUID existing = created(at("10:00"));

        MvcResult rejected = book(at("10:00"));

        String payload = rejected.getResponse().getContentAsString();
        assertThat(payload).doesNotContain(existing.toString(), patient.toString(), "10:00");
    }

    @Test
    void unTurnoCanceladoLiberaElRangoYLosCompletadosONoShowSiguenOcupando() throws Exception {
        UUID id = created(at("10:00"));
        assertThat(status(action(id, "cancel"))).isEqualTo(200);
        assertThat(status(book(at("10:00")))).isEqualTo(201);

        AppointmentFixtures.appointment(patient, service30, at("11:00"), at("11:30"), "COMPLETED");
        AppointmentFixtures.appointment(patient, service30, at("12:00"), at("12:30"), "NO_SHOW");
        assertRejected(book(at("11:00")), 409, "APPOINTMENT_SLOT_TAKEN");
        assertRejected(book(at("12:00")), 409, "APPOINTMENT_SLOT_TAKEN");
    }

    // ---- bloqueos ----

    @Test
    void turnoDentroDeUnBloqueoEs409SinRevelarElMotivoYAdyacenteSeAcepta() throws Exception {
        AppointmentFixtures.scheduleBlock(at("09:00"), at("11:00"));

        MvcResult rejected = book(at("10:30"));
        assertRejected(rejected, 409, "APPOINTMENT_SLOT_BLOCKED");
        assertThat(rejected.getResponse().getContentAsString()).doesNotContain("IT-APPT");

        assertThat(status(book(at("11:00")))).isEqualTo(201);
    }

    @Test
    void sobreturnoDentroDeUnBloqueoFueraDeHorarioEsRechazado() throws Exception {
        AppointmentFixtures.scheduleBlock(at("18:00"), at("20:00"));

        assertRejected(book(service30, at("18:30"), true), 409, "APPOINTMENT_SLOT_BLOCKED");
    }

    // ---- horario de atencion ----

    @Test
    void dentroDelTramoYBordeFinalExactoSeAcepta() throws Exception {
        assertThat(status(book(at("12:30")))).isEqualTo(201);
    }

    @Test
    void queExcedeElFinDelTramoOEmpiezaAntesEs422() throws Exception {
        assertRejected(book(at("12:45")), 422, "OUTSIDE_WORKING_HOURS");
        assertRejected(book(at("08:45")), 422, "OUTSIDE_WORKING_HOURS");
        assertThat(countAppointments()).isZero();
    }

    @Test
    void tramosContiguosSeFusionanYUnHuecoNoSeCruza() throws Exception {
        AppointmentFixtures.cleanAll();
        patient = AppointmentFixtures.patient(true);
        service30 = AppointmentFixtures.service(30, true, true);
        AppointmentFixtures.workingHours(WEEKDAY, "09:00", "12:00");
        AppointmentFixtures.workingHours(WEEKDAY, "12:00", "15:00");

        assertThat(status(book(at("11:45")))).isEqualTo(201);
        assertThat(status(book(at("09:00")))).isEqualTo(201);
        assertThat(status(book(at("14:30")))).isEqualTo(201);
        assertRejected(book(at("14:45")), 422, "OUTSIDE_WORKING_HOURS");
        assertRejected(book(at("08:45")), 422, "OUTSIDE_WORKING_HOURS");

        AppointmentFixtures.execute("DELETE FROM working_hours");
        AppointmentFixtures.workingHours(WEEKDAY, "09:00", "12:00");
        AppointmentFixtures.workingHours(WEEKDAY, "13:00", "15:00");
        UUID service60 = AppointmentFixtures.service(60, true, true);
        assertRejected(book(service60, at("11:30"), null), 422, "OUTSIDE_WORKING_HOURS");
    }

    @Test
    void cruceDeMedianocheDiaSinAtencionYHoraLocalDelConsultorio() throws Exception {
        AppointmentFixtures.execute("DELETE FROM working_hours");
        AppointmentFixtures.workingHours(WEEKDAY, "20:00", "23:59");
        UUID service30b = service30;
        assertRejected(postJson(BASE, body(patient, service30b, at("23:45"), null, null)), 422,
                "OUTSIDE_WORKING_HOURS");

        AppointmentFixtures.execute("DELETE FROM working_hours");
        assertRejected(book(at("10:00")), 422, "OUTSIDE_WORKING_HOURS");

        AppointmentFixtures.workingHours(WEEKDAY, "09:00", "13:00");
        assertThat(status(postJson(BASE, body(patient, service30, "2099-12-10T12:00:00Z", null, null))))
                .isEqualTo(201);
        assertRejected(postJson(BASE, body(patient, service30, "2099-12-10T11:30:00Z", null, null)), 422,
                "OUTSIDE_WORKING_HOURS");
    }

    @Test
    void sobreturnoFueraDeHorarioSeAceptaYSeDevuelve() throws Exception {
        MvcResult result = book(service30, at("19:00"), true);

        assertThat(status(result)).isEqualTo(201);
        assertThat(field(result, "$.overbooking").toString()).isEqualTo("true");
    }

    // ---- parametros de reserva publica no aplicados ----

    @Test
    void noAplicaAnticipacionMinimaNiGranularidad() throws Exception {
        MvcResult unaligned = book(at("10:07"));
        assertThat(status(unaligned)).isEqualTo(201);
        assertThat(Instant.parse(field(unaligned, "$.startsAt"))).isEqualTo(Instant.parse(at("10:07")));

        String soon = Instant.now().plusSeconds(30 * 60).toString();
        assertThat(status(book(service30, soon, true))).isEqualTo(201);
    }

    // ---- consulta y listado ----

    @Test
    void consultaPorIdNoIncluyeDatosPersonalesYElInexistenteEs404() throws Exception {
        UUID id = created(at("10:00"));

        MvcResult read = getJson(BASE + "/" + id);

        assertThat(status(read)).isEqualTo(200);
        String payload = read.getResponse().getContentAsString();
        assertThat(field(read, "$.patientId").toString()).isEqualTo(patient.toString());
        assertThat(payload).doesNotContain("Paciente", "IT-APPT", "dni", "phone", "email", "firstName", "lastName");

        assertRejected(getJson(BASE + "/" + UUID.randomUUID()), 404, "APPOINTMENT_NOT_FOUND");
        assertRejected(action(UUID.randomUUID(), "cancel"), 404, "APPOINTMENT_NOT_FOUND");
        assertRejected(action(UUID.randomUUID(), "complete"), 404, "APPOINTMENT_NOT_FOUND");
        assertRejected(action(UUID.randomUUID(), "no-show"), 404, "APPOINTMENT_NOT_FOUND");
        assertRejected(reschedule(UUID.randomUUID(), at("15:00"), null), 404, "APPOINTMENT_NOT_FOUND");
    }

    @Test
    void listadoPorRangoDevuelveTodosLosEstadosOrdenadosIncluidoElQueCruzaFrom() throws Exception {
        UUID crossing = AppointmentFixtures.appointment(patient, service30, at("08:45"), at("09:15"), "CONFIRMED");
        UUID cancelled = AppointmentFixtures.appointment(patient, service30, at("10:30"), at("11:00"), "CANCELLED");
        UUID completed = AppointmentFixtures.appointment(patient, service30, at("09:30"), at("10:00"), "COMPLETED");
        UUID confirmed = created(at("11:00"));
        AppointmentFixtures.appointment(patient, service30, at("13:00"), at("13:30"), "CONFIRMED");

        MvcResult list = getJson(BASE + "?from=" + at("09:00") + "&to=" + at("12:00"));

        assertThat(status(list)).isEqualTo(200);
        List<String> ids = JsonPath.read(list.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).containsExactly(crossing.toString(), completed.toString(), cancelled.toString(),
                confirmed.toString());
    }

    @Test
    void rangoDeListadoInvalidoEs400InvalidRange() throws Exception {
        for (String query : List.of("", "?from=" + at("09:00"), "?to=" + at("09:00"),
                "?from=" + at("10:00") + "&to=" + at("10:00"), "?from=" + at("11:00") + "&to=" + at("10:00"))) {
            MvcResult result = getJson(BASE + query);
            assertRejected(result, 400, "INVALID_RANGE");
            assertThat(result.getResponse().getContentAsString()).doesNotContain("2099");
        }
    }

    @Test
    void elListadoAceptaExactamente93DiasYRechazaMasDe93Dias() throws Exception {
        Instant from = Instant.parse("2099-01-01T00:00:00Z");
        UUID inside = AppointmentFixtures.appointment(patient, service30, "2099-02-01T13:00:00Z",
                "2099-02-01T13:30:00Z", "CONFIRMED");

        MvcResult accepted = getJson(BASE + "?from=" + from + "&to=" + from.plus(java.time.Duration.ofDays(93)));
        assertThat(status(accepted)).isEqualTo(200);
        List<String> ids = JsonPath.read(accepted.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).containsExactly(inside.toString());

        MvcResult tooWide = getJson(BASE + "?from=" + from + "&to=" + from.plus(java.time.Duration.ofDays(94)));
        assertRejected(tooWide, 400, "INVALID_RANGE");
        assertThat(tooWide.getResponse().getContentAsString()).doesNotContain("2099");

        MvcResult nanos = getJson(BASE + "?from=" + from + "&to="
                + from.plus(java.time.Duration.ofDays(93)).plusNanos(1));
        assertRejected(nanos, 400, "INVALID_RANGE");
    }

    @Test
    void reprogramarSinStartsAtEs400ValidationFailedYElTurnoNoCambia() throws Exception {
        UUID id = created(at("10:00"));

        MvcResult noStart = postJson(BASE + "/" + id + "/reschedule", "{}");
        MvcResult nullStart = postJson(BASE + "/" + id + "/reschedule", "{\"startsAt\":null}");

        assertRejected(noStart, 400, "VALIDATION_FAILED");
        assertRejected(nullStart, 400, "VALIDATION_FAILED");
        assertThat(field(getJson(BASE + "/" + id), "$.startsAt").toString()).isEqualTo(at("10:00"));
    }

    // ---- cancelar y resultado ----

    @Test
    void cancelaUnTurnoFuturoYUnoYaIniciadoYNoUnTerminal() throws Exception {
        UUID future = created(at("10:00"));
        MvcResult cancelled = action(future, "cancel");
        assertThat(status(cancelled)).isEqualTo(200);
        assertThat(field(cancelled, "$.status").toString()).isEqualTo("CANCELLED");
        assertThat(field(cancelled, "$.cancelledBy").toString()).isEqualTo("DENTIST");
        assertThat(Instant.parse(field(cancelled, "$.cancelledAt"))).isBeforeOrEqualTo(Instant.now());

        UUID started = pastConfirmed("10:00", "10:30");
        assertThat(status(action(started, "cancel"))).isEqualTo(200);

        assertRejected(action(future, "cancel"), 409, "INVALID_STATUS_TRANSITION");
    }

    @Test
    void completaYMarcaAusenteUnTurnoYaIniciadoYSigueOcupandoSuRango() throws Exception {
        UUID one = pastConfirmed("10:00", "10:30");
        UUID two = pastConfirmed("11:00", "11:30");

        MvcResult done = action(one, "complete");
        MvcResult absent = action(two, "no-show");

        assertThat(status(done)).isEqualTo(200);
        assertThat(field(done, "$.status").toString()).isEqualTo("COMPLETED");
        assertThat(status(absent)).isEqualTo(200);
        assertThat(field(absent, "$.status").toString()).isEqualTo("NO_SHOW");

        assertRejected(action(one, "complete"), 409, "INVALID_STATUS_TRANSITION");
        assertRejected(action(two, "complete"), 409, "INVALID_STATUS_TRANSITION");
        assertRejected(action(two, "no-show"), 409, "INVALID_STATUS_TRANSITION");
    }

    @Test
    void marcarResultadoAntesDelInicioEs422YSigueConfirmado() throws Exception {
        UUID future = created(at("10:00"));

        assertRejected(action(future, "complete"), 422, "APPOINTMENT_NOT_STARTED");
        assertRejected(action(future, "no-show"), 422, "APPOINTMENT_NOT_STARTED");

        assertThat(field(getJson(BASE + "/" + future), "$.status").toString()).isEqualTo("CONFIRMED");
    }

    // ---- reprogramar ----

    @Test
    void reprogramaConservandoIdYLiberaElRangoAnterior() throws Exception {
        UUID id = created(at("10:00"));

        MvcResult result = reschedule(id, at("12:00"), null);

        assertThat(status(result)).isEqualTo(200);
        assertThat(idOf(result)).isEqualTo(id);
        assertThat(Instant.parse(field(result, "$.startsAt"))).isEqualTo(Instant.parse(at("12:00")));
        assertThat(Instant.parse(field(result, "$.endsAt"))).isEqualTo(Instant.parse(at("12:30")));
        assertThat(field(result, "$.status").toString()).isEqualTo("CONFIRMED");
        assertThat(status(book(at("10:00")))).isEqualTo(201);
    }

    @Test
    void reprogramarAUnRangoOcupadoEs409YElOriginalQuedaIntacto() throws Exception {
        UUID first = created(at("10:00"));
        created(at("12:00"));

        assertRejected(reschedule(first, at("12:00"), null), 409, "APPOINTMENT_SLOT_TAKEN");

        MvcResult read = getJson(BASE + "/" + first);
        assertThat(Instant.parse(field(read, "$.startsAt"))).isEqualTo(Instant.parse(at("10:00")));
        assertThat(Instant.parse(field(read, "$.endsAt"))).isEqualTo(Instant.parse(at("10:30")));
    }

    @Test
    void reprogramarSolapandoConElPropioRangoAnteriorEsValido() throws Exception {
        UUID id = created(at("10:00"));

        MvcResult result = reschedule(id, at("10:15"), null);

        assertThat(status(result)).isEqualTo(200);
        assertThat(Instant.parse(field(result, "$.startsAt"))).isEqualTo(Instant.parse(at("10:15")));
        assertThat(Instant.parse(field(result, "$.endsAt"))).isEqualTo(Instant.parse(at("10:45")));
    }

    @Test
    void reprogramarConservaLaDuracionSnapshotAunqueLaPrestacionCambieOSeDesactive() throws Exception {
        UUID service45 = AppointmentFixtures.service(45, true, true);
        UUID id = idOf(book(service45, at("10:00"), null));
        AppointmentFixtures.updateServiceDuration(service45, 60, false);

        MvcResult result = reschedule(id, at("11:00"), null);

        assertThat(status(result)).isEqualTo(200);
        assertThat((Integer) field(result, "$.durationMin")).isEqualTo(45);
        assertThat(Instant.parse(field(result, "$.endsAt"))).isEqualTo(Instant.parse(at("11:45")));
        assertThat(field(result, "$.serviceId").toString()).isEqualTo(service45.toString());
    }

    @Test
    void reprogramarADentroDeUnBloqueoOFueraDeHorarioSeRechazaYNoCambia() throws Exception {
        UUID id = created(at("10:00"));
        AppointmentFixtures.scheduleBlock(at("11:00"), at("12:00"));

        assertRejected(reschedule(id, at("11:30"), null), 409, "APPOINTMENT_SLOT_BLOCKED");
        assertRejected(reschedule(id, at("15:00"), null), 422, "OUTSIDE_WORKING_HOURS");
        assertThat(status(reschedule(id, at("15:00"), true))).isEqualTo(200);
    }

    @Test
    void turnoNoReprogramableYInicioPasado() throws Exception {
        UUID cancelled = created(at("10:00"));
        action(cancelled, "cancel");
        assertRejected(reschedule(cancelled, at("12:00"), null), 409, "INVALID_STATUS_TRANSITION");

        UUID started = pastConfirmed("10:00", "10:30");
        assertRejected(reschedule(started, at("12:00"), null), 422, "APPOINTMENT_ALREADY_STARTED");

        UUID future = created(at("11:00"));
        assertRejected(reschedule(future, at(PAST_DAY, "10:00"), null), 422, "APPOINTMENT_IN_PAST");
        assertThat(field(getJson(BASE + "/" + future), "$.startsAt").toString())
                .isEqualTo(at("11:00"));
    }

    // ---- sin borrado ----

    @Test
    void noExisteEndpointDeBorradoYElTurnoNoSeElimina() throws Exception {
        UUID id = created(at("10:00"));

        MvcResult result = mockMvc.perform(delete(BASE + "/" + id).session(session)
                .header("X-XSRF-TOKEN", csrf).cookie(new Cookie("XSRF-TOKEN", csrf))).andReturn();

        assertThat(status(result)).isGreaterThanOrEqualTo(400);
        assertThat(code(result)).isNotBlank();
        assertThat(countAppointments()).isEqualTo(1);
    }

    // ---- sesion ----

    private static MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), Role.DENTIST, false);
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_DENTIST"))));
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

    // ---- logs sin datos personales ----

    @Test
    void notasConDatosPersonalesNoApareceNiEnRespuestaNiEnLogsYElLogLlevaSoloIds() throws Exception {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger root = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            String personal = "Juan Perez DNI 12345678 tel 1155551234";
            MvcResult accepted = postJson(BASE, body(patient, service30, at("10:00"), null, personal));
            MvcResult rejected = postJson(BASE, body(patient, service30, at("10:00"), null, personal));

            assertThat(status(accepted)).isEqualTo(201);
            assertRejected(rejected, 409, "APPOINTMENT_SLOT_TAKEN");
            assertThat(rejected.getResponse().getContentAsString()).doesNotContain("Juan", "12345678", "1155551234");
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + " | " + right);
            assertThat(logs).doesNotContain("Juan", "Perez", "12345678", "1155551234");
            assertThat(logs).contains(idOf(accepted).toString(), patient.toString(), service30.toString());
        } finally {
            root.detachAppender(appender);
        }
    }
}
