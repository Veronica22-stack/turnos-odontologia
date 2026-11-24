import type { ClinicalResult } from './clinical-record-gateway';

/** Transforma un fallo de sesión en la señal única que descarta los borradores en memoria. */
export function isSessionExpired<T>(result: ClinicalResult<T>): boolean {
  return !result.ok && result.failure.kind === 'unauthorized';
}
