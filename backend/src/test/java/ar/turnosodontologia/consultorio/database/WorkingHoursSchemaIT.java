package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de esquema de {@code working_hours} y columnas de reserva en {@code practice} (V5).
 */
class WorkingHoursSchemaIT extends AbstractIntegrationTest {

    private static final String SEEDED_PRACTICE_ID = "01900000-0000-7000-8000-000000000001";

    @Test
    void laMigracionV5QuedaRegistradaComoExitosa() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM flyway_schema_history WHERE version = '5' AND success")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void practiceTieneValoresPorDefectoDeReserva() throws SQLException {
        try (Connection connection = DATABASE.appConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT slot_granularity_min, min_notice_min, max_advance_days, reminder_hours_before FROM practice")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt("slot_granularity_min")).isEqualTo(15);
            assertThat(rows.getInt("min_notice_min")).isEqualTo(120);
            assertThat(rows.getInt("max_advance_days")).isEqualTo(30);
            assertThat(rows.getInt("reminder_hours_before")).isEqualTo(24);
        }
    }

    @Test
    void granularidadInvalidaEnPracticeEsRechazada() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE practice SET slot_granularity_min = 25"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("violates check constraint");
        }
    }

    @Test
    void laAplicacionPuedeInsertarLeerYBorrarEnWorkingHours() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int inserted = statement.executeUpdate(String.format(
                        "INSERT INTO working_hours (id, practice_id, weekday, start_time, end_time) VALUES ('%s', '%s', 1, '09:00:00', '13:00:00')",
                        id, SEEDED_PRACTICE_ID));
                assertThat(inserted).isEqualTo(1);

                try (ResultSet rows = statement.executeQuery(
                        String.format("SELECT weekday, start_time, end_time FROM working_hours WHERE id = '%s'", id))) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt("weekday")).isEqualTo(1);
                    assertThat(rows.getString("start_time")).isEqualTo("09:00:00");
                    assertThat(rows.getString("end_time")).isEqualTo("13:00:00");
                }

                int deleted = statement.executeUpdate(String.format("DELETE FROM working_hours WHERE id = '%s'", id));
                assertThat(deleted).isEqualTo(1);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void workingHoursRechazaWeekdayFueraDeRango() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(String.format(
                            "INSERT INTO working_hours (id, practice_id, weekday, start_time, end_time) VALUES ('%s', '%s', 0, '09:00:00', '13:00:00')",
                            UUID.randomUUID(), SEEDED_PRACTICE_ID)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("violates check constraint");

            assertThatThrownBy(() -> statement.executeUpdate(String.format(
                            "INSERT INTO working_hours (id, practice_id, weekday, start_time, end_time) VALUES ('%s', '%s', 8, '09:00:00', '13:00:00')",
                            UUID.randomUUID(), SEEDED_PRACTICE_ID)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("violates check constraint");
        }
    }

    @Test
    void workingHoursRechazaHorarioDeFinMenorOIgualAInicio() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(String.format(
                            "INSERT INTO working_hours (id, practice_id, weekday, start_time, end_time) VALUES ('%s', '%s', 1, '13:00:00', '09:00:00')",
                            UUID.randomUUID(), SEEDED_PRACTICE_ID)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("violates check constraint");

            assertThatThrownBy(() -> statement.executeUpdate(String.format(
                            "INSERT INTO working_hours (id, practice_id, weekday, start_time, end_time) VALUES ('%s', '%s', 1, '09:00:00', '09:00:00')",
                            UUID.randomUUID(), SEEDED_PRACTICE_ID)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("violates check constraint");
        }
    }

    @Test
    void laAplicacionNoPuedeTruncarWorkingHours() throws SQLException {
        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("TRUNCATE TABLE working_hours"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }
}
