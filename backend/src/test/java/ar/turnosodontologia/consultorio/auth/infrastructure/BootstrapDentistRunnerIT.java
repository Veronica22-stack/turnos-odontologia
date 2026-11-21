package ar.turnosodontologia.consultorio.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.ConsultorioApplication;
import ar.turnosodontologia.consultorio.auth.application.BootstrapDentist;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.EmbeddedPostgresSupport;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(OutputCaptureExtension.class)
class BootstrapDentistRunnerIT extends AbstractIntegrationTest {

    private final BootstrapDentist bootstrapDentist;

    BootstrapDentistRunnerIT(BootstrapDentist bootstrapDentist) {
        this.bootstrapDentist = bootstrapDentist;
    }

    private record Dentist(String email, String hash, boolean mustChangePassword) {
    }

    private static List<Dentist> dentists(EmbeddedPostgresSupport.Database database) throws SQLException {
        List<Dentist> found = new ArrayList<>();
        try (Connection connection = database.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT email, password_hash, must_change_password FROM app_user WHERE role = 'DENTIST'")) {
            while (rows.next()) {
                found.add(new Dentist(
                        rows.getString("email"), rows.getString("password_hash"), rows.getBoolean("must_change_password")));
            }
        }
        return found;
    }

    @Test
    void elArranqueCreaElDentistaYElLogSoloTraeElId(CapturedOutput output) throws Exception {
        try (EmbeddedPostgresSupport.Database database = EmbeddedPostgresSupport.startDatabase()) {
            String[] properties = database.properties().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toArray(String[]::new);

            try (ConfigurableApplicationContext app = new SpringApplicationBuilder(ConsultorioApplication.class)
                    .properties(properties)
                    .run("--server.port=0")) {
                List<Dentist> created = dentists(database);

                assertThat(created).hasSize(1);
                assertThat(created.get(0).email()).isEqualTo(BOOTSTRAP_EMAIL);
                assertThat(created.get(0).mustChangePassword()).isTrue();
                assertThat(created.get(0).hash()).startsWith("$argon2id$").doesNotContain(BOOTSTRAP_PASSWORD);
            }
        }

        assertThat(output.getAll())
                .containsPattern("Bootstrap: cuenta DENTIST creada \\(id=[0-9a-f-]{36}\\)")
                .doesNotContain(BOOTSTRAP_EMAIL)
                .doesNotContain(BOOTSTRAP_PASSWORD);
    }

    @Test
    void volverAEjecutarElRunnerNoCreaNiModificaNada(CapturedOutput output) throws Exception {
        List<Dentist> before = dentists(DATABASE);
        assertThat(before).hasSize(1);

        runnerWith(BOOTSTRAP_EMAIL, BOOTSTRAP_PASSWORD).run(new DefaultApplicationArguments());
        runnerWith("otra-persona@it-bootstrap.test", "Otra-contrasena-distinta-123")
                .run(new DefaultApplicationArguments());

        assertThat(dentists(DATABASE)).isEqualTo(before);
        assertThat(output.getAll())
                .contains("Bootstrap omitido: ya existe una cuenta DENTIST")
                .doesNotContain(BOOTSTRAP_EMAIL)
                .doesNotContain(BOOTSTRAP_PASSWORD)
                .doesNotContain("otra-persona@it-bootstrap.test")
                .doesNotContain("Otra-contrasena-distinta-123");
    }

    private BootstrapDentistRunner runnerWith(String email, String password) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("BOOTSTRAP_ADMIN_EMAIL", email)
                .withProperty("BOOTSTRAP_ADMIN_PASSWORD", password);
        return new BootstrapDentistRunner(bootstrapDentist, environment);
    }
}
