package ar.turnosodontologia.consultorio.shared.api;

import java.util.UUID;

/** Respuesta con la configuracion y parametros actuales del consultorio. */
public record PracticeSettingsResponse(
        UUID id,
        String name,
        String timezone,
        String contactEmail,
        String contactPhone,
        String address,
        int slotGranularityMin,
        int minNoticeMin,
        int maxAdvanceDays,
        int reminderHoursBefore
) {}
