package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El rol de aplicación inserta, lee y revoca tokens, pero no los borra (V16). */
class BookingTokenPrivilegesIT extends AbstractIntegrationTest {

    private UUID appointmentId;
    private String hash;

    @BeforeEach
    void setUp() throws SQLException {
        UUID patient = AppointmentFixtures.patient(true);
        UUID service = AppointmentFixtures.service(30, true, true);
        appointmentId = AppointmentFixtures.appointment(patient, service,
                "2099-12-10T13:00:00Z", "2099-12-10T13:30:00Z", "CONFIRMED");
        hash = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO booking_token (id, appointment_id, token_hash, expires_at, "
                                + "privacy_notice_version, privacy_accepted_at) VALUES (?, ?, ?, now(), 'V1', now())")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, appointmentId);
            insert.setString(3, hash);
            insert.executeUpdate();
        }
    }

    @AfterEach
    void cleanUp() {
        AppointmentFixtures.cleanAll();
    }

    @Test
    void laAplicacionPuedeLeerYRevocar() throws SQLException {
        try (Connection connection = DATABASE.appConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT token_hash FROM booking_token WHERE token_hash = '" + hash + "'")) {
            assertThat(rows.next()).isTrue();
        }
        try (Connection connection = DATABASE.appConnection();
                Statement statement = connection.createStatement()) {
            assertThat(statement.executeUpdate(
                    "UPDATE booking_token SET revoked_at = now() WHERE token_hash = '" + hash + "'")).isEqualTo(1);
        }
    }

    @Test
    void laAplicacionNoPuedeBorrarNiTruncar() throws SQLException {
        try (Connection connection = DATABASE.appConnection();
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM booking_token"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> statement.executeUpdate("TRUNCATE booking_token"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(AppointmentFixtures.count("SELECT count(*) FROM booking_token")).isEqualTo(1);
    }
}
