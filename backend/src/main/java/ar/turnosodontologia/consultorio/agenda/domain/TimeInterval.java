package ar.turnosodontologia.consultorio.agenda.domain;

import java.time.Instant;

/** Intervalo semiabierto {@code [start, end)} en UTC, con solapamiento estricto. */
public record TimeInterval(Instant start, Instant end) {

    public TimeInterval {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start y end son requeridos");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end debe ser posterior a start");
        }
    }

    /** Dos intervalos se solapan si comparten algún instante; la adyacencia en el límite no cuenta. */
    public boolean intersects(TimeInterval other) {
        if (other == null) {
            throw new IllegalArgumentException("intervalo requerido");
        }
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
