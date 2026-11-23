package ar.turnosodontologia.consultorio.shared.infrastructure;

import ar.turnosodontologia.consultorio.shared.api.ConfigurationNames;
import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Hace fallar el arranque, antes de crear cualquier bean, si falta una variable obligatoria o si una de sesion/CORS
 * tiene un valor invalido. Los mensajes nombran SOLO la variable; nunca muestran valores (regla dura 6).
 *
 * <p>Spring Boot deja un {@code ${VAR}} sin resolver como texto literal al vincular propiedades, de modo que
 * sin esta verificacion la aplicacion arrancaria con la contrasena literal "${DB_PASSWORD}". Aqui se resuelven
 * las propiedades obligatorias con el {@link Environment}, que lanza un error con el nombre de la variable
 * faltante y sin mostrar los valores de las demas.
 */
@Configuration(proxyBeanMethods = false)
class RequiredConfiguration {

    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "spring.flyway.user",
            "spring.flyway.password");

    @Bean
    static BeanFactoryPostProcessor requiredConfigurationValidator(Environment environment) {
        return new FirstValidator(environment);
    }

    /**
     * Corre antes que los demas validadores de configuracion (p. ej. el de email de notificaciones): asi la base y la
     * sesion se validan primero y el primer error informado es siempre el mismo.
     */
    private static final class FirstValidator implements BeanFactoryPostProcessor, PriorityOrdered {

        private final Environment environment;

        private FirstValidator(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            REQUIRED_PROPERTIES.forEach(property -> resolve(environment, property));
            validateTimeout(environment);
            validateCors(environment);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }

    private static void resolve(Environment environment, String property) {
        try {
            environment.getProperty(property);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(
                    "Configuracion obligatoria faltante para '" + property + "': " + error.getMessage(), error);
        }
    }

    /** El plazo de inactividad es un entero positivo; el valor ausente usa el default de 30 minutos. */
    private static void validateTimeout(Environment environment) {
        String value = environment.getProperty(ConfigurationNames.AUTH_SESSION_TIMEOUT_MIN);
        if (value == null) {
            return;
        }
        try {
            if (Integer.parseInt(value) <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException error) {
            throw new IllegalStateException("Configuracion invalida: "
                    + ConfigurationNames.AUTH_SESSION_TIMEOUT_MIN + " debe ser un entero mayor que 0");
        }
    }

    /** Fuera de desarrollo local los origenes CORS son obligatorios y nunca un comodin con credenciales. */
    private static void validateCors(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of(ConfigurationNames.DEV_PROFILE))) {
            return;
        }
        String origins = environment.getProperty(ConfigurationNames.CORS_ALLOWED_ORIGINS);
        if (origins == null || origins.isBlank() || origins.contains("*")) {
            throw new IllegalStateException("Configuracion obligatoria faltante o invalida para '"
                    + ConfigurationNames.CORS_ALLOWED_ORIGINS + "'");
        }
    }
}
