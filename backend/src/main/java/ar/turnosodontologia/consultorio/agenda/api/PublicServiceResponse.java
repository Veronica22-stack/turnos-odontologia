package ar.turnosodontologia.consultorio.agenda.api;

import ar.turnosodontologia.consultorio.agenda.application.PublicServiceData;
import java.util.UUID;

/** DTO mínimo del catálogo público: solo lo que se publica (design D6). */
public record PublicServiceResponse(UUID id, String name, String description, int durationMin) {

    public static PublicServiceResponse from(PublicServiceData data) {
        return new PublicServiceResponse(data.id(), data.name(), data.description(), data.durationMin());
    }
}
