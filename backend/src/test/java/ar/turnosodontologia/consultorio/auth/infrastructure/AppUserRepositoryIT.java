package ar.turnosodontologia.consultorio.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auth.domain.AppUser;
import ar.turnosodontologia.consultorio.auth.domain.AppUserRepository;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AppUserRepositoryIT extends AbstractIntegrationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC);

    private final AppUserRepository repository;
    private final InstallationPractice installationPractice;

    AppUserRepositoryIT(AppUserRepository repository, InstallationPractice installationPractice) {
        this.repository = repository;
        this.installationPractice = installationPractice;
    }

    /** El rol de aplicacion no puede borrar: se limpia con el rol de migracion. */
    @AfterEach
    void cleanUsers() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM app_user WHERE email LIKE '%@it-repo.test'");
        }
    }

    @Test
    void guardaYRelee() {
        AppUser saved = repository.save(
                AppUser.newDentist(installationPractice.id(), " Dr.Uno@IT-Repo.test ", "$argon2id$x", CLOCK));

        AppUser reloaded = repository.findByEmail("dr.uno@it-repo.test").orElseThrow();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.id().version()).as("UUID v7").isEqualTo(7);
        assertThat(reloaded.id()).isEqualTo(saved.id());
        assertThat(reloaded.createdAt()).isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));
        assertThat(reloaded.email()).isEqualTo("dr.uno@it-repo.test");
        assertThat(reloaded.role()).isEqualTo(Role.DENTIST);
        assertThat(reloaded.mustChangePassword()).isTrue();
    }

    @Test
    void existsByPracticeIdAndRoleDistingueConsultorioYRol() {
        // El arranque (bootstrap) ya creo el DENTIST del consultorio de la instalacion.
        assertThat(repository.existsByPracticeIdAndRole(installationPractice.id(), Role.DENTIST)).isTrue();
        assertThat(repository.existsByPracticeIdAndRole(UUID.randomUUID(), Role.DENTIST)).isFalse();
        assertThat(repository.findByEmail(BOOTSTRAP_EMAIL)).isPresent();
    }

    @Test
    void emailConOtraCapitalizacionEsDuplicado() {
        UUID practiceId = installationPractice.id();
        repository.save(AppUser.newDentist(practiceId, "tres@it-repo.test", "$argon2id$x", CLOCK));

        assertThatThrownBy(() -> repository.save(
                        AppUser.newDentist(practiceId, "TRES@it-repo.TEST", "$argon2id$y", CLOCK)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByEmailDeUnEmailInexistenteEsVacio() {
        assertThat(repository.findByEmail("nadie@it-repo.test")).isEmpty();
    }
}
