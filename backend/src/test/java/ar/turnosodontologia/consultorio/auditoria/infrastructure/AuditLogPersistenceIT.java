package ar.turnosodontologia.consultorio.auditoria.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Persistencia real con el rol de aplicacion y lectura de verificación con el rol de migracion (design D4/D6). */
class AuditLogPersistenceIT extends AbstractIntegrationTest {

    private final AuditLogger auditLogger;

    AuditLogPersistenceIT(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM audit_log");
        }
    }

    @Test
    void persisteElEventoSanitizadoConTodosLosCampos() throws SQLException {
        UUID userId = bootstrapUserId();
        UUID patientId = UUID.randomUUID();
        String entityId = "hc-" + UUID.randomUUID();
        Map<String, Object> details = Map.of(
                "outcome", "ok",
                "source", "it",
                "email", "dr@ejemplo.com",
                "clinical_text", "evolucion reservada");

        Instant before = Clock.systemUTC().instant();
        auditLogger.log(AuditEvent.of(AuditAction.HC_READ, userId, patientId,
                "historia_clinica", entityId, "200.1.2.3", "agente-it", details));
        Instant after = Clock.systemUTC().instant();

        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT practice_id, user_id, patient_id, action, entity_type, entity_id, "
                                + "host(ip)::text AS ip, user_agent, occurred_at, details::text AS details "
                                + "FROM audit_log WHERE entity_id = ?")) {
            select.setString(1, entityId);
            try (ResultSet rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat((UUID) rows.getObject("practice_id"))
                        .isEqualTo(UUID.fromString("01900000-0000-7000-8000-000000000001"));
                assertThat((UUID) rows.getObject("user_id")).isEqualTo(userId);
                assertThat((UUID) rows.getObject("patient_id")).isEqualTo(patientId);
                assertThat(rows.getString("action")).isEqualTo("HC_READ");
                assertThat(rows.getString("entity_type")).isEqualTo("historia_clinica");
                assertThat(rows.getString("entity_id")).isEqualTo(entityId);
                assertThat(rows.getString("ip")).isEqualTo("200.1.2.3");
                assertThat(rows.getString("user_agent")).isEqualTo("agente-it");

                OffsetDateTime occurredAt = rows.getObject("occurred_at", OffsetDateTime.class);
                assertThat(occurredAt.getOffset()).isEqualTo(ZoneOffset.UTC);
                // timestamptz guarda microsegundos: se tolera el redondeo frente al Instant de nanosegundos.
                assertThat(occurredAt.toInstant()).isBetween(before.minusSeconds(1), after.plusSeconds(1));

                String storedDetails = rows.getString("details");
                assertThat(storedDetails).contains("outcome", "ok", "source", "it");
                assertThat(storedDetails).doesNotContain("email", "dr@ejemplo.com", "evolucion reservada",
                        "clinical_text");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void persisteElEventoAnonimoSinUsuarioNiIp() throws SQLException {
        String entityId = "token-" + UUID.randomUUID();

        auditLogger.log(AuditEvent.of(AuditAction.BOOKING_TOKEN_USE, null, null,
                "booking", entityId, null, null, null));

        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT user_id, patient_id, ip, details::text AS details FROM audit_log WHERE entity_id = ?")) {
            select.setString(1, entityId);
            try (ResultSet rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("user_id")).isNull();
                assertThat(rows.getObject("patient_id")).isNull();
                assertThat(rows.getObject("ip")).isNull();
                assertThat(rows.getString("details")).isEqualTo("{}");
            }
        }
    }

    @Test
    void unFalloDeInsercionPropagaElCodigoGenericoYNoDejaFila() throws SQLException {
        String entityId = "fallo-" + UUID.randomUUID();

        assertThatThrownBy(() -> auditLogger.log(AuditEvent.of(AuditAction.HC_READ, UUID.randomUUID(), null,
                "historia_clinica", entityId, null, null, Map.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("AUDIT_WRITE_FAILED");

        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT count(*) FROM audit_log WHERE entity_id = ?")) {
            select.setString(1, entityId);
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }

    private static UUID bootstrapUserId() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement select = connection.prepareStatement("SELECT id FROM app_user WHERE email = ?")) {
            select.setString(1, BOOTSTRAP_EMAIL);
            try (ResultSet rows = select.executeQuery()) {
                assertThat(rows.next()).as("usuario bootstrap creado por C-02").isTrue();
                return (UUID) rows.getObject("id");
            }
        }
    }
}
