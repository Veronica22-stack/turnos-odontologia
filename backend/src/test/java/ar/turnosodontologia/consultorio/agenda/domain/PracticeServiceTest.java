package ar.turnosodontologia.consultorio.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PracticeServiceTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    @Test
    void createNormalizaNombreYDescripcionYActivaPorDefecto() {
        PracticeService service = PracticeService.create(
                PRACTICE_ID, "  Odontologia integral  ", "  Limpieza y descalcificacion ", 45, false);

        assertThat(service.practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(service.name()).isEqualTo("Odontologia integral");
        assertThat(service.description()).isEqualTo("Limpieza y descalcificacion");
        assertThat(service.durationMin()).isEqualTo(45);
        assertThat(service.isActive()).isTrue();
        assertThat(service.isPublicVisible()).isFalse();
        assertThat(service.createdAt()).isNotNull();
        assertThat(service.updatedAt()).isNotNull();
    }

    @Test
    void createSinDescripcionDejaDescripcionNula() {
        PracticeService service = PracticeService.create(PRACTICE_ID, "Odontologia", null, 30, false);

        assertThat(service.description()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nombreVacioLanzaExcepcion(String invalidName) {
        assertThatThrownBy(() -> PracticeService.create(PRACTICE_ID, invalidName, null, 30, false))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("name"));
    }

    @Test
    void nombreSuperaElLimiteDe120Caracteres() {
        assertThatThrownBy(() -> PracticeService.create(PRACTICE_ID, "D".repeat(121), null, 30, false))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("name"));
    }

    @Test
    void descripcionSuperaElLimiteDe500Caracteres() {
        assertThatThrownBy(() -> PracticeService.create(PRACTICE_ID, "Dolor", "D".repeat(501), 30, false))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("description"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void durationMinNoPositivoLanzaExcepcion(int durationMin) {
        assertThatThrownBy(() -> PracticeService.create(PRACTICE_ID, "Dolor", null, durationMin, false))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("duration_min"));
    }

    @Test
    void practiceIdNuloLanzaExcepcion() {
        assertThatThrownBy(() -> PracticeService.create(null, "Dolor", null, 30, false))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("practice_id"));
    }

    @Test
    void deactivateConservaLaVisibilidadPublica() {
        PracticeService service = PracticeService.create(PRACTICE_ID, "Dolor", null, 30, true);

        service.deactivate();

        assertThat(service.isActive()).isFalse();
        assertThat(service.isPublicVisible()).isTrue();
    }

    @Test
    void activateReactivaYActualizaUpdatedAt() {
        Instant oldUpdatedAt = Instant.parse("2020-01-01T00:00:00Z");
        PracticeService service = PracticeService.existing(
                UUID.randomUUID(), PRACTICE_ID, "Dolor", null, 30, false, true,
                Instant.parse("2019-01-01T00:00:00Z"), oldUpdatedAt);

        service.activate();

        assertThat(service.isActive()).isTrue();
        assertThat(service.updatedAt()).isAfter(oldUpdatedAt);
    }

    @Test
    void updateCambiaDuracionYVisibilidad() {
        PracticeService service = PracticeService.create(PRACTICE_ID, "Dolor", null, 30, true);

        service.update("Dolor abultado", "Tomo un analgesico", 60, false, true);

        assertThat(service.name()).isEqualTo("Dolor abultado");
        assertThat(service.description()).isEqualTo("Tomo un analgesico");
        assertThat(service.durationMin()).isEqualTo(60);
        assertThat(service.isPublicVisible()).isFalse();
        assertThat(service.isActive()).isTrue();
        assertThat(service.updatedAt()).isNotNull();
    }
}
