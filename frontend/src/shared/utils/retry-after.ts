/** Segundos de espera -> texto aproximado en minutos ("en 1 minuto", "en unos 14 minutos"). */
export function describeWait(seconds: number | undefined): string {
  if (seconds === undefined || !Number.isFinite(seconds) || seconds <= 0) {
    return 'en unos minutos';
  }
  const minutes = Math.ceil(seconds / 60);
  return minutes === 1 ? 'en 1 minuto' : `en unos ${minutes} minutos`;
}
