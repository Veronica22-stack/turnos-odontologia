import { describe, expect, it } from 'vitest';
import {
  decodeBlock,
  decodeBlocks,
  decodePracticeSettings,
  decodeService,
  decodeServices,
  decodeWorkingHour,
  decodeWorkingHours,
} from './settings-types';

const PRACTICE = {
  id: 'p1',
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

const HOUR = { weekday: 1, startTime: '09:00:00', endTime: '13:00:00' };
const SERVICE = {
  id: 's1',
  name: 'Limpieza',
  description: 'Sesión de limpieza',
  durationMin: 45,
  active: true,
  publicVisible: true,
};
const BLOCK = {
  id: 'b1',
  startsAt: '2026-03-23T12:00:00Z',
  endsAt: '2026-03-23T14:00:00Z',
  reason: 'Mantenimiento',
  createdAt: '2026-03-20T10:00:00Z',
  updatedAt: '2026-03-20T10:00:00Z',
  affectedAppointments: [],
};

describe('settings-types - decodificadores del contrato (C-19 D2)', () => {
  it('decodePracticeSettings convierte un payload completo en DTO readonly', () => {
    const result = decodePracticeSettings(PRACTICE);

    expect(result).toEqual(PRACTICE);
    // El DTO es readonly: el compilador rechazaría una asignación posterior.
    const typed: NonNullable<ReturnType<typeof decodePracticeSettings>> | undefined = result;
    expect(typed?.slotGranularityMin).toBe(15);
  });

  it('decodeWorkingHour normaliza HH:mm:ss a HH:mm y decodeWorkingHours decodifica la lista', () => {
    expect(decodeWorkingHour(HOUR)).toEqual({ weekday: 1, startTime: '09:00', endTime: '13:00' });
    expect(decodeWorkingHours([HOUR, { weekday: 3, startTime: '14:30', endTime: '18:00' }])).toEqual([
      { weekday: 1, startTime: '09:00', endTime: '13:00' },
      { weekday: 3, startTime: '14:30', endTime: '18:00' },
    ]);
  });

  it('decodeServices conserva prestaciones inactivas', () => {
    expect(decodeServices([SERVICE, { ...SERVICE, id: 's2', name: 'Arreglo de caries', active: false }])).toEqual([
      SERVICE,
      { id: 's2', name: 'Arreglo de caries', description: 'Sesión de limpieza', durationMin: 45, active: false, publicVisible: true },
    ]);
  });

  it('decodeBlocks conserva affectedAppointments vacío y con un turno sin exponer datos del paciente', () => {
    const withAppointment = {
      ...BLOCK,
      affectedAppointments: [
        { appointmentId: 'a1', startsAt: '2026-03-23T12:30:00Z', endsAt: '2026-03-23T13:00:00Z' },
      ],
    };

    expect(decodeBlocks([BLOCK, withAppointment])).toEqual([
      BLOCK,
      withAppointment,
    ]);
    expect(decodeBlock(withAppointment)?.affectedAppointments).toHaveLength(1);
  });
});

describe('settings-types - casos de error y borde', () => {
  it('rechaza campos faltantes y tipos incorrectos sin lanzar', () => {
    const { name: _missing, ...withoutName } = PRACTICE;

    expect(decodePracticeSettings(withoutName)).toBeUndefined();
    expect(decodePracticeSettings({ ...PRACTICE, slotGranularityMin: '15' })).toBeUndefined();
    expect(decodePracticeSettings(null)).toBeUndefined();
    expect(decodeWorkingHour({ ...HOUR, weekday: 8 })).toBeUndefined();
    expect(decodeWorkingHour({ ...HOUR, weekday: 0 })).toBeUndefined();
    expect(decodeServices([SERVICE, { ...SERVICE, durationMin: '45' }])).toBeUndefined();
  });

  it('rechaza instantes sin zona también dentro de los turnos afectados', () => {
    expect(decodeBlock({ ...BLOCK, startsAt: '2026-03-10T09:00:00' })).toBeUndefined();
    expect(
      decodeBlock({
        ...BLOCK,
        affectedAppointments: [{ appointmentId: 'a1', startsAt: '2026-03-10T09:00:00', endsAt: '2026-03-10T09:30:00Z' }],
      }),
    ).toBeUndefined();
    expect(decodeBlocks({})).toBeUndefined();
  });

  it('conserva texto hostil como dato literal, sin interpretarlo como HTML', () => {
    const hostile = '<img src=x onerror=alert(1)>';

    expect(decodeService({ ...SERVICE, name: hostile })?.name).toBe(hostile);
    expect(decodeBlock({ ...BLOCK, reason: hostile })?.reason).toBe(hostile);
  });
});
