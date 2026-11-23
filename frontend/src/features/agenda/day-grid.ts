import type { AppointmentDto, AgendaBlock } from './agenda-types';
import { formatTime, parseInstant } from '../../shared/utils/dates';
import { dayBoundsUtc } from '../../shared/utils/dates';
import type { WorkingHour } from '../settings/settings-types';

/** Grilla de un día: tramos de atención, turnos, bloqueos rayados y huecos libres clickeables (C-20 2.1). */

export interface DayGridInput {
  readonly workingHours: readonly WorkingHour[];
  readonly appointments: readonly AppointmentDto[];
  readonly blocks: readonly AgendaBlock[];
  readonly date: string;
}

export interface DayGridAppointmentItem {
  readonly kind: 'appointment';
  readonly startMin: number;
  readonly endMin: number;
  readonly status: AppointmentDto['status'];
  readonly overbooking: boolean;
  readonly patient: string;
  readonly service: string;
  readonly outsideHours: boolean;
  readonly appointment: AppointmentDto;
}

export interface DayGridBlockItem {
  readonly kind: 'block';
  readonly startMin: number;
  readonly endMin: number;
}

export interface DayGridFreeItem {
  readonly kind: 'free';
  readonly startMin: number;
  readonly endMin: number;
}

export type DayGridItem = DayGridAppointmentItem | DayGridBlockItem | DayGridFreeItem;

export interface DayGrid {
  readonly hasTurns: boolean;
  readonly times: readonly string[];
  readonly rows: number;
  readonly items: readonly DayGridItem[];
}

interface Interval {
  readonly startMin: number;
  readonly endMin: number;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** Formato `HH:mm` desde minutos desde medianoche (0-23, 0-59). */
export function formatMinutes(minutes: number): string {
  return `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
}

const CLOCK_PATTERN = /^([01]\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$/;

/**
 * Alto de cada fila de la grilla en minutos. El design system (Revision 1, `components.html` §Agenda)
 * usa franjas de 15 min con etiquetas cada 30 min; un turno de 30 min ocupa 2 filas.
 */
export const GRID_ROW_MINUTES = 15;

/** Fila (en unidades de `GRID_ROW_MINUTES`) donde empieza un minuto del día. */
export function gridRowStart(startMin: number, baseMin: number): number {
  return (startMin - baseMin) / GRID_ROW_MINUTES;
}

/** Filas que ocupa un rango; al menos una para turnos más cortos que la fila. */
export function gridRowSpan(startMin: number, endMin: number): number {
  return Math.max(1, Math.ceil((endMin - startMin) / GRID_ROW_MINUTES));
}

/** Minutos desde medianoche de un 'HH:mm[:ss]' local; undefined si no es válido. */
export function hourMinutes(value: string): number | undefined {
  const match = CLOCK_PATTERN.exec(value);
  return match === null ? undefined : Number(match[1]) * 60 + Number(match[2]);
}

/** Minutos desde medianoche de un instante en la zona del consultorio; undefined si no se puede leer. */
function instantMinutes(iso: string): number | undefined {
  try {
    return hourMinutes(formatTime(parseInstant(iso)));
  } catch {
    return undefined;
  }
}

/** Día de la semana en la zona del consultorio: 1 = lunes ... 7 = domingo. */
function weekdayOf(date: string): number {
  const jsDay = dayBoundsUtc(date).start.getUTCDay();
  return jsDay === 0 ? 7 : jsDay;
}

/** Tramos de atención del día; los contiguos del mismo día se fusionan. */
function workingIntervals(workingHours: readonly WorkingHour[], date: string): readonly Interval[] {
  const day = weekdayOf(date);
  const ranges: Interval[] = [];
  for (const hour of workingHours) {
    const startMin = hourMinutes(hour.startTime);
    const endMin = hourMinutes(hour.endTime);
    if (startMin !== undefined && endMin !== undefined && startMin < endMin && hour.weekday === day) {
      ranges.push({ startMin, endMin });
    }
  }
  ranges.sort((a, b) => a.startMin - b.startMin);
  const merged: Interval[] = [];
  for (const range of ranges) {
    const last = merged[merged.length - 1];
    if (last !== undefined && range.startMin <= last.endMin) {
      merged[merged.length - 1] = { startMin: last.startMin, endMin: Math.max(last.endMin, range.endMin) };
    } else {
      merged.push(range);
    }
  }
  return merged;
}

export function buildDayGrid(input: DayGridInput): DayGrid {
  const { workingHours, appointments, blocks, date } = input;
  const intervals = workingIntervals(workingHours, date);

  const items: DayGridItem[] = [];
  const occupied: Interval[] = [];

  for (const appointment of appointments) {
    const startMin = instantMinutes(appointment.startsAt);
    const endMin = instantMinutes(appointment.endsAt);
    if (startMin === undefined || endMin === undefined) {
      continue;
    }
    const inside = intervals.some((interval) => interval.startMin <= startMin && endMin <= interval.endMin);
    items.push({
      kind: 'appointment',
      startMin,
      endMin,
      status: appointment.status,
      overbooking: appointment.overbooking,
      patient: appointment.patientId,
      service: appointment.serviceId,
      outsideHours: !inside,
      appointment,
    });
    occupied.push({ startMin, endMin });
  }

  for (const block of blocks) {
    const startMin = instantMinutes(block.startsAt);
    const endMin = instantMinutes(block.endsAt);
    if (startMin === undefined || endMin === undefined) {
      continue;
    }
    for (const interval of intervals) {
      const start = Math.max(startMin, interval.startMin);
      const end = Math.min(endMin, interval.endMin);
      if (start < end) {
        items.push({ kind: 'block', startMin: start, endMin: end });
        occupied.push({ startMin: start, endMin: end });
        break;
      }
    }
  }

  for (const interval of intervals) {
    let cursor = interval.startMin;
    const overlapping = occupied
      .filter((occupancy) => occupancy.startMin < interval.endMin && occupancy.endMin > interval.startMin)
      .sort((a, b) => a.startMin - b.startMin);
    for (const occupancy of overlapping) {
      if (occupancy.startMin > cursor) {
        items.push({ kind: 'free', startMin: cursor, endMin: Math.min(occupancy.startMin, interval.endMin) });
      }
      cursor = Math.max(cursor, occupancy.endMin);
    }
    if (cursor < interval.endMin) {
      items.push({ kind: 'free', startMin: cursor, endMin: interval.endMin });
    }
  }

  const times: string[] = [];
  const first = intervals[0]?.startMin;
  const last = intervals.at(-1)?.endMin;
  if (first !== undefined && last !== undefined) {
    for (let minute = first; minute < last; minute += 30) {
      times.push(formatMinutes(minute));
    }
  }
  const rows = first !== undefined && last !== undefined ? Math.ceil((last - first) / GRID_ROW_MINUTES) : 0;

  items.sort((a, b) => a.startMin - b.startMin || a.endMin - b.endMin);

  return { hasTurns: items.length > 0 || intervals.length > 0, times, rows, items };
}
