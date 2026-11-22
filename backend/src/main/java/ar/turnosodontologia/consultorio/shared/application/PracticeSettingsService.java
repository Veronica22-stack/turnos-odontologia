package ar.turnosodontologia.consultorio.shared.application;

import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticeSettingsService {

    private final PracticeRepository practiceRepository;
    private final InstallationPractice installationPractice;

    public PracticeSettingsService(
            PracticeRepository practiceRepository,
            InstallationPractice installationPractice) {
        this.practiceRepository = practiceRepository;
        this.installationPractice = installationPractice;
    }

    @Transactional(readOnly = true)
    public PracticeSettingsData getSettings() {
        Practice practice = getPractice();
        return toData(practice);
    }

    @Transactional
    public PracticeSettingsData updateSettings(PracticeSettingsData data) {
        Practice practice = getPractice();

        practice.updateSettings(
                data.name(),
                data.timezone(),
                data.contactEmail(),
                data.contactPhone(),
                data.address(),
                data.slotGranularityMin(),
                data.minNoticeMin(),
                data.maxAdvanceDays(),
                data.reminderHoursBefore());

        Practice saved = practiceRepository.save(practice);
        return toData(saved);
    }

    private Practice getPractice() {
        UUID practiceId = installationPractice.id();
        return practiceRepository.findById(practiceId)
                .orElseThrow(() -> new NotFoundException("PRACTICE_NOT_FOUND"));
    }

    private static PracticeSettingsData toData(Practice p) {
        return new PracticeSettingsData(
                p.id(),
                p.name(),
                p.timezone(),
                p.contactEmail(),
                p.contactPhone(),
                p.address(),
                p.slotGranularityMin(),
                p.minNoticeMin(),
                p.maxAdvanceDays(),
                p.reminderHoursBefore());
    }
}
