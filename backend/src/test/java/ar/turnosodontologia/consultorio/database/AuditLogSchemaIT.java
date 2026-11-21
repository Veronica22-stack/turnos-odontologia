package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Esquema inmutable de auditoria creado por V3 (design D1, spec database-migrations). */
class AuditLogSchemaIT extends AbstractIntegrationTest {

    private final JdbcTemplate jdbcTemplate;

    AuditLogSchemaIT(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void laMigracionV3QuedaRegistradaComoExitosa() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT success FROM flyway_schema_history WHERE version = '3'")) {
            assertThat(rows.next()).as("V3 registrada en flyway_schema_history").isTrue();
            assertThat(rows.getBoolean("success")).isTrue();
        }
    }

    @Test
    void laTablaAuditLogTieneTodasLasColumnasYTiposDelDiseno() {
        java.util.Map<String, String> types = jdbcTemplate.query(
                "SELECT column_name, data_type FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'audit_log'",
                rows -> {
                    java.util.Map<String, String> found = new java.util.HashMap<>();
                    while (rows.next()) {
                        found.put(rows.getString("column_name"), rows.getString("data_type"));
                    }
                    return found;
                });

        assertThat(types).containsExactlyInAnyOrderEntriesOf(java.util.Map.ofEntries(
                java.util.Map.entry("id", "bigint"),
                java.util.Map.entry("practice_id", "uuid"),
                java.util.Map.entry("user_id", "uuid"),
                java.util.Map.entry("action", "text"),
                java.util.Map.entry("entity_type", "text"),
                java.util.Map.entry("entity_id", "text"),
                java.util.Map.entry("patient_id", "uuid"),
                java.util.Map.entry("ip", "inet"),
                java.util.Map.entry("user_agent", "text"),
                java.util.Map.entry("occurred_at", "timestamp with time zone"),
                java.util.Map.entry("details", "jsonb")));
    }

    @Test
    void losIndicesYChecksDeAuditoriaExisten() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'audit_log'",
                String.class);
        assertThat(indexes).contains("audit_log_practice_occurred_idx", "audit_log_user_occurred_idx",
                "audit_log_patient_occurred_idx", "audit_log_action_occurred_idx");

        List<String> checks = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'audit_log'::regclass AND contype = 'c'",
                String.class);
        assertThat(checks).contains("audit_log_action_check", "audit_log_details_object_check",
                "audit_log_details_no_pii_check");
    }

    @Test
    void patientIdEsOpcionalYSinLlaveForaneaHastaC10() throws SQLException {
        Boolean nullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable = 'YES' FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'audit_log' AND column_name = 'patient_id'",
                Boolean.class);
        assertThat(nullable).isTrue();

        Integer patientAttnum = jdbcTemplate.queryForObject(
                "SELECT attnum FROM pg_attribute WHERE attrelid = 'audit_log'::regclass AND attname = 'patient_id'",
                Integer.class);
        List<String> foreignKeys = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'audit_log'::regclass AND contype = 'f' "
                        + "AND conkey @> ARRAY[?]::smallint[]",
                String.class, patientAttnum);
        assertThat(foreignKeys).isEmpty();
    }

    @Test
    void losPrivilegiosDePracticeYAppUserNoSeModifican() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            assertThat(privilege(statement, "practice", "SELECT")).isTrue();
            assertThat(privilege(statement, "practice", "UPDATE")).isTrue();
            assertThat(privilege(statement, "practice", "INSERT")).isFalse();
            assertThat(privilege(statement, "practice", "DELETE")).isFalse();
            assertThat(privilege(statement, "app_user", "SELECT")).isTrue();
            assertThat(privilege(statement, "app_user", "INSERT")).isTrue();
            assertThat(privilege(statement, "app_user", "UPDATE")).isTrue();
            assertThat(privilege(statement, "app_user", "DELETE")).isFalse();
        }
    }

    private static boolean privilege(Statement statement, String table, String privilege) throws SQLException {
        try (ResultSet rows = statement.executeQuery(
                "SELECT has_table_privilege(current_user, '" + table + "', '" + privilege + "')")) {
            rows.next();
            return rows.getBoolean(1);
        }
    }
}
