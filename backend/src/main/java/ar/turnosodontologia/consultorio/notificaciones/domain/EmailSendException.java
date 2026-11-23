package ar.turnosodontologia.consultorio.notificaciones.domain;

/**
 * Fallo de envio ya traducido a un codigo del catalogo (design D7, D16). Su mensaje es solo el codigo y no conserva
 * la causa original: los mensajes de los servidores SMTP suelen incluir la direccion del destinatario.
 */
public class EmailSendException extends RuntimeException {

    private final NotificationErrorCode code;
    private final boolean retryable;

    public EmailSendException(NotificationErrorCode code, boolean retryable) {
        super(code.name(), null, false, false);
        this.code = code;
        this.retryable = retryable;
    }

    public NotificationErrorCode code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
