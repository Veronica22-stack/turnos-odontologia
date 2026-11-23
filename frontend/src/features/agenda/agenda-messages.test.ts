import { describe, expect, it } from 'vitest';
import { agendaErrorMessage, AGENDA_MESSAGES } from './agenda-messages';
import type { ApiError } from '../../core/http/errors';

/** Única fuente de copy de error de agenda: mensajes genéricos sin datos del turno ocupante. */

function problem(status: number, code: string): ApiError {
  return { kind: 'problem', status, code, errors: [] };
}

describe('agendaErrorMessage', () => {
  it.each([
    ['APPOINTMENT_SLOT_TAKEN', AGENDA_MESSAGES.slotTaken],
    ['APPOINTMENT_SLOT_BLOCKED', AGENDA_MESSAGES.blocked],
    ['OUTSIDE_WORKING_HOURS', AGENDA_MESSAGES.outsideHours],
    ['APPOINTMENT_IN_PAST', AGENDA_MESSAGES.inPast],
  ])('%s -> mensaje amigable del horario', (code, expected) => {
    expect(agendaErrorMessage(problem(409, code))).toBe(expected);
  });

  it.each([
    ['PATIENT_INACTIVE', 409],
    ['SERVICE_NOT_FOUND', 404],
    ['PATIENT_NOT_FOUND', 404],
    ['APPOINTMENT_ALREADY_STARTED', 422],
    ['INVALID_STATUS_TRANSITION', 409],
    ['VALIDATION_FAILED', 400],
    ['SOME_UNKNOWN_CODE', 500],
  ] as const)('%s -> mensaje genérico sin instantes ni datos del turno', (code, status) => {
    const message = agendaErrorMessage(problem(status, code));
    expect(message).toBe(AGENDA_MESSAGES.generic);
    expect(message).not.toContain('10:00');
    expect(message).not.toContain('10:30');
  });

  it.each([
    ['unauthorized', { kind: 'unauthorized' as const }],
    ['network', { kind: 'network' as const }],
    ['timeout', { kind: 'timeout' as const }],
    ['invalid-response', { kind: 'invalid-response' as const }],
  ])('%s -> mensaje genérico de conexión', (_kind, error) => {
    expect(agendaErrorMessage(error as ApiError)).toBe(AGENDA_MESSAGES.connection);
  });
});
