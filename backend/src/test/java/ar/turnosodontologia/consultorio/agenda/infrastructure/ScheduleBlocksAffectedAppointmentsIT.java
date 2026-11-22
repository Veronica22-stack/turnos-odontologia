package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** {@code affectedAppointments} de los bloqueos con el proveedor real de turnos de C-11 (spec schedule-blocks). */
class ScheduleBlocksAffectedAppointmentsIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/schedule-blocks";
    private static final String CSRF_ATTR = HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN");
    private static final String EXPIRES_AT =
            "ar.turnosodontologia.consultorio.auth.infrastructure.SessionTimeout.expiresAt";

    private final WebApplicationContext context;
    private MockMvc mockMvc;
    private MockHttpSession session;
    private String csrf;
    private UUID patient;
    private UUID service;

    ScheduleBlocksAffectedAppointmentsIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        session = session();
        csrf = injectCsrf(session);
        patient = AppointmentFixtures.patient(true);
        service = AppointmentFixtures.service(30, true, true);
    }

    @AfterEach
    void tearDown() {
        AppointmentFixtures.cleanAll();
    }

    private static String at(String time) {
        return "2099-12-10T" + time + ":00Z";
    }

    private UUID appointment(String start, String end, String status) {
        return AppointmentFixtures.appointment(patient, service, at(start), at(end), status);
    }

    private MvcResult createBlock(String start, String end) throws Exception {
        return mockMvc.perform(post(BASE).session(session).header("X-XSRF-TOKEN", csrf)
                .cookie(new Cookie("XSRF-TOKEN", csrf)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startsAt\":\"" + at(start) + "\",\"endsAt\":\"" + at(end)
                        + "\",\"reason\":\"IT-APPT\"}")).andReturn();
    }

    private static List<String> affectedIds(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.affectedAppointments[*].appointmentId");
    }

    @Test
    void elBloqueoSeGuardaYListaLosTurnosConfirmadosAfectadosSinModificarlos() throws Exception {
        UUID late = appointment("11:00", "11:30", "CONFIRMED");
        UUID early = appointment("10:00", "10:30", "CONFIRMED");

        MvcResult created = createBlock("09:00", "12:00");

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(affectedIds(created)).containsExactly(early.toString(), late.toString());
        UUID blockId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        MvcResult read = mockMvc.perform(get(BASE + "/" + blockId).session(session)).andReturn();
        assertThat(affectedIds(read)).containsExactly(early.toString(), late.toString());
        // Los turnos siguen en su estado original.
        assertThat(AppointmentFixtures.statusOf(early)).isEqualTo("CONFIRMED");
        assertThat(AppointmentFixtures.statusOf(late)).isEqualTo("CONFIRMED");
    }

    @Test
    void sinTurnosEnElRangoLaListaEsVaciaYLaOperacionExitosa() throws Exception {
        MvcResult created = createBlock("09:00", "12:00");

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(affectedIds(created)).isEmpty();
    }

    @Test
    void canceladosCompletadosAusentesAdyacentesYDeOtroEstadoNoSeListan() throws Exception {
        appointment("10:00", "10:30", "CANCELLED");
        appointment("09:00", "09:30", "COMPLETED");
        appointment("09:30", "10:00", "NO_SHOW");
        appointment("12:00", "12:30", "CONFIRMED");

        MvcResult created = createBlock("09:00", "12:00");

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(affectedIds(created)).isEmpty();
    }

    @Test
    void elTurnoQueCruzaElBordeDelBloqueoSeLista() throws Exception {
        UUID crossing = appointment("11:45", "12:15", "CONFIRMED");

        MvcResult created = createBlock("09:00", "12:00");

        assertThat(affectedIds(created)).containsExactly(crossing.toString());
    }

    @Test
    void editarElBloqueoTambienInformaLosTurnosAfectados() throws Exception {
        UUID id = UUID.fromString(JsonPath.read(createBlock("09:00", "10:00").getResponse().getContentAsString(),
                "$.id"));
        UUID inside = appointment("10:30", "11:00", "CONFIRMED");

        MvcResult updated = mockMvc.perform(put(BASE + "/" + id).session(session).header("X-XSRF-TOKEN", csrf)
                .cookie(new Cookie("XSRF-TOKEN", csrf)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startsAt\":\"" + at("09:00") + "\",\"endsAt\":\"" + at("12:00")
                        + "\",\"reason\":\"IT-APPT\"}")).andReturn();

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(affectedIds(updated)).containsExactly(inside.toString());
    }

    @Test
    void unBloqueoSolapadoSigueDandoScheduleBlockOverlap() throws Exception {
        createBlock("09:00", "11:00");

        MvcResult overlapping = createBlock("10:00", "12:00");

        assertThat(overlapping.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(overlapping.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("SCHEDULE_BLOCK_OVERLAP");
    }

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
}
