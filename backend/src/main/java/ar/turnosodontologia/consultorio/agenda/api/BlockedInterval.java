package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.UUID;

/** Resultado público de una consulta de rangos bloqueados; sin tipos de persistencia. */
public record BlockedInterval(UUID blockId, Instant startsAt, Instant endsAt) {
}
