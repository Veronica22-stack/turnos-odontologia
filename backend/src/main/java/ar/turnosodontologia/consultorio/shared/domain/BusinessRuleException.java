package ar.turnosodontologia.consultorio.shared.domain;

/** Se incumple una regla de negocio (HTTP 422). */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code) {
        super(code);
    }
}
