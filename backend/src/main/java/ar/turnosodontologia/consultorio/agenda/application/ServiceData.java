package ar.turnosodontologia.consultorio.agenda.application;

import ar.turnosodontologia.consultorio.agenda.domain.PracticeService;
import java.time.Instant;
import java.util.UUID;

public record ServiceData(
        UUID id,
        UUID practiceId,
        String name,
        String description,
        int durationMin,
        boolean active,
        boolean publicVisible,
        Instant createdAt,
        Instant updatedAt) {

    public static ServiceData from(PracticeService service) {
        return new ServiceData(
                service.id(),
                service.practiceId(),
                service.name(),
                service.description(),
                service.durationMin(),
                service.isActive(),
                service.isPublicVisible(),
                service.createdAt(),
                service.updatedAt());
    }
}
