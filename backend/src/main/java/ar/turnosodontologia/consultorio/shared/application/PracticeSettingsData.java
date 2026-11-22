package ar.turnosodontologia.consultorio.shared.application;

import java.util.UUID;

public record PracticeSettingsData(
        UUID id,
        String name,
        String timezone,
        String contactEmail,
        String contactPhone,
        String address,
        int slotGranularityMin,
        int minNoticeMin,
        int maxAdvanceDays,
        int reminderHoursBefore) {
}
