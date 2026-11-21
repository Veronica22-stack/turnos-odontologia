package ar.turnosodontologia.consultorio.shared.domain;

/** Error 401 con un código estable; nunca lleva el motivo exacto ni datos de la cuenta. */
public class UnauthenticatedException extends DomainException {

    public UnauthenticatedException(String code) {
        super(code);
    }
}
