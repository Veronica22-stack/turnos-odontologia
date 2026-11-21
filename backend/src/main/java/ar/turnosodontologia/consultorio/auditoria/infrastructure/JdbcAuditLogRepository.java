package ar.turnosodontologia.consultorio.auditoria.infrastructure;

import ar.turnosodontologia.consultorio.auditoria.domain.AuditLogEntry;
import ar.turnosodontologia.consultorio.auditoria.domain.AuditLogRepository;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Apendice JDBC del rastro (design D4). Sin entidad JPA: la tabla es de solo insercion y los tipos nativos
 * {@code inet}/{@code jsonb} se ligan explicitamente con parametros ligados, nunca por concatenacion.
 */
@Component
final class JdbcAuditLogRepository implements AuditLogRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcAuditLogRepository.class);

    private static final String INSERT = """
            INSERT INTO audit_log (practice_id, user_id, action, entity_type, entity_id, patient_id,
                                   ip, user_agent, occurred_at, details)
            VALUES (?, ?, ?, ?, ?, ?, ?::inet, ?, ?, ?::jsonb)
            RETURNING id
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;

    JdbcAuditLogRepository(JdbcTemplate jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void save(AuditLogEntry entry) {
        Long id = jdbc.query(INSERT, statement -> bind(statement, entry), rows -> {
            rows.next();
            return rows.getLong(1);
        });
        // Solo identificador y accion: nunca detalles, IP, agente ni mensajes de la base (regla dura 6).
        LOG.debug("audit_log id={} action={}", id, entry.action());
    }

    private void bind(PreparedStatement statement, AuditLogEntry entry) throws SQLException {
        statement.setObject(1, entry.practiceId());
        bindUuid(statement, 2, entry.userId());
        statement.setString(3, entry.action());
        statement.setString(4, entry.entityType());
        statement.setString(5, entry.entityId());
        bindUuid(statement, 6, entry.patientId());
        bindInet(statement, 7, entry.ip());
        statement.setString(8, entry.userAgent());
        statement.setObject(9, OffsetDateTime.ofInstant(entry.occurredAt(), ZoneOffset.UTC));
        // El driver es runtime-only (D7): Types.OTHER + el cast ::jsonb liga el texto como jsonb nativo sin
        // agregar dependencias de compilacion. Es equivalente a un PGobject en seguridad de tipos.
        statement.setObject(10, json(entry.details()), Types.OTHER);
    }

    private static void bindUuid(PreparedStatement statement, int index, java.util.UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, value);
        }
    }

    private static void bindInet(PreparedStatement statement, int index, String ip) throws SQLException {
        if (ip == null) {
            statement.setNull(index, Types.OTHER);
            return;
        }
        statement.setObject(index, ip, Types.OTHER);
    }

    private String json(Map<String, Object> details) {
        try {
            return mapper.writeValueAsString(details);
        } catch (JacksonException error) {
            throw new IllegalStateException("No se pudo serializar los metadatos de auditoria", error);
        }
    }
}
