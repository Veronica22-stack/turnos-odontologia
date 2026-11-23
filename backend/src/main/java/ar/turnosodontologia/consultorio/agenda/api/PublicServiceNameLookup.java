package ar.turnosodontologia.consultorio.agenda.api;

import java.util.Optional;
import java.util.UUID;

/** Nombre de una prestación pública para confirmaciones; sin otros datos del catálogo (C-14). */
public interface PublicServiceNameLookup {

    Optional<String> findName(UUID practiceId, UUID serviceId);
}
