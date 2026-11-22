package ar.turnosodontologia.consultorio.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkingHoursTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ID = UUID.fromString("01900000-0000-7000-8000-000000000002");

    @Test
    void creaTramoValidoConTodosLosDatos() {
        WorkingHours wh = WorkingHours.existing(ID, PRACTICE_ID, 1, LocalTime.of(9, 0), LocalTime.of(13, 0));

        assertThat(wh.id()).isEqualTo(ID);
        assertThat(wh.practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(wh.weekday()).isEqualTo(1);
        assertThat(wh.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(wh.endTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void creaTramoNuevoSinId() {
        WorkingHours wh = WorkingHours.create(PRACTICE_ID, 1, LocalTime.of(9, 0), LocalTime.of(13, 0));

        assertThat(wh.id()).isNull();
        assertThat(wh.practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(wh.weekday()).isEqualTo(1);
        assertThat(wh.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(wh.endTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8, -1, 10})
    void weekdayInvalidoLanzaExcepcion(int invalidDay) {
        assertThatThrownBy(() -> WorkingHours.create(PRACTICE_ID, invalidDay, LocalTime.of(9, 0), LocalTime.of(13, 0)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("weekday"));
    }

    @Test
    void endTimeAnteriorOIgualAStartTimeLanzaExcepcion() {
        assertThatThrownBy(() -> WorkingHours.create(PRACTICE_ID, 1, LocalTime.of(13, 0), LocalTime.of(9, 0)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("end_time"));

        assertThatThrownBy(() -> WorkingHours.create(PRACTICE_ID, 1, LocalTime.of(9, 0), LocalTime.of(9, 0)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("end_time"));
    }

    @Test
    void practiceIdNuloLanzaExcepcion() {
        assertThatThrownBy(() -> WorkingHours.create(null, 1, LocalTime.of(9, 0), LocalTime.of(13, 0)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("practice_id"));
    }

    @Test
    void timesNulosLanzaExcepcion() {
        assertThatThrownBy(() -> WorkingHours.create(PRACTICE_ID, 1, null, LocalTime.of(13, 0)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("start_time"));

        assertThatThrownBy(() -> WorkingHours.create(PRACTICE_ID, 1, LocalTime.of(9, 0), null))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("end_time"));
    }
}
