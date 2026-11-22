package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.domain.Appointment;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAppointmentRepository extends JpaRepository<Appointment, UUID>, AppointmentRepository {

    @Override
    default List<Appointment> findOverlappingActive(UUID practiceId, Instant from, Instant to, UUID excludeId) {
        return excludeId == null
                ? activeOverlapping(practiceId, from, to)
                : activeOverlappingExcluding(practiceId, from, to, excludeId);
    }

    @Query("""
            select a from Appointment a
            where a.practiceId = :practiceId
              and a.status <> ar.turnosodontologia.consultorio.agenda.domain.AppointmentStatus.CANCELLED
              and a.startsAt < :to
              and a.endsAt > :from
            """)
    List<Appointment> activeOverlapping(@Param("practiceId") UUID practiceId,
            @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select a from Appointment a
            where a.practiceId = :practiceId
              and a.id <> :excludeId
              and a.status <> ar.turnosodontologia.consultorio.agenda.domain.AppointmentStatus.CANCELLED
              and a.startsAt < :to
              and a.endsAt > :from
            """)
    List<Appointment> activeOverlappingExcluding(@Param("practiceId") UUID practiceId,
            @Param("from") Instant from, @Param("to") Instant to, @Param("excludeId") UUID excludeId);

    @Override
    @Query("""
            select a from Appointment a
            where a.practiceId = :practiceId
              and a.status = ar.turnosodontologia.consultorio.agenda.domain.AppointmentStatus.CONFIRMED
              and a.startsAt < :to
              and a.endsAt > :from
            order by a.startsAt asc
            """)
    List<Appointment> findConfirmedWithin(@Param("practiceId") UUID practiceId,
            @Param("from") Instant from, @Param("to") Instant to);

    @Override
    @Query("""
            select a from Appointment a
            where a.practiceId = :practiceId
              and a.startsAt < :to
              and a.endsAt > :from
            order by a.startsAt asc
            """)
    List<Appointment> findInRange(@Param("practiceId") UUID practiceId,
            @Param("from") Instant from, @Param("to") Instant to);
}
