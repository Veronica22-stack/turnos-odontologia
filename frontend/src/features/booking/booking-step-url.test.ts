import { describe, expect, it } from 'vitest';
import { parseStep, stepPath } from './booking-step-url';

const query = (search: string): URLSearchParams => new URLSearchParams(search);

describe('parseStep', () => {
  it.each([
    ['paso=2', 2],
    ['paso=3', 3],
    ['paso=1', 1],
    ['', 1],
    ['paso=0', 1],
    ['paso=9', 1],
    ['paso=abc', 1],
    ['paso=2x', 1],
    ['paso=', 1],
    ['otro=3', 1],
  ])('"%s" -> paso %i', (search, expected) => {
    expect(parseStep(query(search))).toBe(expected);
  });
});

describe('stepPath', () => {
  it('arma la ruta de cada paso sin datos extra', () => {
    expect(stepPath(1)).toBe('/reservar');
    expect(stepPath(2)).toBe('/reservar?paso=2');
    expect(stepPath(3)).toBe('/reservar?paso=3');
  });
});
