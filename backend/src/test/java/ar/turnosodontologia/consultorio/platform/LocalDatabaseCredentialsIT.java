package ar.turnosodontologia.consultorio.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.ConsultorioApplication;
import ar.turnosodontologia.consultorio.support.EmbeddedPostgresSupport;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Una DB_PASSWORD incorrecta detiene el arranque nombrando al usuario de base y sin exponer ninguna contraseña. */
@ExtendWith(OutputCaptureExtension.class)
class LocalDatabaseCredentialsIT {

    private static final String WRONG_PASSWORD = "contrasena-incorrecta-xyz";

    private static String[] properties(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    private static String stackTraceOf(Throwable error) {
        StringWriter text = new StringWriter();
        error.printStackTrace(new PrintWriter(text));
        return text.toString();
    }

    @Test
    void unaContrasenaIncorrectaDetieneElArranqueNombrandoAlUsuarioSinExponerContrasenas(CapturedOutput output) {
        try (EmbeddedPostgresSupport.Database database = EmbeddedPostgresSupport.startDatabase()) {
            Map<String, String> wrong = new java.util.LinkedHashMap<>(database.properties());
            wrong.put("DB_PASSWORD", WRONG_PASSWORD);
            String appUser = database.credentials().appUser();

            assertThatThrownBy(() -> new SpringApplicationBuilder(ConsultorioApplication.class)
                            .properties(properties(wrong))
                            .run("--server.port=0")
                            .close())
                    .satisfies(error -> {
                        String trace = stackTraceOf(error);
                        // Hibernate oculta la causa en la excepcion; el error de autenticacion queda en el log de arranque.
                        assertThat(trace + output.getAll()).contains("password authentication failed for user \"" + appUser + "\"");
                        assertThat(trace + output.getAll())
                                .doesNotContain(WRONG_PASSWORD)
                                .doesNotContain(database.credentials().appPassword())
                                .doesNotContain(database.credentials().migratorPassword());
                    });
        }
    }

    @Test
    void conLaContrasenaCorrectaElArranqueEsExitoso() {
        try (EmbeddedPostgresSupport.Database database = EmbeddedPostgresSupport.startDatabase()) {
            assertThatCode(() -> new SpringApplicationBuilder(ConsultorioApplication.class)
                            .properties(properties(database.properties()))
                            .run("--server.port=0")
                            .close())
                    .doesNotThrowAnyException();
        }
    }
}
