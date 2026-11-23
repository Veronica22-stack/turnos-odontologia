package ar.turnosodontologia.consultorio.database;

import ar.turnosodontologia.consultorio.support.ClinicalFixtures;
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

/** El rol de aplicacion solo puede INSERT/SELECT en la historia clinica; la base impide cualquier mutacion. */
class ClinicalRecordPrivilegesIT extends AbstractIntegrationTest {

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
                        + patientId + "', '" + PRACTICE + "', 'Ana', 'Priv', '70000003', '1111')");
                statement.executeUpdate("INSERT INTO clinical_record (id, practice_id, patient_id, opened_at, opened_by) "
                        + "VALUES ('" + recordId + "', '" + PRACTICE + "', '" + patientId + "', now(), '" + userId + "')");
                statement.executeUpdate("INSERT INTO anamnesis_entry (id, clinical_record_id, version, content, author_id, created_at) "
                        + "VALUES ('" + entryId + "', '" + recordId + "', 1, '{\"schemaVersion\":1}', '" + userId + "', now())");
            }
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM anamnesis_entry");
            ClinicalFixtures.deleteEvolutions();
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('70000003', '70000004')");
        }
    }

    @Test
    void laAplicacionPuedeInsertarYConsultarEnAmbasTablas() throws SQLException {
        UUID patientId2 = UUID.randomUUID();
        try (Connection migrator = DATABASE.migratorConnection(); Statement statement = migrator.createStatement()) {
            statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) VALUES ('"
                    + patientId2 + "', '" + PRACTICE + "', 'Bea', 'Priv', '70000004', '1111')");
        }
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                UUID newRecord = UUID.randomUUID();
                assertThat(statement.executeUpdate(
                        "INSERT INTO clinical_record (id, practice_id, patient_id, opened_at, opened_by) VALUES ('"
                                + newRecord + "', '" + PRACTICE + "', '" + patientId2 + "', now(), '" + userId + "')"))
                        .isEqualTo(1);
                assertThat(statement.executeUpdate(
                        "INSERT INTO anamnesis_entry (id, clinical_record_id, version, content, author_id, created_at) "
                                + "VALUES ('" + UUID.randomUUID() + "', '" + newRecord + "', 1, '{}', '" + userId
                                + "', now())")).isEqualTo(1);
                try (ResultSet rows = statement.executeQuery(
                        "SELECT (SELECT count(*) FROM clinical_record) AS r, (SELECT count(*) FROM anamnesis_entry) AS a")) {
                    rows.next();
                    assertThat(rows.getInt("r")).isEqualTo(2);
                    assertThat(rows.getInt("a")).isEqualTo(2);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void laAplicacionNoPuedeActualizarBorrarNiTruncarLaHistoriaClinica() throws SQLException {
        assertRejected("UPDATE clinical_record SET opened_by = opened_by WHERE id = '" + recordId + "'");
        assertRejected("DELETE FROM clinical_record WHERE id = '" + recordId + "'");
        assertRejected("TRUNCATE clinical_record");
        assertRowCount("clinical_record", 1);
    }

    @Test
    void laAplicacionNoPuedeActualizarBorrarNiTruncarLaAnamnesis() throws SQLException {
        assertRejected("UPDATE anamnesis_entry SET content = '{\"schemaVersion\":2}' WHERE id = '" + entryId + "'");
        assertRejected("DELETE FROM anamnesis_entry WHERE id = '" + entryId + "'");
        assertRejected("TRUNCATE anamnesis_entry");
        assertRowCount("anamnesis_entry", 1);
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT content->>'schemaVersion' FROM anamnesis_entry")) {
            rows.next();
            assertThat(rows.getString(1)).isEqualTo("1");
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

    private void assertRowCount(String table, int expected) throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(expected);
        }
    }
}
