import { describeWait } from './retry-after';

/** Unica fuente de copy de error de acceso: nunca se muestra `title`/`detail` del servidor. */

export type LoginFailure =
  | { readonly kind: 'invalid-credentials' }
  | { readonly kind: 'locked'; readonly retryAfterSeconds?: number }
  | { readonly kind: 'stale-page' }
  | { readonly kind: 'connection' };

export type SessionNotice = 'expired' | 'offline' | 'password-changed';

export interface AlertMessage {
  readonly title: string;
  readonly detail?: string;
}

const CONNECTION: AlertMessage = {
  title: 'No pudimos conectar con el servidor.',
  detail: 'Revisá tu conexión e intentá de nuevo.',
};
const STALE_PAGE: AlertMessage = { title: 'La página quedó desactualizada.', detail: 'Recargala e intentá de nuevo.' };

/** Titulo y detalle como una sola frase (para mensajes sin jerarquia visual). */
export function messageText(message: AlertMessage): string {
  return message.detail === undefined ? message.title : `${message.title} ${message.detail}`;
}

export const CURRENT_PASSWORD_INVALID = 'La contraseña actual no es correcta.';

const NOTICES: Record<SessionNotice, string> = {
  expired: 'Tu sesión venció. Ingresá de nuevo.',
  offline: 'Sin conexión con el servidor. Reintentá en unos segundos.',
  'password-changed': 'Contraseña actualizada. Ingresá de nuevo con la contraseña nueva.',
};

export function noticeMessage(notice: SessionNotice): string {
  return NOTICES[notice];
}

export function loginFailureMessage(failure: LoginFailure): AlertMessage {
  switch (failure.kind) {
    case 'invalid-credentials':
      return { title: 'Email o contraseña incorrectos.', detail: 'Revisá los datos e intentá de nuevo.' };
    case 'locked':
      return {
        title: 'Ingreso bloqueado temporalmente',
        detail: `Por seguridad, hubo demasiados intentos. Probá de nuevo ${describeWait(failure.retryAfterSeconds)}.`,
      };
    case 'stale-page':
      return STALE_PAGE;
    case 'connection':
      return CONNECTION;
  }
}

export function changePasswordFailureMessage(kind: 'connection' | 'stale-page'): string {
  return messageText(kind === 'connection' ? CONNECTION : STALE_PAGE);
}

/** Mensaje propio por campo y codigo de Bean Validation (`Size`, `NotBlank`). */
export function fieldErrorMessage(field: string, code: string): string {
  if (field === 'newPassword') {
    return code === 'NotBlank' ? 'Ingresá una contraseña nueva.' : 'Usá entre 12 y 128 caracteres.';
  }
  if (field === 'currentPassword') {
    return code === 'NotBlank' ? 'Ingresá tu contraseña actual.' : CURRENT_PASSWORD_INVALID;
  }
  return 'Revisá este dato.';
}
