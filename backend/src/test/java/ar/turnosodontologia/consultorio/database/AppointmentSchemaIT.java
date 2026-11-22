package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.EmbeddedPostgresSupport;
import ar.turnosodontologia.consultorio.support.EmbeddedPostgresSupport.Database;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de esquema de {@code appointment} (V13): columnas, CHECKs, rango generado, exclusion de solapamiento e
 * indices. Cada test trabaja dentro de una transaccion que se revierte.
 */
class AppointmentSchemaIT extends AbstractIntegrationTest {

    private static final String PRACTICE_ID = "01900000-0000-7000-8000-000000000001";
    private static final String OTHER_PRACTICE_ID = "01900000-0000-7000-8000-000000000009";
    private static final String OVERLAP_SQLSTATE = "23P01";

    @Test
    void laMigracionV13QuedaRegistradaComoExitosa() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM flyway_schema_history WHERE version = '13' AND success")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void appointmentTieneColumnasRestriccionesEIndicesDelDiseno() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection()) {
            assertThat(columnTypes(connection))
                    .containsEntry("id", "uuid")
                    .containsEntry("practice_id", "uuid")
                    .containsEntry("patient_id", "uuid")
                    .containsEntry("service_id", "uuid")
                    .containsEntry("starts_at", "timestamptz")
                    .containsEntry("ends_at", "timestamptz")
                    .containsEntry("duration_min_snapshot", "int4")
                    .containsEntry("status", "text")
                    .containsEntry("origin", "text")
                    .containsEntry("overbooking", "bool")
                    .containsEntry("cancelled_by", "text")
                    .containsEntry("cancelled_at", "timestamptz")
                    .containsEntry("notes", "text")
                    .containsEntry("period", "tstzrange")
                    .containsEntry("created_at", "timestamptz")
                    .containsEntry("updated_at", "timestamptz");

            assertThat(constraintTypes(connection))
                    .containsEntry("appointment_status_check", "c")
                    .containsEntry("appointment_origin_check", "c")
                    .containsEntry("appointment_cancelled_by_check", "c")
                    .containsEntry("appointment_duration_check", "c")
                    .containsEntry("appointment_notes_check", "c")
                    .containsEntry("appointment_cancellation_check", "c")
                    .containsEntry("appointment_period_check", "c")
                    .containsEntry("appointment_no_overlap", "x");

            assertThat(foreignKeyDeleteRules(connection))
                    .containsEntry("practice_id", "r")
                    .containsEntry("patient_id", "r")
                    .containsEntry("service_id", "r");

