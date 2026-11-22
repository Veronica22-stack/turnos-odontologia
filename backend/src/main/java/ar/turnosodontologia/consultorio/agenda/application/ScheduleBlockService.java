package ar.turnosodontologia.consultorio.agenda.application;

import ar.turnosodontologia.consultorio.agenda.domain.AffectedAppointmentSlot;
import ar.turnosodontologia.consultorio.agenda.domain.AffectedAppointmentsProvider;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlock;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlockRepository;
import ar.turnosodontologia.consultorio.agenda.domain.TimeInterval;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleBlockService {

    private static final String OVERLAP_CODE = "SCHEDULE_BLOCK_OVERLAP";
    private static final String NOT_FOUND_CODE = "SCHEDULE_BLOCK_NOT_FOUND";

    private final ScheduleBlockRepository repository;
    private final InstallationPractice practice;
    private final Clock clock;
    private final Optional<AffectedAppointmentsProvider> appointments;

    public ScheduleBlockService(ScheduleBlockRepository repository, InstallationPractice practice, Clock clock,
            Optional<AffectedAppointmentsProvider> appointments) {
        this.repository = repository;
        this.practice = practice;
        this.clock = clock;
        this.appointments = appointments;
    }

    @Transactional(readOnly = true)
    public List<ScheduleBlockResult> list(Instant from, Instant to) {
        validateFilter(from, to);
        UUID practiceId = practice.id();
        List<ScheduleBlock> blocks = from == null
                ? repository.findAllByPracticeIdOrderByStartsAtAsc(practiceId)
                : repository.findOverlapping(practiceId, from, to);
        return blocks.stream()
                .sorted(Comparator.comparing(ScheduleBlock::startsAt))
                .map(this::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleBlockResult get(UUID id) {
        return toResult(requireOwned(id));
    }

    @Transactional
    public ScheduleBlockResult create(Instant startsAt, Instant endsAt, String reason) {
        UUID practiceId = practice.id();
        ensureNoOverlap(practiceId, startsAt, endsAt, null);
        ScheduleBlock block = ScheduleBlock.create(practiceId, startsAt, endsAt, reason, clock);
        return toResult(repository.save(block));
    }

    @Transactional
    public ScheduleBlockResult update(UUID id, Instant startsAt, Instant endsAt, String reason) {
        ScheduleBlock block = requireOwned(id);
        ensureNoOverlap(block.practiceId(), startsAt, endsAt, block.id());
        block.update(startsAt, endsAt, reason, clock);
        return toResult(repository.save(block));
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(requireOwned(id));
    }

    private ScheduleBlock requireOwned(UUID id) {
        UUID practiceId = practice.id();
        return repository.findById(id)
                .filter(block -> Objects.equals(block.practiceId(), practiceId))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_CODE));
    }

    private void ensureNoOverlap(UUID practiceId, Instant startsAt, Instant endsAt, UUID excludedId) {
        TimeInterval candidate = new TimeInterval(startsAt, endsAt);
        boolean overlaps = repository.findOverlapping(practiceId, startsAt, endsAt).stream()
                .filter(block -> !Objects.equals(block.id(), excludedId))
                .anyMatch(block -> candidate.intersects(new TimeInterval(block.startsAt(), block.endsAt())));
        if (overlaps) {
            throw new ConflictException(OVERLAP_CODE);
        }
    }

    private void validateFilter(Instant from, Instant to) {
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException("filtro requiere from y to juntos");
        }
        if (from != null && !to.isAfter(from)) {
            throw new IllegalArgumentException("to debe ser posterior a from");
        }
    }

    private ScheduleBlockResult toResult(ScheduleBlock block) {
        List<AffectedAppointmentSlot> affected = appointments
                .map(provider -> provider.findWithin(block.practiceId(), block.startsAt(), block.endsAt()))
                .map(List::copyOf)
                .orElseGet(List::of);
        return new ScheduleBlockResult(block.id(), block.practiceId(), block.startsAt(), block.endsAt(),
                block.reason(), block.createdAt(), block.updatedAt(), affected);
    }
}
