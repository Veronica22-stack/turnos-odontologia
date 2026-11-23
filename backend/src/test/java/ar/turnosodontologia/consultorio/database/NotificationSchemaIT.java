package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de esquema de {@code notification} (V14): columnas, CHECKs, defaults, unicidad por turno y tipo e indice
 * parcial de pendientes. Cada test trabaja dentro de una transaccion que se revierte.
 */
class NotificationSchemaIT extends AbstractIntegrationTest {

    private static final String PRACTICE_ID = "01900000-0000-7000-8000-000000000001";
    private static final String UNIQUE_VIOLATION = "23505";

    private static UUID appointmentId;

    @Test
    void laMigracionV14QuedaRegistradaComoExitosa() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM flyway_schema_history WHERE version = '14' AND success")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void notificationTieneColumnasRestriccionesEIndicesDelDiseno() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection()) {
            assertThat(columnTypes(connection))
                    .containsEntry("id", "uuid")
                    .containsEntry("practice_id", "uuid")
                    .containsEntry("appointment_id", "uuid")
                    .containsEntry("type", "text")
                    .containsEntry("channel", "text")
                    .containsEntry("status", "text")
                    .containsEntry("scheduled_for", "timestamptz")
                    .containsEntry("sent_at", "timestamptz")
                    .containsEntry("attempts", "int4")
                    .containsEntry("last_error", "text")
                    .containsEntry("created_at", "timestamptz")
                    .containsEntry("updated_at", "timestamptz")
                    .hasSize(12);

            assertThat(constraintTypes(connection))
                    .containsEntry("notification_type_check", "c")
                    .containsEntry("notification_channel_check", "c")
                    .containsEntry("notification_status_check", "c")
                    .containsEntry("notification_attempts_check", "c")
                    .containsEntry("notification_last_error_check", "c")
                    .containsEntry("notification_sent_at_check", "c")
                    .containsEntry("notification_appointment_type_key", "u");

            assertThat(foreignKeyDeleteRules(connection))
                    .containsEntry("practice_id", "r")
                    .containsEntry("appointment_id", "r");

