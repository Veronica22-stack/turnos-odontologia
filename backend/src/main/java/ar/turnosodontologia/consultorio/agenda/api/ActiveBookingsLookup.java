package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Conteo de turnos futuros confirmados por contacto, sin exponer datos personales (C-14, RN-RS-08). */
public interface ActiveBookingsLookup {

    long countActiveFuture(UUID practiceId, List<UUID> patientIds, Instant from);
}
