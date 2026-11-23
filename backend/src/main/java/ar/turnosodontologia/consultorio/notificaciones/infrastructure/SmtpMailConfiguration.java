package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import ar.turnosodontologia.consultorio.notificaciones.domain.EmailSender;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Cliente SMTP solo con {@code MAIL_PROVIDER=smtp} (design D9). Se arma a mano con las variables {@code MAIL_*} en
 * lugar de {@code spring.mail.*} para que, con {@code log}, no exista ningun {@code JavaMailSender} (ni su health
 * check). STARTTLS obligatorio y timeouts de 10 s de conexion, lectura y escritura: el lock de fila del despacho se
 * sostiene durante el envio, asi que el SMTP no puede colgarse.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "MAIL_PROVIDER", havingValue = "smtp")
class SmtpMailConfiguration {

    private static final String TIMEOUT_MS = "10000";

    @Bean
    JavaMailSender notificationMailSender(Environment environment) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(environment.getRequiredProperty("MAIL_HOST"));
        sender.setPort(Integer.parseInt(environment.getRequiredProperty("MAIL_PORT")));
        sender.setUsername(environment.getRequiredProperty("MAIL_USER"));
        sender.setPassword(environment.getRequiredProperty("MAIL_PASSWORD"));
        sender.setDefaultEncoding("UTF-8");
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        properties.put("mail.smtp.timeout", TIMEOUT_MS);
        properties.put("mail.smtp.writetimeout", TIMEOUT_MS);
        return sender;
    }

    @Bean
    EmailSender smtpEmailSender(JavaMailSender mailSender, @Value("${MAIL_FROM}") String from) {
        return new SmtpEmailSender(mailSender, from);
    }
}
