package ar.turnosodontologia.consultorio.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppUserTest {

    private static final LockoutPolicy POLICY = LockoutPolicy.defaults();
    private static final Instant START = Instant.parse("2026-03-10T12:00:00Z");

    private static Clock clock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    private static Clock clock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static AppUser dentist() {
        return AppUser.newDentist(UUID.randomUUID(), "dr@ejemplo.com", "$argon2id$inicial", clock(START));
    }

    /** Un fallo por dia: cada intento encuentra el bloqueo anterior vencido. */
    private static void failTimes(AppUser user, int times) {
        failTimes(user, times, START);
    }

    private static void failTimes(AppUser user, int times, Instant start) {
        for (int attempt = 1; attempt <= times; attempt++) {
            user.registerFailedAttempt(clock(start.plus(Duration.ofDays(attempt))), POLICY);
        }
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

    @Test
    void primerFalloIncrementaElContadorSinBloquear() {
        AppUser user = dentist();

        user.registerFailedAttempt(clock(START), POLICY);

        assertThat(user.failedAttempts()).isEqualTo(1);
        assertThat(user.lockedUntil()).isEmpty();
    }

    @Test
    void quintoFalloBloqueaQuinceMinutos() {
        AppUser user = dentist();
        failTimes(user, 4);

        user.registerFailedAttempt(clock(START.plus(Duration.ofDays(5))), POLICY);

        assertThat(user.failedAttempts()).isEqualTo(5);
        assertThat(user.lockedUntil()).contains(START.plus(Duration.ofDays(5)).plus(Duration.ofMinutes(15)));
    }

    @Test
    void decimoFalloBloqueaTreintaMinutosYDecimoquintoBloqueaSesenta() {
        AppUser user = dentist();

        failTimes(user, 10);

        assertThat(user.failedAttempts()).isEqualTo(10);
        assertThat(user.lockedUntil()).contains(START.plus(Duration.ofDays(10)).plus(Duration.ofMinutes(30)));

        Instant afterSecondLock = START.plus(Duration.ofDays(10)).plus(Duration.ofMinutes(31));
        failTimes(user, 5, afterSecondLock);

        assertThat(user.failedAttempts()).isEqualTo(15);
        assertThat(user.lockedUntil()).contains(afterSecondLock.plus(Duration.ofDays(5)).plus(Duration.ofMinutes(60)));
    }

    @Test
    void intentoDuranteBloqueoNoIncrementaElContador() {
        AppUser user = dentist();
        failTimes(user, 5);
        int attempts = user.failedAttempts();
        Instant lockedUntil = user.lockedUntil().orElseThrow();

        user.registerFailedAttempt(clock(lockedUntil.minusSeconds(1)), POLICY);

        assertThat(user.failedAttempts()).isEqualTo(attempts);
        assertThat(user.lockedUntil()).contains(lockedUntil);
    }

    @Test
    void loginCorrectoReiniciaContadorLimpiaBloqueoYFijaUltimoAcceso() {
        AppUser user = dentist();
        failTimes(user, 5);
        Instant now = user.lockedUntil().orElseThrow().plusSeconds(1);

        user.registerSuccessfulLogin(clock(now));

        assertThat(user.failedAttempts()).isZero();
        assertThat(user.lockedUntil()).isEmpty();
        assertThat(user.lastLoginAt()).contains(now);
    }

    @Test
    void passwordDeOnzeCaracteresEsRechazadaYDeDoceAceptada() {
        assertThatThrownBy(() -> PasswordPolicy.requireValid("12345678901"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newPassword");
        assertThatCode(() -> PasswordPolicy.requireValid("123456789012")).doesNotThrowAnyException();
    }

    @Test
    void changePasswordActualizaElHashYLimpiaCambioObligatorio() {
        AppUser user = dentist();
        assertThat(user.mustChangePassword()).isTrue();

        user.changePassword("$argon2id$nuevo");
        user.clearMustChangePassword();

        assertThat(user.passwordHash()).isEqualTo("$argon2id$nuevo");
        assertThat(user.mustChangePassword()).isFalse();
        assertThat(user.toString()).doesNotContain("$argon2id$nuevo");
    }

    @Test
    void falloSermanentementeNoSuperaElTechoDeSesentaMinutos() {
        AppUser user = dentist();
        failTimes(user, 15);
        Instant afterCap = START.plus(Duration.ofDays(15)).plus(Duration.ofMinutes(61));

        user.registerFailedAttempt(clock(afterCap), POLICY);
        user.registerFailedAttempt(clock(afterCap.plus(Duration.ofDays(1))), POLICY);

        assertThat(user.failedAttempts()).isEqualTo(17);
        assertThat(user.lockedUntil()).contains(afterCap.plus(Duration.ofDays(1)).plus(Duration.ofMinutes(60)));
    }

    @Test
    void bloqueoVencidoReanudaLaEscalada() {
        AppUser user = dentist();
        Instant firstLock = START.plus(Duration.ofDays(5));
        failTimes(user, 5);
        assertThat(user.lockedUntil()).contains(firstLock.plus(Duration.ofMinutes(15)));

        user.registerFailedAttempt(clock(firstLock.plus(Duration.ofMinutes(16))), POLICY);
        assertThat(user.failedAttempts()).isEqualTo(6);
        assertThat(user.lockedUntil()).isEmpty();

        failTimes(user, 4, firstLock);
        assertThat(user.failedAttempts()).isEqualTo(10);
        assertThat(user.lockedUntil()).contains(firstLock.plus(Duration.ofDays(4)).plus(Duration.ofMinutes(30)));
    }

    @Test
    void relojFijoEnUtcDeterminaElVencimiento() {
        AppUser user = AppUser.newDentist(
                UUID.randomUUID(), "utc@ejemplo.com", "$argon2id$hash", clock("2026-03-10T12:00:00Z"));

        user.registerFailedAttempt(clock("2026-03-10T12:00:00Z"), POLICY);
        failTimes(user, 4);

        assertThat(user.createdAt()).isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));
        assertThat(user.lockedUntil()).contains(Instant.parse("2026-03-14T12:00:00Z").plus(Duration.ofMinutes(15)));
    }

    @Test
    void falloDeLoginNoLimpiaElCambioObligatorio() {
        AppUser user = dentist();

        user.registerFailedAttempt(clock(START), POLICY);

        assertThat(user.mustChangePassword()).isTrue();
    }
}
