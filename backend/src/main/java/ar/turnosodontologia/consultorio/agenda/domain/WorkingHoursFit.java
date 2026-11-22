package ar.turnosodontologia.consultorio.agenda.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Funcion pura (design D5, D22): decide si un rango de un turno cabe en un intervalo continuo de atencion del dia
 * local. Los tramos del mismo dia que se tocan ({@code fin == inicio del siguiente}) se fusionan; los separados por
 * un hueco no. Sin Spring ni acceso a datos.
 */
public final class WorkingHoursFit {

    private WorkingHoursFit() {
    }

    private record LocalSpan(LocalTime start, LocalTime end) {
    }

    /**
     * @param startsAt inicio del turno (UTC)
     * @param endsAt fin del turno (UTC)
     * @param zone zona horaria del consultorio
     * @param hours tramos de atencion del consultorio (de cualquier dia; se usan los del dia local del inicio)
     */
    public static boolean fits(Instant startsAt, Instant endsAt, ZoneId zone, Collection<WorkingHours> hours) {
        LocalDateTime start = LocalDateTime.ofInstant(startsAt, zone);
        LocalDateTime end = LocalDateTime.ofInstant(endsAt, zone);
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return false;
        }
        int weekday = start.getDayOfWeek().getValue();
        return mergeContiguous(hours, weekday).stream()
                .anyMatch(span -> !start.toLocalTime().isBefore(span.start())
                        && !end.toLocalTime().isAfter(span.end()));
    }

    private static List<LocalSpan> mergeContiguous(Collection<WorkingHours> hours, int weekday) {
        List<WorkingHours> sorted = hours.stream()
                .filter(slot -> slot.weekday() == weekday)
                .sorted(Comparator.comparing(WorkingHours::startTime))
                .toList();
        List<LocalSpan> merged = new ArrayList<>();
        for (WorkingHours slot : sorted) {
            int last = merged.size() - 1;
            if (last >= 0 && !slot.startTime().isAfter(merged.get(last).end())) {
                LocalTime end = slot.endTime().isAfter(merged.get(last).end()) ? slot.endTime() : merged.get(last).end();
                merged.set(last, new LocalSpan(merged.get(last).start(), end));
            } else {
                merged.add(new LocalSpan(slot.startTime(), slot.endTime()));
            }
        }
        return merged;
    }
}
