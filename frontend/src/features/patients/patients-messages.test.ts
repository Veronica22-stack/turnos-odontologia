import { describe, expect, it } from 'vitest';
import type { ApiError } from '../../core/http/errors';
import { MESSAGES, failureCopy, toPatientsFailure } from './patients-messages';

const problem = (status: number, code: string, errors: readonly { field: string; code: string }[] = []): ApiError => ({
  kind: 'problem',
  status,
  code,
  errors,
});

describe('patients-messages - clasificación y textos propios (D4)', () => {
  it('clasifica cada fallo del contrato D2', () => {
    expect(toPatientsFailure(problem(403, 'FORBIDDEN'))).toEqual({ kind: 'forbidden' });
    expect(toPatientsFailure(problem(404, 'PATIENT_NOT_FOUND'))).toEqual({ kind: 'not-found' });
    expect(toPatientsFailure(problem(404, 'OTRO_CODIGO'))).toEqual({ kind: 'not-found' });
    expect(toPatientsFailure(problem(403, 'CSRF_REQUIRED'))).toEqual({ kind: 'stale-page' });
    expect(toPatientsFailure(problem(400, 'VALIDATION_FAILED', [{ field: 'phone', code: 'NotBlank' }]))).toEqual({
      kind: 'invalid',
      errors: [{ field: 'phone', code: 'NotBlank' }],
    });
    expect(toPatientsFailure({ kind: 'network' })).toEqual({ kind: 'connection' });
    expect(toPatientsFailure({ kind: 'timeout' })).toEqual({ kind: 'connection' });
    expect(toPatientsFailure({ kind: 'invalid-response' })).toEqual({ kind: 'connection' });
    expect(toPatientsFailure(problem(500, 'INTERNAL_ERROR'))).toEqual({ kind: 'connection' });
  });

  it('nunca copia title/detail del servidor y cubre baja, revisión y estados vacíos', () => {
    const sensitive = {
      kind: 'problem' as const,
      status: 403,
      code: 'FORBIDDEN',
      errors: [],
      title: 'Julieta Simulada 30.456.789',
      detail: 'julieta@ejemplo.invalid 11 5555 0001',
    };
    const copy = failureCopy(toPatientsFailure(sensitive), 'save');
    const all = [copy, MESSAGES.deactivateError, MESSAGES.deactivated, MESSAGES.review, MESSAGES.noPatients, MESSAGES.noResults]
      .map((item) => `${item.title} ${item.text}`)
      .join(' ');

    expect(copy.title).toBe('No tenés permiso para esta acción.');
    expect(all).not.toContain('Simulada');
    expect(all).not.toContain('30.456.789');
    expect(all).not.toContain('julieta@');
    expect(all).not.toContain('55');
    expect(MESSAGES.deactivateError.text).toContain('no sabemos si se completó');
    expect(MESSAGES.deactivateError.text).not.toMatch(/se dio de baja|se completó correctamente/i);
    expect(MESSAGES.review.text).toContain('no reemplazaron');
    expect(failureCopy({ kind: 'stale-page' }, 'deactivate').title).toContain('desactualizada');
  });

  it('usa el texto aprobado del mockup para el estado sin resultados', () => {
    expect(MESSAGES.noResults.title).toBe('No encontramos pacientes con ese criterio');
    expect(MESSAGES.noResults.text).toBe('Probá buscar por DNI o teléfono, o revisá la ortografía del nombre.');
  });
});
