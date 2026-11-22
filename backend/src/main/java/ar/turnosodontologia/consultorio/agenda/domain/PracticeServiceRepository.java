package ar.turnosodontologia.consultorio.agenda.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puertos de acceso a las prestaciones del catalogo de agenda (design D1). */
public interface PracticeServiceRepository {

    <S extends PracticeService> S save(S entity);

    Optional<PracticeService> findById(UUID id);

    /** Todas las prestaciones del consultorio (inactivas incluidas), ordenadas por nombre ascendente. */
    List<PracticeService> findAllByPracticeIdOrderByNameAsc(UUID practiceId);

    /** Solo las prestaciones publicas del consultorio, ordenadas por nombre ascendente. */
    List<PracticeService> findPublicByPracticeId(UUID practiceId);
}
