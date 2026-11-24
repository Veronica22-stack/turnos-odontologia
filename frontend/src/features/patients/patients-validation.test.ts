import { describe, expect, it } from 'vitest';
import type { FieldError } from '../../core/http/errors';
import type { PatientInput } from './patients-types';
import { mapServerErrors, validatePatientInput } from './patients-validation';

const VALID: PatientInput = {
  firstName: 'Julieta',
  lastName: 'Simulada',
  phone: '11 5555 0001',
  email: 'julieta@ejemplo.invalid',
  birthDate: '1992-04-18',
  address: 'Calle Falsa 123',
};

describe('patients-validation - reglas puras (D5)', () => {
  it('2.1 acepta un formulario válido y marca obligatorios, email y fecha', () => {
    expect(validatePatientInput(VALID)).toEqual({});
    const errors = validatePatientInput({
      ...VALID,
      firstName: '   ',
      lastName: '',
      phone: '  ',
      email: 'sin-arroba',
      birthDate: '2026-02-30',
    });
    expect(Object.keys(errors).sort()).toEqual(['birthDate', 'email', 'firstName', 'lastName', 'phone']);
    expect(Object.values(errors).every((text) => text.length > 0)).toBe(true);
    expect(JSON.stringify(errors)).not.toContain('sin-arroba');
  });

  it('2.1 triangula fechas, longitudes y opcionales vacíos sin repetir valores', () => {
    expect(validatePatientInput({ ...VALID, birthDate: '18/04/1992' })).toHaveProperty('birthDate');
    expect(validatePatientInput({ ...VALID, birthDate: '' })).toEqual({});
    expect(validatePatientInput({ ...VALID, address: 'x'.repeat(256) })).toHaveProperty('address');
    expect(validatePatientInput({ ...VALID, firstName: 'x'.repeat(101) })).toHaveProperty('firstName');
    expect(validatePatientInput({ ...VALID, email: '', address: '' })).toEqual({});
  });

  it('2.2 mapea errores del servidor y avisa de campos desconocidos sin title/detail', () => {
    const errors: readonly FieldError[] = [
      { field: 'phone', code: 'NotBlank' },
      { field: 'firstName', code: 'Size' },
      { field: 'borrado', code: 'AlgoInesperado' },
    ];
    expect(mapServerErrors(errors)).toEqual({ fields: { phone: 'El teléfono no puede estar vacío.', firstName: 'El nombre es demasiado largo.' }, hasUnknown: true });
    expect(mapServerErrors([{ field: 'email', code: 'Email' }])).toEqual({ fields: { email: 'Ingresá un email válido.' }, hasUnknown: false });
    expect(mapServerErrors([])).toEqual({ fields: {}, hasUnknown: false });
  });
});
