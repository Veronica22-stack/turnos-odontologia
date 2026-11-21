package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifica el patron de tablas inmutables: el rol de aplicacion no puede UPDATE/DELETE donde se le revoca. */
class RolePrivilegesIT extends AbstractIntegrationTest {

    @BeforeEach
    void createTables() throws SQLException {
        String app = DATABASE.credentials().appUser();
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE prueba_normal (id int PRIMARY KEY, nota text)");
            statement.execute("CREATE TABLE prueba_inmutable (id int PRIMARY KEY, nota text)");
            statement.execute("REVOKE UPDATE, DELETE ON prueba_inmutable FROM " + app);
        }
    }

    @AfterEach
    void dropTables() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS prueba_normal, prueba_inmutable");
        }
    }

    @Test
    void laAplicacionPuedeLeerEInsertarEnAmbasTablas() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO prueba_normal VALUES (1, 'a')");
            statement.executeUpdate("INSERT INTO prueba_inmutable VALUES (1, 'a')");

            assertThat(count(statement, "prueba_normal")).isEqualTo(1);
            assertThat(count(statement, "prueba_inmutable")).isEqualTo(1);
        }
    }

    @Test
    void laAplicacionPuedeActualizarYBorrarEnLaTablaNormal() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO prueba_normal VALUES (2, 'a')");

            assertThatCode(() -> {
                statement.executeUpdate("UPDATE prueba_normal SET nota = 'b' WHERE id = 2");
                statement.executeUpdate("DELETE FROM prueba_normal WHERE id = 2");
            }).doesNotThrowAnyException();
        }
    }

    @Test
    void laAplicacionNoPuedeActualizarLaTablaInmutable() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO prueba_inmutable VALUES (3, 'a')");

            assertThatThrownBy(() -> statement.executeUpdate("UPDATE prueba_inmutable SET nota = 'b' WHERE id = 3"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void laAplicacionNoPuedeBorrarDeLaTablaInmutable() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO prueba_inmutable VALUES (4, 'a')");

            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM prueba_inmutable WHERE id = 4"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    private static int count(Statement statement, String table) throws SQLException {
        try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
