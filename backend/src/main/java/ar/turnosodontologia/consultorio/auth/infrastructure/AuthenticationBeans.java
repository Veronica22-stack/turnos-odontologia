package ar.turnosodontologia.consultorio.auth.infrastructure;

import ar.turnosodontologia.consultorio.auth.domain.LockoutPolicy;
import ar.turnosodontologia.consultorio.shared.api.RateLimiter;
import ar.turnosodontologia.consultorio.shared.infrastructure.InMemoryRateLimiter;
import java.security.SecureRandom;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** Beans de autenticación sin estado de petición, con valores por defecto seguros (D4/D5/D10). */
@Configuration(proxyBeanMethods = false)
class AuthenticationBeans {

    @Bean
    SessionProperties sessionProperties(Environment environment) {
        return SessionProperties.from(environment);
    }

    @Bean
    RateLimiter rateLimiter() {
        return new InMemoryRateLimiter();
    }

    @Bean
    LockoutPolicy lockoutPolicy() {
        return LockoutPolicy.defaults();
    }

    /** Hash Argon2id de un valor aleatorio descartado: iguala el coste de un email inexistente (D7). */
    @Bean
    String dummyPasswordHash() {
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        String random = UUID.randomUUID().toString() + new SecureRandom().nextLong();
        return encoder.encode(random);
    }
}
