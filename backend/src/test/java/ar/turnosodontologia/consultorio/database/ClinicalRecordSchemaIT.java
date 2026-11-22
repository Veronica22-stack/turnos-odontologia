package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

/** Esquema V11: historia clinica y anamnesis con integridad de correcciones garantizada en la base. */
class ClinicalRecordSchemaIT extends AbstractIntegrationTest {

    private static final String PRACTICE = "01900000-0000-7000-8000-000000000001";

    private UUID userId;
    private UUID patientId;
    private UUID otherPatientId;

    @BeforeEach
    void seed() throws SQLException {
        cleanRows();
        try (Connection connection = DATABASE.migratorConnection()) {
            try (PreparedStatement query = connection.prepareStatement("SELECT id FROM app_user WHERE email = ?")) {
                query.setString(1, BOOTSTRAP_EMAIL);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    userId = UUID.fromString(rows.getString(1));
                }
            }
            patientId = insertPatient(connection, "70000001");
            otherPatientId = insertPatient(connection, "70000002");
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM anamnesis_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM anamnesis_entry");
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('70000001', '70000002')");
        }
    }

    @Test
    void existenLasTablasDeHistoriaClinicaYAnamnesis() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM information_schema.tables "
                                + "WHERE table_name IN ('clinical_record', 'anamnesis_entry')")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void unaSolaHistoriaClinicaPorPaciente() throws SQLException {
        openRecord(patientId);
        assertThatCode(() -> openRecord(otherPatientId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> openRecord(patientId))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("clinical_record_patient_unique");
    }

    @Test
    void rechazaVersionDuplicadaEnLaMismaHistoriaPeroAceptaEnOtra() throws SQLException {
        UUID record = openRecord(patientId);
        UUID otherRecord = openRecord(otherPatientId);
        insertEntry(record, 1, null, null);
        assertThatCode(() -> insertEntry(otherRecord, 1, null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertEntry(record, 1, null, null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("anamnesis_entry_record_version_unique");
    }

    @Test
    void rechazaVersionMenorAUno() throws SQLException {
        UUID record = openRecord(patientId);
        assertThatThrownBy(() -> insertEntry(record, 0, null, null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("anamnesis_entry_version_check");
    }

    @Test
    void contenidoDebeSerUnObjetoJson() throws SQLException {
        UUID record = openRecord(patientId);
        assertThatThrownBy(() -> insertEntryWithContent(record, 1, "'[]'::jsonb"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("anamnesis_entry_content_check");
        assertThatThrownBy(() -> insertEntryWithContent(record, 1, "'\"texto\"'::jsonb"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("anamnesis_entry_content_check");
        assertThatCode(() -> insertEntryWithContent(record, 1, "'{\"schemaVersion\":1}'::jsonb"))
                .doesNotThrowAnyException();
    }

    @Test
    void referenciaYMotivoDebenIrJuntos() throws SQLException {
        UUID record = openRecord(patientId);
        UUID original = insertEntry(record, 1, null, null);
        assertThatThrownBy(() -> insertEntry(record, 2, null, "motivo sin referencia"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("anamnesis_entry_correction_pair_check");
        assertThatThrownBy(() -> insertEntry(record, 2, original, null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("anamnesis_entry_correction_pair_check");
        assertThatCode(() -> insertEntry(record, 2, original, "alergia mal cargada")).doesNotThrowAnyException();
    }

    @Test
    void rechazaMotivoVacioOEnBlancoOExcedido() throws SQLException {
        UUID record = openRecord(patientId);
        UUID original = insertEntry(record, 1, null, null);
        assertThatThrownBy(() -> insertEntry(record, 2, original, ""))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("anamnesis_entry_correction_reason_check");
        assertThatThrownBy(() -> insertEntry(record, 2, original, "   "))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("anamnesis_entry_correction_reason_check");
        assertThatThrownBy(() -> insertEntry(record, 2, original, "x".repeat(501)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("anamnesis_entry_correction_reason_check");
        assertThatCode(() -> insertEntry(record, 2, original, "x".repeat(500))).doesNotThrowAnyException();
    }

    @Test
    void noPermiteCorregirUnaEntradaDeOtraHistoriaClinica() throws SQLException {
        UUID record = openRecord(patientId);
        UUID otherRecord = openRecord(otherPatientId);
        UUID foreignEntry = insertEntry(otherRecord, 1, null, null);
        assertThatThrownBy(() -> insertEntry(record, 1, foreignEntry, "cruzada"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("anamnesis_entry_corrects_same_record_fk");
    }

    @Test
    void unaEntradaSeCorrigeALoSumoUnaVez() throws SQLException {
        UUID record = openRecord(patientId);
        UUID original = insertEntry(record, 1, null, null);
        insertEntry(record, 2, original, "primera correccion");
        assertThatThrownBy(() -> insertEntry(record, 3, original, "segunda correccion"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("anamnesis_entry_corrects_once_unique");
    }

    private UUID openRecord(UUID patient) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO clinical_record (id, practice_id, patient_id, opened_at, opened_by) "
                                + "VALUES (?::uuid, ?::uuid, ?::uuid, now(), ?::uuid)")) {
            insert.setString(1, id.toString());
            insert.setString(2, PRACTICE);
            insert.setString(3, patient.toString());
            insert.setString(4, userId.toString());
            insert.executeUpdate();
        }
        return id;
    }

    private UUID insertEntry(UUID record, int version, UUID corrects, String reason) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO anamnesis_entry (id, clinical_record_id, version, content, corrects_entry_id, "
                                + "correction_reason, author_id, created_at) "
                                + "VALUES (?::uuid, ?::uuid, ?, '{\"schemaVersion\":1}'::jsonb, ?::uuid, ?, ?::uuid, now())")) {
            insert.setString(1, id.toString());
            insert.setString(2, record.toString());
            insert.setInt(3, version);
            insert.setString(4, corrects == null ? null : corrects.toString());
            insert.setString(5, reason);
            insert.setString(6, userId.toString());
            insert.executeUpdate();
        }
        return id;
    }

    private void insertEntryWithContent(UUID record, int version, String contentSql) throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO anamnesis_entry (id, clinical_record_id, version, content, author_id, created_at) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + record + "', " + version + ", " + contentSql
                            + ", '" + userId + "', now())");
        }
    }

    private static UUID insertPatient(Connection connection, String dni) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) "
                        + "VALUES (?::uuid, ?::uuid, 'Ana', 'Test', ?, '1111')")) {
            insert.setString(1, id.toString());
            insert.setString(2, PRACTICE);
            insert.setString(3, dni);
            insert.executeUpdate();
        }
        return id;
    }
}
