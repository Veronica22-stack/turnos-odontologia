import type { ApiError } from '../../core/http/errors';

/** Única fuente de copy de error de agenda: mensajes genéricos sin datos del turno ocupante. */

export const AGENDA_MESSAGES = {
  slotTaken: 'Ese horario ya está ocupado, eligí otro',
  blocked: 'El horario está bloqueado',
  outsideHours: 'Ese horario no está dentro del horario de atención',
  inPast: 'Turnos solo se pueden cargar para el futuro',
  connection: 'No se pudo conectar con la aplicación. Verificá tu conexión e intentá más tarde.',
  generic: 'No se pudo completar la operación. Revisá tu conexión e intentá de nuevo.',
} as const;

export function agendaErrorMessage(error: ApiError): string {
  if (error.kind !== 'problem') {
    return AGENDA_MESSAGES.connection;
  }
  switch (error.code) {
    case 'APPOINTMENT_SLOT_TAKEN':
      return AGENDA_MESSAGES.slotTaken;
    case 'APPOINTMENT_SLOT_BLOCKED':
      return AGENDA_MESSAGES.blocked;
    case 'OUTSIDE_WORKING_HOURS':
      return AGENDA_MESSAGES.outsideHours;
    case 'APPOINTMENT_IN_PAST':
      return AGENDA_MESSAGES.inPast;
    default:
      return AGENDA_MESSAGES.generic;
  }
}
