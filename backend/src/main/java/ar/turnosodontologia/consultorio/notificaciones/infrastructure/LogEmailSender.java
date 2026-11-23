package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import ar.turnosodontologia.consultorio.notificaciones.domain.EmailSender;
import ar.turnosodontologia.consultorio.notificaciones.domain.OutgoingEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de desarrollo ({@code MAIL_PROVIDER=log}, DD-10): "envia" registrando solo identificadores, tipo y
 * plantilla. A proposito NO registra destinatario, asunto ni cuerpo (regla dura 6; ademas el cuerpo puede llevar el link
 * con el token en claro, regla 8): el texto exacto de cada email lo fijan los tests de {@code EmailTemplates}.
 */
@Component
@ConditionalOnProperty(name = "MAIL_PROVIDER", havingValue = "log")
class LogEmailSender implements EmailSender {

    private static final Logger LOG = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void send(OutgoingEmail email) {
        LOG.info("email simulado notificationId={} appointmentId={} type={} template={}", email.notificationId(),
                email.appointmentId(), email.type(), email.templateName());
    }
}
