import { describe, expect, it } from 'vitest';
import { failureCopy, fieldMessage, toSettingsFailure } from './settings-messages';

describe('settings-messages - mapeo por code sin texto técnico', () => {
  it('usa el `code` del problema y nunca el `title`/`detail` del servidor', () => {
    const problem = {
      kind: 'problem' as const,
      status: 400,
      code: 'VALIDATION_FAILED',
      errors: [{ field: 'name', code: 'NotBlank' }],
      title: 'TÉCNICO: Bean Validation',
      detail: 'TÉCNICO: property name may not be null',
    };

    const failure = toSettingsFailure(problem);

    expect(failure).toEqual({ kind: 'fields', errors: [{ field: 'name', code: 'NotBlank' }] });
    const copy = failureCopy(failure);
    expect(copy.title).not.toContain('TÉCNICO');
    expect(copy.text).not.toContain('TÉCNICO');
    expect(JSON.stringify(copy)).not.toContain('TÉCNICO');
  });

  it.each([
    ['DURATION_NOT_MULTIPLE_OF_GRANULARITY', 'duration'],
    ['INVALID_RANGE', 'range'],
    ['SCHEDULE_BLOCK_OVERLAP', 'conflict'],
    ['SERVICE_NOT_FOUND', 'not-found'],
    ['SCHEDULE_BLOCK_NOT_FOUND', 'not-found'],
    ['CSRF_REQUIRED', 'stale-page'],
    ['UNKNOWN_FUTURE_CODE', 'connection'],
  ])('%s se mapea a %s', (code, kind) => {
    const failure = toSettingsFailure({ kind: 'problem', status: 400, code, errors: [] });

    expect(failure.kind).toBe(kind);
    expect(failureCopy(failure).title.length).toBeGreaterThan(0);
    if (kind === 'conflict') {
      expect(failureCopy(failure).title).toBe('Conflicto: el rango se superpone con un bloqueo existente.');
    }
  });

  it('errores de transporte y campos sin code conocido tienen copy propia', () => {
    expect(toSettingsFailure({ kind: 'network' })).toEqual({ kind: 'connection' });
    expect(toSettingsFailure({ kind: 'timeout' })).toEqual({ kind: 'connection' });
    expect(toSettingsFailure({ kind: 'invalid-response' })).toEqual({ kind: 'connection' });
    expect(toSettingsFailure({ kind: 'unauthorized' })).toEqual({ kind: 'connection' });

    expect(fieldMessage('NotBlank', 'name')).toBe('Este campo es obligatorio.');
    expect(fieldMessage('Size', 'description')).toBe('El texto supera la longitud permitida.');
    expect(fieldMessage('CODIGO_NUEVO', 'horizonte')).toContain('horizonte');
    expect(fieldMessage('Min', 'name')).not.toContain('Min');
  });
});
