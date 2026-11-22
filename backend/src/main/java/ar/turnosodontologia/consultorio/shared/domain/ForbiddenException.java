package ar.turnosodontologia.consultorio.shared.domain;

/** Error 403 con un código estable; el cuerpo no revela el motivo interno ni datos de la sesión. */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String code) {
        super(code);
    }
}
