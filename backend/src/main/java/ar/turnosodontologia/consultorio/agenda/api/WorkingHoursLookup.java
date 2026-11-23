package ar.turnosodontologia.consultorio.agenda.api;

import java.util.List;
import java.util.UUID;

/** Puerta de solo lectura de los horarios de atención; contrato estable para otros módulos. */
public interface WorkingHoursLookup {

    List<WeeklyInterval> findWeeklyIntervals(UUID practiceId);
}
