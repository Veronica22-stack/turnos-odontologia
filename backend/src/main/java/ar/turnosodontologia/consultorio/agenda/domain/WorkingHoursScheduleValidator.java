package ar.turnosodontologia.consultorio.agenda.domain;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class WorkingHoursScheduleValidator {

    private WorkingHoursScheduleValidator() {}

    public static void validateNoOverlaps(List<WorkingHours> workingHours) {
        if (workingHours == null || workingHours.isEmpty()) {
            return;
        }

        Map<Integer, List<WorkingHours>> byDay = workingHours.stream()
                .collect(Collectors.groupingBy(WorkingHours::weekday));

        for (Map.Entry<Integer, List<WorkingHours>> entry : byDay.entrySet()) {
            List<WorkingHours> daySlots = entry.getValue().stream()
                    .sorted(Comparator.comparing(WorkingHours::startTime))
                    .toList();

            for (int i = 0; i < daySlots.size() - 1; i++) {
                WorkingHours current = daySlots.get(i);
                WorkingHours next = daySlots.get(i + 1);

                // Solapamiento si next.startTime < current.endTime
                if (next.startTime().isBefore(current.endTime())) {
                    throw new ValidationException(
                            "working_hours",
                            String.format("Solapamiento de horarios en el día %d: [%s - %s] y [%s - %s]",
                                    entry.getKey(),
                                    current.startTime(),
                                    current.endTime(),
                                    next.startTime(),
                                    next.endTime()));
                }
            }
        }
    }
}
