package ar.turnosodontologia.consultorio.notificaciones.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Instante del recordatorio: {@code startsAt - reminderHoursBefore}, solo si es posterior al actual. */
class ReminderScheduleTest {

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void veinticuatroHorasAntesDelInicio() {
        Optional<Instant> at = ReminderSchedule.reminderAt(Instant.parse("2026-12-10T13:00:00Z"), 24, CLOCK);

        assertThat(at).contains(Instant.parse("2026-12-09T13:00:00Z"));
    }

    @Test
    void siElInstanteCoincideConAhoraNoHayRecordatorio() {
        Optional<Instant> at = ReminderSchedule.reminderAt(NOW.plusSeconds(24 * 3600), 24, CLOCK);

        assertThat(at).isEmpty();
    }

    @Test
    void siElTurnoEstaDentroDeLaVentanaNoHayRecordatorio() {
        Optional<Instant> at = ReminderSchedule.reminderAt(NOW.plusSeconds(3 * 3600), 24, CLOCK);

        assertThat(at).isEmpty();
    }

    @Test
    void unaHoraYUnaSemanaDeAnticipacion() {
        Instant startsAt = Instant.parse("2026-12-20T13:00:00Z");

        assertThat(ReminderSchedule.reminderAt(startsAt, 1, CLOCK)).contains(Instant.parse("2026-12-20T12:00:00Z"));
        assertThat(ReminderSchedule.reminderAt(startsAt, 168, CLOCK)).contains(Instant.parse("2026-12-13T13:00:00Z"));
    }
}
