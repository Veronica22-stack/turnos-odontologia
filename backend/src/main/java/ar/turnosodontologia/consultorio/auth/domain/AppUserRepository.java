package ar.turnosodontologia.consultorio.auth.domain;

import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de cuentas. */
public interface AppUserRepository {

    AppUser save(AppUser user);

    boolean existsByPracticeIdAndRole(UUID practiceId, Role role);

    /** Busca por email ya normalizado (minusculas, sin espacios). */
    Optional<AppUser> findByEmail(String email);
}
