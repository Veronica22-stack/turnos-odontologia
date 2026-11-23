package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import ar.turnosodontologia.consultorio.notificaciones.domain.OutgoingEmail;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** El adaptador de desarrollo solo registra metadatos: nunca destinatario, asunto ni cuerpo (design D9, DD-10). */
class LogEmailSenderTest {

    private static final String TO = "ana.paciente@ejemplo.com";
    private static final String SUBJECT = "Turno confirmado — Consultorio Sonrisa";
    private static final String BODY = "Tu turno quedó confirmado. Prestación: Limpieza dental";

    private final Logger logger = (Logger) LoggerFactory.getLogger(LogEmailSender.class);
    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    private OutgoingEmail email(NotificationType type, String template) {
        return new OutgoingEmail(UUID.randomUUID(), UUID.randomUUID(), type, template, TO, SUBJECT, BODY);
    }

    private List<String> logged() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void registraIdentificadoresTipoYPlantillaSinDestinatarioAsuntoNiCuerpo() {
        OutgoingEmail email = email(NotificationType.CONFIRMATION, "confirmation");

        new LogEmailSender().send(email);

        assertThat(logged()).hasSize(1);
        String line = logged().get(0);
        assertThat(line)
                .contains(email.notificationId().toString())
                .contains(email.appointmentId().toString())
                .contains("CONFIRMATION")
                .contains("confirmation")
                .doesNotContain(TO)
                .doesNotContain("Consultorio Sonrisa")
                .doesNotContain("Limpieza dental");
    }

    @Test
    void cadaTipoRegistraSuPropiaPlantilla() {
        OutgoingEmail email = email(NotificationType.DENTIST_CANCELLATION, "dentist-cancellation");

        new LogEmailSender().send(email);

        assertThat(logged().get(0)).contains("DENTIST_CANCELLATION").contains("dentist-cancellation")
                .doesNotContain(TO);
    }

    @Test
    void laRepresentacionTextualDelEmailSalienteNoIncluyeDatosPersonales() {
        OutgoingEmail email = email(NotificationType.REMINDER, "reminder");

        assertThat(email.toString())
                .contains(email.notificationId().toString())
                .contains("REMINDER")
                .doesNotContain(TO)
                .doesNotContain(SUBJECT)
                .doesNotContain("Limpieza dental");
    }
}
