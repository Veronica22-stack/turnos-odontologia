package ar.turnosodontologia.consultorio.shared.domain;

/** La operacion choca con el estado actual (HTTP 409). */
public class ConflictException extends DomainException {

    public ConflictException(String code) {
        super(code);
    }
}
