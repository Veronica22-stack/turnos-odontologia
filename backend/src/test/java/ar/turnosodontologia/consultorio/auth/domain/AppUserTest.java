package ar.turnosodontologia.consultorio.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppUserTest {

    private static Clock clock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void newDentistNormalizaElEmailYArrancaConValoresIniciales() {
        UUID practiceId = UUID.randomUUID();

        AppUser user = AppUser.newDentist(
                practiceId, "  Dr.Perez@Ejemplo.COM ", "$argon2id$hash-uno", clock("2026-03-10T12:00:00Z"));

        assertThat(user.email()).isEqualTo("dr.perez@ejemplo.com");
        assertThat(user.role()).isEqualTo(Role.DENTIST);
        assertThat(user.practiceId()).isEqualTo(practiceId);
        assertThat(user.passwordHash()).isEqualTo("$argon2id$hash-uno");
        assertThat(user.failedAttempts()).isZero();
        assertThat(user.lockedUntil()).isEmpty();
        assertThat(user.lastLoginAt()).isEmpty();
        assertThat(user.createdAt()).isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));
        assertThat(user.mustChangePassword()).isTrue();
    }

    @Test
    void newDentistConOtroEmailYRelojUsaEsosValores() {
        AppUser user = AppUser.newDentist(
                UUID.randomUUID(), "otra@clinica.org", "$argon2id$hash-dos", clock("2027-01-02T03:04:05Z"));

        assertThat(user.email()).isEqualTo("otra@clinica.org");
        assertThat(user.createdAt()).isEqualTo(Instant.parse("2027-01-02T03:04:05Z"));
        assertThat(user.passwordHash()).isEqualTo("$argon2id$hash-dos");
    }

    @Test
    void toStringNoExponeElHashNiElEmail() {
        AppUser user = AppUser.newDentist(
                UUID.randomUUID(), "dr@ejemplo.com", "$argon2id$hash-secreto", clock("2026-03-10T12:00:00Z"));

        assertThat(user.toString()).doesNotContain("$argon2id$hash-secreto").doesNotContain("dr@ejemplo.com");
    }
}
