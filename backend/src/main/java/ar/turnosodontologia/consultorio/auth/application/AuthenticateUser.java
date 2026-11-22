package ar.turnosodontologia.consultorio.auth.application;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.auth.domain.AppUser;
import ar.turnosodontologia.consultorio.auth.domain.AppUserRepository;
import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.LockoutPolicy;
import ar.turnosodontologia.consultorio.auth.domain.PasswordHasher;
import ar.turnosodontologia.consultorio.shared.api.RateLimiter;
import ar.turnosodontologia.consultorio.shared.domain.RateLimitedException;
import ar.turnosodontologia.consultorio.shared.domain.UnauthenticatedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autenticación con igualdad de respuesta, bloqueo progresivo, límite de tasa y auditoría fail-closed (D4/D7/D9). */
@Service
public class AuthenticateUser {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final String ENTITY_TYPE = "app_user";
    private static final String SOURCE = "HTTP";

    private final AppUserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;
    private final RateLimiter rateLimiter;
    private final AuditLogger auditLogger;
    private final LoginSession session;
    private final LockoutPolicy lockoutPolicy;
    private final String dummyHash;

    public AuthenticateUser(AppUserRepository users, PasswordHasher hasher, Clock clock, RateLimiter rateLimiter,
            AuditLogger auditLogger, LoginSession session, LockoutPolicy lockoutPolicy, String dummyHash) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
        this.rateLimiter = rateLimiter;
        this.auditLogger = auditLogger;
        this.session = session;
        this.lockoutPolicy = lockoutPolicy;
        this.dummyHash = dummyHash;
    }

    @Transactional
    public LoginResult execute(LoginCommand command) {
        Instant now = clock.instant();
        String email = AppUser.normalize(command.email());
        enforceRateLimit(email, command.ip(), now);

        Optional<AppUser> found = users.findByEmail(email);
        if (found.isPresent() && found.get().lockedAt(now)) {
            audit(command, found.get(), AuditAction.LOGIN_FAIL, "ACCOUNT_LOCKED", found.get().failedAttempts());
            throw invalidCredentials();
        }

        AppUser user = found.orElse(null);
        String hash = user == null ? dummyHash : user.passwordHash();
        if (!hasher.matches(command.password(), hash)) {
            if (user != null) {
                user.registerFailedAttempt(clock, lockoutPolicy);
                users.save(user);
            }
            audit(command, user, AuditAction.LOGIN_FAIL, "INVALID_CREDENTIALS", user == null ? 0 : user.failedAttempts());
            throw invalidCredentials();
        }

        user.registerSuccessfulLogin(clock);
        users.save(user);
        audit(command, user, AuditAction.LOGIN_OK, "OK", 0);
        AuthenticatedUser principal = new AuthenticatedUser(
                user.id(), user.practiceId(), user.role(), user.mustChangePassword());
        session.establish(principal);
        return new LoginResult(principal, now);
    }

    private void enforceRateLimit(String email, String ip, Instant now) {
        String source = ip == null ? "" : ip;
        Optional<Instant> retryAt = rateLimiter.tryAcquire("login|" + source + "|" + email, RATE_LIMIT_WINDOW, clock);
        retryAt.ifPresent(instant -> {
            throw new RateLimitedException(RateLimiter.retryAfterSeconds(now, instant));
        });
    }

    private void audit(LoginCommand command, AppUser user, AuditAction action, String reasonCode, int attempt) {
        auditLogger.log(auditEvent(command, user, action, reasonCode, attempt));
    }

    /** Mapper puro: lista cerrada de detalles y agente irreversible; nunca email, contraseña ni token. */
    private static AuditEvent auditEvent(LoginCommand command, AppUser user, AuditAction action,
            String reasonCode, int attempt) {
        UUID userId = user == null ? null : user.id();
        Map<String, Object> details = Map.of(
                "outcome", action == AuditAction.LOGIN_OK ? "OK" : "FAIL",
                "reason_code", reasonCode,
                "attempt", attempt,
                "source", SOURCE);
        return AuditEvent.of(action, userId, null, ENTITY_TYPE,
                userId == null ? null : userId.toString(), command.ip(),
                "ua:" + digest(command.userAgent()).substring(0, 16), details);
    }

    private static UnauthenticatedException invalidCredentials() {
        return new UnauthenticatedException("INVALID_CREDENTIALS");
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
        }
    }
}
