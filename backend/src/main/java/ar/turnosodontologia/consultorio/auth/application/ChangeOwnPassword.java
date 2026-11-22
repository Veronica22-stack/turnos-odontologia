package ar.turnosodontologia.consultorio.auth.application;

import ar.turnosodontologia.consultorio.auth.domain.AppUser;
import ar.turnosodontologia.consultorio.auth.domain.AppUserRepository;
import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.PasswordHasher;
import ar.turnosodontologia.consultorio.auth.domain.PasswordPolicy;
import ar.turnosodontologia.consultorio.shared.domain.UnauthenticatedException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cambio de la propia contraseña: política compartida y invalidación de todas las sesiones de la cuenta (D8). */
@Service
public class ChangeOwnPassword {

    private final AppUserRepository users;
    private final PasswordHasher hasher;
    private final SessionInvalidation sessions;

    public ChangeOwnPassword(AppUserRepository users, PasswordHasher hasher, SessionInvalidation sessions) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
    }

    @Transactional
    public ChangePasswordResult execute(AuthenticatedUser principal, ChangePasswordCommand command) {
        Objects.requireNonNull(principal, "principal");
        AppUser user = users.findById(principal.userId())
                .orElseThrow(() -> new UnauthenticatedException("INVALID_CREDENTIALS"));
        if (!hasher.matches(command.currentPassword(), user.passwordHash())) {
            throw new UnauthenticatedException("INVALID_CREDENTIALS");
        }
        try {
            PasswordPolicy.requireValid(command.newPassword(), "newPassword");
        } catch (IllegalArgumentException error) {
            throw new ValidationException("newPassword", "Size");
        }
        user.changePassword(hasher.hash(command.newPassword()));
        user.clearMustChangePassword();
        users.save(user);
        sessions.invalidateAllSessions(user.id());
        return new ChangePasswordResult(user.id());
    }
}