            assertThat(partialIndexDefinition(connection, "notification_pending_due_idx"))
                    .contains("(scheduled_for)")
                    .contains("PENDING");
        }
    }

    @Test
    void losDefaultsSonPendienteEmailYSinIntentos() throws SQLException {
        inRolledBackTransaction(statement -> {
            UUID id = insert(statement, "CONFIRMATION");
            try (ResultSet rows = statement.executeQuery(
                    "SELECT status, channel, attempts, sent_at, last_error FROM notification WHERE id = '" + id + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo("PENDING");
                assertThat(rows.getString("channel")).isEqualTo("EMAIL");
                assertThat(rows.getInt("attempts")).isZero();
                assertThat(rows.getObject("sent_at")).isNull();
                assertThat(rows.getObject("last_error")).isNull();
            }
        });
    }

    @Test
    void laBaseRechazaTipoCanalEstadoEIntentosFueraDeDominio() throws SQLException {
        inRolledBackTransaction(statement -> {
            assertRejected(statement, "notification_type_check", insertSql(UUID.randomUUID(), "BOGUS", "PENDING",
                    "EMAIL", "0", "NULL", "NULL"));
            assertRejected(statement, "notification_channel_check", insertSql(UUID.randomUUID(), "REMINDER",
                    "PENDING", "SMS", "0", "NULL", "NULL"));
            assertRejected(statement, "notification_status_check", insertSql(UUID.randomUUID(), "REMINDER", "BOGUS",
                    "EMAIL", "0", "NULL", "NULL"));
            assertRejected(statement, "notification_attempts_check", insertSql(UUID.randomUUID(), "REMINDER",
                    "PENDING", "EMAIL", "-1", "NULL", "NULL"));
        });
    }

    @Test
    void aceptaLosCincoTiposYLosCuatroEstados() throws SQLException {
        inRolledBackTransaction(statement -> {
            for (String type : List.of("CONFIRMATION", "REMINDER", "RESCHEDULE", "CANCELLATION",
                    "DENTIST_CANCELLATION")) {
                assertThat(statement.executeUpdate(insertSql(UUID.randomUUID(), type, "PENDING", "EMAIL", "0", "NULL",
                        "NULL"))).isEqualTo(1);
            }
            UUID other = newAppointment(statement, "2026-12-11T10:00:00Z", "2026-12-11T10:30:00Z");
            assertThat(statement.executeUpdate(insertSqlFor(other, UUID.randomUUID(), "REMINDER", "FAILED", "EMAIL",
                    "5", "NULL", "'SMTP_REJECTED'"))).isEqualTo(1);
            assertThat(statement.executeUpdate(insertSqlFor(other, UUID.randomUUID(), "CONFIRMATION", "DISCARDED",
                    "EMAIL", "0", "NULL", "'NO_RECIPIENT'"))).isEqualTo(1);
        });
    }

    @Test
    void lastErrorAdmiteHasta64CaracteresYRechazaMas() throws SQLException {
        inRolledBackTransaction(statement -> {
            assertThat(statement.executeUpdate(insertSql(UUID.randomUUID(), "REMINDER", "PENDING", "EMAIL", "1",
                    "NULL", "'" + "E".repeat(64) + "'"))).isEqualTo(1);
            assertRejected(statement, "notification_last_error_check", insertSql(UUID.randomUUID(), "CONFIRMATION",
                    "PENDING", "EMAIL", "1", "NULL", "'" + "E".repeat(65) + "'"));
        });
    }

    @Test
    void enviadaExigeSentAtYSoloEnviadaLoTiene() throws SQLException {
        inRolledBackTransaction(statement -> {
            assertThat(statement.executeUpdate(insertSql(UUID.randomUUID(), "REMINDER", "SENT", "EMAIL", "1",
                    "'2026-12-01T12:00:00Z'", "NULL"))).isEqualTo(1);
            assertRejected(statement, "notification_sent_at_check", insertSql(UUID.randomUUID(), "CONFIRMATION",
                    "SENT", "EMAIL", "1", "NULL", "NULL"));
            assertRejected(statement, "notification_sent_at_check", insertSql(UUID.randomUUID(), "RESCHEDULE",
                    "PENDING", "EMAIL", "0", "'2026-12-01T12:00:00Z'", "NULL"));
        });
    }

    @Test
    void unTurnoNoTieneDosNotificacionesDelMismoTipoPeroSiDeTiposDistintos() throws SQLException {
        inRolledBackTransaction(statement -> {
            insert(statement, "CONFIRMATION");
            statement.execute("SAVEPOINT duplicado");
            assertThatThrownBy(() -> insert(statement, "CONFIRMATION"))
                    .isInstanceOfSatisfying(SQLException.class, error -> {
                        assertThat(error.getSQLState()).isEqualTo(UNIQUE_VIOLATION);
                        assertThat(error.getMessage()).contains("notification_appointment_type_key");
                    });
            statement.execute("ROLLBACK TO SAVEPOINT duplicado");

            assertThat(statement.executeUpdate(insertSql(UUID.randomUUID(), "REMINDER", "PENDING", "EMAIL", "0",
                    "NULL", "NULL"))).isEqualTo(1);
        });
    }

    @Test
    void elMismoTipoEnOtroTurnoSeAcepta() throws SQLException {
        inRolledBackTransaction(statement -> {
            insert(statement, "CONFIRMATION");
            UUID other = newAppointment(statement, "2026-12-11T10:00:00Z", "2026-12-11T10:30:00Z");

            assertThat(statement.executeUpdate(insertSqlFor(other, UUID.randomUUID(), "CONFIRMATION", "PENDING",
                    "EMAIL", "0", "NULL", "NULL"))).isEqualTo(1);
        });
    }

    // ---- helpers ----

    @FunctionalInterface
    private interface Body {
        void run(Statement statement) throws SQLException;
    }

    private static void inRolledBackTransaction(Body body) throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                appointmentId = newAppointment(statement, "2026-12-10T10:00:00Z", "2026-12-10T10:30:00Z");
                body.run(statement);
            } finally {
                connection.rollback();
            }
        }
    }

    private static UUID newAppointment(Statement statement, String startsAt, String endsAt) throws SQLException {
        UUID patient = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        UUID appointment = UUID.randomUUID();
        statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) VALUES ('"
                + patient + "', '" + PRACTICE_ID + "', 'Test', 'Notif', '" + patient.toString().substring(0, 8)
                + "', '1100000000')");
        statement.executeUpdate("INSERT INTO service (id, practice_id, name, duration_min) VALUES ('" + service
                + "', '" + PRACTICE_ID + "', 'Limpieza', 30)");
        statement.executeUpdate("INSERT INTO appointment (id, practice_id, patient_id, service_id, starts_at, "
                + "ends_at, duration_min_snapshot, status, origin) VALUES ('" + appointment + "', '" + PRACTICE_ID
                + "', '" + patient + "', '" + service + "', '" + startsAt + "', '" + endsAt
                + "', 30, 'CONFIRMED', 'INTERNAL')");
        return appointment;
    }

    private static UUID insert(Statement statement, String type) throws SQLException {
        UUID id = UUID.randomUUID();
        statement.executeUpdate("INSERT INTO notification (id, practice_id, appointment_id, type, scheduled_for) "
                + "VALUES ('" + id + "', '" + PRACTICE_ID + "', '" + appointmentId + "', '" + type
                + "', '2026-12-09T13:00:00Z')");
        return id;
    }

    private static String insertSql(UUID id, String type, String status, String channel, String attempts,
            String sentAt, String lastError) {
        return insertSqlFor(appointmentId, id, type, status, channel, attempts, sentAt, lastError);
    }

    private static String insertSqlFor(UUID appointment, UUID id, String type, String status, String channel,
            String attempts, String sentAt, String lastError) {
        return "INSERT INTO notification (id, practice_id, appointment_id, type, channel, status, scheduled_for, "
                + "sent_at, attempts, last_error) VALUES ('" + id + "', '" + PRACTICE_ID + "', '" + appointment
                + "', '" + type + "', '" + channel + "', '" + status + "', '2026-12-09T13:00:00Z', " + sentAt + ", "
                + attempts + ", " + lastError + ")";
    }

    private static void assertRejected(Statement statement, String constraint, String sql) throws SQLException {
        statement.execute("SAVEPOINT rechazo");
        assertThatThrownBy(() -> statement.executeUpdate(sql))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(constraint);
        statement.execute("ROLLBACK TO SAVEPOINT rechazo");
    }

    private static Map<String, String> columnTypes(Connection connection) throws SQLException {
        Map<String, String> types = new LinkedHashMap<>();
        try (ResultSet columns = connection.getMetaData().getColumns(null, "public", "notification", null)) {
            while (columns.next()) {
                types.put(columns.getString("COLUMN_NAME"), columns.getString("TYPE_NAME"));
            }
        }
        return types;
    }

    private static Map<String, String> constraintTypes(Connection connection) throws SQLException {
        Map<String, String> types = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT conname, contype FROM pg_constraint WHERE conrelid = 'notification'::regclass")) {
            while (rows.next()) {
                types.put(rows.getString("conname"), rows.getString("contype"));
            }
        }
        return types;
    }

    /** Regla ON DELETE por columna FK: 'r' = RESTRICT. */
    private static Map<String, String> foreignKeyDeleteRules(Connection connection) throws SQLException {
        Map<String, String> rules = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT a.attname, c.confdeltype FROM pg_constraint c "
                                + "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1] "
                                + "WHERE c.conrelid = 'notification'::regclass AND c.contype = 'f'")) {
            while (rows.next()) {
                rules.put(rows.getString(1), rows.getString(2));
            }
        }
        return rules;
    }

    private static String partialIndexDefinition(Connection connection, String indexName) throws SQLException {
        List<String> definitions = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT indexdef FROM pg_indexes WHERE tablename = 'notification' AND indexname = '"
                                + indexName + "'")) {
            while (rows.next()) {
                definitions.add(rows.getString(1));
            }
        }
        assertThat(definitions).hasSize(1);
        return definitions.get(0);
    }
}
