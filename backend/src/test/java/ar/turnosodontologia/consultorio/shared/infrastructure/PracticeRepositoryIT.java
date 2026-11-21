package ar.turnosodontologia.consultorio.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El contexto arranca con ddl-auto=validate y el consultorio sembrado por V2 llega a la aplicacion. */
class PracticeRepositoryIT extends AbstractIntegrationTest {

    private static final UUID SEEDED_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    private final InstallationPractice installationPractice;
    private final PracticeRepository practiceRepository;

    PracticeRepositoryIT(InstallationPractice installationPractice, PracticeRepository practiceRepository) {
        this.installationPractice = installationPractice;
        this.practiceRepository = practiceRepository;
    }

    @Test
    void elConsultorioDeLaInstalacionEsElSembradoPorV2() {
        assertThat(installationPractice.id()).isEqualTo(SEEDED_ID);
        assertThat(installationPractice.zone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    @Test
    void elRepositorioDevuelveUnaSolaFilaConNombreYZona() {
        List<Practice> practices = practiceRepository.findAll();

        assertThat(practices).hasSize(1);
        assertThat(practices.get(0).name()).isEqualTo("Consultorio");
        assertThat(practices.get(0).timezone()).isEqualTo("America/Argentina/Buenos_Aires");
    }
}
