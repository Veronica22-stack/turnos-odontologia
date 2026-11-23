import { describe, expect, it } from 'vitest';
import { normalizeDni, validateBookingForm, type BookingFormValues } from './validation';

const VALID: BookingFormValues = {
  firstName: 'Ana',
  lastName: 'Pérez',
  dni: '30456789',
  phone: '11 2345 6789',
  email: 'ana@ejemplo.com',
  website: '',
  privacyAccepted: true,
};

function errorsOf(overrides: Partial<BookingFormValues>): Record<string, string> {
  const result = validateBookingForm({ ...VALID, ...overrides });
  if (result.ok) {
    return {};
  }
  return Object.fromEntries(result.errors.map((e) => [e.field, e.message]));
}

describe('validateBookingForm', () => {
  it('con el formulario vacio informa todos los campos en orden', () => {
    const result = validateBookingForm({
      firstName: '',
      lastName: '',
      dni: '',
      phone: '',
      email: '',
      website: '',
      privacyAccepted: false,
    });

    expect(result.ok).toBe(false);
    expect(result.ok ? [] : result.errors.map((e) => e.field)).toEqual([
      'firstName',
      'lastName',
      'dni',
      'phone',
      'email',
      'privacyAccepted',
    ]);
  });

  it('acepta datos validos y recorta espacios, dejando el honeypot tal cual', () => {
    const result = validateBookingForm({ ...VALID, firstName: '  Ana ', dni: ' 30.456.789 ', website: '  x ' });

    expect(result).toEqual({
      ok: true,
      value: {
        firstName: 'Ana',
        lastName: 'Pérez',
        dni: '30.456.789',
        phone: '11 2345 6789',
        email: 'ana@ejemplo.com',
        website: '  x ',
        privacyAccepted: true,
      },
    });
  });

  it('limita nombre y apellido a 100 caracteres', () => {
    expect(errorsOf({ firstName: 'a'.repeat(101) })).toEqual({ firstName: 'Usá como máximo 100 caracteres.' });
    expect(errorsOf({ lastName: 'a'.repeat(101) })).toEqual({ lastName: 'Usá como máximo 100 caracteres.' });
    expect(errorsOf({ firstName: 'a'.repeat(100) })).toEqual({});
  });

  it('mide las longitudes en unidades UTF-16 como el servidor', () => {
    expect(errorsOf({ firstName: '😀'.repeat(50) })).toEqual({});
    expect(errorsOf({ firstName: '😀'.repeat(51) })).toEqual({ firstName: 'Usá como máximo 100 caracteres.' });
  });

  it.each([
    ['30.456.789', true],
    ['30 456 789', true],
    ['123456', true],
    ['123456789', true],
    ['12345', false],
    ['1234567890', false],
    ['abc', false],
  ])('DNI %s valido=%s', (dni, valid) => {
    const errors = errorsOf({ dni });

    expect(errors['dni']).toBe(valid ? undefined : 'Ingresá un DNI de 6 a 9 números.');
  });

  it('exige al menos 6 digitos y hasta 50 caracteres en el telefono', () => {
    expect(errorsOf({ phone: '12-34' })).toEqual({ phone: 'Ingresá un teléfono con código de área.' });
    expect(errorsOf({ phone: '+54 11 2345-6789' })).toEqual({});
    expect(errorsOf({ phone: '1'.repeat(51) })).toEqual({ phone: 'Usá como máximo 50 caracteres.' });
  });

  it('valida el formato y el largo del email', () => {
    expect(errorsOf({ email: 'ana@' })).toEqual({ email: 'Ingresá un email válido.' });
    expect(errorsOf({ email: 'ana@ejemplo' })).toEqual({ email: 'Ingresá un email válido.' });
    expect(errorsOf({ email: `${'a'.repeat(251)}@b.co` })).toEqual({ email: 'Usá como máximo 255 caracteres.' });
    expect(errorsOf({ email: `${'a'.repeat(250)}@b.co` })).toEqual({});
    expect(errorsOf({ email: ' ana@ejemplo.com ' })).toEqual({});
  });

  it('exige aceptar el aviso de privacidad', () => {
    expect(errorsOf({ privacyAccepted: false })).toEqual({
      privacyAccepted: 'Tenés que aceptar el aviso de privacidad para reservar.',
    });
  });
});

describe('normalizeDni', () => {
  it('quita todo lo que no es digito', () => {
    expect(normalizeDni('30.456.789')).toBe('30456789');
    expect(normalizeDni(' 30-456 789 ')).toBe('30456789');
    expect(normalizeDni('abc')).toBe('');
  });
});
