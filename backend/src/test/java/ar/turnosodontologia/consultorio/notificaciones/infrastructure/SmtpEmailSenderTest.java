package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.notificaciones.domain.EmailSendException;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationErrorCode;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import ar.turnosodontologia.consultorio.notificaciones.domain.OutgoingEmail;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/** Adaptador SMTP con {@link JavaMailSender} mockeado: armado del mensaje y traduccion de errores (design D7, D9). */
class SmtpEmailSenderTest {

    private static final String TO = "ana.paciente@ejemplo.com";
    private static final String SUBJECT = "Turno confirmado — Consultorio Sonrisa";
    private static final String BODY = "Tu turno quedó confirmado.\nPrestación: Limpieza dental\n";

    private JavaMailSender mailSender;
    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        sender = new SmtpEmailSender(mailSender, "turnos@ejemplo.com");
    }

    private static OutgoingEmail email() {
        return new OutgoingEmail(UUID.randomUUID(), UUID.randomUUID(), NotificationType.CONFIRMATION,
                "confirmation", TO, SUBJECT, BODY);
    }

    @Test
    void armaElMensajeConRemitenteDestinatarioAsuntoYCuerpoDeTextoPlanoUtf8() throws Exception {
        sender.send(email());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        assertThat(message.getFrom()).extracting(Address::toString).containsExactly("turnos@ejemplo.com");
        assertThat(message.getRecipients(Message.RecipientType.TO)).extracting(Address::toString)
                .containsExactly(TO);
        assertThat(message.getSubject()).isEqualTo(SUBJECT);
        assertThat(message.getContent().toString().strip()).isEqualTo(BODY.strip());
        assertThat(message.getContentType()).startsWith("text/plain").containsIgnoringCase("utf-8");
    }

    @Test
    void unaConexionFallidaEsTransitoria() {
        doThrow(new MailSendException("no conecta a " + TO, new MessagingException("Could not connect to SMTP host")))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(email()))
                .isInstanceOfSatisfying(EmailSendException.class, error -> {
                    assertThat(error.code()).isEqualTo(NotificationErrorCode.SMTP_UNAVAILABLE);
                    assertThat(error.retryable()).isTrue();
                });
    }

    @Test
    void unRechazoPermanenteDelDestinatarioEsSmtpRejectedSinReintento() throws Exception {
        SendFailedException rejected = new SendFailedException("550 usuario inexistente " + TO, null,
                new Address[0], new Address[0], new Address[] {new InternetAddress(TO)});
        doThrow(new MailSendException("rechazado", rejected)).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(email()))
                .isInstanceOfSatisfying(EmailSendException.class, error -> {
                    assertThat(error.code()).isEqualTo(NotificationErrorCode.SMTP_REJECTED);
                    assertThat(error.retryable()).isFalse();
                });
    }

    @Test
    void unFalloDeAutenticacionEsSmtpAuth() {
        doThrow(new MailAuthenticationException("credenciales de usuario-secreto invalidas"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(email()))
                .isInstanceOfSatisfying(EmailSendException.class, error -> {
                    assertThat(error.code()).isEqualTo(NotificationErrorCode.SMTP_AUTH);
                    assertThat(error.retryable()).isTrue();
                });
    }

    @Test
    void unaDireccionMalFormadaEsUnRechazoPermanente() {
        OutgoingEmail invalid = new OutgoingEmail(UUID.randomUUID(), UUID.randomUUID(), NotificationType.REMINDER,
                "reminder", "esto no es un email", SUBJECT, BODY);

        assertThatThrownBy(() -> sender.send(invalid))
                .isInstanceOfSatisfying(EmailSendException.class, error -> {
                    assertThat(error.code()).isEqualTo(NotificationErrorCode.SMTP_REJECTED);
                    assertThat(error.retryable()).isFalse();
                });
    }

    @Test
    void laExcepcionTraducidaNoConservaElMensajeNiLaCausaOriginal() {
        doThrow(new MailSendException("fallo para " + TO, new MessagingException("550 " + TO)))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(email()))
                .isInstanceOfSatisfying(EmailSendException.class, error -> {
                    assertThat(error.getMessage()).doesNotContain(TO).isEqualTo("SMTP_UNAVAILABLE");
                    assertThat(error.getCause()).isNull();
                    assertThat(error.toString()).doesNotContain(TO);
                });
    }
}
