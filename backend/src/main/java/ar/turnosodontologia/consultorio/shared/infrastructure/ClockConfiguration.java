package ar.turnosodontologia.consultorio.shared.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj unico de la aplicacion, en UTC; el codigo nunca llama a {@code Instant.now()} sin un {@link Clock}. */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
