package ar.turnosodontologia.consultorio.notificaciones.domain;

/**
 * Catalogo cerrado de codigos de error y de descarte (design D16). Es lo unico que se guarda en {@code last_error}:
 * nunca el texto del proveedor, que suele incluir direcciones de email.
 */
public enum NotificationErrorCode {
    SMTP_UNAVAILABLE,
    SMTP_REJECTED,
    SMTP_AUTH,
    UNEXPECTED,
    NO_RECIPIENT,
    APPOINTMENT_NOT_FOUND,
    APPOINTMENT_NOT_CONFIRMED,
    APPOINTMENT_CANCELLED,
    EXPIRED,
    REMINDER_WINDOW_PASSED
}
