import type { FieldError } from '../../core/http/errors';
import { isLocalDate, type PatientInput } from './patients-types';

/** Validación local pura del formulario de ficha (D5). No incluye valores rechazados en el mensaje. */

export type PatientField = keyof PatientInput;
export type FieldErrors = Readonly<Partial<Record<PatientField, string>>>;

const LIMITS: Readonly<Record<PatientField, number>> = {
  firstName: 100,
  lastName: 100,
  phone: 50,
  email: 255,
  birthDate: 10,
  address: 255,
};

const LABELS: Readonly<Record<PatientField, string>> = {
  firstName: 'El nombre',
  lastName: 'El apellido',
  phone: 'El teléfono',
  email: 'El email',
  birthDate: 'La fecha de nacimiento',
  address: 'La dirección',
};

const SIMPLE_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const KNOWN_FIELDS: ReadonlySet<string> = new Set<PatientField>([
  'firstName',
  'lastName',
  'phone',
  'email',
  'birthDate',
  'address',
]);

function isKnownField(field: string): field is PatientField {
  return KNOWN_FIELDS.has(field);
}

function copy(field: PatientField, code: 'required' | 'length' | 'email' | 'date'): string {
  switch (code) {
    case 'required':
      return `${LABELS[field]} no puede estar vacío.`;
    case 'length':
      return `${LABELS[field]} es demasiado largo.`;
    case 'email':
      return 'Ingresá un email válido.';
    case 'date':
      return 'Ingresá una fecha válida (AAAA-MM-DD).';
  }
}

export function validatePatientInput(input: PatientInput): FieldErrors {
  const errors: Partial<Record<PatientField, string>> = {};
  const required = ['firstName', 'lastName', 'phone'] as const;
  for (const field of required) {
    const value = input[field];
    if (value.trim() === '') {
      errors[field] = copy(field, 'required');
    } else if (value.length > LIMITS[field]) {
      errors[field] = copy(field, 'length');
    }
  }
  if (input.email !== null && input.email.trim() !== '') {
    if (input.email.length > LIMITS.email) {
      errors.email = copy('email', 'length');
    } else if (!SIMPLE_EMAIL.test(input.email)) {
      errors.email = copy('email', 'email');
    }
  }
  if (input.birthDate !== null && input.birthDate !== '' && !isLocalDate(input.birthDate)) {
    errors.birthDate = copy('birthDate', 'date');
  }
  if (input.address !== null && input.address.length > LIMITS.address) {
    errors.address = copy('address', 'length');
  }
  return errors;
}

/** Los `field` del `problem` se reducen a mensajes propios; los desconocidos solo activan un aviso genérico. */
export function mapServerErrors(errors: readonly FieldError[]): { readonly fields: FieldErrors; readonly hasUnknown: boolean } {
  const fields: Partial<Record<PatientField, string>> = {};
  let hasUnknown = false;
  for (const item of errors) {
    if (!isKnownField(item.field)) {
      hasUnknown = true;
      continue;
    }
    if (fields[item.field] !== undefined) {
      continue;
    }
    if (item.code.toLowerCase().includes('blank')) {
      fields[item.field] = copy(item.field, 'required');
    } else if (item.field === 'email' && item.code.toLowerCase().includes('email')) {
      fields[item.field] = copy('email', 'email');
    } else if (item.field === 'birthDate') {
      fields[item.field] = copy('birthDate', 'date');
    } else {
      fields[item.field] = copy(item.field, 'length');
    }
  }
  return { fields, hasUnknown };
}
