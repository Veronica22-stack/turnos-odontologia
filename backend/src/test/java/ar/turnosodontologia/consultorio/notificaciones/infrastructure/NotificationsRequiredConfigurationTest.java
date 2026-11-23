package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationRetryPolicy;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.annotation.Scheduled;

/** Validacion de la configuracion de email y del despacho al arrancar (runtime-configuration, design D15). */
class NotificationsRequiredConfigurationTest {

    private static final String OTHER_SECRET = "usuario-smtp-secreto";

    private final Logger logger = (Logger) LoggerFactory.getLogger(NotificationsRequiredConfiguration.class);
    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    private static MockEnvironment smtp() {
        return new MockEnvironment()
                .withProperty("MAIL_PROVIDER", "smtp")
                .withProperty("MAIL_HOST", "smtp.ejemplo.com")
                .withProperty("MAIL_PORT", "587")
                .withProperty("MAIL_FROM", "turnos@ejemplo.com")
                .withProperty("MAIL_USER", OTHER_SECRET)
                .withProperty("MAIL_PASSWORD", "clave-secreta");
    }

    private List<String> warnings() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    // ---- MAIL_PROVIDER ----

    @Test
    void proveedorAusenteFalla() {
        assertThatThrownBy(() -> NotificationsRequiredConfiguration.validate(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_PROVIDER");
    }

    @Test
    void proveedorEnBlancoFalla() {
        MockEnvironment environment = new MockEnvironment().withProperty("MAIL_PROVIDER", "  ");

        assertThatThrownBy(() -> NotificationsRequiredConfiguration.validate(environment))
                .hasMessageContaining("MAIL_PROVIDER");
    }

    @Test
    void valorInvalidoFallaMencionandoLaVariableSinMostrarElValor() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MAIL_PROVIDER", "sendgrid")
                .withProperty("MAIL_USER", OTHER_SECRET);

        assertThatThrownBy(() -> NotificationsRequiredConfiguration.validate(environment))
                .hasMessageContaining("MAIL_PROVIDER")
                .message().doesNotContain("sendgrid").doesNotContain(OTHER_SECRET);
    }

    @Test
    void elPerfilDevAportaElProveedorLogPorDefecto() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-dev", new ClassPathResource("application-dev.yml"));
        sources.forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("MAIL_PROVIDER")).isEqualTo("log");
        assertThatCode(() -> NotificationsRequiredConfiguration.validate(environment)).doesNotThrowAnyException();
        assertThat(warnings()).isEmpty();
    }

    @Test
    void logFueraDeDevArrancaPeroAdvierteQueNoSeEnvianEmails() {
        MockEnvironment environment = new MockEnvironment().withProperty("MAIL_PROVIDER", "log");

        assertThatCode(() -> NotificationsRequiredConfiguration.validate(environment)).doesNotThrowAnyException();

        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0)).contains("MAIL_PROVIDER=log").contains("NO se envian");
    }

    // ---- smtp ----

    @Test
    void smtpCompletoEsValidoSinAdvertencias() {
        assertThatCode(() -> NotificationsRequiredConfiguration.validate(smtp())).doesNotThrowAnyException();
        assertThat(warnings()).isEmpty();
    }

    @Test
    void smtpSinContrasenaFallaMencionandoMailPasswordYSinMostrarElUsuario() {
        MockEnvironment environment = smtp();
        environment.getPropertySources().remove("mockProperties");
        MockEnvironment withoutPassword = new MockEnvironment()
                .withProperty("MAIL_PROVIDER", "smtp")
                .withProperty("MAIL_HOST", "smtp.ejemplo.com")
                .withProperty("MAIL_PORT", "587")
                .withProperty("MAIL_FROM", "turnos@ejemplo.com")
                .withProperty("MAIL_USER", OTHER_SECRET);

        assertThatThrownBy(() -> NotificationsRequiredConfiguration.validate(withoutPassword))
                .hasMessageContaining("MAIL_PASSWORD")
                .message().doesNotContain(OTHER_SECRET);
    }

    @Test
    void smtpExigeCadaVariableObligatoria() {
        for (String missing : List.of("MAIL_HOST", "MAIL_PORT", "MAIL_FROM", "MAIL_USER", "MAIL_PASSWORD")) {
            MockEnvironment environment = smtp();
            environment.withProperty(missing, "");

            assertThatThrownBy(() -> NotificationsRequiredConfiguration.validate(environment))
                    .as(missing).hasMessageContaining(missing);
        }
    }

    @Test
    void smtpConPuertoInvalidoFallaMencionandoMailPort() {
        for (String port : List.of("abc", "0", "70000")) {
            MockEnvironment environment = smtp().withProperty("MAIL_PORT", port);

            assertThatThrownBy(() -> NotificationsRequiredConfiguration.validate(environment))
                    .as(port).hasMessageContaining("MAIL_PORT");
        }
    }

    // ---- despacho ----

    @Test
    void maximoDeIntentosMenorQueUnoOInvalidoFalla() {
        for (String value : List.of("0", "-3", "cinco")) {
            MockEnvironment environment = new MockEnvironment().withProperty("MAIL_PROVIDER", "log")
                    .withProperty("NOTIFICATION_MAX_ATTEMPTS", value);

            assertThatThrownBy(() -> NotificationsRequiredConfiguration.validate(environment))
                    .as(value).hasMessageContaining("NOTIFICATION_MAX_ATTEMPTS");
        }
    }

    @Test
    void maximoDeIntentosValidoSeAcepta() {
        MockEnvironment environment = new MockEnvironment().withProperty("MAIL_PROVIDER", "log")
                .withProperty("NOTIFICATION_MAX_ATTEMPTS", "1");

        assertThatCode(() -> NotificationsRequiredConfiguration.validate(environment)).doesNotThrowAnyException();
    }

    @Test
    void intervaloNoPositivoOInvalidoFalla() {
        for (String value : List.of("PT0S", "-PT5M", "5 minutos", "300")) {
            MockEnvironment environment = new MockEnvironment().withProperty("MAIL_PROVIDER", "log")
                    .withProperty("NOTIFICATION_POLL_INTERVAL", value);

            assertThatThrownBy(() -> NotificationsRequiredConfiguration.validate(environment))
                    .as(value).hasMessageContaining("NOTIFICATION_POLL_INTERVAL");
        }
    }

    @Test
    void intervaloIsoValidoSeAcepta() {
        MockEnvironment environment = new MockEnvironment().withProperty("MAIL_PROVIDER", "log")
                .withProperty("NOTIFICATION_POLL_INTERVAL", "PT15M");

        assertThatCode(() -> NotificationsRequiredConfiguration.validate(environment)).doesNotThrowAnyException();
    }

    @Test
    void laActivacionDelDespachoDebeSerTrueOFalse() {
        MockEnvironment invalid = new MockEnvironment().withProperty("MAIL_PROVIDER", "log")
                .withProperty("NOTIFICATION_DISPATCH_ENABLED", "quizas");
        MockEnvironment off = new MockEnvironment().withProperty("MAIL_PROVIDER", "log")
                .withProperty("NOTIFICATION_DISPATCH_ENABLED", "false");

        assertThatThrownBy(() -> NotificationsRequiredConfiguration.validate(invalid))
                .hasMessageContaining("NOTIFICATION_DISPATCH_ENABLED");
        assertThatCode(() -> NotificationsRequiredConfiguration.validate(off)).doesNotThrowAnyException();
    }

    // ---- valores por defecto ----

    @Test
    void sinVariablesElDespachoCorreCadaCincoMinutosConUnMaximoDeCincoIntentos() throws Exception {
        Method job = NotificationsConfiguration.NotificationDispatchJob.class.getDeclaredMethod("run");
        String intervalExpression = job.getAnnotation(Scheduled.class).fixedDelayString();
        MockEnvironment defaults = new MockEnvironment();
        MockEnvironment overridden = new MockEnvironment().withProperty("NOTIFICATION_POLL_INTERVAL", "PT1M");

        assertThat(Duration.parse(defaults.resolvePlaceholders(intervalExpression))).isEqualTo(Duration.ofMinutes(5));
        assertThat(Duration.parse(overridden.resolvePlaceholders(intervalExpression))).isEqualTo(Duration.ofMinutes(1));

        Method policy = NotificationsConfiguration.class.getDeclaredMethod("notificationRetryPolicy", int.class);
        String attemptsExpression = policy.getParameters()[0].getAnnotation(Value.class).value();
        int maxAttempts = Integer.parseInt(defaults.resolvePlaceholders(attemptsExpression));
        assertThat(maxAttempts).isEqualTo(5);
        assertThat(NotificationRetryPolicy.standard(maxAttempts).decide(5, true, java.time.Instant.EPOCH).retry())
                .isFalse();
        assertThat(NotificationRetryPolicy.standard(maxAttempts).decide(4, true, java.time.Instant.EPOCH).retry())
                .isTrue();
    }
}
