import { describe, expect, it } from 'vitest';
import { formatTime } from '../../shared/utils/dates';
import { groupSlots, suggestNearest, type Slot } from './slots';

function slot(startsAt: string, minutes = 30): Slot {
  const end = new Date(new Date(startsAt).getTime() + minutes * 60_000).toISOString().replace('.000Z', 'Z');
  return { startsAt, endsAt: end };
}

const times = (slots: readonly Slot[]): string[] => slots.map((s) => formatTime(s.startsAt));

describe('groupSlots', () => {
  it('separa Manana y Tarde por la hora local, con el borde en las 13:00', () => {
    const grouped = groupSlots([
      slot('2026-11-24T12:00:00Z'),
      slot('2026-11-24T12:30:00Z'),
      slot('2026-11-24T15:45:00Z'),
      slot('2026-11-24T16:00:00Z'),
      slot('2026-11-24T18:00:00Z'),
    ]);

    expect(times(grouped.morning)).toEqual(['09:00', '09:30', '12:45']);
    expect(times(grouped.afternoon)).toEqual(['13:00', '15:00']);
  });

  it('usa la hora de Buenos Aires aunque el dia UTC sea otro', () => {
    const grouped = groupSlots([slot('2026-11-25T02:00:00Z')]);

    expect(times(grouped.morning)).toEqual([]);
    expect(times(grouped.afternoon)).toEqual(['23:00']);
  });

  it('con una lista vacia devuelve ambos grupos vacios', () => {
    expect(groupSlots([])).toEqual({ morning: [], afternoon: [] });
  });
});

describe('suggestNearest', () => {
  const offered = [
    slot('2026-11-24T13:00:00Z'), // 10:00
    slot('2026-11-24T14:00:00Z'), // 11:00
    slot('2026-11-24T14:30:00Z'), // 11:30
    slot('2026-11-24T18:00:00Z'), // 15:00
  ];

  it('devuelve los 3 mas cercanos en orden cronologico', () => {
    const result = suggestNearest(offered, '2026-11-24T13:30:00Z', 3);

    expect(times(result)).toEqual(['10:00', '11:00', '11:30']);
  });

  it('con menos slots que el limite devuelve los que hay', () => {
    expect(times(suggestNearest(offered.slice(0, 2), '2026-11-24T13:30:00Z', 3))).toEqual(['10:00', '11:00']);
    expect(suggestNearest([], '2026-11-24T13:30:00Z', 3)).toEqual([]);
  });

  it('excluye el instante rechazado aunque siga ofrecido', () => {
    const result = suggestNearest(offered, '2026-11-24T14:00:00Z', 3);

    expect(times(result)).toEqual(['10:00', '11:30', '15:00']);
  });

  it('ante un empate de distancia prefiere el mas temprano', () => {
    const result = suggestNearest(offered, '2026-11-24T13:30:00Z', 1);

    expect(times(result)).toEqual(['10:00']);
  });
});
