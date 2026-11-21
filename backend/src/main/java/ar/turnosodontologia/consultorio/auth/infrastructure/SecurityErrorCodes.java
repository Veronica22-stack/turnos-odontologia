package ar.turnosodontologia.consultorio.auth.infrastructure;

/** Códigos estables de error de seguridad, sin detalles de la cuenta ni del framework. */
final class SecurityErrorCodes {

    static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    static final String FORBIDDEN = "FORBIDDEN";
    static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";
    static final String CSRF_REQUIRED = "CSRF_REQUIRED";
    static final String ACCESS_DENIED_REASON = SecurityErrorCodes.class.getName() + ".code";

    private SecurityErrorCodes() {
    }
}
