import { describe, expect, it } from 'vitest';
import {
  CURRENT_PASSWORD_INVALID,
  changePasswordFailureMessage,
  fieldErrorMessage,
  loginFailureMessage,
  messageText,
  noticeMessage,
} from './auth-messages';

describe('loginFailureMessage', () => {
  it('credenciales invalidas: texto generico fijo', () => {
    expect(loginFailureMessage({ kind: 'invalid-credentials' })).toEqual({
      title: 'Email o contraseña incorrectos.',
      detail: 'Revisá los datos e intentá de nuevo.',
    });
  });

  it('limite de tasa: titulo y cuerpo con el tiempo de Retry-After', () => {
    expect(loginFailureMessage({ kind: 'locked', retryAfterSeconds: 45 })).toEqual({
      title: 'Ingreso bloqueado temporalmente',
      detail: 'Por seguridad, hubo demasiados intentos. Probá de nuevo en 1 minuto.',
    });
    expect(loginFailureMessage({ kind: 'locked', retryAfterSeconds: 840 }).detail).toBe(
      'Por seguridad, hubo demasiados intentos. Probá de nuevo en unos 14 minutos.',
    );
  });

  it('limite de tasa sin Retry-After: en unos minutos', () => {
    expect(loginFailureMessage({ kind: 'locked' }).detail).toBe(
      'Por seguridad, hubo demasiados intentos. Probá de nuevo en unos minutos.',
    );
  });

  it('conexion y pagina desactualizada', () => {
    expect(loginFailureMessage({ kind: 'connection' })).toEqual({
      title: 'No pudimos conectar con el servidor.',
      detail: 'Revisá tu conexión e intentá de nuevo.',
    });
    expect(loginFailureMessage({ kind: 'stale-page' })).toEqual({
      title: 'La página quedó desactualizada.',
      detail: 'Recargala e intentá de nuevo.',
    });
  });
});

describe('changePasswordFailureMessage', () => {
  it('reutiliza los textos de conexion y pagina desactualizada', () => {
    expect(changePasswordFailureMessage('connection')).toBe(
      'No pudimos conectar con el servidor. Revisá tu conexión e intentá de nuevo.',
    );
    expect(changePasswordFailureMessage('stale-page')).toBe('La página quedó desactualizada. Recargala e intentá de nuevo.');
  });
});

describe('fieldErrorMessage', () => {
  it.each([
    ['newPassword', 'Size', 'Usá entre 12 y 128 caracteres.'],
    ['newPassword', 'NotBlank', 'Ingresá una contraseña nueva.'],
    ['currentPassword', 'NotBlank', 'Ingresá tu contraseña actual.'],
    ['currentPassword', 'Size', CURRENT_PASSWORD_INVALID],
    ['otroCampo', 'Pattern', 'Revisá este dato.'],
  ])('%s/%s -> %s (nunca el texto del servidor)', (field, code, expected) => {
    expect(fieldErrorMessage(field, code)).toBe(expected);
  });
});

describe('noticeMessage', () => {
  it.each([
    ['expired', 'Tu sesión venció. Ingresá de nuevo.'],
    ['offline', 'Sin conexión con el servidor. Reintentá en unos segundos.'],
    ['password-changed', 'Contraseña actualizada. Ingresá de nuevo con la contraseña nueva.'],
  ] as const)('%s', (notice, text) => {
    expect(noticeMessage(notice)).toBe(text);
  });
});

describe('messageText', () => {
  it('une titulo y detalle con un espacio; sin detalle devuelve solo el titulo', () => {
    expect(messageText({ title: 'A.', detail: 'B.' })).toBe('A. B.');
    expect(messageText({ title: 'Solo.' })).toBe('Solo.');
  });
});
