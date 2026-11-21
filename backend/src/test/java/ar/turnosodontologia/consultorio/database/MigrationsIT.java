package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationsIT extends AbstractIntegrationTest {

    private final JdbcTemplate jdbcTemplate;

    MigrationsIT(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void laMigracionV1QuedaRegistradaComoExitosa() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT success FROM flyway_schema_history WHERE version = '1'")) {
            assertThat(rows.next()).as("V1 registrada en flyway_schema_history").isTrue();
            assertThat(rows.getBoolean("success")).isTrue();
        }
    }

    @Test
    void laExtensionBtreeGistEstaInstalada() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'btree_gist'")) {
            assertThat(rows.next()).isTrue();
        }
    }

    @Test
    void elRolDeAplicacionNoPuedeEjecutarDdl() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("CREATE TABLE no_deberia_existir (id int)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void elRolDeMigracionSiPuedeEjecutarDdl() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ddl_migrator_ok (id int)");
            statement.execute("DROP TABLE ddl_migrator_ok");
        }
    }

    @Test
    void laSesionDeLaAplicacionUsaUtc() {
        String zone = jdbcTemplate.queryForObject("SHOW TimeZone", String.class);

        assertThat(zone).isEqualTo("UTC");
    }

    @Test
    void laAplicacionSeConectaConElRolDeAplicacion() {
        String user = jdbcTemplate.queryForObject("SELECT current_user", String.class);

        assertThat(user).isEqualTo(DATABASE.credentials().appUser());
    }
}
