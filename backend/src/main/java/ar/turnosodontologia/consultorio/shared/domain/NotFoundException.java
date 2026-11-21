package ar.turnosodontologia.consultorio.shared.domain;

/** El recurso pedido no existe (HTTP 404). */
public class NotFoundException extends DomainException {

    public NotFoundException(String code) {
        super(code);
    }
}
