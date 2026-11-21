package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El rol de aplicacion solo puede INSERT/SELECT en el rastro; la base impide cualquier mutacion. */
class AuditLogPrivilegesIT extends AbstractIntegrationTest {

    private static final String PRACTICE = "01900000-0000-7000-8000-000000000001";

    private long rowId;

    @BeforeEach
    void seedRow() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO audit_log (practice_id, action, details) VALUES (?::uuid, 'HC_READ', '{}'::jsonb) RETURNING id")) {
            statement.setString(1, PRACTICE);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                rowId = rows.getLong(1);
            }
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM audit_log");
        }
    }

    @Test
    void laAplicacionPuedeInsertarYConsultarSuPropiaFila() throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO audit_log (practice_id, action) VALUES (?::uuid, 'LOGIN_OK') RETURNING id")) {
                insert.setString(1, PRACTICE);
                try (ResultSet rows = insert.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
            }
            try (PreparedStatement select = connection.prepareStatement("SELECT count(*) FROM audit_log WHERE id = ?")) {
                select.setLong(1, rowId);
                try (ResultSet rows = select.executeQuery()) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void laAplicacionNoPuedeActualizarBorrarNiTruncarYLaFilaSobrevive() throws SQLException {
        assertRejected("UPDATE audit_log SET action = 'HC_CREATE' WHERE id = " + rowId);
        assertRejected("DELETE FROM audit_log WHERE id = " + rowId);
        assertRejected("TRUNCATE audit_log");

        try (Connection connection = DATABASE.appConnection();
                PreparedStatement select = connection.prepareStatement("SELECT action FROM audit_log WHERE id = ?")) {
            select.setLong(1, rowId);
            try (ResultSet rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("action")).isEqualTo("HC_READ");
            }
        }
    }

    @Test
    void laBaseRechazaAccionesDesconocidasDetailsNoObjetoYClavesPersonales() throws SQLException {
        assertInsertRejected("DELETE_EVERYTHING", "'{}'::jsonb", "audit_log_action_check");
        assertInsertRejected("HC_READ", "'[]'::jsonb", "audit_log_details_object_check");
        assertInsertRejected("HC_READ", "'{\"email\":\"dr@ejemplo.com\"}'::jsonb",
                "audit_log_details_no_pii_check");

        try (Connection connection = DATABASE.appConnection(); Statement statement = connection.createStatement()) {
            assertThatCode(() -> {
                try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM audit_log")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isGreaterThanOrEqualTo(1);
                }
            }).doesNotThrowAnyException();
        }
    }

    private void assertRejected(String sql) throws SQLException {
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

    private void assertInsertRejected(String action, String detailsSql, String constraint) throws SQLException {
        try (Connection connection = DATABASE.appConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                String sql = "INSERT INTO audit_log (practice_id, action, details) VALUES ('" + PRACTICE
                        + "'::uuid, '" + action + "', " + detailsSql + ")";
                assertThatThrownBy(() -> statement.executeUpdate(sql))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining(constraint);
            } finally {
                connection.rollback();
            }
        }
    }
}
