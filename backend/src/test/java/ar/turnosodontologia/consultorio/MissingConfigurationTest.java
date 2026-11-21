package ar.turnosodontologia.consultorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * El arranque debe fallar, sin base de datos, cuando falta una variable obligatoria
 * y nombrar la variable faltante sin revelar valores de otras.
 */
class MissingConfigurationTest {

    private static final String OTHER_SECRET = "valor-secreto-que-no-debe-aparecer";

    @BeforeEach
    void requireCleanEnvironment() {
        // Si el host ya exporta DB_PASSWORD el escenario "falta la variable" no es reproducible.
        assumeThat(System.getenv("DB_PASSWORD")).isNull();
    }

    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(ConsultorioApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "DB_USER=app",
                        "DB_MIGRATION_USER=migrator",
                        "DB_MIGRATION_PASSWORD=" + OTHER_SECRET);
    }

    @Test
    void fallaMencionandoDbPasswordSinPerfil() {
        SpringApplicationBuilder app = application().properties("DB_URL=jdbc:postgresql://localhost:5432/consultorio");

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("DB_PASSWORD")
                .satisfies(error -> assertThat(rootMessages(error)).doesNotContain(OTHER_SECRET));
    }

    @Test
    void fallaMencionandoDbPasswordConPerfilDev() {
        // El perfil dev aporta DB_URL por defecto pero ninguna contrasena.
        SpringApplicationBuilder app = application().profiles("dev");

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("DB_PASSWORD")
                .satisfies(error -> assertThat(rootMessages(error)).doesNotContain(OTHER_SECRET));
    }

    @Test
    void fallaMencionandoLaVariableFaltanteDeMigracion() {
        SpringApplicationBuilder app = new SpringApplicationBuilder(ConsultorioApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "DB_URL=jdbc:postgresql://localhost:5432/consultorio",
                        "DB_USER=app",
                        "DB_PASSWORD=" + OTHER_SECRET,
                        "DB_MIGRATION_USER=migrator");

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("DB_MIGRATION_PASSWORD")
                .satisfies(error -> assertThat(rootMessages(error)).doesNotContain(OTHER_SECRET));
    }

    private static String rootMessages(Throwable error) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            messages.append(current.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
