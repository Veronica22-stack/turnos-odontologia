package ar.turnosodontologia.consultorio.auth.infrastructure;

import ar.turnosodontologia.consultorio.auth.domain.AppUser;
import ar.turnosodontologia.consultorio.auth.domain.AppUserRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Adaptador Spring Data del puerto {@link AppUserRepository}; los metodos derivados los implementa Spring Data. */
interface SpringDataAppUserRepository extends JpaRepository<AppUser, UUID>, AppUserRepository {
}
