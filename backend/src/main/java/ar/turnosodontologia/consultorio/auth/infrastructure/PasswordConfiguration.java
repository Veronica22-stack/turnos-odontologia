package ar.turnosodontologia.consultorio.auth.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Argon2id con el minimo de OWASP: m = 19 MiB, t = 2, p = 1 (design D2). */
@Configuration(proxyBeanMethods = false)
class PasswordConfiguration {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19456;
    private static final int ITERATIONS = 2;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS);
    }

    @Bean
    Argon2PasswordHasher argon2PasswordHasher() {
        return new Argon2PasswordHasher(passwordEncoder());
    }
}
