package ar.turnosodontologia.consultorio.shared.domain;

/**
 * Error de dominio con un codigo estable. Solo lleva el codigo (nunca texto libre ni argumentos) para que sea
 * imposible filtrar datos personales a la respuesta por descuido.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
