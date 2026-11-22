package ar.turnosodontologia.consultorio.agenda.domain;

import java.util.List;
import java.util.UUID;

public interface WorkingHoursRepository {

    List<WorkingHours> findByPracticeId(UUID practiceId);

    void deleteByPracticeId(UUID practiceId);

    <S extends WorkingHours> List<S> saveAll(Iterable<S> entities);
}
