package ar.turnosodontologia.consultorio.agenda.application;

import ar.turnosodontologia.consultorio.agenda.domain.PracticeService;
import java.util.UUID;

/** Proyección mínima del catálogo público: solo lo que se publica (design D6). */
public record PublicServiceData(UUID id, String name, String description, int durationMin) {

    public static PublicServiceData from(PracticeService service) {
        return new PublicServiceData(service.id(), service.name(), service.description(), service.durationMin());
    }
}
