package ar.turnosodontologia.consultorio.historiaclinica.domain;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.util.regex.Pattern;

/**
 * Pieza dental en notacion FDI de dos digitos: permanente (cuadrantes 1-4, piezas 1-8) o temporal (cuadrantes 5-8,
 * piezas 1-5). Misma regla que el CHECK de {@code odontogram_entry.tooth_code}.
 */
public record ToothCode(String value) {

    /** Expresion del catalogo FDI, reutilizable en Bean Validation de la API. */
    public static final String PATTERN = "^([1-4][1-8]|[5-8][1-5])$";

    private static final Pattern FDI = Pattern.compile(PATTERN);

    public enum Dentition {
        PERMANENT,
        TEMPORARY
    }

    public ToothCode {
        if (value == null || !FDI.matcher(value).matches()) {
            throw new ValidationException("toothCode", "Pattern");
        }
    }

    public static ToothCode of(String raw) {
        return new ToothCode(raw);
    }

    public Dentition dentition() {
        return value.charAt(0) <= '4' ? Dentition.PERMANENT : Dentition.TEMPORARY;
    }
}
