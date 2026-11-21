package ar.turnosodontologia.consultorio.shared.infrastructure;

import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Adaptador Spring Data del puerto {@link PracticeRepository}. */
interface SpringDataPracticeRepository extends JpaRepository<Practice, UUID>, PracticeRepository {
}
