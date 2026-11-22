import { describe, expect, it } from 'vitest';
import { describeWait } from './retry-after';

describe('describeWait', () => {
  it.each([
    [840, 'en unos 14 minutos'],
    [61, 'en unos 2 minutos'],
    [120, 'en unos 2 minutos'],
    [60, 'en 1 minuto'],
    [30, 'en 1 minuto'],
    [1, 'en 1 minuto'],
  ])('%s segundos -> %s', (seconds, expected) => {
    expect(describeWait(seconds)).toBe(expected);
  });

  it.each([[undefined], [0], [-5], [Number.NaN]])('%s -> en unos minutos', (seconds) => {
    expect(describeWait(seconds)).toBe('en unos minutos');
  });
});
