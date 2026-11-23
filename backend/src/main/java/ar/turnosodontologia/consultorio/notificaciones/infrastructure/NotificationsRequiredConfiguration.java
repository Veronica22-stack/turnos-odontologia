package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import ar.turnosodontologia.consultorio.shared.api.ConfigurationNames;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Valida al arrancar, antes de crear cualquier bean, la configuracion de email y del despacho (design D15). Sigue el
 * patron de {@code RequiredConfiguration}: los mensajes nombran SOLO la variable, nunca valores (regla dura 6; p. ej.
 * un {@code MAIL_USER} valido no debe aparecer cuando falta {@code MAIL_PASSWORD}).
 */
@Configuration(proxyBeanMethods = false)
class NotificationsRequiredConfiguration {

    static final String MAIL_PROVIDER = "MAIL_PROVIDER";
    static final String POLL_INTERVAL = "NOTIFICATION_POLL_INTERVAL";
    static final String MAX_ATTEMPTS = "NOTIFICATION_MAX_ATTEMPTS";
    static final String DISPATCH_ENABLED = "NOTIFICATION_DISPATCH_ENABLED";

    private static final Logger LOG = LoggerFactory.getLogger(NotificationsRequiredConfiguration.class);
    private static final List<String> SMTP_REQUIRED = List.of("MAIL_HOST", "MAIL_PORT", "MAIL_FROM", "MAIL_USER",
            "MAIL_PASSWORD");

    @Bean
    static BeanFactoryPostProcessor notificationsConfigurationValidator(Environment environment) {
        return beanFactory -> validate(environment);
    }

    static void validate(Environment environment) {
        validateMail(environment);
        validatePositiveDuration(environment, POLL_INTERVAL);
        validateMinimumInteger(environment, MAX_ATTEMPTS, 1);
        validateBoolean(environment, DISPATCH_ENABLED);
    }

    private static void validateMail(Environment environment) {
        String provider = environment.getProperty(MAIL_PROVIDER);
        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException("Configuracion obligatoria faltante para '" + MAIL_PROVIDER
                    + "' (valores admitidos: log, smtp)");
        }
        switch (provider.trim()) {
            case "log" -> warnIfNotDev(environment);
            case "smtp" -> validateSmtp(environment);
            default -> throw new IllegalStateException(
                    "Configuracion invalida: " + MAIL_PROVIDER + " debe ser 'log' o 'smtp'");
        }
    }

    private static void warnIfNotDev(Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of(ConfigurationNames.DEV_PROFILE))) {
            LOG.warn("MAIL_PROVIDER=log fuera del perfil dev: los emails NO se envian, solo se registran metadatos");
        }
    }

    private static void validateSmtp(Environment environment) {
        for (String variable : SMTP_REQUIRED) {
            String value = environment.getProperty(variable);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Configuracion obligatoria faltante para '" + variable + "' con MAIL_PROVIDER=smtp");
            }
        }
        if (!isInteger(environment.getProperty("MAIL_PORT"), 1, 65_535)) {
            throw new IllegalStateException("Configuracion invalida: MAIL_PORT debe ser un puerto entre 1 y 65535");
        }
    }

    private static void validatePositiveDuration(Environment environment, String variable) {
        String value = environment.getProperty(variable);
        if (value == null) {
            return;
        }
        try {
            Duration duration = Duration.parse(value.trim());
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalStateException(invalid(variable, "una duracion ISO-8601 mayor que 0 (p. ej. PT5M)"));
            }
        } catch (DateTimeException error) {
            throw new IllegalStateException(invalid(variable, "una duracion ISO-8601 mayor que 0 (p. ej. PT5M)"));
        }
    }

    private static void validateMinimumInteger(Environment environment, String variable, int minimum) {
        String value = environment.getProperty(variable);
        if (value != null && !isInteger(value, minimum, Integer.MAX_VALUE)) {
            throw new IllegalStateException(invalid(variable, "un entero mayor o igual que " + minimum));
        }
    }

    private static void validateBoolean(Environment environment, String variable) {
        String value = environment.getProperty(variable);
        if (value != null && !"true".equals(value.trim()) && !"false".equals(value.trim())) {
            throw new IllegalStateException(invalid(variable, "true o false"));
        }
    }

    private static boolean isInteger(String value, int minimum, int maximum) {
        if (value == null) {
            return false;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= minimum && parsed <= maximum;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    private static String invalid(String variable, String expected) {
        return "Configuracion invalida: " + variable + " debe ser " + expected;
    }
}
