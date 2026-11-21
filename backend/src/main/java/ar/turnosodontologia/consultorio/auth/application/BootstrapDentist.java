package ar.turnosodontologia.consultorio.auth.application;

import ar.turnosodontologia.consultorio.auth.domain.AppUser;
import ar.turnosodontologia.consultorio.auth.domain.AppUserRepository;
import ar.turnosodontologia.consultorio.auth.domain.BootstrapCredentials;
import ar.turnosodontologia.consultorio.auth.domain.PasswordHasher;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea la primera cuenta DENTIST si el consultorio no tiene ninguna (design D11). Es idempotente: si ya existe un
 * DENTIST no lee ni valida las variables de arranque y no modifica nada.
 */
@Service
public class BootstrapDentist {

    public enum Outcome {
        CREATED,
        SKIPPED
    }

    /** Resultado; {@code userId} solo existe cuando se creo la cuenta. */
    public record Result(Outcome outcome, UUID userId) {
    }

    private final AppUserRepository users;
    private final PasswordHasher passwordHasher;
    private final InstallationPractice practice;
    private final Clock clock;

    public BootstrapDentist(
            AppUserRepository users, PasswordHasher passwordHasher, InstallationPractice practice, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.practice = practice;
        this.clock = clock;
    }

    /** @param variables lee una variable de configuracion por nombre (null si no esta definida) */
    @Transactional
    public Result execute(Function<String, String> variables) {
        UUID practiceId = practice.id();
        if (users.existsByPracticeIdAndRole(practiceId, Role.DENTIST)) {
            return new Result(Outcome.SKIPPED, null);
        }
        String email = require(variables, BootstrapCredentials.EMAIL_VARIABLE);
        String password = require(variables, BootstrapCredentials.PASSWORD_VARIABLE);
        BootstrapCredentials credentials = BootstrapCredentials.of(email, password);
        AppUser saved = users.save(AppUser.newDentist(
                practiceId, credentials.email(), passwordHasher.hash(credentials.password()), clock));
        return new Result(Outcome.CREATED, saved.id());
    }

    private static String require(Function<String, String> variables, String name) {
        String value = variables.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable " + name + ": es obligatoria en el primer arranque, cuando aun no existe "
                            + "una cuenta DENTIST");
        }
        return value;
    }
}
