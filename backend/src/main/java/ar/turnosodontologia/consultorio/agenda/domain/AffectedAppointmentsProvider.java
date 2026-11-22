package ar.turnosodontologia.consultorio.agenda.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Puerto interno para que {@code agenda.application} obtenga turnos afectados sin depender de {@code agenda.api}
 * (la capa application no puede depender de la api de su propio módulo). El contrato público
 * {@code agenda.api.AffectedAppointments} es la vista para otros módulos.
 */
public interface AffectedAppointmentsProvider {

    List<AffectedAppointmentSlot> findWithin(UUID practiceId, Instant from, Instant to);
}
