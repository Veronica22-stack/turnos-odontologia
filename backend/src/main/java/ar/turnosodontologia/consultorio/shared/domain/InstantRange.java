package ar.turnosodontologia.consultorio.shared.domain;

import java.time.Instant;

/** Rango semiabierto de instantes {@code [start, end)}. */
public record InstantRange(Instant start, Instant end) {

    public InstantRange {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("El inicio del rango debe ser anterior al fin");
        }
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }
}
