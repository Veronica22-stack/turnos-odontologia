package ar.turnosodontologia.consultorio.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new PasswordConfiguration().argon2PasswordHasher();

    @Test
    void generaHashesArgon2idConLosParametrosDeOwasp() {
        String first = hasher.hash("una-contrasena-larga");
        String second = hasher.hash("una-contrasena-larga");

        assertThat(first).startsWith("$argon2id$").contains("m=19456,t=2,p=1");
        assertThat(second).startsWith("$argon2id$").contains("m=19456,t=2,p=1");
        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("una-contrasena-larga");
    }

    @Test
    void matchesVerdaderoConLaOriginalYFalsoConOtra() {
        String hash = hasher.hash("otra-contrasena-12345");

        assertThat(hasher.matches("otra-contrasena-12345", hash)).isTrue();
        assertThat(hasher.matches("otra-contrasena-54321", hash)).isFalse();
        assertThat(hasher.matches("", hash)).isFalse();
    }
}
