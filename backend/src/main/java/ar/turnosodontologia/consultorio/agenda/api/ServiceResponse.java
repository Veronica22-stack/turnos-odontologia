package ar.turnosodontologia.consultorio.agenda.api;

import ar.turnosodontologia.consultorio.agenda.application.ServiceData;
import java.util.UUID;

/** DTO de salida del CRUD de gestión: sin entidades JPA ni `practice_id` (design D5/D6). */
public record ServiceResponse(
        UUID id,
        String name,
        String description,
        int durationMin,
        boolean active,
        boolean publicVisible) {

    public static ServiceResponse from(ServiceData data) {
        return new ServiceResponse(
                data.id(), data.name(), data.description(), data.durationMin(), data.active(), data.publicVisible());
    }
}
