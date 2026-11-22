package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.domain.WorkingHours;
import ar.turnosodontologia.consultorio.agenda.domain.WorkingHoursRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface SpringDataWorkingHoursRepository extends JpaRepository<WorkingHours, UUID>, WorkingHoursRepository {

    @Override
    @Transactional
    void deleteByPracticeId(UUID practiceId);
}

