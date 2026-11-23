package ar.turnosodontologia.consultorio.shared.infrastructure;

import ar.turnosodontologia.consultorio.shared.api.PracticeBookingSettings;
import ar.turnosodontologia.consultorio.shared.api.PracticeBookingSettingsLookup;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.application.PracticeSettingsData;
import ar.turnosodontologia.consultorio.shared.application.PracticeSettingsService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adaptador delgado: de `PracticeSettingsData` al contrato mínimo de reserva. */
@Component
public class PracticeBookingSettingsLookupAdapter implements PracticeBookingSettingsLookup {

    private final PracticeSettingsService settingsService;
    private final InstallationPractice installationPractice;

    public PracticeBookingSettingsLookupAdapter(
            PracticeSettingsService settingsService,
            InstallationPractice installationPractice) {
        this.settingsService = settingsService;
        this.installationPractice = installationPractice;
    }

    @Override
    public PracticeBookingSettings findBookingSettings(UUID practiceId) {
        if (!installationPractice.id().equals(practiceId)) {
            throw new IllegalStateException("El consultorio solicitado no es el de la instalación");
        }
        PracticeSettingsData data = settingsService.getSettings();
        return new PracticeBookingSettings(
                data.timezone(),
                data.slotGranularityMin(),
                data.minNoticeMin(),
                data.maxAdvanceDays());
    }
}
