/** Paso del flujo reflejado en la URL (design D3): solo el numero, nunca datos del paciente. */

export type BookingStep = 1 | 2 | 3;

export const BOOKING_PATH = '/reservar';

export function parseStep(query: URLSearchParams): BookingStep {
  const raw = query.get('paso');
  return raw === '2' ? 2 : raw === '3' ? 3 : 1;
}

export function stepPath(step: BookingStep): string {
  return step === 1 ? BOOKING_PATH : `${BOOKING_PATH}?paso=${step}`;
}
