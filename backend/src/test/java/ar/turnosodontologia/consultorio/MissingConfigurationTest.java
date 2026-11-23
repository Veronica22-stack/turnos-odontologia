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
                        "DB_MIGRATION_PASSWORD=" + OTHER_SECRET,
                        "MAIL_PROVIDER=log");
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
                        "DB_MIGRATION_USER=migrator",
                        "MAIL_PROVIDER=log");

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("DB_MIGRATION_PASSWORD")
                .satisfies(error -> assertThat(rootMessages(error)).doesNotContain(OTHER_SECRET));
    }

    /** Todas las variables de base estan presentes: asi el unico motivo de fallo puede ser el que se prueba. */
    private static SpringApplicationBuilder configuredApplication() {
        return new SpringApplicationBuilder(ConsultorioApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "DB_URL=jdbc:postgresql://localhost:5432/consultorio",
                        "DB_USER=app",
                        "DB_PASSWORD=" + OTHER_SECRET,
                        "DB_MIGRATION_USER=migrator",
                        "DB_MIGRATION_PASSWORD=" + OTHER_SECRET);
    }

    /** Igual que {@link #configuredApplication()} pero con el email resuelto: aisla el motivo de fallo bajo prueba. */
    private static SpringApplicationBuilder configuredWithMail() {
        return configuredApplication().properties("MAIL_PROVIDER=log");
    }

    @Test
    void fallaMencionandoAuthSessionTimeoutMinCuandoNoEsEntero() {
        SpringApplicationBuilder app = configuredWithMail().properties("AUTH_SESSION_TIMEOUT_MIN=abc");

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("AUTH_SESSION_TIMEOUT_MIN")
                .satisfies(error -> assertThat(rootMessages(error)).doesNotContain(OTHER_SECRET));
    }

    @Test
    void fallaMencionandoCorsAllowedOriginsEnPerfilProductivo() {
        SpringApplicationBuilder app = configuredWithMail();

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("CORS_ALLOWED_ORIGINS")
                .satisfies(error -> assertThat(rootMessages(error)).doesNotContain(OTHER_SECRET));
    }

    @Test
    void fallaMencionandoAuthSessionTimeoutMinCuandoEsCero() {
        SpringApplicationBuilder app = configuredWithMail().properties("AUTH_SESSION_TIMEOUT_MIN=0");

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("AUTH_SESSION_TIMEOUT_MIN")
                .satisfies(error -> assertThat(rootMessages(error)).doesNotContain(OTHER_SECRET));
    }

    @Test
    void fallaMencionandoAuthSessionTimeoutMinCuandoEsNegativo() {
        SpringApplicationBuilder app = configuredWithMail().properties("AUTH_SESSION_TIMEOUT_MIN=-1");

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("AUTH_SESSION_TIMEOUT_MIN")
                .satisfies(error -> assertThat(rootMessages(error)).doesNotContain(OTHER_SECRET));
    }

    // ---- Email de notificaciones (C-13): el arranque falla antes de crear beans, sin base de datos ----

    private static SpringApplicationBuilder configuredWithCors() {
        return configuredApplication().properties("CORS_ALLOWED_ORIGINS=https://turnos.ejemplo.com");
    }

    @Test
    void fallaMencionandoMailProviderCuandoFaltaFueraDeDev() {
        assumeThat(System.getenv("MAIL_PROVIDER")).isNull();
        SpringApplicationBuilder app = configuredWithCors();

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("MAIL_PROVIDER")
                .satisfies(error -> assertThat(rootMessages(error)).doesNotContain(OTHER_SECRET));
    }

    @Test
    void fallaMencionandoMailProviderCuandoElValorEsInvalido() {
        SpringApplicationBuilder app = configuredWithCors().properties("MAIL_PROVIDER=sendgrid");

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("MAIL_PROVIDER")
                .satisfies(error -> assertThat(rootMessages(error)).doesNotContain("sendgrid").doesNotContain(OTHER_SECRET));
    }

    @Test
    void fallaMencionandoMailPasswordConSmtpSinContrasenaYSinMostrarElUsuario() {
        assumeThat(System.getenv("MAIL_PASSWORD")).isNull();
        SpringApplicationBuilder app = configuredWithCors().properties("MAIL_PROVIDER=smtp",
                "MAIL_HOST=smtp.ejemplo.com", "MAIL_PORT=587", "MAIL_FROM=turnos@ejemplo.com",
                "MAIL_USER=usuario-smtp-visible");

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("MAIL_PASSWORD")
                .satisfies(error -> assertThat(rootMessages(error))
                        .doesNotContain("usuario-smtp-visible").doesNotContain(OTHER_SECRET));
    }

    @Test
    void fallaMencionandoNotificationMaxAttemptsCuandoEsCero() {
        SpringApplicationBuilder app = configuredWithCors().properties("MAIL_PROVIDER=log",
                "NOTIFICATION_MAX_ATTEMPTS=0");

        assertThatThrownBy(() -> app.run().close())
                .hasStackTraceContaining("NOTIFICATION_MAX_ATTEMPTS")
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
