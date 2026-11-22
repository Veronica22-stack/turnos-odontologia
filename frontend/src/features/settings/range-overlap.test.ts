import { describe, expect, it } from 'vitest';
import { findOverlaps, overlaps } from './range-overlap';

describe('range-overlap - intervalos semiabiertos [inicio, fin)', () => {
  it('acepta tramos adyacentes: el fin de uno iguala el inicio del otro', () => {
    const morning = { start: 9 * 60, end: 13 * 60 };
    const afternoon = { start: 13 * 60, end: 18 * 60 };

    expect(overlaps(morning, afternoon)).toBe(false);
    expect(findOverlaps([morning, afternoon])).toEqual([]);
  });

  it('detecta un solape parcial devolviendo los índices de ambos tramos', () => {
    const first = { start: 9 * 60, end: 13 * 60 };
    const second = { start: 12 * 60 + 30, end: 18 * 60 };

    expect(overlaps(first, second)).toBe(true);
    expect(findOverlaps([first, second])).toEqual([{ a: 0, b: 1 }]);
  });

  it('un tramo contenido en otro también se considera solape', () => {
    const outer = { start: 9 * 60, end: 18 * 60 };
    const inner = { start: 10 * 60, end: 11 * 60 };

    expect(overlaps(outer, inner)).toBe(true);
    expect(overlaps(inner, outer)).toBe(true);
  });
});
