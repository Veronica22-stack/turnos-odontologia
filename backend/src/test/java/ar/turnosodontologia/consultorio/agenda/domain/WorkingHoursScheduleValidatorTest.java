package ar.turnosodontologia.consultorio.agenda.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkingHoursScheduleValidatorTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    @Test
    void tramosSinSolapamientoMismoDiaPasanValidacion() {
        List<WorkingHours> tramos = List.of(
                WorkingHours.create(PRACTICE_ID, 1, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                WorkingHours.create(PRACTICE_ID, 1, LocalTime.of(14, 0), LocalTime.of(18, 0)),
                WorkingHours.create(PRACTICE_ID, 2, LocalTime.of(9, 0), LocalTime.of(17, 0)));

        assertThatCode(() -> WorkingHoursScheduleValidator.validateNoOverlaps(tramos))
                .doesNotThrowAnyException();
    }

    @Test
    void tramosSolapadosMismoDiaLanzanExcepcion() {
        List<WorkingHours> tramos = List.of(
                WorkingHours.create(PRACTICE_ID, 1, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                WorkingHours.create(PRACTICE_ID, 1, LocalTime.of(12, 0), LocalTime.of(16, 0)));

        assertThatThrownBy(() -> WorkingHoursScheduleValidator.validateNoOverlaps(tramos))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void tramosIdenticosMismoDiaLanzanExcepcion() {
        List<WorkingHours> tramos = List.of(
                WorkingHours.create(PRACTICE_ID, 1, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                WorkingHours.create(PRACTICE_ID, 1, LocalTime.of(9, 0), LocalTime.of(13, 0)));

        assertThatThrownBy(() -> WorkingHoursScheduleValidator.validateNoOverlaps(tramos))
                .isInstanceOf(ValidationException.class);
    }
}
