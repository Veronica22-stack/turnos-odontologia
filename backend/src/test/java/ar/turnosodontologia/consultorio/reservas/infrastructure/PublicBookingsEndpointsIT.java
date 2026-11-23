package ar.turnosodontologia.consultorio.reservas.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig.MutableClock;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Flujo 3 completo: disponibilidad → reserva → turno CONFIRMED → token hasheado → errores públicos. */
@Import(NotificationTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class PublicBookingsEndpointsIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/v1/public/bookings";
    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final String START = "2026-12-10T13:00:00Z";
    private static final AtomicInteger IP_SEQ = new AtomicInteger();

    @DynamicPropertySource
    static void rateLimit(DynamicPropertyRegistry registry) {
        registry.add("RATE_LIMIT_PUBLIC_PER_MIN", () -> "3");
    }

    @Autowired private WebApplicationContext context;
    @Autowired private MutableClock clock;

    private MockMvc mockMvc;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        serviceId = AppointmentFixtures.service(30, true, true);
        AppointmentFixtures.workingHours(4, "09:00", "18:00"); // 2026-12-10 es jueves
        AppointmentFixtures.workingHours(2, "09:00", "18:00"); // 2026-12-01 es martes
    }

    @AfterEach
    void cleanUp() {
        AppointmentFixtures.cleanAll();
        clock.reset();
    }

    @Test
    void reservaAnonimaConCsrfCreaTurnoYTokenSoloHasheado() throws Exception {
        MvcResult result = mockMvc.perform(booking(null).with(csrfAndIp()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").exists())
                .andExpect(jsonPath("$.startsAt").value(START))
                .andExpect(jsonPath("$.endsAt").value("2026-12-10T13:30:00Z"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.serviceName").exists())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.dni").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("ana@example.com", "12345678", "/turno/");
        String appointmentId = body.replaceAll(".*\"bookingId\":\"([^\"]+)\".*", "$1");
        assertThat(AppointmentFixtures.statusOf(UUID.fromString(appointmentId))).isEqualTo("CONFIRMED");
        assertThat(AppointmentFixtures.count("SELECT count(*) FROM booking_token WHERE appointment_id = '"
                + appointmentId + "'")).isEqualTo(1);
        try (var connection = DATABASE.migratorConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT overbooking, duration_min_snapshot FROM appointment "
                        + "WHERE id = '" + appointmentId + "'")) {
            rows.next();
            assertThat(rows.getBoolean(1)).isFalse();
            assertThat(rows.getInt(2)).isEqualTo(30);
        }
        try (var connection = DATABASE.migratorConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT token_hash, privacy_notice_version FROM booking_token "
                        + "WHERE appointment_id = '" + appointmentId + "'")) {
            rows.next();
            assertThat(rows.getString(1)).matches("[0-9a-f]{64}");
            assertThat(rows.getString(2)).isEqualTo("PRIVACY_NOTICE_V1");
        }
    }

    @Test
    void sinCsrfDevuelve403YNoCreaTurno() throws Exception {
        mockMvc.perform(booking(null).with(ipOnly()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));
        assertThat(AppointmentFixtures.countAppointments()).isZero();
    }

    @Test
    void honeypotDevuelveRespuestaGenericaSinEfectos() throws Exception {
        mockMvc.perform(booking("relleno").with(csrfAndIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.bookingId").doesNotExist());
        assertThat(AppointmentFixtures.countAppointments()).isZero();
        assertThat(AppointmentFixtures.count("SELECT count(*) FROM booking_token")).isZero();
    }

    @Test
    void slotOcupadoDevuelve409SinCrearOtroTurno() throws Exception {
        UUID patient = AppointmentFixtures.patient(true);
        AppointmentFixtures.appointment(patient, serviceId, START, "2026-12-10T13:30:00Z", "CONFIRMED");

        mockMvc.perform(booking(null).with(csrfAndIp()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
        assertThat(AppointmentFixtures.countAppointments()).isEqualTo(1);
    }

    @Test
    void validacionIncompletaDevuelve400() throws Exception {
        String body = """
                {"serviceId":"%s","startsAt":"%s","firstName":"Ana","lastName":"Pérez",
                 "dni":"12345678","phone":"1133333333","email":"no-es-email","privacyAccepted":true}
                """.formatted(serviceId, START);
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body).with(csrfAndIp()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void sinAceptarAvisoDePrivacidadDevuelve400() throws Exception {
        String body = """
                {"serviceId":"%s","startsAt":"%s","firstName":"Ana","lastName":"Pérez",
                 "dni":"12345678","phone":"1133333333","email":"ana@example.com","privacyAccepted":false}
                """.formatted(serviceId, START);
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body).with(csrfAndIp()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inicioDentroDeLaAnticipacionMinimaDevuelve422() throws Exception {
        String body = """
                {"serviceId":"%s","startsAt":"%s","firstName":"Ana","lastName":"Pérez",
                 "dni":"12345678","phone":"1133333333","email":"ana@example.com","privacyAccepted":true}
                """.formatted(serviceId, "2026-12-01T13:00:00Z");
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body).with(csrfAndIp()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOKING_TOO_SOON"));
        assertThat(AppointmentFixtures.countAppointments()).isZero();
    }

    @Test
    void elClienteNoPuedeElegirFinNiDuracion() throws Exception {
        String body = """
                {"serviceId":"%s","startsAt":"%s","endsAt":"2030-01-01T00:00:00Z","durationMin":480,
                 "firstName":"Ana","lastName":"Pérez","dni":"12345678","phone":"1133333333",
                 "email":"ana@example.com","privacyAccepted":true}
                """.formatted(serviceId, START);
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body).with(csrfAndIp()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endsAt").value("2026-12-10T13:30:00Z"));
        assertThat(AppointmentFixtures.count(
                "SELECT count(*) FROM appointment WHERE duration_min_snapshot = 480")).isZero();
    }

    @Test
    void prestacionNoPublicaEsRechazada() throws Exception {
        UUID hidden = AppointmentFixtures.service(30, true, false);
        String body = """
                {"serviceId":"%s","startsAt":"%s","firstName":"Ana","lastName":"Pérez",
                 "dni":"12345678","phone":"1133333333","email":"ana@example.com","privacyAccepted":true}
                """.formatted(hidden, START);
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body).with(csrfAndIp()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
        assertThat(AppointmentFixtures.countAppointments()).isZero();
    }

    @Test
    void dniExistenteNoSobreescribeLosDatosDelPaciente() throws Exception {
        UUID existing = UUID.randomUUID();
        String dni = "87654321";
        AppointmentFixtures.execute("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone, email, active) "
                + "VALUES ('" + existing + "', '" + AppointmentFixtures.PRACTICE_ID
                + "', 'IT-APPT-NombreOriginal', 'ApellidoOriginal', '" + dni
                + "', '1100000000', 'original@example.com', true)");
        String body = """
                {"serviceId":"%s","startsAt":"%s","firstName":"Otro","lastName":"Nombre",
                 "dni":"%s","phone":"1199999999","email":"otro@example.com","privacyAccepted":true}
                """.formatted(serviceId, START, dni);

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body).with(csrfAndIp()))
                .andExpect(status().isCreated());

        try (var connection = DATABASE.migratorConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT first_name, phone, email FROM patient WHERE id = '"
                        + existing + "'")) {
            rows.next();
            assertThat(rows.getString(1)).isEqualTo("IT-APPT-NombreOriginal");
            assertThat(rows.getString(2)).isEqualTo("1100000000");
            assertThat(rows.getString(3)).isEqualTo("original@example.com");
        }
        assertThat(AppointmentFixtures.count("SELECT count(*) FROM appointment WHERE patient_id = '"
                + existing + "'")).isEqualTo(1);
    }

    @Test
    void rateLimitDevuelve429ConRetryAfter() throws Exception {
        String fixedIp = "203.0.113." + (IP_SEQ.incrementAndGet() % 250 + 1);
        // El limitador cuenta intentos que llegan al caso de uso (201 o 409 por slot tomado).
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(booking(null).with(csrfAndFixedIp(fixedIp)));
        }
        mockMvc.perform(booking(null).with(csrfAndFixedIp(fixedIp)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void datosPersonalesNoAparecenEnLogs(CapturedOutput output) throws Exception {
        mockMvc.perform(booking(null).with(csrfAndIp())).andExpect(status().isCreated());
        assertThat(output.getAll()).doesNotContain("ana@example.com", "12345678", "1133333333");
    }

    private MockHttpServletRequestBuilder booking(String honeypot) {
        String body = """
                {"serviceId":"%s","startsAt":"%s","firstName":"Ana","lastName":"Pérez",
                 "dni":"12345678","phone":"1133333333","email":"ana@example.com",
                 "website":%s,"privacyAccepted":true}
                """.formatted(serviceId, START, honeypot == null ? "null" : "\"" + honeypot + "\"");
        return post(PATH).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** CSRF de doble envío: la misma cookie legible y cabecera que espera {@code CookieCsrfTokenRepository}. */
    private org.springframework.test.web.servlet.request.RequestPostProcessor csrfAndIp() {
        return request -> {
            ipOnly().postProcessRequest(request);
            String token = UUID.randomUUID().toString();
            request.addHeader("X-XSRF-TOKEN", token);
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            return request;
        };
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor csrfAndFixedIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            String token = UUID.randomUUID().toString();
            request.addHeader("X-XSRF-TOKEN", token);
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            return request;
        };
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor ipOnly() {
        return request -> {
            int n = IP_SEQ.incrementAndGet();
            request.setRemoteAddr("10." + (n / 65536 % 256) + "." + (n / 256 % 256) + "." + (n % 256));
            return request;
        };
    }
}
