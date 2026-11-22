package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrato público para consultar los bloqueos que intersectan un intervalo semiabierto. */
public interface BlockedRanges {

    List<BlockedInterval> findOverlapping(UUID practiceId, Instant from, Instant to);
}
