package ar.turnosodontologia.consultorio.agenda.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta de solo lectura de un turno para otros modulos (notificaciones, design D10): estado vigente, paciente,
 * instantes y nombre actual de la prestacion, sin notas ni datos personales. Devuelve vacio si el turno no existe en
 * el consultorio de la instalacion.
 */
public interface AppointmentNotificationLookup {

    Optional<AppointmentNotificationView> find(UUID appointmentId);
}
