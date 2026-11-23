package ar.turnosodontologia.consultorio.notificaciones.domain;

import java.util.UUID;

/**
 * Email listo para enviar. {@code toString()} omite destinatario, asunto y cuerpo a proposito (regla dura 6): un
 * registro accidental del objeto nunca filtra datos personales.
 */
public record OutgoingEmail(UUID notificationId, UUID appointmentId, NotificationType type, String templateName,
        String to, String subject, String body) {

    @Override
    public String toString() {
        return "OutgoingEmail[notificationId=" + notificationId + ", appointmentId=" + appointmentId + ", type=" + type
                + ", template=" + templateName + "]";
    }
}
