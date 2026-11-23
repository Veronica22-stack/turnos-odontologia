package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import ar.turnosodontologia.consultorio.support.EmbeddedPostgresSupport;
import ar.turnosodontologia.consultorio.support.EmbeddedPostgresSupport.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifica V16: columnas, hash único, FK y ausencia de token en claro. */
class BookingTokenSchemaIT extends AbstractIntegrationTest {

    private UUID appointmentId;

    @BeforeEach
    void setUp() {
        UUID patient = AppointmentFixtures.patient(true);
        UUID service = AppointmentFixtures.service(30, true, true);
        appointmentId = AppointmentFixtures.appointment(patient, service,
                "2099-12-10T13:00:00Z", "2099-12-10T13:30:00Z", "CONFIRMED");
    }

    @AfterEach
    void cleanUp() {
        AppointmentFixtures.cleanAll();
    }

    @Test
    void laMigracionV16QuedaRegistradaComoExitosa() throws Exception {
        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT success FROM flyway_schema_history WHERE version = '16'")) {
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isTrue();
            }
        }
    }

    @Test
    void bookingTokenTieneLasColumnasYRestriccionesDelDiseno() throws Exception {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet columns = statement.executeQuery(
                        "SELECT column_name, data_type FROM information_schema.columns "
                                + "WHERE table_name = 'booking_token' ORDER BY ordinal_position")) {
            java.util.List<String> names = new java.util.ArrayList<>();
            while (columns.next()) {
                names.add(columns.getString(1));
            }
            assertThat(names).containsExactly("id", "appointment_id", "token_hash", "expires_at",
                    "revoked_at", "created_at", "privacy_notice_version", "privacy_accepted_at");
        }
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet checks = statement.executeQuery(
                        "SELECT conname FROM pg_constraint WHERE conrelid = 'booking_token'::regclass "
                                + "AND contype IN ('c','u') ORDER BY conname")) {
            java.util.List<String> names = new java.util.ArrayList<>();
            while (checks.next()) {
                names.add(checks.getString(1));
            }
            assertThat(names).contains("booking_token_hash_check", "booking_token_hash_unique");
        }
    }

    @Test
    void hashDuplicadoEsRechazadoYVariosTokensPorTurnoSeAceptan() throws Exception {
        String hash = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        insert(hash);
        assertThatThrownBy(() -> insert(hash))
                .hasMessageContaining("duplicate key");
        insert(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM booking_token WHERE appointment_id = '" + appointmentId + "'")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void laMigracionV16SeAplicaDesdeUnaBaseMigradaHastaV15SinVersionesFueraDeOrden() throws SQLException {
        try (Database database = EmbeddedPostgresSupport.startDatabase()) {
            migrate(database, "15");
            migrate(database, "16");
            try (Connection connection = database.migratorConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL "
                                    + "ORDER BY installed_rank")) {
                List<String> versions = new ArrayList<>();
                while (rows.next()) {
                    assertThat(rows.getBoolean("success")).isTrue();
                    versions.add(rows.getString("version"));
                }
                assertThat(versions).endsWith("15", "16").doesNotHaveDuplicates();
            }
            try (Connection connection = database.migratorConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT 1 FROM information_schema.columns WHERE table_name = 'booking_token' "
                                    + "AND column_name = 'token_hash'")) {
                assertThat(rows.next()).isTrue();
            }
        }
    }

    private static void migrate(Database database, String target) {
        Flyway.configure()
                .dataSource(database.jdbcUrl(), database.credentials().migratorUser(),
                        database.credentials().migratorPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("app_role", database.credentials().appUser()))
                .target(target)
                .load()
                .migrate();
    }

    private void insert(String hash) throws SQLException {
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
}
