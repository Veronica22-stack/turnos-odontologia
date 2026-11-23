import type { FieldMessage, Validation } from '../auth/validation';

/** Validacion local del formulario de reserva (funciones puras, sin DOM). Design D5. */

export interface BookingFormValues {
  readonly firstName: string;
  readonly lastName: string;
  readonly dni: string;
  readonly phone: string;
  readonly email: string;
  /** Honeypot: viaja tal cual, sin recortar. */
  readonly website: string;
  readonly privacyAccepted: boolean;
}

export const EMPTY_BOOKING_FORM: BookingFormValues = {
  firstName: '',
  lastName: '',
  dni: '',
  phone: '',
  email: '',
  website: '',
  privacyAccepted: false,
};

/** Mismos limites que el servidor (C-14), en unidades UTF-16 como `String.length()`. */
const NAME_MAX = 100;
const PHONE_MAX = 50;
const EMAIL_MAX = 255;
const DNI_MIN_DIGITS = 6;
const DNI_MAX_DIGITS = 9;
/** Solo del cliente: atrapa errores de tipeo (el servidor exige NotBlank). */
const PHONE_MIN_DIGITS = 6;
const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const DNI_MESSAGE = 'Ingresá un DNI de 6 a 9 números.';
export const EMAIL_MESSAGE = 'Ingresá un email válido.';
const PHONE_MESSAGE = 'Ingresá un teléfono con código de área.';

function tooLong(max: number): string {
  return `Usá como máximo ${max} caracteres.`;
}

/** Igual que `Patient.normalizeAndValidateDni` del servidor: solo digitos. */
export function normalizeDni(value: string): string {
  return value.replace(/\D/g, '');
}

function digitCount(value: string): number {
  return normalizeDni(value).length;
}

function nameError(value: string, required: string): string | undefined {
  if (value === '') {
    return required;
  }
  return value.length > NAME_MAX ? tooLong(NAME_MAX) : undefined;
}

export function validateBookingForm(input: BookingFormValues): Validation<BookingFormValues> {
  const value: BookingFormValues = {
    ...input,
    firstName: input.firstName.trim(),
    lastName: input.lastName.trim(),
    dni: input.dni.trim(),
    phone: input.phone.trim(),
    email: input.email.trim(),
  };
  const errors: FieldMessage[] = [];
  const add = (field: string, message: string | undefined): void => {
    if (message !== undefined) {
      errors.push({ field, message });
    }
  };

  add('firstName', nameError(value.firstName, 'Ingresá tu nombre.'));
  add('lastName', nameError(value.lastName, 'Ingresá tu apellido.'));

  const dniDigits = digitCount(value.dni);
  add('dni', dniDigits < DNI_MIN_DIGITS || dniDigits > DNI_MAX_DIGITS ? DNI_MESSAGE : undefined);

  if (value.phone.length > PHONE_MAX) {
    add('phone', tooLong(PHONE_MAX));
  } else {
    add('phone', digitCount(value.phone) < PHONE_MIN_DIGITS ? PHONE_MESSAGE : undefined);
  }

  if (value.email.length > EMAIL_MAX) {
    add('email', tooLong(EMAIL_MAX));
  } else {
    add('email', EMAIL_SHAPE.test(value.email) ? undefined : value.email === '' ? 'Ingresá tu email.' : EMAIL_MESSAGE);
  }

  add('privacyAccepted', value.privacyAccepted ? undefined : 'Tenés que aceptar el aviso de privacidad para reservar.');

  return errors.length === 0 ? { ok: true, value } : { ok: false, errors };
}
