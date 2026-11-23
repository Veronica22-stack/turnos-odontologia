import { formatTime, parseInstant } from '../../shared/utils/dates';

/** Funciones puras sobre los slots de la API (design D4). */

export interface Slot {
  /** Instante ISO UTC exactamente como lo informa el servidor. */
  readonly startsAt: string;
  readonly endsAt: string;
}

export interface GroupedSlots {
  readonly morning: readonly Slot[];
  readonly afternoon: readonly Slot[];
}

const AFTERNOON_FROM_HOUR = 13;

export function groupSlots(slots: readonly Slot[]): GroupedSlots {
  const morning: Slot[] = [];
  const afternoon: Slot[] = [];
  for (const slot of slots) {
    const hour = Number(formatTime(slot.startsAt).slice(0, 2));
    (hour < AFTERNOON_FROM_HOUR ? morning : afternoon).push(slot);
  }
  return { morning, afternoon };
}

/** Los `limit` slots mas cercanos al rechazado (sin incluirlo), en orden cronologico; empate: el mas temprano. */
export function suggestNearest(slots: readonly Slot[], takenStartsAt: string, limit: number): Slot[] {
  const taken = parseInstant(takenStartsAt).getTime();
  return slots
    .map((slot) => ({ slot, at: parseInstant(slot.startsAt).getTime() }))
    .filter(({ at }) => at !== taken)
    .sort((a, b) => Math.abs(a.at - taken) - Math.abs(b.at - taken) || a.at - b.at)
    .slice(0, limit)
    .sort((a, b) => a.at - b.at)
    .map(({ slot }) => slot);
}
