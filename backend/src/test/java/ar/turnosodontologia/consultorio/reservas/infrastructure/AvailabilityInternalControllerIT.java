package ar.turnosodontologia.consultorio.reservas.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityLookup;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityQuery;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityResult;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityStatus;
import ar.turnosodontologia.consultorio.reservas.api.AvailableSlot;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class AvailabilityInternalControllerIT extends AbstractIntegrationTest {

    private static final String SERVICE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String DATE = "2026-03-10";

    @Autowired private WebApplicationContext context;

    @MockitoBean private AvailabilityLookup availabilityLookup;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void dentistaAutenticadoSinCsrfObtiene200ConSlots() throws Exception {
        when(availabilityLookup.availability(any())).thenReturn(resultConUnSlot());

        mockMvc.perform(get("/api/v1/availability")
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE)
                        .session(dentistSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.slots[0].startsAt").value("2026-03-10T12:00:00Z"))
                .andExpect(jsonPath("$.slots[0].endsAt").value("2026-03-10T12:30:00Z"))
                .andExpect(jsonPath("$.slots[0].patientId").doesNotExist())
                .andExpect(jsonPath("$.slots[0].appointmentId").doesNotExist());

        ArgumentCaptor<AvailabilityQuery> captor = ArgumentCaptor.forClass(AvailabilityQuery.class);
        verify(availabilityLookup).availability(captor.capture());
        assertThat(captor.getValue().audience().name()).isEqualTo("INTERNAL");
        assertThat(captor.getValue().date()).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    void anonimoRecibe401Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/availability")
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rolDistintoRecibe403Forbidden() throws Exception {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "otro-rol", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.setAttribute("ar.turnosodontologia.consultorio.auth.infrastructure.SessionTimeout.expiresAt",
                Instant.now().plusSeconds(3600).toEpochMilli());

        mockMvc.perform(get("/api/v1/availability")
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE)
                        .session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void serviceIdMalFormadoDevuelve400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/v1/availability")
                        .param("serviceId", "no-es-uuid")
                        .param("date", DATE)
                        .session(dentistSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void fechaInvalidaDevuelve400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/v1/availability")
                        .param("serviceId", SERVICE_ID)
                        .param("date", "2026-02-30")
                        .session(dentistSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void parametroAusenteDevuelve400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/v1/availability")
                        .param("date", DATE)
                        .session(dentistSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void serviceIdVacioDevuelve400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/v1/availability")
                        .param("serviceId", "   ")
                        .param("date", DATE)
                        .session(dentistSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void fechaConFormatoInvalidoDevuelve400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/v1/availability")
                        .param("serviceId", SERVICE_ID)
                        .param("date", "10-03-2026")
                        .session(dentistSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void postNoEstaPermitido() throws Exception {
        assertMetodoRechazado(post("/api/v1/availability"));
    }

    @Test
    void putNoEstaPermitido() throws Exception {
        assertMetodoRechazado(put("/api/v1/availability"));
    }

    @Test
    void deleteNoEstaPermitido() throws Exception {
        assertMetodoRechazado(delete("/api/v1/availability"));
    }

    @Test
    void vistaInternaIgnoraLosFiltrosDeLaReservaPublica() throws Exception {
        when(availabilityLookup.availability(any())).thenReturn(resultConUnSlot());

        mockMvc.perform(get("/api/v1/availability")
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE)
                        .param("minNoticeMin", "9999")
                        .param("maxAdvanceDays", "0")
                        .session(dentistSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.slots[0].startsAt").value("2026-03-10T12:00:00Z"))
                .andExpect(jsonPath("$.minNoticeMin").doesNotExist())
                .andExpect(jsonPath("$.maxAdvanceDays").doesNotExist());

        ArgumentCaptor<AvailabilityQuery> captor = ArgumentCaptor.forClass(AvailabilityQuery.class);
        verify(availabilityLookup).availability(captor.capture());
        assertThat(captor.getValue().audience().name()).isEqualTo("INTERNAL");
        assertThat(captor.getValue().date()).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    private void assertMetodoRechazado(MockHttpServletRequestBuilder request) throws Exception {
        when(availabilityLookup.availability(any())).thenReturn(resultConUnSlot());
        MockHttpSession session = dentistSession();
        MvcResult preflight = mockMvc.perform(get("/api/v1/availability")
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE)
                        .session(session))
                .andReturn();
        String csrf = preflight.getResponse().getHeader("X-CSRF-TOKEN");

        mockMvc.perform(request
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE)
                        .session(session)
                        .cookie(new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isMethodNotAllowed());
    }

    private static AvailabilityResult resultConUnSlot() {
        return new AvailabilityResult(AvailabilityStatus.OK, List.of(new AvailableSlot(
                Instant.parse("2026-03-10T12:00:00Z"), Instant.parse("2026-03-10T12:30:00Z"))));
    }

    private static MockHttpSession dentistSession() {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), Role.DENTIST, false);
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_DENTIST"))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.setAttribute("ar.turnosodontologia.consultorio.auth.infrastructure.SessionTimeout.expiresAt",
                Instant.now().plusSeconds(3600).toEpochMilli());
        return session;
    }
}
