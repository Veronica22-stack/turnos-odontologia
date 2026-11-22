package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.domain.PracticeService;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeServiceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Adaptador JPA de las prestaciones (design D1); consultas derivadas usan los indices de V4. */
interface SpringDataPracticeServiceRepository extends JpaRepository<PracticeService, UUID>, PracticeServiceRepository {

    @Override
    Optional<PracticeService> findById(UUID id);

    @Override
    List<PracticeService> findAllByPracticeIdOrderByNameAsc(UUID practiceId);

    @Override
    @Query("""
            SELECT s FROM PracticeService s
            WHERE s.practiceId = :practiceId
            AND s.active = true
            AND s.publicVisible = true
            ORDER BY s.name ASC
            """)
    List<PracticeService> findPublicByPracticeId(UUID practiceId);
}
