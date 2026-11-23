package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import ar.turnosodontologia.consultorio.support.ClinicalFixtures;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Esquema V15: evoluciones con descripcion acotada, turno opcional e integridad de correcciones en la base. */
class EvolutionSchemaIT extends AbstractIntegrationTest {

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
            patientId = insertPatient(connection, "76100001");
            otherPatientId = insertPatient(connection, "76100002");
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        ClinicalFixtures.deleteEvolutions();
        AppointmentFixtures.cleanAll();
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('76100001', '76100002')");
        }
    }

    @Test
    void existeLaTablaDeEvolucionesConSusColumnas() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT string_agg(column_name, ',' ORDER BY column_name) "
                        + "FROM information_schema.columns WHERE table_name = 'evolution_entry'")) {
            rows.next();
            assertThat(rows.getString(1)).isEqualTo("appointment_id,author_id,clinical_record_id,correction_reason,"
                    + "corrects_entry_id,created_at,description,id");
        }
    }

    @Test
    void rechazaDescripcionEnBlancoOExcedida() throws SQLException {
        UUID record = openRecord(patientId);
        for (String description : new String[] {"", "   ", "\n\t ", "\r\n\t\f "}) {
            assertThatThrownBy(() -> insert(record, description, null, null, null))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("evolution_entry_description_check");
        }
        assertThatThrownBy(() -> insert(record, "x".repeat(5001), null, null, null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("evolution_entry_description_check");
    }

    @Test
    void aceptaDescripcionDeHasta5000Caracteres() throws SQLException {
        UUID record = openRecord(patientId);
        assertThatCode(() -> insert(record, "x".repeat(5000), null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> insert(record, "Control de rutina", null, null, null)).doesNotThrowAnyException();
    }

    @Test
    void elTurnoEsOpcionalPeroSiSeInformaDebeExistir() throws SQLException {
        UUID record = openRecord(patientId);
        assertThatCode(() -> insert(record, "sin turno", null, null, null)).doesNotThrowAnyException();
        UUID appointment = appointmentOf(patientId);
        assertThatCode(() -> insert(record, "con turno", appointment, null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> insert(record, "turno fantasma", UUID.randomUUID(), null, null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("appointment_id");
    }

    @Test
    void referenciaYMotivoDebenIrJuntos() throws SQLException {
        UUID record = openRecord(patientId);
        UUID original = insert(record, "original", null, null, null);
        assertThatThrownBy(() -> insert(record, "nueva", null, null, "motivo sin referencia"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("evolution_entry_correction_pair_check");
        assertThatThrownBy(() -> insert(record, "nueva", null, original, null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("evolution_entry_correction_pair_check");
        assertThatCode(() -> insert(record, "nueva", null, original, "error de tipeo")).doesNotThrowAnyException();
    }

    @Test
    void rechazaMotivoVacioOEnBlancoOExcedido() throws SQLException {
        UUID record = openRecord(patientId);
        UUID original = insert(record, "original", null, null, null);
        for (String reason : new String[] {"", "   ", "x".repeat(501)}) {
            assertThatThrownBy(() -> insert(record, "nueva", null, original, reason))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("evolution_entry_correction_reason_check");
        }
        assertThatCode(() -> insert(record, "nueva", null, original, "x".repeat(500))).doesNotThrowAnyException();
    }

    @Test
    void noPermiteCorregirUnaEvolucionDeOtraHistoriaClinica() throws SQLException {
        UUID record = openRecord(patientId);
        UUID otherRecord = openRecord(otherPatientId);
        UUID foreignEntry = insert(otherRecord, "ajena", null, null, null);
        assertThatThrownBy(() -> insert(record, "cruzada", null, foreignEntry, "cruzada"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("evolution_entry_corrects_same_record_fk");
    }

    @Test
    void unaEvolucionSeCorrigeALoSumoUnaVez() throws SQLException {
        UUID record = openRecord(patientId);
        UUID original = insert(record, "original", null, null, null);
        insert(record, "primera correccion", null, original, "primera");
        assertThatThrownBy(() -> insert(record, "segunda correccion", null, original, "segunda"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("evolution_entry_corrects_once_unique");
    }

    @Test
    void elHelperVaciaUnaCadenaDeCorreccionesQueLaClaveForaneaImpideBorrarDeUnaVez() throws SQLException {
        UUID record = openRecord(patientId);
        UUID a = insert(record, "A", null, null, null);
        UUID b = insert(record, "B", null, a, "corrige A");
        insert(record, "C", null, b, "corrige B");
        assertThat(count()).isEqualTo(3);

        ClinicalFixtures.deleteEvolutions();

        assertThat(count()).isZero();
    }

    @Test
    void elHelperTambienVaciaCadenasIndependientesYUnaTablaVacia() throws SQLException {
        ClinicalFixtures.deleteEvolutions();
        UUID record = openRecord(patientId);
        UUID otherRecord = openRecord(otherPatientId);
        UUID a = insert(record, "A", null, null, null);
        insert(record, "B", null, a, "corrige A");
        insert(otherRecord, "suelta", null, null, null);

        ClinicalFixtures.deleteEvolutions();

        assertThat(count()).isZero();
    }

    private int count() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM evolution_entry")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private UUID appointmentOf(UUID patient) {
        UUID service = AppointmentFixtures.service(30, true, true);
        return AppointmentFixtures.appointment(
                patient, service, "2026-01-10T10:00:00Z", "2026-01-10T10:30:00Z", "COMPLETED");
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

    private UUID insert(UUID record, String description, UUID appointment, UUID corrects, String reason)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO evolution_entry (id, clinical_record_id, appointment_id, description, "
                                + "corrects_entry_id, correction_reason, author_id, created_at) "
                                + "VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?::uuid, ?, ?::uuid, now())")) {
            insert.setString(1, id.toString());
            insert.setString(2, record.toString());
            insert.setString(3, appointment == null ? null : appointment.toString());
            insert.setString(4, description);
            insert.setString(5, corrects == null ? null : corrects.toString());
            insert.setString(6, reason);
            insert.setString(7, userId.toString());
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
