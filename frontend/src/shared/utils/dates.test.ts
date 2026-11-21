import { afterEach, describe, expect, it } from 'vitest';
import {
  InvalidInstantError,
  dayBoundsUtc,
  formatDate,
  formatDateTime,
  formatTime,
  localDateOf,
  parseInstant,
  today,
} from './dates';

declare const process: { env: Record<string, string | undefined> };

describe('parseInstant', () => {
  it('acepta un instante con Z', () => {
    expect(parseInstant('2026-03-10T12:00:00Z').toISOString()).toBe('2026-03-10T12:00:00.000Z');
  });

  it('rechaza un instante sin zona, mencionando la zona', () => {
    expect(() => parseInstant('2026-03-10T12:00:00')).toThrowError(InvalidInstantError);
    expect(() => parseInstant('2026-03-10T12:00:00')).toThrowError(/zona/);
  });

  it('interpreta offsets: -03:00 equivale a 12:00Z', () => {
    expect(parseInstant('2026-03-10T09:00:00-03:00').getTime()).toBe(parseInstant('2026-03-10T12:00:00Z').getTime());
  });

  it('acepta milisegundos y rechaza texto basura', () => {
    expect(parseInstant('2026-03-10T12:00:00.250Z').getUTCMilliseconds()).toBe(250);
    expect(() => parseInstant('manana')).toThrowError(InvalidInstantError);
    expect(() => parseInstant('2026-13-40T99:00:00Z')).toThrowError(InvalidInstantError);
  });
});

describe('formatDate / formatTime / formatDateTime', () => {
  const originalTz = process.env['TZ'];
  afterEach(() => {
    if (originalTz === undefined) {
      delete process.env['TZ'];
    } else {
      process.env['TZ'] = originalTz;
    }
  });

  it('muestra la hora del consultorio (UTC-3)', () => {
    expect(formatDate('2026-03-10T12:00:00Z')).toBe('10/03/2026');
    expect(formatTime('2026-03-10T12:00:00Z')).toBe('09:00');
    expect(formatDateTime('2026-03-10T12:00:00Z')).toBe('10/03/2026 09:00');
  });

  it('cruza el dia hacia atras: 02:00Z es 23:00 del dia anterior', () => {
    expect(formatDate('2026-03-11T02:00:00Z')).toBe('10/03/2026');
    expect(formatTime('2026-03-11T02:00:00Z')).toBe('23:00');
  });

  it('no depende de la zona del navegador (Europe/Madrid)', () => {
    process.env['TZ'] = 'Europe/Madrid';

    expect(formatDate('2026-03-10T12:00:00Z')).toBe('10/03/2026');
    expect(formatTime('2026-03-10T12:00:00Z')).toBe('09:00');
    expect(formatTime('2026-03-11T02:00:00Z')).toBe('23:00');
  });

  it('acepta objetos Date', () => {
    expect(formatTime(new Date('2026-03-10T15:30:00Z'))).toBe('12:30');
  });
});

describe('localDateOf / today / dayBoundsUtc', () => {
  it('localDateOf usa la fecha del consultorio', () => {
    expect(localDateOf('2026-03-11T02:00:00Z')).toBe('2026-03-10');
    expect(localDateOf('2026-03-10T12:00:00Z')).toBe('2026-03-10');
  });

  it('today usa el reloj inyectado', () => {
    expect(today(() => new Date('2026-03-11T02:30:00Z'))).toBe('2026-03-10');
    expect(today(() => new Date('2026-03-10T12:00:00Z'))).toBe('2026-03-10');
  });

  it('dayBoundsUtc devuelve [00:00 local, 00:00 local siguiente) en UTC', () => {
    const { start, end } = dayBoundsUtc('2026-03-10');

    expect(start.toISOString()).toBe('2026-03-10T03:00:00.000Z');
    expect(end.toISOString()).toBe('2026-03-11T03:00:00.000Z');
  });

  it('dayBoundsUtc cruza fin de mes y de anio', () => {
    expect(dayBoundsUtc('2026-12-31').end.toISOString()).toBe('2027-01-01T03:00:00.000Z');
    expect(dayBoundsUtc('2026-02-28').end.toISOString()).toBe('2026-03-01T03:00:00.000Z');
  });

  it('dayBoundsUtc rechaza fechas invalidas', () => {
    expect(() => dayBoundsUtc('2026-02-30')).toThrowError(/fecha/i);
    expect(() => dayBoundsUtc('2026-3-1')).toThrowError(/fecha/i);
  });
});
