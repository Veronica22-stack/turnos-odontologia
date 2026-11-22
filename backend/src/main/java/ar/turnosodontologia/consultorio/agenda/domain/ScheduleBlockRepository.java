package ar.turnosodontologia.consultorio.agenda.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de bloqueos; la implementación Spring Data vive en infrastructure. */
public interface ScheduleBlockRepository {

    ScheduleBlock save(ScheduleBlock block);

    Optional<ScheduleBlock> findById(UUID id);

    List<ScheduleBlock> findAllByPracticeIdOrderByStartsAtAsc(UUID practiceId);

    /** Bloqueos con intersección no vacía contra {@code [from, to)} en el consultorio. */
    List<ScheduleBlock> findOverlapping(UUID practiceId, Instant from, Instant to);

    void delete(ScheduleBlock block);
}
