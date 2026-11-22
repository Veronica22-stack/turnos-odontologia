/** Solapamiento de intervalos semiabiertos `[inicio, fin)`. Sin DOM ni HTTP. */

export interface HalfOpenRange {
  readonly start: number;
  readonly end: number;
}

export interface OverlappingPair {
  readonly a: number;
  readonly b: number;
}

export function overlaps(a: HalfOpenRange, b: HalfOpenRange): boolean {
  return a.start < b.end && b.start < a.end;
}

/** Índices de todos los pares que se solapan (se aceptan tramos adyacentes). */
export function findOverlaps(ranges: readonly HalfOpenRange[]): readonly OverlappingPair[] {
  const pairs: OverlappingPair[] = [];
  for (let a = 0; a < ranges.length; a += 1) {
    const first = ranges[a];
    if (first === undefined) {
      continue;
    }
    for (let b = a + 1; b < ranges.length; b += 1) {
      const second = ranges[b];
      if (second !== undefined && overlaps(first, second)) {
        pairs.push({ a, b });
      }
    }
  }
  return pairs;
}
