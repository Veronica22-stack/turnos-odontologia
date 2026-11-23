package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.WeeklyInterval;
import ar.turnosodontologia.consultorio.agenda.api.WorkingHoursLookup;
import ar.turnosodontologia.consultorio.agenda.application.WorkingHoursService;
import ar.turnosodontologia.consultorio.agenda.application.WorkingHoursSlot;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adaptador delgado: traduce `WorkingHoursSlot` al contrato propio de `agenda.api`. */
@Component
public class WorkingHoursLookupAdapter implements WorkingHoursLookup {

    private final WorkingHoursService workingHoursService;

    public WorkingHoursLookupAdapter(WorkingHoursService workingHoursService) {
        this.workingHoursService = workingHoursService;
    }

    @Override
    public List<WeeklyInterval> findWeeklyIntervals(UUID practiceId) {
        return workingHoursService.getWorkingHours().stream()
                .map(this::toWeeklyInterval)
                .toList();
    }

    private WeeklyInterval toWeeklyInterval(WorkingHoursSlot slot) {
        return new WeeklyInterval(slot.weekday(), slot.startTime(), slot.endTime());
    }
}
