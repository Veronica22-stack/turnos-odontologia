package ar.turnosodontologia.consultorio.auditoria.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Datos que el reportante puede aportar. No incluye practice_id ni occurred_at: los fija la aplicacion. */
public record AuditLogCommand(
        String action,
        UUID userId,
        UUID patientId,
        String entityType,
        String entityId,
        String ip,
        String userAgent,
        Map<String, Object> details) {

    public AuditLogCommand {
        Map<String, Object> copy = new HashMap<>();
        if (details != null) {
            details.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
        }
        details = Map.copyOf(copy);
    }
}
