package ar.turnosodontologia.consultorio.reservas.domain;

import java.time.Instant;
import java.util.Objects;

/** Intervalo semiabierto {@code [inicio, fin)} en UTC. */
public record Interval(Instant start, Instant end) {

    public Interval {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("El fin del intervalo debe ser posterior al inicio");
        }
    }

    public boolean overlaps(Interval other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
