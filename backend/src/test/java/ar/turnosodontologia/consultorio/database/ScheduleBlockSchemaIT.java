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
 * Pruebas de esquema de {@code schedule_block} (V8): columnas, rango generado, no solapamiento y privilegios.
 */
class ScheduleBlockSchemaIT extends AbstractIntegrationTest {

    private static final String PRACTICE_ID = "01900000-0000-7000-8000-000000000001";

    @Test
    void laMigracionV8QuedaRegistradaComoExitosa() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM flyway_schema_history WHERE version = '8' AND success")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void scheduleBlockTieneLasColumnasYRestriccionesDelDiseno() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection()) {
            assertThat(columnTypes(connection))
                    .containsEntry("id", "uuid")
                    .containsEntry("practice_id", "uuid")
                    .containsEntry("starts_at", "timestamptz")
                    .containsEntry("ends_at", "timestamptz")
                    .containsEntry("reason", "text")
                    .containsEntry("period", "tstzrange")
                    .containsEntry("created_at", "timestamptz")
                    .containsEntry("updated_at", "timestamptz");

            assertThat(constraintTypes(connection))
                    .containsEntry("schedule_block_reason_check", "c")
                    .containsEntry("schedule_block_period_check", "c")
                    .containsEntry("schedule_block_no_overlap", "x");

