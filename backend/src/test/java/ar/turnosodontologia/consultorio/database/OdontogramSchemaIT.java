package ar.turnosodontologia.consultorio.database;

import ar.turnosodontologia.consultorio.support.ClinicalFixtures;
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

/** Esquema V12: odontograma con dominio FDI/catalogos e integridad de correcciones garantizados en la base. */
class OdontogramSchemaIT extends AbstractIntegrationTest {

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
            patientId = insertPatient(connection, "76000001");
            otherPatientId = insertPatient(connection, "76000002");
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM odontogram_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM odontogram_entry");
            ClinicalFixtures.deleteEvolutions();
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('76000001', '76000002')");
        }
    }

    @Test
    void existeLaTablaDelOdontograma() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'odontogram_entry'")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void aceptaPiezasPermanentesYTemporalesValidas() throws SQLException {
        UUID record = openRecord(patientId);
        for (String tooth : new String[] {"11", "18", "41", "48", "51", "55", "81", "85"}) {
            assertThatCode(() -> insert(record, tooth, null, "HEALTHY", null, null, null)).doesNotThrowAnyException();
        }
    }

    @Test
    void rechazaCodigosFdiInvalidos() throws SQLException {
        UUID record = openRecord(patientId);
        for (String tooth : new String[] {"19", "56", "59", "90", "00", "1", "111", "10", "49", "86", "ab"}) {
            assertThatThrownBy(() -> insert(record, tooth, null, "HEALTHY", null, null, null))
                    .as("pieza %s", tooth)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("odontogram_entry_tooth_code_check");
        }
    }

    @Test
    void aceptaCadaEstadoDelCatalogoYRechazaOtros() throws SQLException {
        UUID record = openRecord(patientId);
        int tooth = 11;
        for (String condition : new String[] {"HEALTHY", "CARIES", "FILLED", "MISSING", "CROWN", "ROOT_CANAL"}) {
            String code = String.valueOf(tooth++);
            assertThatCode(() -> insert(record, code, null, condition, null, null, null)).doesNotThrowAnyException();
        }
        for (String invalid : new String[] {"FRACTURED", "caries", ""}) {
            assertThatThrownBy(() -> insert(record, "21", null, invalid, null, null, null))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("odontogram_entry_condition_check");
        }
    }

    @Test
    void aceptaCadaCaraDelCatalogoYRechazaOtras() throws SQLException {
        UUID record = openRecord(patientId);
        for (String surface : new String[] {"OCCLUSAL", "MESIAL", "DISTAL", "VESTIBULAR", "LINGUAL"}) {
            assertThatCode(() -> insert(record, "16", surface, "CARIES", null, null, null)).doesNotThrowAnyException();
        }
        for (String invalid : new String[] {"CERVICAL", "mesial"}) {
            assertThatThrownBy(() -> insert(record, "16", invalid, "CARIES", null, null, null))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("odontogram_entry_surface_check");
        }
    }

    @Test
    void laCaraSoloSeAdmiteParaCariesYObturacion() throws SQLException {
        UUID record = openRecord(patientId);
        assertThatCode(() -> insert(record, "16", "MESIAL", "FILLED", null, null, null)).doesNotThrowAnyException();
        for (String condition : new String[] {"MISSING", "HEALTHY", "CROWN", "ROOT_CANAL"}) {
            assertThatThrownBy(() -> insert(record, "17", "MESIAL", condition, null, null, null))
                    .as("estado %s", condition)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("odontogram_entry_surface_condition_check");
        }
    }

    @Test
    void rechazaNotasDeMasDe500Caracteres() throws SQLException {
        UUID record = openRecord(patientId);
        assertThatCode(() -> insert(record, "11", null, "CARIES", "x".repeat(500), null, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> insert(record, "12", null, "CARIES", "x".repeat(501), null, null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("odontogram_entry_notes_check");
    }

    @Test
    void referenciaYMotivoDebenIrJuntos() throws SQLException {
        UUID record = openRecord(patientId);
        UUID original = insert(record, "36", null, "CARIES", null, null, null);
        assertThatThrownBy(() -> insert(record, "36", null, "HEALTHY", null, null, "motivo sin referencia"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("odontogram_entry_correction_pair_check");
        assertThatThrownBy(() -> insert(record, "36", null, "HEALTHY", null, original, null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("odontogram_entry_correction_pair_check");
        assertThatCode(() -> insert(record, "36", null, "HEALTHY", null, original, "pieza sana"))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaMotivoVacioOEnBlancoOExcedido() throws SQLException {
        UUID record = openRecord(patientId);
        UUID original = insert(record, "36", null, "CARIES", null, null, null);
        for (String reason : new String[] {"", "   ", "x".repeat(501)}) {
            assertThatThrownBy(() -> insert(record, "36", null, "HEALTHY", null, original, reason))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("odontogram_entry_correction_reason_check");
        }
        assertThatCode(() -> insert(record, "36", null, "HEALTHY", null, original, "x".repeat(500)))
                .doesNotThrowAnyException();
    }

    @Test
    void noPermiteCorregirUnaEntradaDeOtraHistoriaClinica() throws SQLException {
        UUID record = openRecord(patientId);
        UUID otherRecord = openRecord(otherPatientId);
        UUID foreignEntry = insert(otherRecord, "36", null, "CARIES", null, null, null);
        assertThatThrownBy(() -> insert(record, "36", null, "HEALTHY", null, foreignEntry, "cruzada"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("odontogram_entry_corrects_same_record_fk");
    }

    @Test
    void unaEntradaSeCorrigeALoSumoUnaVez() throws SQLException {
        UUID record = openRecord(patientId);
        UUID original = insert(record, "36", null, "CARIES", null, null, null);
        insert(record, "36", null, "HEALTHY", null, original, "primera correccion");
        assertThatThrownBy(() -> insert(record, "36", null, "FILLED", null, original, "segunda correccion"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("odontogram_entry_corrects_once_unique");
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

    private UUID insert(UUID record, String tooth, String surface, String condition, String notes, UUID corrects,
            String reason) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO odontogram_entry (id, clinical_record_id, tooth_code, surface, condition, notes, "
                                + "corrects_entry_id, correction_reason, author_id, created_at) "
                                + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?::uuid, ?, ?::uuid, now())")) {
            insert.setString(1, id.toString());
            insert.setString(2, record.toString());
            insert.setString(3, tooth);
            insert.setString(4, surface);
            insert.setString(5, condition);
            insert.setString(6, notes);
            insert.setString(7, corrects == null ? null : corrects.toString());
            insert.setString(8, reason);
            insert.setString(9, userId.toString());
            insert.executeUpdate();
        }
        return id;
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
