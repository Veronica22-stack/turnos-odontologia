package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlock;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlockRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataScheduleBlockRepository extends JpaRepository<ScheduleBlock, UUID>, ScheduleBlockRepository {

    @Override
    @Query("""
            select b from ScheduleBlock b
            where b.practiceId = :practiceId
              and b.startsAt < :to
              and b.endsAt > :from
            """)
    List<ScheduleBlock> findOverlapping(@Param("practiceId") UUID practiceId,
            @Param("from") Instant from, @Param("to") Instant to);
}
