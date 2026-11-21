package ar.turnosodontologia.consultorio.platform;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class HealthEndpointIT extends AbstractIntegrationTest {

    private final WebApplicationContext context;

    private MockMvc mockMvc;

    HealthEndpointIT(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void healthResponde200ConEstadoUpSinDetalles() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void envNoEstaDisponible() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(notOk());
    }

    @Test
    void infoNoEstaDisponible() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(notOk());
    }

    @Test
    void beansNoEstaDisponible() throws Exception {
        mockMvc.perform(get("/actuator/beans")).andExpect(notOk());
    }

    private static ResultMatcher notOk() {
        return result -> {
            int status = result.getResponse().getStatus();
            if (status == 200) {
                throw new AssertionError("El endpoint no deberia responder 200 pero respondio " + status);
            }
        };
    }
}
