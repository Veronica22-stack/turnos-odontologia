package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuración del generador CSPRNG de tokens de gestión (única instancia en la aplicación). */
@Configuration(proxyBeanMethods = false)
class BookingTokenConfiguration {

    @Bean
    BookingTokenGenerator bookingTokenGenerator() {
        return new BookingTokenGenerator();
    }
}
