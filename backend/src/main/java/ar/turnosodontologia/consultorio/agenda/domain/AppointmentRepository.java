package ar.turnosodontologia.consultorio.agenda.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de turnos; la implementacion Spring Data vive en infrastructure. */
public interface AppointmentRepository {

    <S extends Appointment> S save(S appointment);

    /** Guarda y fuerza el flush: una violacion de la exclusion de la base aflora aca (design D10). */
    <S extends Appointment> S saveAndFlush(S appointment);

    Optional<Appointment> findById(UUID id);

    /**
     * Turnos no cancelados del consultorio cuyo rango intersecta {@code [from, to)}, sin el turno {@code excludeId}
     * (puede ser {@code null}).
     */
    List<Appointment> findOverlappingActive(UUID practiceId, Instant from, Instant to, UUID excludeId);

    /** Turnos {@code CONFIRMED} del consultorio que intersectan {@code [from, to)}, ordenados por inicio. */
    List<Appointment> findConfirmedWithin(UUID practiceId, Instant from, Instant to);

    /** Turnos de cualquier estado del consultorio que intersectan {@code [from, to)}, ordenados por inicio. */
    List<Appointment> findInRange(UUID practiceId, Instant from, Instant to);
}
