package ar.turnosodontologia.consultorio.agenda.application;

import ar.turnosodontologia.consultorio.agenda.domain.WorkingHours;
import ar.turnosodontologia.consultorio.agenda.domain.WorkingHoursRepository;
import ar.turnosodontologia.consultorio.agenda.domain.WorkingHoursScheduleValidator;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkingHoursService {

    private final WorkingHoursRepository workingHoursRepository;
    private final InstallationPractice installationPractice;

    public WorkingHoursService(
            WorkingHoursRepository workingHoursRepository,
            InstallationPractice installationPractice) {
        this.workingHoursRepository = workingHoursRepository;
        this.installationPractice = installationPractice;
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursSlot> getWorkingHours() {
        UUID practiceId = installationPractice.id();
        return workingHoursRepository.findByPracticeId(practiceId).stream()
                .sorted(Comparator.comparing(WorkingHours::weekday).thenComparing(WorkingHours::startTime))
                .map(wh -> new WorkingHoursSlot((int) wh.weekday(), wh.startTime(), wh.endTime()))
                .toList();
    }

    @Transactional
    public List<WorkingHoursSlot> replaceWorkingHours(List<WorkingHoursSlot> slots) {
        UUID practiceId = installationPractice.id();
        List<WorkingHoursSlot> safeSlots = slots != null ? slots : List.of();

        List<WorkingHours> domainEntities = safeSlots.stream()
                .map(s -> WorkingHours.create(practiceId, s.weekday(), s.startTime(), s.endTime()))
                .toList();

        WorkingHoursScheduleValidator.validateNoOverlaps(domainEntities);

        workingHoursRepository.deleteByPracticeId(practiceId);
        workingHoursRepository.saveAll(domainEntities);

        return domainEntities.stream()
                .sorted(Comparator.comparing(WorkingHours::weekday).thenComparing(WorkingHours::startTime))
                .map(wh -> new WorkingHoursSlot((int) wh.weekday(), wh.startTime(), wh.endTime()))
                .toList();
    }
}