            assertThat(indexColumns(connection, "schedule_block_practice_starts_idx"))
                    .containsExactly("practice_id", "starts_at");
        }
    }

    @Test
    void laAplicacionPuedeGestionarBloqueosPeroNoTruncarlos() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate(insertBlock(id, PRACTICE_ID, "2026-12-10T12:00:00Z",
                        "2026-12-10T14:00:00Z", "Vacaciones"))).isEqualTo(1);

                try (ResultSet rows = statement.executeQuery(
                        "SELECT reason FROM schedule_block WHERE id = '" + id + "'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("reason")).isEqualTo("Vacaciones");
                }

                assertThat(statement.executeUpdate("UPDATE schedule_block SET reason = 'Tramite' WHERE id = '"
                        + id + "'")).isEqualTo(1);
                assertThat(statement.executeUpdate("DELETE FROM schedule_block WHERE id = '" + id + "'"))
                        .isEqualTo(1);
            } finally {
                connection.rollback();
            }
        }

        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("TRUNCATE TABLE schedule_block"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void laBaseRechazaRangosInvertidosYDuraNula() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            // Con la columna generada, PostgreSQL puede rechazar el rango invertido al construir tstzrange antes
            // de evaluar el CHECK; en ambos casos la fila se rechaza y la restriccion CHECK sigue presente.
            assertThatThrownBy(() -> statement.executeUpdate(insertBlock(UUID.randomUUID(), PRACTICE_ID,
                    "2026-12-10T14:00:00Z", "2026-12-10T12:00:00Z", "Vacaciones")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageMatching("(?s).*(violates check constraint|range lower bound must be less).*");

            assertThatThrownBy(() -> statement.executeUpdate(insertBlock(UUID.randomUUID(), PRACTICE_ID,
                    "2026-12-10T12:00:00Z", "2026-12-10T12:00:00Z", "Vacaciones")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageMatching("(?s).*(violates check constraint|range lower bound must be less).*");
        }
    }

    @Test
    void laBaseRechazaMotivoVacioYMuyLargo() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(insertBlock(UUID.randomUUID(), PRACTICE_ID,
                    "2026-12-10T12:00:00Z", "2026-12-10T14:00:00Z", "   ")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("schedule_block_reason_check");

            assertThatThrownBy(() -> statement.executeUpdate(insertBlock(UUID.randomUUID(), PRACTICE_ID,
                    "2026-12-10T12:00:00Z", "2026-12-10T14:00:00Z", "x".repeat(501))))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("schedule_block_reason_check");
        }
    }

    @Test
    void laBaseRechazaSolapamientoParcialEIdentico() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate(insertBlock(UUID.randomUUID(), PRACTICE_ID,
                        "2026-12-10T09:00:00Z", "2026-12-10T11:00:00Z", "Vacaciones"))).isEqualTo(1);

                // Cada intento fallido aborta la transaccion: se aísla con savepoints para poder probar el segundo.
                statement.execute("SAVEPOINT solapamiento_parcial");
                assertThatThrownBy(() -> statement.executeUpdate(insertBlock(UUID.randomUUID(), PRACTICE_ID,
                        "2026-12-10T10:00:00Z", "2026-12-10T12:00:00Z", "Tramite")))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("violates exclusion constraint")
                        .hasMessageContaining("schedule_block_no_overlap");
                statement.execute("ROLLBACK TO SAVEPOINT solapamiento_parcial");

                statement.execute("SAVEPOINT solapamiento_identico");
                assertThatThrownBy(() -> statement.executeUpdate(insertBlock(UUID.randomUUID(), PRACTICE_ID,
                        "2026-12-10T09:00:00Z", "2026-12-10T11:00:00Z", "Tramite")))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("schedule_block_no_overlap");
                statement.execute("ROLLBACK TO SAVEPOINT solapamiento_identico");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void laBaseAceptaBloqueosAdyacentesYDeOtroConsultorio() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate(insertBlock(UUID.randomUUID(), PRACTICE_ID,
                        "2026-12-11T09:00:00Z", "2026-12-11T11:00:00Z", "Vacaciones"))).isEqualTo(1);
                assertThat(statement.executeUpdate(insertBlock(UUID.randomUUID(), PRACTICE_ID,
                        "2026-12-11T11:00:00Z", "2026-12-11T12:00:00Z", "Tramite"))).isEqualTo(1);
            } finally {
                connection.rollback();
            }
        }

        // El consultorio de la instalacion tiene indice unico de una fila: se suspende dentro de una transaccion
        // del rol de migracion que se revierte al final, para probar la componente practice_id del EXCLUDE.
        String otherPracticeId = "01900000-0000-7000-8000-000000000009";
        try (Connection connection = DATABASE.migratorConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP INDEX practice_single_row");
                assertThat(statement.executeUpdate("INSERT INTO practice (id, name) VALUES ('"
                        + otherPracticeId + "', 'Otro consultorio')")).isEqualTo(1);
                assertThat(statement.executeUpdate(insertBlock(UUID.randomUUID(), PRACTICE_ID,
                        "2026-12-12T10:00:00Z", "2026-12-12T12:00:00Z", "Vacaciones"))).isEqualTo(1);
                assertThat(statement.executeUpdate(insertBlock(UUID.randomUUID(), otherPracticeId,
                        "2026-12-12T10:00:00Z", "2026-12-12T12:00:00Z", "Vacaciones"))).isEqualTo(1);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void elPeriodoGeneradoEsSemiabiertoYConsistenteConLosInstantes() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate(insertBlock(id, PRACTICE_ID,
                        "2026-12-10T12:00:00Z", "2026-12-10T14:00:00Z", "Vacaciones"))).isEqualTo(1);
                try (ResultSet rows = statement.executeQuery(
                        "SELECT period::text FROM schedule_block WHERE id = '" + id + "'")) {
                    assertThat(rows.next()).isTrue();
                    String period = rows.getString(1);
                    assertThat(period).startsWith("[").endsWith(")")
                            .contains("2026-12-10 12:00:00+00", "2026-12-10 14:00:00+00");
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void laMigracionV8SeAplicaDesdeUnaBaseMigradaHastaV7() throws SQLException {
        try (Database database = EmbeddedPostgresSupport.startDatabase()) {
            migrate(database, "7");
            migrate(database, "8");

            try (Connection connection = database.migratorConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank")) {
                List<String> versions = new ArrayList<>();
                while (rows.next()) {
                    assertThat(rows.getBoolean("success")).isTrue();
                    versions.add(rows.getString("version"));
                }
                assertThat(versions).containsExactly("1", "2", "3", "5", "7", "8");
            }
            try (Connection connection = database.migratorConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT 1 FROM information_schema.columns WHERE table_name = 'schedule_block' "
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

    private static String insertBlock(UUID id, String practiceId, String startsAt, String endsAt, String reason) {
        return String.format(
                "INSERT INTO schedule_block (id, practice_id, starts_at, ends_at, reason) "
                        + "VALUES ('%s', '%s', '%s', '%s', '%s')",
                id, practiceId, startsAt, endsAt, reason);
    }

    private static Map<String, String> columnTypes(Connection connection) throws SQLException {
        Map<String, String> types = new LinkedHashMap<>();
        try (ResultSet columns = connection.getMetaData().getColumns(null, "public", "schedule_block", null)) {
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
                        "SELECT conname, contype FROM pg_constraint "
                                + "WHERE conrelid = 'schedule_block'::regclass")) {
            while (rows.next()) {
                types.put(rows.getString("conname"), rows.getString("contype"));
            }
        }
        return types;
    }

    private static List<String> indexColumns(Connection connection, String indexName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(null, "public", "schedule_block", false, false)) {
            while (indexes.next()) {
                if (indexName.equals(indexes.getString("INDEX_NAME"))) {
                    columns.add(indexes.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }
}
