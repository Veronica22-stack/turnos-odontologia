import { describe, expect, it } from 'vitest';
import { dayBoundsUtc } from '../../shared/utils/dates';
import type { AppointmentDto } from './agenda-types';
import {
  buildDayGrid,
  formatMinutes,
  gridRowSpan,
  gridRowStart,
  GRID_ROW_MINUTES,
  hourMinutes,
  type DayGridAppointmentItem,
} from './day-grid';
import type { WorkingHour } from '../settings/settings-types';

const DATE = '2026-11-23'; // lunes (día de la semana 1 según la zona del consultorio)

const HOURS: readonly WorkingHour[] = [{ weekday: 1, startTime: '09:00', endTime: '13:00' }];

/** Instant ISO Z tal que en hora de Buenos Aires sea `hhmm` del día local `date`. */
function instantWithLocalTime(date: string, hhmm: string): string {
  const parts = hhmm.split(':').map(Number);
  const hours = parts[0] ?? 0;
  const minutes = parts[1] ?? 0;
  return new Date(dayBoundsUtc(date).start.getTime() + (hours * 60 + minutes) * 60_000).toISOString();
}

function appointment(overrides: Partial<AppointmentDto> & Pick<AppointmentDto, 'startsAt' | 'endsAt'>): AppointmentDto {
  return {
    id: 'appt-1',
    patientId: 'patient-1',
    serviceId: 'service-1',
    durationMin: 30,
    status: 'CONFIRMED',
    origin: 'INTERNAL',
    overbooking: false,
    cancelledBy: null,
    cancelledAt: null,
    notes: null,
    createdAt: '2026-11-22T09:00:00Z',
    updatedAt: '2026-11-22T09:00:00Z',
    ...overrides,
  };
}

function findAppointment(grid: ReturnType<typeof buildDayGrid>): DayGridAppointmentItem | undefined {
  return grid.items.find((item): item is DayGridAppointmentItem => item.kind === 'appointment');
}

describe('formatMinutes / hourMinutes', () => {
  it('formatea minutos desde medianoche con ceros', () => {
    expect(formatMinutes(0)).toBe('00:00');
    expect(formatMinutes(543)).toBe('09:03');
  });

  it('parses HH:mm (con y sin segundos) a minutos; horas inválidas sin resultado', () => {
    expect(hourMinutes('09:00')).toBe(540);
    expect(hourMinutes('23:59:30')).toBe(1439);
    expect(hourMinutes('24:00')).toBeUndefined();
    expect(hourMinutes('9:00')).toBeUndefined();
  });
});

