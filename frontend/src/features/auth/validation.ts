/** Validacion local de los formularios de acceso (funciones puras, sin DOM). */

export interface FieldMessage {
  readonly field: string;
  readonly message: string;
}

export type Validation<T> =
  | { readonly ok: true; readonly value: T }
  | { readonly ok: false; readonly errors: readonly FieldMessage[] };

export interface LoginInput {
  readonly email: string;
  readonly password: string;
}

export interface PasswordChangeInput {
  readonly currentPassword: string;
  readonly newPassword: string;
  readonly confirmPassword: string;
}

export interface PasswordChangeValue {
  readonly currentPassword: string;
  readonly newPassword: string;
}

export interface PasswordRules {
  readonly minLength: boolean;
  readonly differs: boolean;
  readonly matches: boolean;
}

/** Misma politica que el servidor (C-04): longitud en unidades UTF-16 (`String.length`). */
export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 128;

const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateLogin(input: LoginInput): Validation<LoginInput> {
  const email = input.email.trim();
  const errors: FieldMessage[] = [];
  if (email === '') {
    errors.push({ field: 'email', message: 'Ingresá tu email.' });
  } else if (!EMAIL_SHAPE.test(email)) {
    errors.push({ field: 'email', message: 'Ingresá un email válido.' });
  }
  if (input.password === '') {
    errors.push({ field: 'password', message: 'Ingresá tu contraseña.' });
  }
  return errors.length > 0 ? { ok: false, errors } : { ok: true, value: { email, password: input.password } };
}

function newPasswordError(input: PasswordChangeInput): string | undefined {
  const { newPassword, currentPassword } = input;
  if (newPassword.trim() === '') {
    return 'Ingresá una contraseña nueva.';
  }
  if (newPassword.length < PASSWORD_MIN_LENGTH) {
    return `Usá al menos ${PASSWORD_MIN_LENGTH} caracteres.`;
  }
  if (newPassword.length > PASSWORD_MAX_LENGTH) {
    return `Usá como máximo ${PASSWORD_MAX_LENGTH} caracteres.`;
  }
  if (newPassword === currentPassword) {
    return 'La contraseña nueva tiene que ser distinta de la actual.';
  }
  return undefined;
}

export function validatePasswordChange(input: PasswordChangeInput): Validation<PasswordChangeValue> {
  const errors: FieldMessage[] = [];
  if (input.currentPassword === '') {
    errors.push({ field: 'currentPassword', message: 'Ingresá tu contraseña actual.' });
  }
  const newError = newPasswordError(input);
  if (newError !== undefined) {
    errors.push({ field: 'newPassword', message: newError });
  }
  if (input.confirmPassword === '') {
    errors.push({ field: 'confirmPassword', message: 'Repetí la contraseña nueva.' });
  } else if (input.confirmPassword !== input.newPassword) {
    errors.push({ field: 'confirmPassword', message: 'Las contraseñas no coinciden.' });
  }
  return errors.length > 0
    ? { ok: false, errors }
    : { ok: true, value: { currentPassword: input.currentPassword, newPassword: input.newPassword } };
}

/** Estado de cada requisito para la lista en vivo (sin mensajes: la vista decide como mostrarlos). */
export function passwordRules(input: PasswordChangeInput): PasswordRules {
  const { newPassword } = input;
  return {
    minLength: newPassword.length >= PASSWORD_MIN_LENGTH,
    differs: newPassword !== '' && newPassword !== input.currentPassword,
    matches: input.confirmPassword !== '' && input.confirmPassword === newPassword,
  };
}
