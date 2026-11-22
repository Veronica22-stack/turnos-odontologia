package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.agenda.domain.PracticeService;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeServiceRepository;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServiceRepositoryIT extends AbstractIntegrationTest {

    private static final UUID SEEDED_PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID OTHER_PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");

    private final PracticeServiceRepository repository;

    ServiceRepositoryIT(PracticeServiceRepository repository) {
        this.repository = repository;
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM service");
        }
    }

    @Test
    void guardaYListsLasPrestacionesOrdenadasPorNombre() {
        PracticeService zeta = PracticeService.create(SEEDED_PRACTICE_ID, "Zeta", null, 45, false);
        PracticeService alpha = PracticeService.create(SEEDED_PRACTICE_ID, "Alpha", null, 30, true);
        repository.save(zeta);
        repository.save(alpha);

        List<PracticeService> ordered = repository.findAllByPracticeIdOrderByNameAsc(SEEDED_PRACTICE_ID);

        assertThat(ordered).extracting(PracticeService::name).containsExactly("Alpha", "Zeta");
        assertThat(repository.findById(alpha.id())).isPresent();
    }

    @Test
    void listaSoloLasPrestacionesDeLaPracticePedida() {
        PracticeService mine = PracticeService.create(SEEDED_PRACTICE_ID, "Alpha", null, 30, false);
        repository.save(mine);

        assertThat(repository.findAllByPracticeIdOrderByNameAsc(SEEDED_PRACTICE_ID))
                .extracting(PracticeService::name)
                .containsExactly("Alpha");
        // El modelo single-tenant permite una sola fila de practice: la "otra práctica" no existe en la base.
        assertThat(repository.findAllByPracticeIdOrderByNameAsc(OTHER_PRACTICE_ID)).isEmpty();
    }

    @Test
    void listaGestionIncluyeInactivasYCatalogoPublicoSoloLosActivosVisibles() {
        PracticeService activaVisible = PracticeService.create(SEEDED_PRACTICE_ID, "A ActivoVisible", null, 30, true);
        PracticeService activaNoVisible = PracticeService.create(SEEDED_PRACTICE_ID, "B ActivoNoVisible", null, 30, false);
        PracticeService inactivaVisible = PracticeService.create(SEEDED_PRACTICE_ID, "C InactivaVisible", null, 30, true);
        inactivaVisible.deactivate();
        repository.save(activaVisible);
        repository.save(activaNoVisible);
        repository.save(inactivaVisible);

        List<PracticeService> gestion = repository.findAllByPracticeIdOrderByNameAsc(SEEDED_PRACTICE_ID);
        assertThat(gestion)
                .extracting(PracticeService::name)
                .containsExactly("A ActivoVisible", "B ActivoNoVisible", "C InactivaVisible");

        List<PracticeService> publico = repository.findPublicByPracticeId(SEEDED_PRACTICE_ID);
        assertThat(publico).extracting(PracticeService::name).containsExactly("A ActivoVisible");
    }

    @Test
    void leeLaPrestacionCompletaConTodosSusCampos() {
        PracticeService created = PracticeService.create(SEEDED_PRACTICE_ID, "Dolor", "Descripcion", 45, true);
        PracticeService saved = repository.save(created);

        PracticeService read = repository.findById(saved.id()).orElseThrow();

        assertThat(read.practiceId()).isEqualTo(SEEDED_PRACTICE_ID);
        assertThat(read.name()).isEqualTo("Dolor");
        assertThat(read.description()).isEqualTo("Descripcion");
        assertThat(read.durationMin()).isEqualTo(45);
        assertThat(read.isActive()).isTrue();
        assertThat(read.isPublicVisible()).isTrue();
        assertThat(read.createdAt()).isNotNull();
        assertThat(read.updatedAt()).isNotNull();
    }

    @Test
    void elRolDeAplicacionNoPuedeBorrarFisicamente() throws SQLException {
        PracticeService created = PracticeService.create(SEEDED_PRACTICE_ID, "Dolor", null, 45, true);
        repository.save(created);

        try (Connection connection = DATABASE.appConnection()) {
            assertThatThrownBy(() -> connection.prepareStatement("DELETE FROM service").executeUpdate())
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }
}
