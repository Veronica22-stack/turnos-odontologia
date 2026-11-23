package ar.turnosodontologia.consultorio.agenda.api;

import java.util.Optional;
import java.util.UUID;

/** Puerta de solo lectura del catálogo para el motor de disponibilidad. */
public interface ServiceAvailabilityLookup {

    Optional<PublicService> findPublicService(UUID practiceId, UUID serviceId);

    boolean hasPublicServices(UUID practiceId);
}
