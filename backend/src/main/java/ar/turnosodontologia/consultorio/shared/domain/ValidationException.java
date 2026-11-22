package ar.turnosodontologia.consultorio.shared.domain;

/** Error de validación de un campo, sin incluir nunca el valor rechazado. */
public final class ValidationException extends DomainException {

    private final String field;
    private final String constraint;

    public ValidationException(String field, String constraint) {
        super("VALIDATION_FAILED");
        this.field = field;
        this.constraint = constraint;
    }

    public String field() {
        return field;
    }

    public String constraint() {
        return constraint;
    }
}
