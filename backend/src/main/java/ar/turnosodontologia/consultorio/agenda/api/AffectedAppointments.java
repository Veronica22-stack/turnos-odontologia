package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contrato público para consultar turnos comprendidos en un rango bloqueado.
 * Hasta C-11 no hay implementación registrada: los consumidores reciben {@code Optional.empty()}.
 */
public interface AffectedAppointments {

    List<AffectedAppointment> findWithin(UUID practiceId, Instant from, Instant to);
}
