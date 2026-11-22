package ar.turnosodontologia.consultorio.shared.infrastructure;

import ar.turnosodontologia.consultorio.shared.api.PracticeSettingsRequest;
import ar.turnosodontologia.consultorio.shared.api.PracticeSettingsResponse;
import ar.turnosodontologia.consultorio.shared.application.PracticeSettingsData;
import ar.turnosodontologia.consultorio.shared.application.PracticeSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/practice/settings")
public class PracticeSettingsController {

    private final PracticeSettingsService practiceSettingsService;

    public PracticeSettingsController(PracticeSettingsService practiceSettingsService) {
        this.practiceSettingsService = practiceSettingsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<PracticeSettingsResponse> getSettings() {
        PracticeSettingsData data = practiceSettingsService.getSettings();
        return ResponseEntity.ok(toResponse(data));
    }

    @PutMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<PracticeSettingsResponse> updateSettings(
            @Valid @RequestBody PracticeSettingsRequest request) {
        PracticeSettingsData input = new PracticeSettingsData(
                null,
                request.name(),
                request.timezone(),
                request.contactEmail(),
                request.contactPhone(),
                request.address(),
                request.slotGranularityMin(),
                request.minNoticeMin(),
                request.maxAdvanceDays(),
                request.reminderHoursBefore());

        PracticeSettingsData saved = practiceSettingsService.updateSettings(input);
        return ResponseEntity.ok(toResponse(saved));
    }

    private static PracticeSettingsResponse toResponse(PracticeSettingsData data) {
        return new PracticeSettingsResponse(
                data.id(),
                data.name(),
                data.timezone(),
                data.contactEmail(),
                data.contactPhone(),
                data.address(),
                data.slotGranularityMin(),
                data.minNoticeMin(),
                data.maxAdvanceDays(),
                data.reminderHoursBefore());
    }
}
