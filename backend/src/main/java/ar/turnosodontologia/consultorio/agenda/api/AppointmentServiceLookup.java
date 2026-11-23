package ar.turnosodontologia.consultorio.agenda.api;

import java.util.Optional;
import java.util.UUID;

/** Identificador de la prestación de un turno, para validar disponibilidad desde la reserva pública (C-15). */
public interface AppointmentServiceLookup {

    Optional<UUID> findServiceId(UUID appointmentId);
}
