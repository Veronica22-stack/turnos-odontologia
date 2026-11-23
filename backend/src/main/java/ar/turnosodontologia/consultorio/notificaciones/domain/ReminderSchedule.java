package ar.turnosodontologia.consultorio.notificaciones.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Calculo del instante del recordatorio (design D4): {@code startsAt - reminderHoursBefore}, solo si es futuro. */
public final class ReminderSchedule {

    private ReminderSchedule() {
    }

    /** Instante del recordatorio, o vacio si no es posterior al actual (el turno ya esta dentro de la ventana). */
    public static Optional<Instant> reminderAt(Instant startsAt, int reminderHoursBefore, Clock clock) {
        Instant at = startsAt.minus(Duration.ofHours(reminderHoursBefore));
        return at.isAfter(clock.instant()) ? Optional.of(at) : Optional.empty();
    }
}
