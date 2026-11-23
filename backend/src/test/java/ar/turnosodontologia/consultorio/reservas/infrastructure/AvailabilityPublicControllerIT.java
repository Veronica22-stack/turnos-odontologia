package ar.turnosodontologia.consultorio.reservas.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.agenda.api.ServiceAvailabilityLookup;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityLookup;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityQuery;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityResult;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityStatus;
import ar.turnosodontologia.consultorio.reservas.api.AvailableSlot;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.infrastructure.PublicReadRateLimiter;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class AvailabilityPublicControllerIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/v1/public/availability";
    private static final String SERVICE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER_SERVICE_ID = "22222222-2222-4222-8222-222222222222";
    private static final String DATE = "2026-03-10";
    private static final String FORWARDED_IP = "203.0.113.9";
    private static final String REMOTE_IP = "198.51.100.7";

    @Autowired private WebApplicationContext context;
    @Autowired private Clock clock;

    @MockitoBean private AvailabilityLookup availabilityLookup;
    @MockitoBean private ServiceAvailabilityLookup serviceAvailabilityLookup;
    @MockitoBean private PublicReadRateLimiter rateLimiter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        lenient().when(rateLimiter.tryAcquire(anyString(), any())).thenReturn(Optional.empty());
        lenient().when(serviceAvailabilityLookup.hasPublicServices(any())).thenReturn(true);
        lenient().when(availabilityLookup.availability(any())).thenReturn(resultConUnSlot());
    }

    @Test
    void anonimoObtiene200SinDatosPersonales() throws Exception {
        MvcResult result = mockMvc.perform(get(PATH)
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.slots[0].startsAt").value("2026-03-10T12:00:00Z"))
                .andExpect(jsonPath("$.slots[0].endsAt").value("2026-03-10T12:30:00Z"))
                .andExpect(jsonPath("$.slots[0].patientId").doesNotExist())
                .andExpect(jsonPath("$.slots[0].appointmentId").doesNotExist())
                .andExpect(jsonPath("$.slots[0].notes").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("email", "dni", "telefono", REMOTE_IP, FORWARDED_IP);

        ArgumentCaptor<AvailabilityQuery> captor = ArgumentCaptor.forClass(AvailabilityQuery.class);
        verify(availabilityLookup).availability(captor.capture());
        assertThat(captor.getValue().audience().name()).isEqualTo("PUBLIC");
        assertThat(captor.getValue().date()).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    void catalogoVacioDevuelve200NoActiveServices() throws Exception {
        when(serviceAvailabilityLookup.hasPublicServices(any())).thenReturn(false);

        mockMvc.perform(get(PATH)
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_ACTIVE_SERVICES"))
                .andExpect(jsonPath("$.slots").isEmpty());

        verifyNoInteractions(availabilityLookup);
    }

    @Test
    void catalogoVacioConOtroServiceIdSigueSiendoNoActiveServices() throws Exception {
        when(serviceAvailabilityLookup.hasPublicServices(any())).thenReturn(false);

        mockMvc.perform(get(PATH)
                        .param("serviceId", OTHER_SERVICE_ID)
                        .param("date", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_ACTIVE_SERVICES"))
                .andExpect(jsonPath("$.slots").isEmpty());
    }

    @Test
    void servicioAjenoConCatalogoNoVacioDevuelve404SinEstadoNoActiveServices() throws Exception {
        when(availabilityLookup.availability(any())).thenThrow(new NotFoundException("SERVICE_NOT_FOUND"));

        String body = mockMvc.perform(get(PATH)
                        .param("serviceId", OTHER_SERVICE_ID)
                        .param("date", DATE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("NO_ACTIVE_SERVICES");
    }

    @Test
    void servicioNoVisibleDevuelve404ServiceNotFound() throws Exception {
        when(availabilityLookup.availability(any())).thenThrow(new NotFoundException("SERVICE_NOT_FOUND"));

        mockMvc.perform(get(PATH)
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
    }

    @Test
    void servicioInactivoDevuelve422ServiceInactive() throws Exception {
        when(availabilityLookup.availability(any())).thenThrow(new BusinessRuleException("SERVICE_INACTIVE"));

        mockMvc.perform(get(PATH)
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SERVICE_INACTIVE"));
    }

    @Test
    void servicioInactivoConOtraFechaSigueSiendo422() throws Exception {
        when(availabilityLookup.availability(any())).thenThrow(new BusinessRuleException("SERVICE_INACTIVE"));

        mockMvc.perform(get(PATH)
                        .param("serviceId", SERVICE_ID)
                        .param("date", "2026-04-15"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SERVICE_INACTIVE"));
    }

    @Test
    void serviceIdMalFormadoDevuelve400ValidationFailed() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("serviceId", "no-es-uuid")
                        .param("date", DATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void fechaInvalidaDevuelve400ValidationFailed() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("serviceId", SERVICE_ID)
                        .param("date", "2026-02-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void parametroAusenteDevuelve400ValidationFailed() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("date", DATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void limiteDeTasaDevuelve429ConRetryAfterYSinIpEnElCuerpo() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), any()))
                .thenReturn(Optional.of(clock.instant().plusSeconds(30)));

        MvcResult result = mockMvc.perform(get(PATH)
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE)
                        .with(request -> {
                            request.setRemoteAddr(REMOTE_IP);
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();

        String retryAfter = result.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotBlank();
        assertThat(Long.parseLong(retryAfter)).isGreaterThanOrEqualTo(1L);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(REMOTE_IP, FORWARDED_IP, "tryAcquire");
    }

    @Test
    void retryAfterNuncaEsMenorAUnSegundo() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), any()))
                .thenReturn(Optional.of(clock.instant().minusSeconds(10)));

        MvcResult result = mockMvc.perform(get(PATH)
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    }

    @Test
    void laClaveDelLimitadorEsRemoteAddrYNuncaXForwardedFor() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("serviceId", SERVICE_ID)
                        .param("date", DATE)
                        .header("X-Forwarded-For", FORWARDED_IP)
                        .with(request -> {
                            request.setRemoteAddr(REMOTE_IP);
                            return request;
                        }))
                .andExpect(status().isOk());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter).tryAcquire(key.capture(), any());
        assertThat(key.getValue()).isEqualTo(REMOTE_IP).isNotEqualTo(FORWARDED_IP);
    }

    @Test
    void ipsDistintasDentroDelLimiteDevuelven200() throws Exception {
        for (String ip : List.of(REMOTE_IP, "198.51.100.8")) {
            mockMvc.perform(get(PATH)
                            .param("serviceId", SERVICE_ID)
                            .param("date", DATE)
                            .with(request -> {
                                request.setRemoteAddr(ip);
                                return request;
                            }))
                    .andExpect(status().isOk());
        }

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter, org.mockito.Mockito.times(2)).tryAcquire(keys.capture(), any());
        assertThat(keys.getAllValues()).containsExactly(REMOTE_IP, "198.51.100.8");
    }

    @Test
    void postNoEstaPermitido() throws Exception {
        assertMetodoRechazado(post(PATH));
    }

    @Test
    void putNoEstaPermitido() throws Exception {
        assertMetodoRechazado(put(PATH));
    }

    @Test
    void deleteNoEstaPermitido() throws Exception {
        assertMetodoRechazado(delete(PATH));
    }

    private void assertMetodoRechazado(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult preflight = mockMvc.perform(get(PATH)
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
}
