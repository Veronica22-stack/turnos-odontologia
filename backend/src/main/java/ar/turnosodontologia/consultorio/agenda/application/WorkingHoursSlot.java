package ar.turnosodontologia.consultorio.agenda.application;

import java.time.LocalTime;

public record WorkingHoursSlot(Integer weekday, LocalTime startTime, LocalTime endTime) {
}
