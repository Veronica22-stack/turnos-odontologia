package ar.turnosodontologia.consultorio.shared.infrastructure;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Hace fallar el arranque, antes de crear cualquier bean, si falta una variable obligatoria.
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
        return beanFactory -> REQUIRED_PROPERTIES.forEach(property -> resolve(environment, property));
    }

    private static void resolve(Environment environment, String property) {
        try {
            environment.getProperty(property);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(
                    "Configuracion obligatoria faltante para '" + property + "': " + error.getMessage(), error);
        }
    }
}
