/**
 * Confirmacion en memoria (design D6): variable de modulo que se consume una sola vez.
 * Nunca storage ni URL. El email se guarda ya enmascarado: el completo no se conserva tras el envio.
 */

export interface ConfirmationData {
  readonly serviceName: string;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly practiceName: string;
  readonly maskedEmail: string;
}

let pending: ConfirmationData | null = null;

export function setConfirmation(data: ConfirmationData): void {
  pending = data;
}

/** Lee y borra: recargar, abrir directo o volver a entrar no repite los datos. */
export function consumeConfirmation(): ConfirmationData | null {
  const current = pending;
  pending = null;
  return current;
}

export function clearConfirmation(): void {
  pending = null;
}
