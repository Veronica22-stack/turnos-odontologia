package ar.turnosodontologia.consultorio.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.turnosodontologia.consultorio.ConsultorioApplication;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class AccessPolicyIT extends AbstractIntegrationTest {

    private final WebApplicationContext context;

    private MockMvc mockMvc;

    AccessPolicyIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void rutaDeApiSinAutenticacionResponde401() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/cualquier-cosa")).andReturn();

        assertDenied(result, 401);
    }

    @Test
    void rutaInexistenteSinAutenticacionResponde401() throws Exception {
        MvcResult result = mockMvc.perform(get("/no-existe")).andReturn();

        assertDenied(result, 401);
    }

    @Test
    void postAnonimoAlHealthEsRechazado() throws Exception {
        int status = mockMvc.perform(post("/actuator/health")).andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    void elArranqueNoGeneraNiLoguaUnaContrasenaDeSeguridad(CapturedOutput output) {
        try (ConfigurableApplicationContext started = new SpringApplicationBuilder(ConsultorioApplication.class)
                .properties(connectionProperties())
                .run("--server.port=0")) {
            assertThat(started.isRunning()).isTrue();
        }

        assertThat(output.getAll()).doesNotContain("Using generated security password");
    }

    private static String[] connectionProperties() {
        return DATABASE.properties().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    private static void assertDenied(MvcResult result, int expectedStatus) {
        var response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat(response.getHeader("Location")).isNull();
        assertThat(response.getRedirectedUrl()).isNull();
    }
}
