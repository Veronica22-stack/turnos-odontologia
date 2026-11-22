package ar.turnosodontologia.consultorio.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auth.domain.AppUser;
import ar.turnosodontologia.consultorio.auth.domain.AppUserRepository;
import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.PasswordHasher;
import ar.turnosodontologia.consultorio.shared.domain.UnauthenticatedException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangeOwnPasswordTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final String CURRENT = "actual-12345";
    private static final String NEW = "nueva-123456";

    private FakeUsers users;
    private FakeHasher hasher;
    private FakeInvalidation invalidation;
    private ChangeOwnPassword useCase;

    @BeforeEach
    void setUp() {
        users = new FakeUsers();
        hasher = new FakeHasher();
        invalidation = new FakeInvalidation();
        useCase = new ChangeOwnPassword(users, hasher, invalidation);
    }

    private static ChangePasswordCommand command(String current, String newPassword) {
        return new ChangePasswordCommand(current, newPassword);
    }

    private AppUser dentist() {
        AppUser user = AppUser.newDentist(UUID.randomUUID(), "dr@ejemplo.com", "$argon2id$viejo",
                Clock.fixed(NOW, ZoneOffset.UTC));
        setId(user, UUID.randomUUID());
        users.byId.put(user.id(), user);
        return user;
    }

    private static void setId(AppUser user, UUID id) {
        try {
            Field field = AppUser.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void contrasenaActualIncorrectaDaErrorGenericoSinRevelarCualFalló() {
        AppUser user = dentist();
        hasher.matches = false;

        assertThatThrownBy(() -> useCase.execute(principal(user), command("mala-1234567", NEW)))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("INVALID_CREDENTIALS");
        assertThat(user.passwordHash()).isEqualTo("$argon2id$viejo");
        assertThat(invalidation.invalidatedUsers).isEmpty();
    }

    @Test
    void nuevaContrasenaDeOchoCaracteresEsValidadaSinMostrarElValor() {
        AppUser user = dentist();

        assertThatThrownBy(() -> useCase.execute(principal(user), command(CURRENT, "corta123")))
                .isInstanceOf(ValidationException.class)
                .satisfies(error -> {
                    ValidationException validation = (ValidationException) error;
                    assertThat(validation.field()).isEqualTo("newPassword");
                    assertThat(validation.code()).isEqualTo("VALIDATION_FAILED");
                    assertThat(validation.getMessage()).doesNotContain("corta123");
                });
        assertThat(user.passwordHash()).isEqualTo("$argon2id$viejo");
    }

    @Test
    void cambioCorrectoActualizaHashLimpiaBanderaEInvalidaSesionesDelUsuario() {
        AppUser user = dentist();

        ChangePasswordResult result = useCase.execute(principal(user), command(CURRENT, NEW));

        assertThat(user.passwordHash()).isEqualTo("$argon2id$nuevo");
        assertThat(user.mustChangePassword()).isFalse();
        assertThat(result.userId()).isEqualTo(user.id());
        assertThat(invalidation.invalidatedUsers).containsExactly(user.id());
    }

    @Test
    void segundoCambioConBanderaYaLimpiaFunciona() {
        AppUser user = dentist();
        user.clearMustChangePassword();

        useCase.execute(principal(user), command(CURRENT, NEW));

        assertThat(user.mustChangePassword()).isFalse();
        assertThat(invalidation.invalidatedUsers).containsExactly(user.id());
    }

    @Test
    void noInvalidaLaSesionDeOtroUsuario() {
        AppUser subject = dentist();
        AppUser other = dentist();

        useCase.execute(principal(subject), command(CURRENT, NEW));

        assertThat(invalidation.invalidatedUsers).containsExactly(subject.id()).doesNotContain(other.id());
    }

    private static AuthenticatedUser principal(AppUser user) {
        return new AuthenticatedUser(user.id(), user.practiceId(), user.role(), user.mustChangePassword());
    }

    private static final class FakeUsers implements AppUserRepository {

        private final java.util.Map<UUID, AppUser> byId = new java.util.HashMap<>();

        @Override
        public AppUser save(AppUser user) {
            byId.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<AppUser> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public boolean existsByPracticeIdAndRole(UUID practiceId, ar.turnosodontologia.consultorio.auth.domain.Role role) {
            return false;
        }

        @Override
        public Optional<AppUser> findByEmail(String email) {
            return Optional.empty();
        }
    }

    private static final class FakeHasher implements PasswordHasher {

        private boolean matches = true;

        @Override
        public String hash(CharSequence raw) {
            return "$argon2id$nuevo";
        }

        @Override
        public boolean matches(CharSequence raw, String hash) {
            return matches;
        }
    }

    private static final class FakeInvalidation implements SessionInvalidation {

        private final List<UUID> invalidatedUsers = new ArrayList<>();

        @Override
        public void invalidateAllSessions(UUID userId) {
            invalidatedUsers.add(userId);
        }

        @Override
        public void invalidateCurrentSession() {
        }
    }
}
