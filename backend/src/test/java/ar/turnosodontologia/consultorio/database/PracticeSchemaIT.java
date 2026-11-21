package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/** Esquema de {@code practice} (V2): una sola fila, restricciones y privilegios del rol de aplicacion. */
class PracticeSchemaIT extends AbstractIntegrationTest {

    private static final String SEEDED_ID = "01900000-0000-7000-8000-000000000001";

    @Test
    void existeExactamenteUnConsultorioSembradoConZonaDeBuenosAiresYContactosNulos() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT id, timezone, contact_email, contact_phone, address FROM practice")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("id")).isEqualTo(SEEDED_ID);
            assertThat(rows.getString("timezone")).isEqualTo("America/Argentina/Buenos_Aires");
            assertThat(rows.getString("contact_email")).isNull();
            assertThat(rows.getString("contact_phone")).isNull();
            assertThat(rows.getString("address")).isNull();
            assertThat(rows.next()).as("solo una fila").isFalse();
        }
    }

    @Test
    void lasMigracionesV1YV2QuedanRegistradasComoExitosas() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM flyway_schema_history WHERE version IN ('1', '2') AND success")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void unSegundoConsultorioEsRechazadoPorLaFilaUnica() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO practice (id, name) VALUES ('01900000-0000-7000-8000-000000000002', 'Otro')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("practice_single_row");
        }
    }

    @Test
    void nombreOZonaVaciosSonRechazados() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE practice SET name = ''"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("violates check constraint");
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE practice SET timezone = ''"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("violates check constraint");
        }
    }

    @Test
    void laAplicacionNoPuedeInsertarNiBorrarConsultorios() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO practice (id, name) VALUES ('01900000-0000-7000-8000-000000000003', 'X')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM practice"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void laAplicacionPuedeLeerYActualizarElConsultorio() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate("UPDATE practice SET name = 'Consultorio Norte'")).isEqualTo(1);
                try (ResultSet rows = statement.executeQuery("SELECT name FROM practice")) {
                    rows.next();
                    assertThat(rows.getString(1)).isEqualTo("Consultorio Norte");
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