            assertThat(indexColumns(connection, "appointment_practice_starts_idx"))
                    .containsExactly("practice_id", "starts_at");
            assertThat(indexColumns(connection, "appointment_patient_idx")).containsExactly("patient_id");
            assertThat(indexColumns(connection, "appointment_service_idx")).containsExactly("service_id");
            assertThat(indexColumns(connection, "appointment_status_starts_idx"))
                    .containsExactly("status", "starts_at");
        }
    }

    @Test
    void elPeriodoGeneradoEsSemiabierto() throws SQLException {
        inRolledBackTransaction(statement -> {
            UUID id = insert(statement, "CONFIRMED", "2026-12-10T13:00:00Z", "2026-12-10T13:30:00Z");
            try (ResultSet rows = statement.executeQuery(
                    "SELECT period::text FROM appointment WHERE id = '" + id + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).startsWith("[").endsWith(")")
                        .contains("2026-12-10 13:00:00+00", "2026-12-10 13:30:00+00");
            }
        });
    }

    @Test
    void laBaseRechazaValoresDeDominioFueraDeRango() throws SQLException {
        inRolledBackTransaction(statement -> {
            assertRejected(statement, "appointment_status_check", insertSql(UUID.randomUUID(), "BOGUS",
                    "2026-12-10T10:00:00Z", "2026-12-10T10:30:00Z", 30, "INTERNAL", "NULL", "NULL", "NULL"));
            assertRejected(statement, "appointment_origin_check", insertSql(UUID.randomUUID(), "CONFIRMED",
                    "2026-12-10T10:00:00Z", "2026-12-10T10:30:00Z", 30, "BOGUS", "NULL", "NULL", "NULL"));
            assertRejected(statement, "appointment_cancelled_by_check", insertSql(UUID.randomUUID(), "CANCELLED",
                    "2026-12-10T10:00:00Z", "2026-12-10T10:30:00Z", 30, "INTERNAL", "'BOGUS'",
                    "'2026-12-01T00:00:00Z'", "NULL"));
            assertRejected(statement, "appointment_duration_check", insertSql(UUID.randomUUID(), "CONFIRMED",
                    "2026-12-10T10:00:00Z", "2026-12-10T10:30:00Z", 0, "INTERNAL", "NULL", "NULL", "NULL"));
            assertRejected(statement, "appointment_duration_check", insertSql(UUID.randomUUID(), "CONFIRMED",
                    "2026-12-10T10:00:00Z", "2026-12-10T10:30:00Z", -5, "INTERNAL", "NULL", "NULL", "NULL"));
        });
    }

    @Test
    void laBaseRechazaRangosInvertidosOVacios() throws SQLException {
        inRolledBackTransaction(statement -> {
            // Con la columna generada PostgreSQL puede rechazar antes por el constructor del rango; en ambos casos
            // la fila se rechaza.
            for (String[] range : new String[][] {
                    {"2026-12-10T11:00:00Z", "2026-12-10T10:00:00Z"},
                    {"2026-12-10T10:00:00Z", "2026-12-10T10:00:00Z"}}) {
                statement.execute("SAVEPOINT rango");
                assertThatThrownBy(() -> statement.executeUpdate(insertSql(UUID.randomUUID(), "CONFIRMED", range[0],
                        range[1], 30, "INTERNAL", "NULL", "NULL", "NULL")))
                        .isInstanceOf(SQLException.class)
                        .hasMessageMatching("(?s).*(violates check constraint|range lower bound must be less).*");
                statement.execute("ROLLBACK TO SAVEPOINT rango");
            }
        });
    }

    @Test
    void notasAdmiteHasta1000CaracteresYRechazaMas() throws SQLException {
        inRolledBackTransaction(statement -> {
            assertThat(statement.executeUpdate(insertSql(UUID.randomUUID(), "CONFIRMED", "2026-12-10T10:00:00Z",
                    "2026-12-10T10:30:00Z", 30, "INTERNAL", "NULL", "NULL", "'" + "x".repeat(1000) + "'")))
                    .isEqualTo(1);
            assertRejected(statement, "appointment_notes_check", insertSql(UUID.randomUUID(), "CONFIRMED",
                    "2026-12-11T10:00:00Z", "2026-12-11T10:30:00Z", 30, "INTERNAL", "NULL", "NULL",
                    "'" + "x".repeat(1001) + "'"));
        });
    }

    @Test
    void laCancelacionExigeCancelledByYCancelledAtJuntosSoloEnCancelled() throws SQLException {
        inRolledBackTransaction(statement -> {
            assertThat(statement.executeUpdate(insertSql(UUID.randomUUID(), "CANCELLED", "2026-12-10T10:00:00Z",
                    "2026-12-10T10:30:00Z", 30, "INTERNAL", "'DENTIST'", "'2026-12-01T00:00:00Z'", "NULL")))
                    .isEqualTo(1);
            // cancelado sin alguno de los dos
            assertRejected(statement, "appointment_cancellation_check", insertSql(UUID.randomUUID(), "CANCELLED",
                    "2026-12-11T10:00:00Z", "2026-12-11T10:30:00Z", 30, "INTERNAL", "NULL",
                    "'2026-12-01T00:00:00Z'", "NULL"));
            assertRejected(statement, "appointment_cancellation_check", insertSql(UUID.randomUUID(), "CANCELLED",
                    "2026-12-12T10:00:00Z", "2026-12-12T10:30:00Z", 30, "INTERNAL", "'PATIENT'", "NULL", "NULL"));
            // no cancelado con cualquiera de los dos
            assertRejected(statement, "appointment_cancellation_check", insertSql(UUID.randomUUID(), "CONFIRMED",
                    "2026-12-13T10:00:00Z", "2026-12-13T10:30:00Z", 30, "INTERNAL", "'PATIENT'", "NULL", "NULL"));
            assertRejected(statement, "appointment_cancellation_check", insertSql(UUID.randomUUID(), "COMPLETED",
                    "2026-12-14T10:00:00Z", "2026-12-14T10:30:00Z", 30, "INTERNAL", "NULL",
                    "'2026-12-01T00:00:00Z'", "NULL"));
        });
    }

    @Test
    void elSolapamientoDeTurnosNoCanceladosSeRechazaConSqlState23P01() throws SQLException {
        for (String status : List.of("CONFIRMED", "COMPLETED", "NO_SHOW")) {
            inRolledBackTransaction(statement -> {
                insert(statement, status, "2026-12-10T10:00:00Z", "2026-12-10T10:30:00Z");
                statement.execute("SAVEPOINT solapa");
                assertThatThrownBy(() -> insert(statement, "CONFIRMED", "2026-12-10T10:15:00Z",
                        "2026-12-10T10:45:00Z"))
                        .isInstanceOfSatisfying(SQLException.class, error -> {
                            assertThat(error.getSQLState()).isEqualTo(OVERLAP_SQLSTATE);
                            assertThat(error.getMessage()).contains("appointment_no_overlap");
                        });
                statement.execute("ROLLBACK TO SAVEPOINT solapa");
            });
        }
    }

    @Test
    void adyacenciaCanceladosYOtroConsultorioSeAceptan() throws SQLException {
        inRolledBackTransaction(statement -> {
            insert(statement, "CONFIRMED", "2026-12-10T10:00:00Z", "2026-12-10T10:30:00Z");
            insert(statement, "CANCELLED", "2026-12-10T11:00:00Z", "2026-12-10T11:30:00Z");
            assertThat(statement.executeUpdate(insertSql(UUID.randomUUID(), "CONFIRMED", "2026-12-10T10:30:00Z",
                    "2026-12-10T11:00:00Z", 30, "INTERNAL", "NULL", "NULL", "NULL"))).isEqualTo(1);
            // solapa con el cancelado: se acepta
            assertThat(statement.executeUpdate(insertSql(UUID.randomUUID(), "CONFIRMED", "2026-12-10T11:00:00Z",
                    "2026-12-10T11:30:00Z", 30, "INTERNAL", "NULL", "NULL", "NULL"))).isEqualTo(1);
        });
    }

    @Test
    void elMismoRangoEnOtroConsultorioSeAcepta() throws SQLException {
        // El consultorio de la instalacion tiene indice unico de una fila: se suspende dentro de una transaccion del
        // rol de migracion que se revierte, para probar la componente practice_id de la exclusion.
        try (Connection connection = DATABASE.migratorConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP INDEX practice_single_row");
                statement.executeUpdate("INSERT INTO practice (id, name) VALUES ('" + OTHER_PRACTICE_ID
                        + "', 'Otro consultorio')");
                UUID patient = insertPatient(statement);
                UUID service = insertService(statement);
                assertThat(statement.executeUpdate(insertFor(PRACTICE_ID, patient, service))).isEqualTo(1);
                assertThat(statement.executeUpdate(insertFor(OTHER_PRACTICE_ID, patient, service))).isEqualTo(1);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void reactivarUnCanceladoSobreUnRangoOcupadoSeRechaza() throws SQLException {
        inRolledBackTransaction(statement -> {
            UUID cancelled = UUID.randomUUID();
            statement.executeUpdate(insertSql(cancelled, "CANCELLED", "2026-12-10T10:00:00Z",
                    "2026-12-10T10:30:00Z", 30, "INTERNAL", "'DENTIST'", "'2026-12-01T00:00:00Z'", "NULL"));
            insert(statement, "CONFIRMED", "2026-12-10T10:00:00Z", "2026-12-10T10:30:00Z");

            statement.execute("SAVEPOINT reactivar");
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE appointment SET status = 'CONFIRMED', "
                    + "cancelled_by = NULL, cancelled_at = NULL WHERE id = '" + cancelled + "'"))
                    .isInstanceOfSatisfying(SQLException.class,
                            error -> assertThat(error.getSQLState()).isEqualTo(OVERLAP_SQLSTATE));
            statement.execute("ROLLBACK TO SAVEPOINT reactivar");
        });
    }


    @Test
    void laMigracionV13SeAplicaDesdeUnaBaseMigradaHastaV12SinVersionesFueraDeOrden() throws SQLException {
        try (Database database = EmbeddedPostgresSupport.startDatabase()) {
            migrate(database, "12");
            migrate(database, "13");

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
                assertThat(versions).endsWith("12", "13").doesNotHaveDuplicates();
                assertThat(versions.get(versions.size() - 1)).isEqualTo("13");
            }
            try (Connection connection = database.migratorConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT 1 FROM information_schema.columns WHERE table_name = 'appointment' "
                                    + "AND column_name = 'period'")) {
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

    // ---- helpers ----

    @FunctionalInterface
    private interface Body {
        void run(Statement statement) throws SQLException;
    }

    private static void inRolledBackTransaction(Body body) throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                patientId = insertPatient(statement);
                serviceId = insertService(statement);
                body.run(statement);
            } finally {
                connection.rollback();
            }
        }
    }

    private static UUID patientId;
    private static UUID serviceId;

    private static UUID insertPatient(Statement statement) throws SQLException {
        UUID id = UUID.randomUUID();
        statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) VALUES ('"
                + id + "', '" + PRACTICE_ID + "', 'Test', 'Paciente', '" + id.toString().substring(0, 8)
                + "', '1100000000')");
        return id;
    }

    private static UUID insertService(Statement statement) throws SQLException {
        UUID id = UUID.randomUUID();
        statement.executeUpdate("INSERT INTO service (id, practice_id, name, duration_min) VALUES ('" + id + "', '"
                + PRACTICE_ID + "', 'Limpieza', 30)");
        return id;
    }

    private static UUID insert(Statement statement, String status, String startsAt, String endsAt)
            throws SQLException {
        UUID id = UUID.randomUUID();
        boolean cancelled = "CANCELLED".equals(status);
        statement.executeUpdate(insertSql(id, status, startsAt, endsAt, 30, "INTERNAL",
                cancelled ? "'DENTIST'" : "NULL", cancelled ? "'2026-12-01T00:00:00Z'" : "NULL", "NULL"));
        return id;
    }

    private static String insertSql(UUID id, String status, String startsAt, String endsAt, int duration,
            String origin, String cancelledBy, String cancelledAt, String notes) throws SQLException {
        return "INSERT INTO appointment (id, practice_id, patient_id, service_id, starts_at, ends_at, "
                + "duration_min_snapshot, status, origin, cancelled_by, cancelled_at, notes) VALUES ('" + id + "', '"
                + PRACTICE_ID + "', '" + patientId + "', '" + serviceId + "', '" + startsAt + "', '" + endsAt + "', "
                + duration + ", '" + status + "', '" + origin + "', " + cancelledBy + ", " + cancelledAt + ", "
                + notes + ")";
    }

    private static String insertFor(String practiceId, UUID patient, UUID service) {
        return "INSERT INTO appointment (id, practice_id, patient_id, service_id, starts_at, ends_at, "
                + "duration_min_snapshot, status, origin) VALUES ('" + UUID.randomUUID() + "', '" + practiceId + "', '"
                + patient + "', '" + service + "', '2026-12-12T10:00:00Z', '2026-12-12T10:30:00Z', 30, 'CONFIRMED', "
                + "'INTERNAL')";
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
        try (ResultSet columns = connection.getMetaData().getColumns(null, "public", "appointment", null)) {
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
                        "SELECT conname, contype FROM pg_constraint WHERE conrelid = 'appointment'::regclass")) {
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
                                + "WHERE c.conrelid = 'appointment'::regclass AND c.contype = 'f'")) {
            while (rows.next()) {
                rules.put(rows.getString(1), rows.getString(2));
            }
        }
        return rules;
    }

    private static List<String> indexColumns(Connection connection, String indexName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(null, "public", "appointment", false, false)) {
            while (indexes.next()) {
                if (indexName.equals(indexes.getString("INDEX_NAME"))) {
                    columns.add(indexes.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }
}