describe('buildDayGrid', () => {
  it('turno dentro del tramo aparece como appointment en su rango devuelto por el servidor', () => {
    const grid = buildDayGrid({
      workingHours: HOURS,
      appointments: [appointment({ startsAt: instantWithLocalTime(DATE, '10:13'), endsAt: instantWithLocalTime(DATE, '10:47') })],
      blocks: [],
      date: DATE,
    });
    const item = findAppointment(grid);
    expect(item).toBeDefined();
    expect(item?.startMin).toBe(613);
    expect(item?.endMin).toBe(647);
    expect(item?.status).toBe('CONFIRMED');
    expect(item?.overbooking).toBe(false);
    expect(item?.outsideHours).toBe(false);
    expect(grid.hasTurns).toBe(true);
  });

  it('turno fuera de horario marcado como terminal', () => {
    const grid = buildDayGrid({
      workingHours: HOURS,
      appointments: [
        appointment({ startsAt: instantWithLocalTime(DATE, '13:15'), endsAt: instantWithLocalTime(DATE, '13:30') }),
      ],
      blocks: [],
      date: DATE,
    });
    const item = findAppointment(grid);
    expect(item?.outsideHours).toBe(true);
    expect(item?.status).toBe('CONFIRMED');
  });

  it('turno terminal (COMPLETED) sigue ocupando su rango', () => {
    const grid = buildDayGrid({
      workingHours: HOURS,
      appointments: [
        appointment({
          startsAt: instantWithLocalTime(DATE, '10:00'),
          endsAt: instantWithLocalTime(DATE, '10:30'),
          status: 'COMPLETED',
        }),
      ],
      blocks: [],
      date: DATE,
    });
    const item = findAppointment(grid);
    expect(item?.status).toBe('COMPLETED');
    const overlap = grid.items.some((i) => i.kind === 'free' && i.startMin < 630 && i.endMin > 600);
    expect(overlap).toBe(false);
  });

  it('adjacencia turno-bloqueo no se superponen; el bloqueo se rayuelo en su rango', () => {
    const grid = buildDayGrid({
      workingHours: HOURS,
      appointments: [
        appointment({ startsAt: instantWithLocalTime(DATE, '10:00'), endsAt: instantWithLocalTime(DATE, '10:30') }),
      ],
      blocks: [{ startsAt: instantWithLocalTime(DATE, '10:30'), endsAt: instantWithLocalTime(DATE, '11:00') }],
      date: DATE,
    });
    const turno = findAppointment(grid);
    const bloqueo = grid.items.find((item) => item.kind === 'block');
    expect(turno?.startMin).toBe(600);
    expect(turno?.endMin).toBe(630);
    expect(bloqueo).toEqual({ kind: 'block', startMin: 630, endMin: 660 });
    if (turno !== undefined && bloqueo !== undefined && bloqueo.kind === 'block') {
      expect(turno.startMin < bloqueo.endMin && bloqueo.startMin < turno.endMin).toBe(false);
    }
  });

  it('día sin tramos → grid vacío', () => {
    const grid = buildDayGrid({ workingHours: [], appointments: [], blocks: [], date: DATE });
    expect(grid).toEqual({ hasTurns: false, times: [], items: [], rows: 0 });
  });

  it('grilla de 30 min desde los tramos de atención', () => {
    const grid = buildDayGrid({ workingHours: HOURS, appointments: [], blocks: [], date: DATE });
    expect(grid.times).toEqual(['09:00', '09:30', '10:00', '10:30', '11:00', '11:30', '12:00', '12:30']);
    expect(grid.hasTurns).toBe(true);
  });

  it('la grilla tiene filas de 15 min y etiquetas cada 30 min (design system Revision 1)', () => {
    const grid = buildDayGrid({ workingHours: HOURS, appointments: [], blocks: [], date: DATE });
    // 4 h de tramo: 16 filas de 15 min con 8 etiquetas de 30 min.
    expect(GRID_ROW_MINUTES).toBe(15);
    expect(grid.rows).toBe(16);
    expect(grid.rows).toBe(grid.times.length * 2);
  });

  it('las filas cubren el tramo aunque no termine en múltiplo de 30 min', () => {
    const grid = buildDayGrid({
      workingHours: [{ weekday: 1, startTime: '09:00', endTime: '13:10' }],
      appointments: [],
      blocks: [],
      date: DATE,
    });
    expect(grid.times.at(-1)).toBe('13:00');
    expect(grid.rows).toBe(17); // ceil(250 min / 15)
  });

  it('huecos libres generados entre turnos', () => {
    const grid = buildDayGrid({
      workingHours: HOURS,
      appointments: [
        appointment({ startsAt: instantWithLocalTime(DATE, '10:00'), endsAt: instantWithLocalTime(DATE, '10:30') }),
      ],
      blocks: [],
      date: DATE,
    });
    const free = grid.items.filter((item) => item.kind === 'free');
    expect(free).toContainEqual({ kind: 'free', startMin: 540, endMin: 600 });
    expect(free).toContainEqual({ kind: 'free', startMin: 630, endMin: 780 });
  });

  it('tramos contiguos del mismo día se unen', () => {
    const grid = buildDayGrid({
      workingHours: [
        { weekday: 1, startTime: '09:00', endTime: '12:00' },
        { weekday: 1, startTime: '12:00', endTime: '15:00' },
      ],
      appointments: [],
      blocks: [],
      date: DATE,
    });
    expect(grid.times[0]).toBe('09:00');
    expect(grid.times[grid.times.length - 1]).toBe('14:30');
  });
});

describe('geometría de filas de la grilla (15 min por fila)', () => {
  it('un turno de 30 min empieza en la fila 4 y ocupa 2 filas (mockup day.html)', () => {
    expect(gridRowStart(600, 540)).toBe(4);
    expect(gridRowSpan(600, 630)).toBe(2);
  });

  it('un turno de 45 min ocupa 3 filas', () => {
    expect(gridRowSpan(600, 645)).toBe(3);
  });

  it('un turno desalineado conserva su inicio fraccionario y redondea su alto hacia arriba', () => {
    expect(gridRowStart(613, 540)).toBeCloseTo(4.8667, 3);
    expect(gridRowSpan(613, 647)).toBe(3);
  });

  it('un turno muy corto ocupa al menos una fila', () => {
    expect(gridRowSpan(600, 605)).toBe(1);
    expect(gridRowSpan(600, 600)).toBe(1);
  });
});
