package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El rol de aplicacion no borra notificaciones (V14) pero si las inserta, lee, bloquea y actualiza. */
class NotificationPrivilegesIT extends AbstractIntegrationTest {

    private UUID notificationId;

    @BeforeEach
    void seed() {
        AppointmentFixtures.cleanAll();
        UUID patient = AppointmentFixtures.patient(true);
        UUID service = AppointmentFixtures.service(30, true, true);
        UUID appointment =
                AppointmentFixtures.appointment(patient, service, "2026-12-10T10:00:00Z", "2026-12-10T10:30:00Z",
                        "CONFIRMED");
        notificationId = UUID.randomUUID();
        AppointmentFixtures.execute("INSERT INTO notification (id, practice_id, appointment_id, type, scheduled_for) "
                + "VALUES ('" + notificationId + "', '" + AppointmentFixtures.PRACTICE_ID + "', '" + appointment
                + "', 'CONFIRMATION', '2026-12-09T13:00:00Z')");
    }

    @AfterEach
    void cleanRows() {
        AppointmentFixtures.cleanAll();
    }

    @Test
    void laAplicacionPuedeConsultarYActualizarNotificaciones() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rows = statement.executeQuery(
                        "SELECT status FROM notification WHERE id = '" + notificationId + "'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("PENDING");
                }
                assertThat(statement.executeUpdate("UPDATE notification SET status = 'DISCARDED', "
                        + "last_error = 'EXPIRED' WHERE id = '" + notificationId + "'")).isEqualTo(1);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void laAplicacionPuedeTomarFilasConForUpdateSkipLocked() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT id FROM notification WHERE status = 'PENDING' "
                            + "ORDER BY scheduled_for LIMIT 1 FOR UPDATE SKIP LOCKED")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1, UUID.class)).isEqualTo(notificationId);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void laAplicacionPuedeInsertarNotificaciones() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate("INSERT INTO notification (id, practice_id, appointment_id, type, "
                        + "scheduled_for) SELECT '" + UUID.randomUUID() + "', practice_id, appointment_id, 'REMINDER',"
                        + " scheduled_for FROM notification WHERE id = '" + notificationId + "'")).isEqualTo(1);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void laAplicacionNoPuedeBorrarNiTruncarNotificaciones() throws SQLException {
        assertRejected("DELETE FROM notification WHERE id = '" + notificationId + "'");
        assertRejected("TRUNCATE notification");
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM notification WHERE id = '" + notificationId + "'")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
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
