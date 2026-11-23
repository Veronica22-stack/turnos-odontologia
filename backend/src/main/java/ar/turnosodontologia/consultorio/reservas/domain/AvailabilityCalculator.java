package ar.turnosodontologia.consultorio.reservas.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Cálculo puro de slots libres sobre intervalos UTC semiabiertos. */
public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    public static List<Interval> availableSlots(
            List<Interval> working,
            List<Interval> occupied,
            Duration duration,
            Duration granularity) {
        requirePositive(duration, "duration");
        requirePositive(granularity, "granularity");
        List<Interval> freeRanges = subtractOccupied(merge(working), merge(occupied));
        return enumerateStarts(freeRanges, duration, granularity);
    }

    static List<Interval> subtractOccupied(List<Interval> working, List<Interval> occupied) {
        List<Interval> free = new ArrayList<>();
        for (Interval tramo : working) {
            Instant cursor = tramo.start();
            for (Interval bloqueo : occupied) {
                if (!tramo.overlaps(bloqueo)) {
                    continue;
                }
                if (cursor.isBefore(bloqueo.start())) {
                    free.add(new Interval(cursor, bloqueo.start()));
                }
                if (bloqueo.end().isAfter(cursor)) {
                    cursor = bloqueo.end();
                }
                if (!cursor.isBefore(tramo.end())) {
                    break;
                }
            }
            if (cursor.isBefore(tramo.end())) {
                free.add(new Interval(cursor, tramo.end()));
            }
        }
        return free;
    }

    static List<Interval> enumerateStarts(List<Interval> freeRanges, Duration duration, Duration granularity) {
        List<Interval> slots = new ArrayList<>();
        for (Interval free : freeRanges) {
            Instant candidate = free.start();
            while (candidate.isBefore(free.end())) {
                Instant end = candidate.plus(duration);
                if (end.isAfter(free.end())) {
                    break;
                }
                slots.add(new Interval(candidate, end));
                candidate = candidate.plus(granularity);
            }
        }
        return slots;
    }

    private static List<Interval> merge(List<Interval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }
        List<Interval> sorted = intervals.stream()
                .sorted(Comparator.comparing(Interval::start))
                .toList();
        List<Interval> merged = new ArrayList<>();
        Interval current = sorted.getFirst();
        for (int i = 1; i < sorted.size(); i++) {
            Interval next = sorted.get(i);
            if (next.start().isAfter(current.end())) {
                merged.add(current);
                current = next;
            } else if (next.end().isAfter(current.end())) {
                current = new Interval(current.start(), next.end());
            }
        }
        merged.add(current);
        return merged;
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " debe ser positiva");
        }
    }
}
