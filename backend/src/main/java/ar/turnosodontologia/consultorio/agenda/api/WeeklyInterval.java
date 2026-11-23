package ar.turnosodontologia.consultorio.agenda.api;

import java.time.LocalTime;

/** Tramo semanal de atención, con tipos del JDK y sin entidades de persistencia. */
public record WeeklyInterval(int weekday, LocalTime startTime, LocalTime endTime) {
}
