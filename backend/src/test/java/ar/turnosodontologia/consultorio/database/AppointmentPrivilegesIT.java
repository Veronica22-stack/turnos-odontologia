package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El rol de aplicacion no borra turnos fisicamente (V13) pero si los inserta, lee y actualiza. */
class AppointmentPrivilegesIT extends AbstractIntegrationTest {

    private static final String PRACTICE = "01900000-0000-7000-8000-000000000001";
    private static final String PATIENT_DNI = "76000013";

    private UUID patientId;
    private UUID serviceId;
    private UUID appointmentId;

    @BeforeEach
    void seed() throws SQLException {
        cleanRows();
        patientId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        appointmentId = UUID.randomUUID();
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) VALUES ('"
                    + patientId + "', '" + PRACTICE + "', 'Ana', 'PrivTurno', '" + PATIENT_DNI + "', '1111')");
            statement.executeUpdate("INSERT INTO service (id, practice_id, name, duration_min) VALUES ('" + serviceId
                    + "', '" + PRACTICE + "', 'PrivTurno', 30)");
            statement.executeUpdate(insert(appointmentId, "2026-12-10T10:00:00Z", "2026-12-10T10:30:00Z"));
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM appointment WHERE patient_id IN "
                    + "(SELECT id FROM patient WHERE dni = '" + PATIENT_DNI + "')");
            statement.executeUpdate("DELETE FROM patient WHERE dni = '" + PATIENT_DNI + "'");
            statement.executeUpdate("DELETE FROM service WHERE name = 'PrivTurno'");
        }
    }

    @Test
    void laAplicacionPuedeInsertarConsultarYActualizarTurnos() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate(insert(UUID.randomUUID(), "2026-12-11T10:00:00Z",
                        "2026-12-11T10:30:00Z"))).isEqualTo(1);
                try (ResultSet rows = statement.executeQuery(
                        "SELECT status FROM appointment WHERE id = '" + appointmentId + "'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("CONFIRMED");
                }
                assertThat(statement.executeUpdate("UPDATE appointment SET status = 'COMPLETED' WHERE id = '"
                        + appointmentId + "'")).isEqualTo(1);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void laAplicacionNoPuedeBorrarNiTruncarTurnos() throws SQLException {
        assertRejected("DELETE FROM appointment WHERE id = '" + appointmentId + "'");
        assertRejected("TRUNCATE appointment");
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM appointment WHERE id = '" + appointmentId + "'")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void laAplicacionPuedeBloquearLaFilaDelConsultorio() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT id FROM practice WHERE id = '" + PRACTICE + "' FOR UPDATE")) {
                assertThat(rows.next()).isTrue();
            } finally {
                connection.rollback();
            }
        }
    }

    private String insert(UUID id, String startsAt, String endsAt) {
        return "INSERT INTO appointment (id, practice_id, patient_id, service_id, starts_at, ends_at, "
                + "duration_min_snapshot, status, origin) VALUES ('" + id + "', '" + PRACTICE + "', '" + patientId
                + "', '" + serviceId + "', '" + startsAt + "', '" + endsAt + "', 30, 'CONFIRMED', 'INTERNAL')";
    }

    private static void assertRejected(String sql) throws SQLException {
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
