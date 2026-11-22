package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.agenda.domain.PracticeService;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeServiceRepository;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class PublicServicesIT extends AbstractIntegrationTest {

    private static final String LIMIT_MAX_REQUESTS = "app.public-read.rate-limit.max-requests";
    private static final String LIMIT_WINDOW_SECONDS = "app.public-read.rate-limit.window-seconds";
    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    private final WebApplicationContext context;
    private final PracticeServiceRepository practiceServiceRepository;
    private MockMvc mockMvc;

    PublicServicesIT(WebApplicationContext context, PracticeServiceRepository practiceServiceRepository) {
        this.context = context;
        this.practiceServiceRepository = practiceServiceRepository;
    }

    // Ventana pequeña para exceder el límite sin hacer 120 peticiones: cada test usa su propia IP.
    @DynamicPropertySource
    static void rateLimitProperties(DynamicPropertyRegistry registry) {
        registry.add(LIMIT_MAX_REQUESTS, () -> "3");
        registry.add(LIMIT_WINDOW_SECONDS, () -> "60");
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM service");
        }
    }

    @Test
    void devuelveSoloCatalogoPublicoConCamposPublicos() throws Exception {
        savePublic("Centrotota", null, 30);
        savePublic("Dolor dental", "Descripcion del dolor", 45);

        mockMvc.perform(getCatalog("192.168.1.10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").value("Centrotota"))
                .andExpect(jsonPath("$[0].durationMin").value(30))
                .andExpect(jsonPath("$[0].active").doesNotExist())
                .andExpect(jsonPath("$[0].publicVisible").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].practiceId").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("Dolor dental"))
                .andExpect(jsonPath("$[1].description").value("Descripcion del dolor"));
    }

    @Test
    void excluyeInactivasYNovalVisibles() throws Exception {
        savePublic("A Publico", null, 30);
        PracticeService inactivaVisible = PracticeService.create(PRACTICE_ID, "B InactivaVisible", null, 30, true);
        PracticeService activaNoVisible = PracticeService.create(PRACTICE_ID, "C ActivaNoVisible", null, 30, false);
        PracticeService inactivaNoVisible = PracticeService.create(PRACTICE_ID, "D InactivaNoVisible", null, 30, false);
        inactivaVisible.deactivate();
        practiceServiceRepository.save(inactivaVisible);
        practiceServiceRepository.save(activaNoVisible);
        practiceServiceRepository.save(inactivaNoVisible);

        mockMvc.perform(getCatalog("192.168.2.10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("A Publico"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void devuelveListaVaciaCuandoNoHayPublicadas() throws Exception {
        PracticeService inactiva = PracticeService.create(PRACTICE_ID, "A Inactiva", null, 30, true);
        inactiva.deactivate();
        practiceServiceRepository.save(inactiva);

        mockMvc.perform(getCatalog("192.168.3.10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void respuestaIncluyeLaCabeceraDeCacheCorto() throws Exception {
        savePublic("A Publico", null, 30);

        mockMvc.perform(getCatalog("192.168.4.10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60"));
    }

    @Test
    void metodoDistintoAGetEsRechazadoSinModificarPrestaciones() throws Exception {
        List<RequestBuilder> requests = List.of(
                post("/api/v1/public/services").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/public/services/01900000-0000-7000-8000-000000000002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"X\", \"durationMin\": 30}"),
                delete("/api/v1/public/services/01900000-0000-7000-8000-000000000002"));
        for (var request : requests) {
            MvcResult result = mockMvc.perform(request).andReturn();
            assertThat(result.getResponse().getStatus()).isIn(403, 405);
        }
    }

    @Test
    void superaElLimiteDevuelve429ConRetryAfter() throws Exception {
        String ip = "192.168.9.77";
        RequestBuilder request = getCatalog(ip);
        mockMvc.perform(request).andExpect(status().isOk());
        mockMvc.perform(request).andExpect(status().isOk());
        mockMvc.perform(request).andExpect(status().isOk());

        MvcResult blocked = mockMvc.perform(request).andReturn();

        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getHeader("Retry-After")).isNotEmpty();
        assertThat(blocked.getResponse().getContentAsString()).contains("RATE_LIMITED");
        assertThat(blocked.getResponse().getContentAsString()).doesNotContain(ip);
    }

    private PracticeService savePublic(String name, String description, int durationMin) {
        PracticeService service = PracticeService.create(PRACTICE_ID, name, description, durationMin, true);
        return practiceServiceRepository.save(service);
    }

    private static RequestBuilder getCatalog(String remoteAddr) {
        return builder -> {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setRequestURI("/api/v1/public/services");
            request.setRemoteAddr(remoteAddr);
            return request;
        };
    }
}
