package ar.turnosodontologia.consultorio.platform;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.ConsultorioApplication;
import ar.turnosodontologia.consultorio.support.EmbeddedPostgresSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Con la base caída el health debe ser 503/DOWN. Usa su propia instancia embebida para no afectar a la compartida. */
class HealthDownIT {

    @Test
    void healthResponde503ConEstadoDownCuandoLaBaseNoEstaDisponible() throws Exception {
        try (EmbeddedPostgresSupport.Database database = EmbeddedPostgresSupport.startDatabase()) {
            String[] properties = database.properties().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toArray(String[]::new);

            try (ConfigurableApplicationContext app = new SpringApplicationBuilder(ConsultorioApplication.class)
                    .properties(properties)
                    .run("--server.port=0", "--spring.datasource.hikari.connection-timeout=1000")) {
                String port = app.getEnvironment().getProperty("local.server.port");
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                        .GET()
                        .build();
                HttpClient client = HttpClient.newHttpClient();

                HttpResponse<String> up = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(up.statusCode()).isEqualTo(200);

                database.instance().close();

                HttpResponse<String> down = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(down.statusCode()).isEqualTo(503);
                assertThat(down.body()).contains("\"status\":\"DOWN\"");
            }
        }
    }
}
