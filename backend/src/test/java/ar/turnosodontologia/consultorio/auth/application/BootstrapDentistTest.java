package ar.turnosodontologia.consultorio.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auth.domain.AppUser;
import ar.turnosodontologia.consultorio.auth.domain.AppUserRepository;
import ar.turnosodontologia.consultorio.auth.domain.PasswordHasher;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BootstrapDentistTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    private static final class FakeAppUserRepository implements AppUserRepository {
        private final List<AppUser> saved = new ArrayList<>();
        private final boolean dentistExists;

        FakeAppUserRepository(boolean dentistExists) {
            this.dentistExists = dentistExists;
        }

        @Override
        public AppUser save(AppUser user) {
            saved.add(user);
            return user;
        }

        @Override
        public boolean existsByPracticeIdAndRole(UUID practiceId, Role role) {
            return dentistExists && practiceId.equals(PRACTICE_ID) && role == Role.DENTIST;
        }

        @Override
        public Optional<AppUser> findByEmail(String email) {
            return Optional.empty();
        }
    }

    private static final PasswordHasher FAKE_HASHER = new PasswordHasher() {
        @Override
        public String hash(CharSequence raw) {
            return "fake-hash(" + raw.length() + ")";
        }

        @Override
        public boolean matches(CharSequence raw, String hash) {
            return hash(raw).equals(hash);
        }
    };

    private final InstallationPractice practice = new InstallationPractice(
            () -> List.of(Practice.existing(PRACTICE_ID, "Consultorio", "America/Argentina/Buenos_Aires")));
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC);

    private BootstrapDentist useCase(FakeAppUserRepository repository) {
        return new BootstrapDentist(repository, FAKE_HASHER, practice, clock);
    }

    @Test
    void sinDentistaYConVariablesValidasCreaLaCuentaConCambioObligatorioDeContrasena() {
        FakeAppUserRepository repository = new FakeAppUserRepository(false);

        BootstrapDentist.Result result = useCase(repository)
                .execute(variables("Dr@Ejemplo.com", "contrasena-segura-1"));

        assertThat(result.outcome()).isEqualTo(BootstrapDentist.Outcome.CREATED);
        assertThat(repository.saved).hasSize(1);
        AppUser user = repository.saved.get(0);
        assertThat(user.email()).isEqualTo("dr@ejemplo.com");
        assertThat(user.role()).isEqualTo(Role.DENTIST);
        assertThat(user.mustChangePassword()).isTrue();
        assertThat(user.practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(user.passwordHash()).isEqualTo("fake-hash(19)");
        assertThat(user.createdAt()).isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));
    }

    @Test
    void conUnDentistaExistenteSeOmiteSinLeerVariablesNiGuardar() {
        FakeAppUserRepository repository = new FakeAppUserRepository(true);
        AtomicInteger reads = new AtomicInteger();

        BootstrapDentist.Result result = useCase(repository).execute(name -> {
            reads.incrementAndGet();
            return "x";
        });

        assertThat(result.outcome()).isEqualTo(BootstrapDentist.Outcome.SKIPPED);
        assertThat(reads.get()).isZero();
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void sinDentistaYSinContrasenaFallaNombrandoLaVariableSinMostrarElEmail() {
        for (String missing : new String[] {null, "   "}) {
            FakeAppUserRepository repository = new FakeAppUserRepository(false);
            Map<String, String> env = new HashMap<>();
            env.put("BOOTSTRAP_ADMIN_EMAIL", "dr@ejemplo.com");
            env.put("BOOTSTRAP_ADMIN_PASSWORD", missing);

            assertThatThrownBy(() -> useCase(repository).execute(env::get))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD")
                    .hasMessageNotContaining("dr@ejemplo.com");
            assertThat(repository.saved).isEmpty();
        }
    }

    @Test
    void sinDentistaYSinEmailFallaNombrandoLaVariable() {
        FakeAppUserRepository repository = new FakeAppUserRepository(false);
        Map<String, String> env = Map.of("BOOTSTRAP_ADMIN_PASSWORD", "contrasena-segura-1");

        assertThatThrownBy(() -> useCase(repository).execute(env::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL")
                .hasMessageNotContaining("contrasena-segura-1");
    }

    @Test
    void contrasenaCortaFallaYNoGuardaNada() {
        FakeAppUserRepository repository = new FakeAppUserRepository(false);

        assertThatThrownBy(() -> useCase(repository).execute(variables("dr@ejemplo.com", "corta")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD")
                .hasMessageNotContaining("corta ");
        assertThat(repository.saved).isEmpty();
    }

    private static java.util.function.Function<String, String> variables(String email, String password) {
        Map<String, String> env = Map.of("BOOTSTRAP_ADMIN_EMAIL", email, "BOOTSTRAP_ADMIN_PASSWORD", password);
        return env::get;
    }
}
