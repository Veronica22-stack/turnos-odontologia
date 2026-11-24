import { describe, expect, it } from 'vitest';
import { formatClinicalInstant } from './clinical-record-dates';

describe('formatClinicalInstant', () => {
  it('muestra el instante UTC en hora local de Buenos Aires (horario de invierno)', () => {
    expect(formatClinicalInstant('2026-06-20T15:00:00Z')).toBe('20/06/2026 12:00');
  });

  it('aplica el horario de verano argentino al mismo valor temporal', () => {
    expect(formatClinicalInstant('2026-01-20T13:00:00Z')).toBe('20/01/2026 10:00');
  });

  it('formatea la autoría, el motivo y el turno con la misma zona', () => {
    expect(formatClinicalInstant('2026-01-20T13:00:00Z', 'UTC-3')).toBe('20/01/2026 10:00 UTC-3');
    expect(formatClinicalInstant('2026-01-20T13:00:00Z', 'Motivo corregido')).toBe('20/01/2026 10:00 · Motivo corregido');
  });

  it('rechaza un instante inválido sin mostrarlo', () => {
    expect(() => formatClinicalInstant('2026-11-20')).toThrow('Instante inválido');
  });
});
