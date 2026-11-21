package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/** Esquema de {@code app_user} (V2) con el rol de aplicacion; cada caso corre en una transaccion revertida. */
class AppUserSchemaIT extends AbstractIntegrationTest {

    private static final String PRACTICE_ID = "01900000-0000-7000-8000-000000000001";
    private static final String HASH = "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA";
    private static final String ID_1 = "01900000-0000-7000-8000-0000000000a1";
    private static final String ID_2 = "01900000-0000-7000-8000-0000000000a2";

    private interface SqlAction {
        void run(Statement statement) throws SQLException;
    }

    private static void inRolledBackTransaction(SqlAction action) throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                action.run(statement);
            } finally {
                connection.rollback();
            }
        }
    }

    private static String insert(String practiceId, String id, String email, String role, int failedAttempts) {
        return ("INSERT INTO app_user (id, practice_id, email, password_hash, role, failed_attempts) "
                        + "VALUES ('%s', '%s', '%s', '%s', '%s', %d)")
                .formatted(id, practiceId, email, HASH, role, failedAttempts);
    }

    @Test
    void aceptaUnaFilaValidaConValoresPorDefecto() throws SQLException {
        inRolledBackTransaction(statement -> {
            assertThat(statement.executeUpdate(insert(PRACTICE_ID, ID_1, "dr@ejemplo.com", "DENTIST", 0)))
                    .isEqualTo(1);
            try (ResultSet rows = statement.executeQuery(
                    "SELECT must_change_password, locked_until, last_login_at, created_at FROM app_user WHERE id = '" + ID_1 + "'")) {
                rows.next();
                assertThat(rows.getBoolean("must_change_password")).isFalse();
                assertThat(rows.getObject("locked_until")).isNull();
                assertThat(rows.getObject("last_login_at")).isNull();
                assertThat(rows.getObject("created_at")).isNotNull();
            }
        });
    }

    @Test
    void rechazaUnConsultorioInexistente() throws SQLException {
        inRolledBackTransaction(statement -> assertThatThrownBy(() -> statement.executeUpdate(
                        insert("01900000-0000-7000-8000-0000000000ff", ID_1, "dr@ejemplo.com", "DENTIST", 0)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("foreign key"));
    }

    @Test
    void rechazaRolesFueraDeLaLista() throws SQLException {
        inRolledBackTransaction(statement -> assertThatThrownBy(() -> statement.executeUpdate(
                        insert(PRACTICE_ID, ID_1, "dr@ejemplo.com", "SECRETARY", 0)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("violates check constraint"));
    }

    @Test
    void rechazaEmailConMayusculas() throws SQLException {
        inRolledBackTransaction(statement -> assertThatThrownBy(() -> statement.executeUpdate(
                        insert(PRACTICE_ID, ID_1, "Dr@Ejemplo.com", "DENTIST", 0)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("violates check constraint"));
    }

    @Test
    void rechazaEmailConEspaciosAlrededor() throws SQLException {
        inRolledBackTransaction(statement -> assertThatThrownBy(() -> statement.executeUpdate(
                        insert(PRACTICE_ID, ID_1, " dr@ejemplo.com ", "DENTIST", 0)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("violates check constraint"));
    }

    @Test
    void rechazaEmailDuplicado() throws SQLException {
        inRolledBackTransaction(statement -> {
            statement.executeUpdate(insert(PRACTICE_ID, ID_1, "dr@ejemplo.com", "DENTIST", 0));
            assertThatThrownBy(() -> statement.executeUpdate(insert(PRACTICE_ID, ID_2, "dr@ejemplo.com", "DENTIST", 0)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("app_user_email_key");
        });
    }

    @Test
    void rechazaIntentosFallidosNegativos() throws SQLException {
        inRolledBackTransaction(statement -> assertThatThrownBy(() -> statement.executeUpdate(
                        insert(PRACTICE_ID, ID_1, "dr@ejemplo.com", "DENTIST", -1)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("violates check constraint"));
    }

    @Test
    void laAplicacionPuedeActualizarPeroNoBorrarUsuarios() throws SQLException {
        inRolledBackTransaction(statement -> {
            statement.executeUpdate(insert(PRACTICE_ID, ID_1, "dr@ejemplo.com", "DENTIST", 0));
            assertThat(statement.executeUpdate("UPDATE app_user SET failed_attempts = 3 WHERE id = '" + ID_1 + "'"))
                    .isEqualTo(1);
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM app_user"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        });
    }
}
