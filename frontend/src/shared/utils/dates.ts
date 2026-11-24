export const PRACTICE_ZONE = 'America/Argentina/Buenos_Aires';

export type Clock = () => Date;

export class InvalidInstantError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InvalidInstantError';
  }
}

const INSTANT_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?(?:Z|[+-]\d{2}:\d{2})$/;
const LOCAL_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

const partsFormatter = new Intl.DateTimeFormat('es-AR', {
  timeZone: PRACTICE_ZONE,
  hourCycle: 'h23',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

interface LocalParts {
  readonly year: number;
  readonly month: number;
  readonly day: number;
  readonly hour: number;
  readonly minute: number;
  readonly second: number;
}

function localParts(instant: Date): LocalParts {
  const values: Record<string, number> = {};
  for (const part of partsFormatter.formatToParts(instant)) {
    if (part.type !== 'literal') {
      values[part.type] = Number(part.value);
    }
  }
  return {
    year: values['year'] ?? 0,
    month: values['month'] ?? 0,
    day: values['day'] ?? 0,
    hour: values['hour'] ?? 0,
    minute: values['minute'] ?? 0,
    second: values['second'] ?? 0,
  };
}

function pad(value: number, length = 2): string {
  return String(value).padStart(length, '0');
}

/** Interpreta un instante ISO-8601 que DEBE traer zona (Z u offset). */
export function parseInstant(iso: string): Date {
  if (!INSTANT_PATTERN.test(iso)) {
    throw new InvalidInstantError('Instante invalido: se espera ISO-8601 con zona (Z o +-HH:mm)');
  }
  // El backend serializa Instant con nanosegundos; JS solo interpreta hasta milisegundos.
  const date = new Date(iso.replace(/(\.\d{3})\d+(Z|[+-]\d{2}:\d{2})$/, '$1$2'));
  if (Number.isNaN(date.getTime())) {
    throw new InvalidInstantError('Instante invalido: fecha u hora fuera de rango');
  }
  return date;
}

function toDate(value: string | Date): Date {
  return typeof value === 'string' ? parseInstant(value) : value;
}

export function formatDate(value: string | Date): string {
  const p = localParts(toDate(value));
  return `${pad(p.day)}/${pad(p.month)}/${pad(p.year, 4)}`;
}

export function formatTime(value: string | Date): string {
  const p = localParts(toDate(value));
  return `${pad(p.hour)}:${pad(p.minute)}`;
}

export function formatDateTime(value: string | Date): string {
  return `${formatDate(value)} ${formatTime(value)}`;
}

/** Fecha calendario (YYYY-MM-DD) en la zona del consultorio. */
export function localDateOf(value: string | Date): string {
  const p = localParts(toDate(value));
  return `${pad(p.year, 4)}-${pad(p.month)}-${pad(p.day)}`;
}

export function today(clock: Clock = () => new Date()): string {
  return localDateOf(clock());
}

/** Diferencia (ms) entre la hora local de la zona y UTC para un instante dado. */
function zoneOffsetMs(instant: Date): number {
  const p = localParts(instant);
  const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  const truncated = Math.floor(instant.getTime() / 1000) * 1000;
  return asUtc - truncated;
}

function localMidnightUtc(year: number, month: number, day: number): Date {
  const naive = Date.UTC(year, month - 1, day);
  let result = naive - zoneOffsetMs(new Date(naive));
  // Segunda pasada por si el offset difiere en el instante resultante (cambios de horario).
  result = naive - zoneOffsetMs(new Date(result));
  return new Date(result);
}

interface CalendarDate {
  readonly year: number;
  readonly month: number;
  readonly day: number;
}

function parseLocalDate(date: string): CalendarDate {
  const match = LOCAL_DATE_PATTERN.exec(date);
  const year = Number(match?.[1]);
  const month = Number(match?.[2]);
  const day = Number(match?.[3]);
  const probe = new Date(Date.UTC(year, month - 1, day));
  if (
    match === null ||
    probe.getUTCFullYear() !== year ||
    probe.getUTCMonth() !== month - 1 ||
    probe.getUTCDate() !== day
  ) {
    throw new Error('Fecha invalida: se espera YYYY-MM-DD');
  }
  return { year, month, day };
}

function formatCalendarDate(value: Date): string {
  return `${pad(value.getUTCFullYear(), 4)}-${pad(value.getUTCMonth() + 1)}-${pad(value.getUTCDate())}`;
}

/** Suma `days` a una fecha calendario local (aritmetica sobre el calendario, sin zonas ni DST). */
export function addLocalDays(date: string, days: number): string {
  const { year, month, day } = parseLocalDate(date);
  return formatCalendarDate(new Date(Date.UTC(year, month - 1, day + days)));
}

export interface DayLabels {
  /** `mar` */
  readonly short: string;
  /** `24` */
  readonly day: string;
  /** `mar 24/11` */
  readonly medium: string;
  /** `martes 24 de noviembre` (sin coma) */
  readonly long: string;
}

const weekdayFormatter = new Intl.DateTimeFormat('es-AR', { timeZone: 'UTC', weekday: 'long' });
const monthFormatter = new Intl.DateTimeFormat('es-AR', { timeZone: 'UTC', month: 'long' });

/** Etiquetas de un dia calendario local; independiente de la zona del navegador. */
export function dayLabels(date: string): DayLabels {
  const { year, month, day } = parseLocalDate(date);
  const calendar = new Date(Date.UTC(year, month - 1, day));
  const weekday = weekdayFormatter.format(calendar);
  const short = weekday.slice(0, 3);
  return {
    short,
    day: String(day),
    medium: `${short} ${day}/${pad(month)}`,
    long: `${weekday} ${day} de ${monthFormatter.format(calendar)}`,
  };
}

/** Limites UTC [start, end) del dia calendario `YYYY-MM-DD` en la zona del consultorio. */
export function dayBoundsUtc(date: string): { start: Date; end: Date } {
  const { year, month, day } = parseLocalDate(date);
  const next = new Date(Date.UTC(year, month - 1, day + 1));
  return {
    start: localMidnightUtc(year, month, day),
    end: localMidnightUtc(next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate()),
  };
}
