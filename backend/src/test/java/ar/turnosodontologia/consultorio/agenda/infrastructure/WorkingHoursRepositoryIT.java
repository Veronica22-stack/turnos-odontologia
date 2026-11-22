package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.agenda.domain.WorkingHours;
import ar.turnosodontologia.consultorio.agenda.domain.WorkingHoursRepository;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkingHoursRepositoryIT extends AbstractIntegrationTest {

    private static final UUID SEEDED_PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    private final WorkingHoursRepository workingHoursRepository;

    WorkingHoursRepositoryIT(WorkingHoursRepository workingHoursRepository) {
        this.workingHoursRepository = workingHoursRepository;
    }

    @Test
    void guardaYRecuperaTramosPorPracticeId() {
        WorkingHours wh1 = WorkingHours.create(SEEDED_PRACTICE_ID, 1, LocalTime.of(8, 0), LocalTime.of(12, 0));
        WorkingHours wh2 = WorkingHours.create(SEEDED_PRACTICE_ID, 1, LocalTime.of(13, 0), LocalTime.of(17, 0));

        workingHoursRepository.saveAll(List.of(wh1, wh2));

        List<WorkingHours> recovered = workingHoursRepository.findByPracticeId(SEEDED_PRACTICE_ID);
        assertThat(recovered).hasSizeGreaterThanOrEqualTo(2);

        workingHoursRepository.deleteByPracticeId(SEEDED_PRACTICE_ID);
        assertThat(workingHoursRepository.findByPracticeId(SEEDED_PRACTICE_ID)).isEmpty();
    }
}
