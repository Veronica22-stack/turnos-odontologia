package ar.turnosodontologia.consultorio.reservas.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Consulta de si un inicio está ofrecido al público, implementada sobre el motor de disponibilidad (C-12). */
public interface PublicSlotLookup {

    boolean isOffered(UUID practiceId, UUID serviceId, LocalDate date, Instant startsAt);
}
