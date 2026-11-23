package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import ar.turnosodontologia.consultorio.notificaciones.domain.EmailSendException;
import ar.turnosodontologia.consultorio.notificaciones.domain.EmailSender;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationErrorCode;
import ar.turnosodontologia.consultorio.notificaciones.domain.OutgoingEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Adaptador SMTP ({@code MAIL_PROVIDER=smtp}, design D7, D9): arma un mensaje de texto plano UTF-8 y traduce los
 * errores a codigos del catalogo SIN copiar el mensaje original (los servidores SMTP suelen incluir la direccion del
 * destinatario) ni conservar la causa. Autenticacion fallida y falta de conexion son transitorias; un destinatario
 * rechazado de forma permanente (5xx) no se reintenta.
 */
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(OutgoingEmail email) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            new InternetAddress(email.to()).validate();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.body(), false);
        } catch (MessagingException | IllegalArgumentException error) {
            // Direccion mal formada: reintentar no lo arregla.
            throw new EmailSendException(NotificationErrorCode.SMTP_REJECTED, false);
        }
        try {
            mailSender.send(message);
        } catch (MailAuthenticationException error) {
            throw new EmailSendException(NotificationErrorCode.SMTP_AUTH, true);
        } catch (MailSendException error) {
            throw translate(error);
        } catch (MailException error) {
            throw new EmailSendException(NotificationErrorCode.SMTP_UNAVAILABLE, true);
        }
    }

    private static EmailSendException translate(MailSendException error) {
        if (rejectsRecipient(error)) {
            return new EmailSendException(NotificationErrorCode.SMTP_REJECTED, false);
        }
        return new EmailSendException(NotificationErrorCode.SMTP_UNAVAILABLE, true);
    }

    /** {@code true} si alguna causa informa direcciones invalidas (el servidor respondio 5xx al destinatario). */
    private static boolean rejectsRecipient(MailSendException error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (isRecipientRejection(current)) {
                return true;
            }
        }
        for (Exception messageError : error.getMessageExceptions()) {
            if (isRecipientRejection(messageError)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRecipientRejection(Throwable error) {
        return error instanceof SendFailedException failed && failed.getInvalidAddresses() != null
                && failed.getInvalidAddresses().length > 0;
    }
}
