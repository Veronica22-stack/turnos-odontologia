package ar.turnosodontologia.consultorio.auditoria.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Fila de auditoria ya completa y sanitizada, lista para insertar (no es una entidad JPA). */
public record AuditLogEntry(
        UUID practiceId,
        String action,
        UUID userId,
        UUID patientId,
        String entityType,
        String entityId,
        String ip,
        String userAgent,
        Instant occurredAt,
        Map<String, Object> details) {
}
