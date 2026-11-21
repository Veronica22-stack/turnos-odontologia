package ar.turnosodontologia.consultorio.auditoria.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Evento que un caso de uso reporta al puerto de auditoria. Es inmutable y NO expone {@code practiceId} ni
 * {@code occurredAt}: los fija la aplicacion con {@code InstallationPractice} y {@code Clock} (design D2).
 *
 * <p>{@code details} se trata como una entrada NO confiable: se sanitiza antes de persistir o loguear.
 */
public record AuditEvent(
        AuditAction action,
        UUID userId,
        UUID patientId,
        String entityType,
        String entityId,
        String ip,
        String userAgent,
        Map<String, Object> details) {

    private static final int MAX_USER_AGENT = 512;
    private static final int MAX_ENTITY_TYPE = 100;
    private static final int MAX_ENTITY_ID = 200;
    private static final Pattern IP_V4 = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IP_V6 = Pattern.compile("^[0-9a-fA-F:.%]+$");

    /** Fabrica unica: valida longitudes, normaliza un IP invalido a nulo y copia los detalles. */
    public static AuditEvent of(AuditAction action, UUID userId, UUID patientId, String entityType, String entityId,
            String ip, String userAgent, Map<String, Object> details) {
        Objects.requireNonNull(action, "action");
        requireLength(entityType, MAX_ENTITY_TYPE, "entityType");
        requireLength(entityId, MAX_ENTITY_ID, "entityId");
        requireLength(userAgent, MAX_USER_AGENT, "userAgent");

        Map<String, Object> copy = new HashMap<>();
        if (details != null) {
            details.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
        }
        return new AuditEvent(action, userId, patientId, entityType, entityId,
                validIp(ip) ? ip : null, userAgent, Map.copyOf(copy));
    }

    private static void requireLength(String value, int maximum, String name) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(name + " excede " + maximum + " caracteres");
        }
    }

    /** Solo metadatos de contexto y referencias: nunca los detalles ni el agente (regla dura 6). */
    @Override
    public String toString() {
        return "AuditEvent[action=" + action + ", userId=" + userId + ", patientId=" + patientId
                + ", entityType=" + entityType + ", entityId=" + entityId + "]";
    }

    private static boolean validIp(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (IP_V4.matcher(value).matches()) {
            for (String octet : value.split("\\.", -1)) {
                if (Integer.parseInt(octet) > 255) {
                    return false;
                }
            }
            return true;
        }
        return value.indexOf(':') >= 0 && IP_V6.matcher(value).matches();
    }
}
