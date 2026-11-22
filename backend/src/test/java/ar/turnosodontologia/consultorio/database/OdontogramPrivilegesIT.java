package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El rol de aplicacion solo puede INSERT/SELECT en el odontograma; la base impide cualquier mutacion. */
class OdontogramPrivilegesIT extends AbstractIntegrationTest {

    private static final String PRACTICE = "01900000-0000-7000-8000-000000000001";

    private UUID userId;
    private UUID recordId;
    private UUID entryId;

    @BeforeEach
    void seed() throws SQLException {
        cleanRows();
        UUID patientId = UUID.randomUUID();
        recordId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        try (Connection connection = DATABASE.migratorConnection()) {
            try (PreparedStatement query = connection.prepareStatement("SELECT id FROM app_user WHERE email = ?")) {
                query.setString(1, BOOTSTRAP_EMAIL);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    userId = UUID.fromString(rows.getString(1));
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) VALUES ('"
                        + patientId + "', '" + PRACTICE + "', 'Ana', 'Priv', '76000003', '1111')");
                statement.executeUpdate("INSERT INTO clinical_record (id, practice_id, patient_id, opened_at, opened_by) "
                        + "VALUES ('" + recordId + "', '" + PRACTICE + "', '" + patientId + "', now(), '" + userId + "')");
                statement.executeUpdate("INSERT INTO odontogram_entry (id, clinical_record_id, tooth_code, condition, "
                        + "author_id, created_at) VALUES ('" + entryId + "', '" + recordId + "', '16', 'CARIES', '"
                        + userId + "', now())");
            }
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM odontogram_entry");
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM patient WHERE dni = '76000003'");
        }
    }

    @Test
    void laAplicacionPuedeInsertarYConsultarElOdontograma() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate(
                        "INSERT INTO odontogram_entry (id, clinical_record_id, tooth_code, surface, condition, "
                                + "author_id, created_at) VALUES ('" + UUID.randomUUID() + "', '" + recordId
                                + "', '21', 'MESIAL', 'FILLED', '" + userId + "', now())")).isEqualTo(1);
                try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM odontogram_entry")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void laAplicacionNoPuedeActualizarBorrarNiTruncarElOdontograma() throws SQLException {
        assertRejected("UPDATE odontogram_entry SET condition = 'HEALTHY' WHERE id = '" + entryId + "'");
        assertRejected("DELETE FROM odontogram_entry WHERE id = '" + entryId + "'");
        assertRejected("TRUNCATE odontogram_entry");
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*), min(condition) FROM odontogram_entry")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
            assertThat(rows.getString(2)).isEqualTo("CARIES");
        }
    }

    private void assertRejected(String sql) throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.executeUpdate(sql))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            } finally {
                connection.rollback();
            }
        }
    }
}
