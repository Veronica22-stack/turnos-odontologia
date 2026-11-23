package ar.turnosodontologia.consultorio.reservas.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig;
import ar.turnosodontologia.consultorio.notificaciones.NotificationTestConfig.MutableClock;
import ar.turnosodontologia.consultorio.reservas.domain.TokenHasher;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Flujo 4: ver, cancelar y reprogramar con el token del link, con errores genéricos y auditoría. */
@Import(NotificationTestConfig.class)
class BookingManagementEndpointsIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/v1/public/bookings";
    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final String START = "2026-12-10T13:00:00Z";
    private static final String NEW_START = "2026-12-11T13:00:00Z";
    private static final String RAW_TOKEN = "token-de-gestion-abc123-def456_GHI789";
    private static final AtomicInteger IP_SEQ = new AtomicInteger();

    @DynamicPropertySource
    static void rateLimit(DynamicPropertyRegistry registry) {
        registry.add("RATE_LIMIT_PUBLIC_PER_MIN", () -> "3");
    }

    @Autowired private WebApplicationContext context;
    @Autowired private MutableClock clock;

    private MockMvc mockMvc;
    private UUID serviceId;
    private UUID appointmentId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(NOW);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        serviceId = AppointmentFixtures.service(30, true, true);
        AppointmentFixtures.workingHours(4, "09:00", "18:00");
        AppointmentFixtures.workingHours(5, "09:00", "18:00");
        UUID patient = AppointmentFixtures.patient(true);
        appointmentId = AppointmentFixtures.appointment(patient, serviceId, START, "2026-12-10T13:30:00Z", "CONFIRMED");
        insertToken(RAW_TOKEN, "2099-12-11T13:00:00Z");
    }

    @AfterEach
    void cleanUp() {
        AppointmentFixtures.cleanAll();
        AppointmentFixtures.execute("DELETE FROM audit_log");
        clock.reset();
    }

    @Test
    void vistaConTokenValidoEsAcotadaYAudita() throws Exception {
        mockMvc.perform(get(PATH + "/" + RAW_TOKEN).with(ipOnly()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(appointmentId.toString()))
                .andExpect(jsonPath("$.serviceId").value(serviceId.toString()))
                .andExpect(jsonPath("$.serviceName").exists())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.dni").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        assertThat(AppointmentFixtures.count("SELECT count(*) FROM audit_log WHERE action = 'BOOKING_TOKEN_USE'"))
                .isEqualTo(1);
        try (var connection = DATABASE.migratorConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT details FROM audit_log WHERE action = 'BOOKING_TOKEN_USE'")) {
            rows.next();
            assertThat(rows.getString(1)).contains("reason_code", "TOKEN_VIEW").doesNotContain(RAW_TOKEN);
        }
    }

    @Test
    void tokenInvalidoRevocadoYExpiradoDanElMismoError() throws Exception {
        String invalid = mockMvc.perform(get(PATH + "/token-inexistente").with(ipOnly()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"))
                .andExpect(jsonPath("$.status").value(404))
                .andReturn().getResponse().getContentAsString();

        AppointmentFixtures.execute("UPDATE booking_token SET revoked_at = now() WHERE token_hash = '"
                + TokenHasher.hash(RAW_TOKEN) + "'");
        String revoked = mockMvc.perform(get(PATH + "/" + RAW_TOKEN).with(ipOnly()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"))
                .andReturn().getResponse().getContentAsString();

        // El único campo que puede diferir es `instance` (la ruta solicitada): el resto del cuerpo es idéntico.
        assertThat(revoked.replaceAll("\"instance\":\"[^\"]+\"", ""))
                .isEqualTo(invalid.replaceAll("\"instance\":\"[^\"]+\"", ""));
    }

    @Test
    void tokenSoloDaAccesoAlTurnoVinculado() throws Exception {
        UUID otroPaciente = AppointmentFixtures.patient(true);
        UUID otroTurno = AppointmentFixtures.appointment(otroPaciente, serviceId,
                "2026-12-12T13:00:00Z", "2026-12-12T13:30:00Z", "CONFIRMED");
        AppointmentFixtures.execute("INSERT INTO booking_token (id, appointment_id, token_hash, expires_at, "
                + "privacy_notice_version, privacy_accepted_at) VALUES ('" + UUID.randomUUID() + "', '"
                + otroTurno + "', '" + TokenHasher.hash("token-del-otro-turno") + "', '2099-12-12T13:00:00Z', "
                + "'V1', now())");

        mockMvc.perform(get(PATH + "/" + RAW_TOKEN).with(ipOnly()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(appointmentId.toString()));
        mockMvc.perform(get(PATH + "/token-del-otro-turno").with(ipOnly()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(otroTurno.toString()));
    }

    @Test
    void tokenExpiradoDevuelveElMismo404Generico() throws Exception {
        AppointmentFixtures.execute("UPDATE booking_token SET expires_at = '2026-11-30T12:00:00Z' "
                + "WHERE token_hash = '" + TokenHasher.hash(RAW_TOKEN) + "'");
        mockMvc.perform(get(PATH + "/" + RAW_TOKEN).with(ipOnly()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void reprogramacionSinCsrfDevuelve403() throws Exception {
        mockMvc.perform(reschedule(NEW_START).with(ipOnly()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));
        try (var connection = DATABASE.migratorConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT starts_at FROM appointment WHERE id = '" + appointmentId + "'")) {
            rows.next();
            assertThat(rows.getString(1)).isEqualTo("2026-12-10 13:00:00+00");
        }
    }

    @Test
    void enumeracionExcedidaDevuelve429ConRetryAfter() throws Exception {
        String fixedIp = "203.0.113." + (IP_SEQ.incrementAndGet() % 250 + 1);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(PATH + "/token-invalido-" + i)
                    .with(request -> {
                        request.setRemoteAddr(fixedIp);
                        return request;
                    }));
        }
        mockMvc.perform(get(PATH + "/" + RAW_TOKEN)
                        .header("X-Forwarded-For", "203.0.113.200")
                        .with(request -> {
                            request.setRemoteAddr(fixedIp);
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void cancelarConTokenRegistraPatientYRevocaElToken() throws Exception {
        mockMvc.perform(delete(PATH + "/" + RAW_TOKEN).with(csrfAndIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(appointmentId.toString()))
                .andExpect(jsonPath("$.serviceId").value(serviceId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(AppointmentFixtures.statusOf(appointmentId)).isEqualTo("CANCELLED");
        try (var connection = DATABASE.migratorConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT cancelled_by, revoked_at FROM appointment a "
                        + "JOIN booking_token t ON t.appointment_id = a.id WHERE a.id = '" + appointmentId + "'")) {
            rows.next();
            assertThat(rows.getString(1)).isEqualTo("PATIENT");
            assertThat(rows.getString(2)).isNotNull();
        }
    }

    @Test
    void sinCsrfNoCancela() throws Exception {
        mockMvc.perform(delete(PATH + "/" + RAW_TOKEN).with(ipOnly()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));
        assertThat(AppointmentFixtures.statusOf(appointmentId)).isEqualTo("CONFIRMED");
    }

    @Test
    void reprogramarMueveElMismoTurnoYConservaElToken() throws Exception {
        mockMvc.perform(reschedule(NEW_START).with(csrfAndIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(appointmentId.toString()))
                .andExpect(jsonPath("$.serviceId").value(serviceId.toString()));

        try (var connection = DATABASE.migratorConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT starts_at FROM appointment WHERE id = '" + appointmentId + "'")) {
            rows.next();
            assertThat(rows.getString(1)).isEqualTo("2026-12-11 13:00:00+00");
        }
        mockMvc.perform(get(PATH + "/" + RAW_TOKEN).with(ipOnly())).andExpect(status().isOk());
    }

    @Test
    void reprogramarASlotOcupadoDevuelve409YConservaElOriginal() throws Exception {
        UUID patient = AppointmentFixtures.patient(true);
        AppointmentFixtures.appointment(patient, serviceId, NEW_START, "2026-12-11T13:30:00Z", "CONFIRMED");

        mockMvc.perform(reschedule(NEW_START).with(csrfAndIp()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));

        try (var connection = DATABASE.migratorConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT starts_at FROM appointment WHERE id = '" + appointmentId + "'")) {
            rows.next();
            assertThat(rows.getString(1)).isEqualTo("2026-12-10 13:00:00+00");
        }
    }

    @Test
    void turnoPasadoEsSoloLectura() throws Exception {
        AppointmentFixtures.execute("UPDATE appointment SET starts_at = '2026-11-30T13:00:00Z', "
                + "ends_at = '2026-11-30T13:30:00Z' WHERE id = '" + appointmentId + "'");
        mockMvc.perform(get(PATH + "/" + RAW_TOKEN).with(ipOnly()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellable").value(false));
        mockMvc.perform(delete(PATH + "/" + RAW_TOKEN).with(csrfAndIp()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOKING_MODIFICATION_NOT_ALLOWED"));
    }

    private MockHttpServletRequestBuilder reschedule(String startsAt) {
        return post(PATH + "/" + RAW_TOKEN + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startsAt\":\"" + startsAt + "\"}");
    }

    private void insertToken(String raw, String expiresAt) throws Exception {
        AppointmentFixtures.execute("INSERT INTO booking_token (id, appointment_id, token_hash, expires_at, "
                + "privacy_notice_version, privacy_accepted_at) VALUES ('" + UUID.randomUUID() + "', '"
                + appointmentId + "', '" + TokenHasher.hash(raw) + "', '" + expiresAt + "', 'V1', now())");
    }

    private RequestPostProcessor csrfAndIp() {
        return request -> {
            ipOnly().postProcessRequest(request);
            String token = UUID.randomUUID().toString();
            request.addHeader("X-XSRF-TOKEN", token);
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            return request;
        };
    }

    private RequestPostProcessor ipOnly() {
        return request -> {
            int n = IP_SEQ.incrementAndGet();
            request.setRemoteAddr("10." + (n / 65536 % 256) + "." + (n / 256 % 256) + "." + (n % 256));
            return request;
        };
    }
}
