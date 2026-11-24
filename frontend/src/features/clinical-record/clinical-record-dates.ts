import { InvalidInstantError, parseInstant, PRACTICE_ZONE } from '../../shared/utils/dates';

/** Formatea instantes del servidor en la zona del consultorio (D8). Nunca muestra el UTC crudo. */

const formatter = new Intl.DateTimeFormat('es-AR', {
  timeZone: PRACTICE_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

export function formatClinicalInstant(instant: string, suffix?: string): string {
  try {
    const date = parseInstant(instant);
    const parts = Object.fromEntries(
      formatter.formatToParts(date).filter((part) => part.type !== 'literal').map((part) => [part.type, part.value]),
    );
    const base = `${parts['day']}/${parts['month']}/${parts['year']} ${parts['hour']}:${parts['minute']}`;
    if (suffix === undefined || suffix === '') {
      return base;
    }
    return suffix.startsWith('UTC') ? `${base} ${suffix}` : `${base} · ${suffix}`;
  } catch (error) {
    if (error instanceof InvalidInstantError) {
      throw new InvalidInstantError('Instante inválido');
    }
    throw error;
  }
}
