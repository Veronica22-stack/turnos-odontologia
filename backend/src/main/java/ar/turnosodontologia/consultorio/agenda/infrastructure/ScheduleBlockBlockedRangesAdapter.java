package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.BlockedInterval;
import ar.turnosodontologia.consultorio.agenda.api.BlockedRanges;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlock;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlockRepository;
import ar.turnosodontologia.consultorio.agenda.domain.TimeInterval;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implementación de {@link BlockedRanges} sobre los bloqueos persistidos por agenda. */
@Component
final class ScheduleBlockBlockedRangesAdapter implements BlockedRanges {

    private final ScheduleBlockRepository repository;

    ScheduleBlockBlockedRangesAdapter(ScheduleBlockRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<BlockedInterval> findOverlapping(UUID practiceId, Instant from, Instant to) {
        TimeInterval requested = new TimeInterval(from, to);
        return repository.findOverlapping(practiceId, from, to).stream()
                .filter(block -> requested.intersects(new TimeInterval(block.startsAt(), block.endsAt())))
                .map(this::toInterval)
                .toList();
    }

    private BlockedInterval toInterval(ScheduleBlock block) {
        return new BlockedInterval(block.id(), block.startsAt(), block.endsAt());
    }
}
