package ar.turnosodontologia.consultorio.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InstallationPracticeTest {

    private static final class FakePracticeRepository implements PracticeRepository {
        private final List<Practice> practices;
        private final AtomicInteger queries = new AtomicInteger();

        FakePracticeRepository(Practice... practices) {
            this.practices = List.of(practices);
        }

        @Override
        public List<Practice> findAll() {
            queries.incrementAndGet();
            return practices;
        }
    }

    private static Practice practice(UUID id, String zone) {
        return Practice.existing(id, "Consultorio", zone);
    }

    @Test
    void conUnaFilaDevuelveSuIdYSuZona() {
        UUID id = UUID.fromString("01900000-0000-7000-8000-000000000001");
        InstallationPractice installation =
                new InstallationPractice(new FakePracticeRepository(practice(id, "America/Argentina/Buenos_Aires")));

        assertThat(installation.id()).isEqualTo(id);
        assertThat(installation.zone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    @Test
    void conOtraFilaDevuelveSusPropiosDatos() {
        UUID id = UUID.fromString("01900000-0000-7000-8000-0000000000b2");
        InstallationPractice installation =
                new InstallationPractice(new FakePracticeRepository(practice(id, "America/Montevideo")));

        assertThat(installation.id()).isEqualTo(id);
        assertThat(installation.zone()).isEqualTo(ZoneId.of("America/Montevideo"));
    }

    @Test
    void sinFilasFallaDiciendoQueFaltaElConsultorio() {
        InstallationPractice installation = new InstallationPractice(new FakePracticeRepository());

        assertThatThrownBy(installation::id)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falta el consultorio de la instalacion");
    }

    @Test
    void conZonaInvalidaFallaSinMostrarOtrosDatos() {
        UUID id = UUID.fromString("01900000-0000-7000-8000-0000000000c3");
        InstallationPractice installation =
                new InstallationPractice(new FakePracticeRepository(practice(id, "Mars/Olympus")));

        assertThatThrownBy(installation::zone)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zona horaria del consultorio es invalida")
                .hasMessageNotContaining("Mars/Olympus")
                .hasMessageNotContaining(id.toString());
    }

    @Test
    void conMasDeUnaFilaFalla() {
        InstallationPractice installation = new InstallationPractice(new FakePracticeRepository(
                practice(UUID.randomUUID(), "America/Argentina/Buenos_Aires"),
                practice(UUID.randomUUID(), "America/Argentina/Buenos_Aires")));

        assertThatThrownBy(installation::id)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mas de un consultorio");
    }

    @Test
    void laSegundaLlamadaNoVuelveAConsultarElRepositorio() {
        FakePracticeRepository repository =
                new FakePracticeRepository(practice(UUID.randomUUID(), "America/Argentina/Buenos_Aires"));
        InstallationPractice installation = new InstallationPractice(repository);

        installation.id();
        installation.zone();
        installation.id();

        assertThat(repository.queries.get()).isEqualTo(1);
    }
}
