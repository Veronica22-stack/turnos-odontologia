import { describe, expect, it, vi } from 'vitest';
import { validateBlock, validatePracticeSettings, validateService, validateWorkingHours } from './validation';

const PRACTICE = {
  name: 'Consultorio Villa Crespo',
  timezone: 'America/Argentina/Buenos_Aires',
  contactEmail: 'recepcion@villacrespo.example',
  contactPhone: '+54 11 4471 0916',
  address: 'Av. Corrientes 3954',
  slotGranularityMin: 15,
  minNoticeMin: 120,
  maxAdvanceDays: 30,
  reminderHoursBefore: 24,
};

const SERVICE = { name: 'Limpieza', description: 'Sesión de limpieza', durationMin: 45, publicVisible: true };
const BLOCK = {
  startsAt: '2026-03-23T12:00:00Z',
  endsAt: '2026-03-23T14:00:00Z',
  reason: 'Mantenimiento',
};

describe('validation - casos felices', () => {
  it('acepta duración 45 múltiplo de 15', () => {
    expect(validateService(SERVICE, 15)).toEqual({ ok: true });
  });

  it('acepta parámetros 15/120/30/24', () => {
    expect(validatePracticeSettings(PRACTICE)).toEqual({ ok: true });
  });

  it('acepta tramos separados y adyacentes del mismo día', () => {
    expect(
      validateWorkingHours([
        { weekday: 1, startTime: '09:00', endTime: '13:00' },
        { weekday: 1, startTime: '14:00', endTime: '18:00' },
      ]),
    ).toEqual({ ok: true });
    expect(
      validateWorkingHours([
        { weekday: 1, startTime: '09:00', endTime: '13:00' },
        { weekday: 1, startTime: '13:00', endTime: '18:00' },
      ]),
    ).toEqual({ ok: true });
  });

  it('acepta un bloqueo con motivo y rango válidos', () => {
    expect(validateBlock(BLOCK)).toEqual({ ok: true });
  });

  it('acepta listas vacías de horarios', () => {
    expect(validateWorkingHours([])).toEqual({ ok: true });
  });
});

describe('validation - errores de negocio y borde', () => {
  it('rechaza un tramo invertido y vincula el error a ambos campos', () => {
    const result = validateWorkingHours([{ weekday: 1, startTime: '13:00', endTime: '09:00' }]);

    expect(result.ok).toBe(false);
    if (result.ok) {
      return;
    }
    expect(result.fields['start-0']).toContain('posterior');
    expect(result.fields['end-0']).toContain('posterior');
    expect(result.message).toBe('No se guardó: revisá el tramo marcado. La hora de fin debe ser posterior a la de inicio.');
  });

  it('rechaza solapes dentro del mismo día pero permite el mismo rango en días distintos', () => {
    const overlapped = validateWorkingHours([
      { weekday: 1, startTime: '09:00', endTime: '13:00' },
      { weekday: 1, startTime: '12:30', endTime: '18:00' },
    ]);

    expect(overlapped.ok).toBe(false);
    if (!overlapped.ok) {
      expect(overlapped.fields['start-0']).toContain('superpone');
      expect(overlapped.fields['start-1']).toContain('superpone');
      expect(overlapped.message).toBe('No se guardó: hay tramos superpuestos. Corregí los horarios marcados antes de enviar.');
    }
    expect(
      validateWorkingHours([
        { weekday: 1, startTime: '09:00', endTime: '13:00' },
        { weekday: 2, startTime: '09:00', endTime: '13:00' },
      ]),
    ).toEqual({ ok: true });
  });

  it('rechaza duración 20 con granularidad 15 y sugiere múltiplos', () => {
    const result = validateService({ ...SERVICE, durationMin: 20 }, 15);

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.fields['durationMin']).toContain('15');
      expect(result.fields['durationMin']).toContain('30');
      expect(result.message).toBe('No se guardó la prestación. La duración debe ser múltiplo de la granularidad vigente (15 min).');
    }
  });

  it('rechaza granularidad 25, horizonte 0 y recordatorio 169', () => {
    const result = validatePracticeSettings({
      ...PRACTICE,
      slotGranularityMin: 25,
      maxAdvanceDays: 0,
      reminderHoursBefore: 169,
    });

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.fields['slotGranularityMin']).toContain('10, 15, 20, 30 o 60');
      expect(result.fields['maxAdvanceDays']).toBeDefined();
      expect(result.fields['reminderHoursBefore']).toBeDefined();
    }
  });

  it('rechaza instantes sin zona y motivo vacío en bloqueos', () => {
    const noZone = validateBlock({ ...BLOCK, startsAt: '2026-03-23T12:00:00' });
    const noReason = validateBlock({ ...BLOCK, reason: '   ' });

    expect(noZone.ok).toBe(false);
    expect(noReason.ok).toBe(false);
    if (!noZone.ok) {
      expect(noZone.fields['startsAt']).toBeDefined();
    }
    if (!noReason.ok) {
      expect(noReason.fields['reason']).toContain('obligatorio');
    }
  });

  it('acepta exactamente 120 y 500 caracteres, y rechaza uno más', () => {
    expect(
      validateService({ ...SERVICE, name: 'n'.repeat(120), description: 'd'.repeat(500) }, 15),
    ).toEqual({ ok: true });
    expect(validateBlock({ ...BLOCK, reason: 'r'.repeat(500) })).toEqual({ ok: true });

    const tooLongService = validateService({ ...SERVICE, name: 'n'.repeat(121) }, 15);
    const tooLongBlock = validateBlock({ ...BLOCK, reason: 'r'.repeat(501) });
    expect(tooLongService.ok).toBe(false);
    expect(tooLongBlock.ok).toBe(false);
  });

  it('es pura: ninguna validación dispara fetch', () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);

    validatePracticeSettings(PRACTICE);
    validateWorkingHours([{ weekday: 1, startTime: '09:00', endTime: '13:00' }]);
    validateService(SERVICE, 15);
    validateBlock(BLOCK);

    expect(fetchSpy).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});
