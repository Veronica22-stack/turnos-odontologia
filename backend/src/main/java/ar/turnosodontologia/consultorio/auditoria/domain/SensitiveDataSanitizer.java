package ar.turnosodontologia.consultorio.auditoria.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sanitizacion de los metadatos operacionales de un evento (design D3, RN-AD-03/RN-HC-08). Funciones puras, sin
 * Spring: solo claves de una lista de permitidos, redaccion de valores con forma de dato personal o credencial y
 * limites de profundidad, tamano y cantidad. Nunca se sanitiza despues de construir el INSERT ni el log.
 */
public final class SensitiveDataSanitizer {

    public static final String REDACTED = "[REDACTED]";

    private static final int MAX_DEPTH = 2;
    private static final int MAX_ENTRIES = 20;
    private static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_VALUE_LENGTH = 512;

    /** Unica tabla inmutable de reglas: normalizacion de claves y patrones de redaccion. */
    private static final class Rules {
        private static final Pattern KEY_SEPARATOR = Pattern.compile("[\\s-]+");
        private static final Set<String> ALLOWED_KEYS = Set.of(
                "outcome", "reason_code", "attempt", "entity_version", "field_names", "duration_ms", "source");
        private static final Set<String> BLACKLISTED_KEYS = Set.of(
                "dni", "document", "documento", "email", "mail", "phone", "telefono", "name", "nombre",
                "apellido", "fullname", "full_name", "password", "pass", "secret", "token", "authorization",
                "header", "body", "content", "description", "detalle", "clinical", "clinical_text",
                "diagnostico", "alergia", "medicacion", "evolucion", "note");
        private static final List<Pattern> REDACTION_PATTERNS = List.of(
                Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}"),
                Pattern.compile("(?<!\\d)\\d{7,}(?!\\d)"),
                Pattern.compile("[A-Za-z0-9_-]{32,}"),
                Pattern.compile("\\$argon2[\\w$./]{8,}"),
                Pattern.compile("(?i)bearer\\s+\\S+"));

        private Rules() {
        }
    }

    private SensitiveDataSanitizer() {
    }

    /** Devuelve un mapa inmutable con solo metadatos autorizados y valores seguros. */
    public static Map<String, Object> sanitize(Map<String, Object> details) {
        return details == null ? Map.of() : sanitizeMap(details, 0);
    }

    private static Map<String, Object> sanitizeMap(Map<?, ?> source, int depth) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (depth > MAX_DEPTH) {
            return result;
        }
        int entries = 0;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entries >= MAX_ENTRIES) {
                break;
            }
            if (!(entry.getKey() instanceof String rawKey) || rawKey.length() > MAX_KEY_LENGTH) {
                continue;
            }
            String key = normalize(rawKey);
            if (!Rules.ALLOWED_KEYS.contains(key) || Rules.BLACKLISTED_KEYS.contains(key)) {
                continue;
            }
            Object value = sanitizeValue(entry.getValue(), depth);
            if (value != null) {
                result.put(key, value);
                entries++;
            }
        }
        return Map.copyOf(result);
    }

    private static Object sanitizeValue(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> nested) {
            return sanitizeMap(nested, depth + 1);
        }
        if (value instanceof List<?> nested) {
            List<Object> safe = new ArrayList<>();
            for (Object element : nested) {
                if (safe.size() >= MAX_ENTRIES) {
                    break;
                }
                Object sanitized = sanitizeValue(element, depth + 1);
                if (sanitized != null) {
                    safe.add(sanitized);
                }
            }
            return List.copyOf(safe);
        }
        if (value instanceof String text) {
            return sanitizeScalar(text);
        }
        if (value instanceof Number number) {
            return sanitizeScalar(number.toString());
        }
        if (value instanceof Boolean) {
            return value;
        }
        // Estructuras no escalares (POJOs, fechas, etc.) se descartan: no se puede garantizar su contenido.
        return null;
    }

    private static Object sanitizeScalar(String text) {
        if (text.length() > MAX_VALUE_LENGTH
                || Rules.REDACTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find())) {
            return REDACTED;
        }
        return text;
    }

    /** Comparacion insensible a mayusculas; guiones y espacios se tratan como guion bajo. */
    private static String normalize(String key) {
        return Rules.KEY_SEPARATOR.matcher(key.trim().toLowerCase(Locale.ROOT)).replaceAll("_");
    }
}
