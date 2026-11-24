import { describe, expect, it } from 'vitest';
import type { ApiError } from '../../core/http/errors';
import { clinicalFailureCopy, toClinicalFailure, type ClinicalFailure } from './clinical-record-messages';

function problem(status: number, code: string, detail = 'Dato clínico secreto'): ApiError {
  // El contrato de ApiError no expone title/detail; un servidor puede enviarlos igualmente.
  return {
    kind: 'problem', status, code, errors: [{ field: 'notas', code: 'INVALID' }],
    title: 'Título con dato clínico', detail,
  } as ApiError;
}

describe('toClinicalFailure', () => {
  it('mapea códigos de validación, paciente, entrada, conflicto y reglas clínicas', () => {
    const cases: ReadonlyArray<readonly [ApiError, string]> = [
      [problem(400, 'VALIDATION_FAILED'), 'validation'],
      [problem(404, 'PATIENT_NOT_FOUND'), 'patient-not-found'],
      [problem(409, 'PATIENT_INACTIVE'), 'patient-inactive'],
      [problem(404, 'CLINICAL_ENTRY_NOT_FOUND'), 'entry-not-found'],
      [problem(409, 'CLINICAL_ENTRY_ALREADY_CORRECTED'), 'already-corrected'],
      [problem(409, 'CLINICAL_ENTRY_NOT_CURRENT'), 'not-current'],
      [problem(409, 'CLINICAL_RECORD_CONCURRENT_WRITE'), 'concurrent-write'],
      [problem(422, 'EVOLUTION_APPOINTMENT_NOT_STARTED'), 'appointment-not-started'],
      [problem(422, 'EVOLUTION_APPOINTMENT_NOT_ATTENDED'), 'appointment-not-attended'],
      [problem(404, 'EVOLUTION_APPOINTMENT_NOT_FOUND'), 'appointment-not-found'],
      [problem(422, 'ODONTOGRAM_SURFACE_NOT_ALLOWED'), 'surface-not-allowed'],
    ];
    for (const [error, expected] of cases) {
      expect(toClinicalFailure(error)).toEqual({ kind: expected });
    }
  });

  it('mapea fallos de red y respuestas desconocidas sin incluir contenido del error', () => {
    expect(toClinicalFailure({ kind: 'network' })).toEqual({ kind: 'connection' });
    expect(toClinicalFailure({ kind: 'unauthorized' })).toEqual({ kind: 'unauthorized' });
    expect(toClinicalFailure(problem(500, 'UNEXPECTED_RESPONSE'))).toEqual({ kind: 'connection' });
  });
});

describe('clinicalFailureCopy', () => {
  it('devuelve textos propios que no repiten title, detail ni el valor rechazado', () => {
    const failure: ClinicalFailure = { kind: 'validation' };
    const copy = clinicalFailureCopy(failure);

    expect(copy.text.length).toBeGreaterThan(10);
    expect(copy.text).not.toContain('Dato clínico secreto');
    expect(copy.text).not.toContain('Título con dato clínico');
    expect(copy.text).not.toContain('notas');
  });

  it('invita a recargar ante conflictos y no afirma que se guardó', () => {
    expect(clinicalFailureCopy({ kind: 'already-corrected' }).text).toContain('Recargá');
    expect(clinicalFailureCopy({ kind: 'not-current' }).text).toContain('Recargá');
    expect(clinicalFailureCopy({ kind: 'concurrent-write' }).text).toContain('Recargá');
    expect(clinicalFailureCopy({ kind: 'connection' }).text).not.toContain('guardó');
  });
});
