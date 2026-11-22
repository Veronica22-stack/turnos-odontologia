package ar.turnosodontologia.consultorio.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.auth.domain.AppUser;
import ar.turnosodontologia.consultorio.auth.domain.AppUserRepository;
import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.LockoutPolicy;
import ar.turnosodontologia.consultorio.auth.domain.PasswordHasher;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.shared.api.RateLimiter;
import ar.turnosodontologia.consultorio.shared.domain.RateLimitedException;
import ar.turnosodontologia.consultorio.shared.domain.UnauthenticatedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticateUserTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final String EMAIL = "Dr.Perez@Ejemplo.com";
    private static final String NORMALIZED_EMAIL = "dr.perez@ejemplo.com";
    private static final String RAW_PASSWORD = "correcta-123";
    private static final String STORED_HASH = "$argon2id$guardado";
    private static final String DUMMY_HASH = "$argon2id$dummy";
    private static final String RAW_USER_AGENT = "Mozilla/5.0 dr.perez@ejemplo.com";

    private FakeUsers users;
    private FakeHasher hasher;
    private FakeRateLimiter limiter;
    private FakeAuditLogger audit;
    private FakeSession session;
    private AuthenticateUser useCase;

    @BeforeEach
    void setUp() {
        users = new FakeUsers();
        hasher = new FakeHasher();
        limiter = new FakeRateLimiter();
        audit = new FakeAuditLogger();
        session = new FakeSession();
        useCase = new AuthenticateUser(users, hasher, Clock.fixed(NOW, ZoneOffset.UTC), limiter, audit,
                session, LockoutPolicy.defaults(), DUMMY_HASH);
    }

    private static LoginCommand command() {
        return new LoginCommand(EMAIL, RAW_PASSWORD, "203.0.113.7", RAW_USER_AGENT);
    }

    private AppUser existingUser() {
        AppUser user = AppUser.newDentist(UUID.randomUUID(), NORMALIZED_EMAIL, STORED_HASH,
                Clock.fixed(NOW, ZoneOffset.UTC));
        users.byEmail.put(NORMALIZED_EMAIL, user);
        return user;
    }

    @Test
    void loginCorrectoDevuelvePrincipalYEmitirLoginOk() {
        AppUser user = existingUser();

        LoginResult result = useCase.execute(command());

        assertThat(result.user().userId()).isEqualTo(user.id());
        assertThat(result.user().practiceId()).isEqualTo(user.practiceId());
        assertThat(result.user().role()).isEqualTo(Role.DENTIST);
        assertThat(result.user().mustChangePassword()).isTrue();
        assertThat(result.authenticatedAt()).isEqualTo(NOW);
        assertThat(session.user).isEqualTo(result.user());
        assertThat(audit.events).hasSize(1);
        assertThat(audit.events.get(0).action()).isEqualTo(AuditAction.LOGIN_OK);
        assertThat(audit.events.get(0).userId()).isEqualTo(user.id());
        assertThat(user.lastLoginAt()).contains(NOW);
    }

    @Test
    void contrasenaIncorrectaEmiteLoginFailY401Generico() {
        existingUser();
        LoginCommand wrong = new LoginCommand(EMAIL, "incorrecta-123", "203.0.113.7", RAW_USER_AGENT);

        assertThatThrownBy(() -> useCase.execute(wrong))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("INVALID_CREDENTIALS");
        assertThat(audit.events).hasSize(1);
        assertThat(audit.events.get(0).action()).isEqualTo(AuditAction.LOGIN_FAIL);
        assertThat(audit.events.get(0).details()).containsEntry("outcome", "FAIL")
                .containsEntry("reason_code", "INVALID_CREDENTIALS");
        assertThat(session.user).isNull();
    }

    @Test
    void emailInexistenteUsaHashDeReferenciaYAuditaUsuarioNulo() {
        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("INVALID_CREDENTIALS");
        assertThat(hasher.attemptedHashes).containsExactly(DUMMY_HASH);
        assertThat(audit.events).hasSize(1);
        assertThat(audit.events.get(0).userId()).isNull();
        assertThat(audit.events.get(0).entityId()).isNull();
    }

    @Test
    void cuentaBloqueadaNoEjecutaElHasher() {
        AppUser user = existingUser();
        LockoutPolicy policy = LockoutPolicy.defaults();
        for (int day = 5; day >= 2; day--) {
            user.registerFailedAttempt(Clock.fixed(NOW.minus(Duration.ofDays(day)), ZoneOffset.UTC), policy);
        }
        user.registerFailedAttempt(Clock.fixed(NOW, ZoneOffset.UTC), policy);
        assertThat(user.lockedAt(NOW)).isTrue();

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("INVALID_CREDENTIALS");
        assertThat(hasher.attemptedHashes).isEmpty();
        assertThat(audit.events.get(0).details()).containsEntry("reason_code", "ACCOUNT_LOCKED");
        assertThat(session.user).isNull();
    }

    @Test
    void limiteExcedidoDevuelve429ConSegundosDeReintento() {
        limiter.retryAt = Optional.of(NOW.plusSeconds(43));

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(RateLimitedException.class)
                .satisfies(error -> assertThat(((RateLimitedException) error).retryAfterSeconds()).isEqualTo(43));
        assertThat(hasher.attemptedHashes).isEmpty();
        assertThat(audit.events).isEmpty();
    }

    @Test
    void detalleDeAuditoriaNoContieneEmailContrasenaNiUserAgentCrudo() {
        existingUser();

        useCase.execute(command());

        String details = audit.events.get(0).details().toString();
        assertThat(details).doesNotContain(EMAIL, NORMALIZED_EMAIL, RAW_PASSWORD, RAW_USER_AGENT);
        assertThat(audit.events.get(0).toString())
                .doesNotContain(EMAIL, NORMALIZED_EMAIL, RAW_PASSWORD, RAW_USER_AGENT);
        assertThat(audit.events.get(0).details().keySet())
                .containsExactlyInAnyOrder("outcome", "reason_code", "attempt", "source");
    }

    @Test
    void ipInvalidaSeNormalizaYUserAgentSeSustituyePorDigest() {
        existingUser();
        LoginCommand command = new LoginCommand(EMAIL, RAW_PASSWORD, "no-es-una-ip", RAW_USER_AGENT);

        useCase.execute(command);

        AuditEvent event = audit.events.get(0);
        assertThat(event.ip()).isNull();
        assertThat(event.userAgent()).startsWith("ua:").hasSize(19);
        assertThat(event.userAgent()).doesNotContain(RAW_USER_AGENT, NORMALIZED_EMAIL);
    }

    @Test
    void siLaAuditoriaFallaNoSeCreaSesionYElErrorEsGenerico() {
        existingUser();
        audit.fail = true;

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AUDIT_UNAVAILABLE");
        assertThat(session.user).isNull();
    }

    private static final class FakeUsers implements AppUserRepository {

        private final Map<String, AppUser> byEmail = new HashMap<>();

        @Override
        public AppUser save(AppUser user) {
            byEmail.put(user.email(), user);
            return user;
        }

        @Override
        public Optional<AppUser> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public boolean existsByPracticeIdAndRole(UUID practiceId, Role role) {
            return false;
        }

        @Override
        public Optional<AppUser> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }
    }

    private static final class FakeHasher implements PasswordHasher {

        private final List<String> attemptedHashes = new ArrayList<>();

        @Override
        public String hash(CharSequence raw) {
            return "hash-" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String hash) {
            attemptedHashes.add(hash);
            return RAW_PASSWORD.contentEquals(raw) && STORED_HASH.equals(hash);
        }
    }

    private static final class FakeRateLimiter implements RateLimiter {

        private Optional<Instant> retryAt = Optional.empty();
        private String lastKey;

        @Override
        public Optional<Instant> tryAcquire(String key, Duration window, Clock clock) {
            lastKey = key;
            return retryAt;
        }
    }

    private static final class FakeAuditLogger implements AuditLogger {

        private final List<AuditEvent> events = new ArrayList<>();
        private boolean fail;

        @Override
        public void log(AuditEvent event) {
            if (fail) {
                throw new IllegalStateException("AUDIT_UNAVAILABLE");
            }
            events.add(event);
        }
    }

    private static final class FakeSession implements LoginSession {

        private AuthenticatedUser user;

        @Override
        public void establish(AuthenticatedUser user) {
            this.user = user;
        }
    }
}
