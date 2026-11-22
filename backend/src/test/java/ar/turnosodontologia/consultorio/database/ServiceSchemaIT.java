package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Esquema de {@code service}, sus checks, indices y la revocación de borrado físico (V4, design.md D2/D3).
 */
class ServiceSchemaIT extends AbstractIntegrationTest {

    private static final String SEEDED_PRACTICE_ID = "01900000-0000-7000-8000-000000000001";

    @Test
    void laMigracionV4QuedaRegistradaComoExitosa() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT success FROM flyway_schema_history WHERE version = '4'")) {
            assertThat(rows.next()).as("V4 registrada en flyway_schema_history").isTrue();
            assertThat(rows.getBoolean("success")).isTrue();
        }
    }

    @Test
    void tablaServiceTieneLasColumnasConLosTipos() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection()) {
            Map<String, ColumnInfo> columns = columnsOfService(connection);
            assertThat(columns.keySet())
                    .as("columnas de service")
                    .containsExactlyInAnyOrder(
                            "id", "practice_id", "name", "description", "duration_min", "active",
                            "public_visible", "created_at", "updated_at");

            assertColumn(columns, "id", "uuid", "-", false);
            assertColumn(columns, "practice_id", "uuid", "-", false);
            assertColumn(columns, "name", "character varying", "120", false);
            assertColumn(columns, "description", "character varying", "500", true);
            assertColumn(columns, "duration_min", "integer", "-", false);
            assertColumn(columns, "active", "boolean", "-", false);
            assertColumn(columns, "public_visible", "boolean", "-", false);
            assertColumn(columns, "created_at", "timestamp with time zone", "-", false);
            assertColumn(columns, "updated_at", "timestamp with time zone", "-", false);
        }
    }

    @Test
    void serviceTieneElCheckDeDuracionPositivaEIndices() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet checks = statement.executeQuery(
                        "SELECT conname FROM pg_constraint WHERE conrelid = 'service'::regclass AND contype = 'c'")) {
            boolean hasDurationCheck = false;
            while (checks.next()) {
                if ("service_duration_min_check".equals(checks.getString("conname"))) {
                    hasDurationCheck = true;
                }
            }
            assertThat(hasDurationCheck).as("check service_duration_min_check").isTrue();
        }

        Set<String> indexes = new HashSet<>();
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT indexname FROM pg_indexes WHERE tablename = 'service' AND schemaname = 'public'")) {
            while (rows.next()) {
                indexes.add(rows.getString("indexname"));
            }
        }
        assertThat(indexes).contains("service_practice_name_idx", "service_public_idx");
    }

    @Test
    void serviceTieneLaFkConPracticeConRestriccionRestrict() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT pg_get_constraintdef(oid) AS definition, confdeltype AS onDeleteAction "
                                + "FROM pg_constraint WHERE conrelid = 'service'::regclass AND contype = 'f'")) {
            assertThat(rows.next()).as("FK de service hacia practice").isTrue();
            assertThat(rows.getString("definition")).contains("REFERENCES practice", "ON DELETE RESTRICT");
            assertThat(rows.getString("onDeleteAction")).isEqualTo("r");
        }
    }

    @Test
    void serviceAplicaLosDefaultsAlInsertarSinMarcas() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = DATABASE.appConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO service (id, practice_id, name, duration_min) VALUES (?::uuid, ?::uuid, 'Dolor', 45)")) {
                insert.setString(1, id.toString());
                insert.setString(2, SEEDED_PRACTICE_ID);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT active, public_visible FROM service WHERE id = ?::uuid")) {
                select.setString(1, id.toString());
                try (ResultSet rows = select.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean("active")).isTrue();
                    assertThat(rows.getBoolean("public_visible")).isFalse();
                }
            }
        }
    }

    @Test
    void serviceRechazaDuracionCeroYNegativa() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            assertThatThrownBy(() -> insertDuration(connection, 0))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("violates check constraint");
            assertThatThrownBy(() -> insertDuration(connection, -1))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("violates check constraint");
        }
    }

    @Test
    void laAplicacionPuedeInsertarLeerYActualizarService() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = DATABASE.appConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO service (id, practice_id, name, description, duration_min, active, public_visible) "
                            + "VALUES (?::uuid, ?::uuid, 'Dolor', 'Descripcion', 45, true, true)")) {
                insert.setString(1, id.toString());
                insert.setString(2, SEEDED_PRACTICE_ID);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT name, duration_min, active, public_visible FROM service WHERE id = ?::uuid")) {
                select.setString(1, id.toString());
                try (ResultSet rows = select.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("name")).isEqualTo("Dolor");
                    assertThat(rows.getInt("duration_min")).isEqualTo(45);
                    assertThat(rows.getBoolean("active")).isTrue();
                    assertThat(rows.getBoolean("public_visible")).isTrue();
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE service SET duration_min = 30, active = false, public_visible = true WHERE id = ?::uuid")) {
                update.setString(1, id.toString());
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT duration_min, active, public_visible FROM service WHERE id = ?::uuid")) {
                select.setString(1, id.toString());
                try (ResultSet rows = select.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt("duration_min")).isEqualTo(30);
                    assertThat(rows.getBoolean("active")).isFalse();
                    assertThat(rows.getBoolean("public_visible")).isTrue();
                }
            }
        }
    }

    @Test
    void laAplicacionNoPuedeBorrarService() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            assertThatThrownBy(() -> connection.prepareStatement("DELETE FROM service").executeUpdate())
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void laAplicacionNoPuedeTruncarService() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            assertThatThrownBy(() -> connection.prepareStatement("TRUNCATE TABLE service").executeUpdate())
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    private void insertDuration(Connection connection, int durationMin) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO service (id, practice_id, name, duration_min) VALUES (?::uuid, ?::uuid, 'Dolor', ?)")) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, SEEDED_PRACTICE_ID);
            insert.setInt(3, durationMin);
            insert.executeUpdate();
        }
    }

    private static Map<String, ColumnInfo> columnsOfService(Connection connection) throws SQLException {
        Map<String, ColumnInfo> columns = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT column_name, data_type, is_nullable, coalesce(character_maximum_length::text, '-') AS length"
                                + " FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'service'")) {
            while (rows.next()) {
                columns.put(
                        rows.getString("column_name"),
                        new ColumnInfo(
                                rows.getString("data_type"),
                                rows.getString("length"),
                                "YES".equals(rows.getString("is_nullable"))));
            }
        }
        return columns;
    }

    private static void assertColumn(
            Map<String, ColumnInfo> columns, String name, String dataType, String length, boolean nullable) {
        ColumnInfo info = columns.get(name);
        assertThat(info).as("columna %s existe", name).isNotNull();
        assertThat(info.dataType()).as("tipo de %s", name).isEqualTo(dataType);
        assertThat(info.length()).as("longitud de %s", name).isEqualTo(length);
        assertThat(info.nullable()).as("nulabilidad de %s", name).isEqualTo(nullable);
    }

    private record ColumnInfo(String dataType, String length, boolean nullable) {}
}
