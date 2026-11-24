import type { ApiError } from '../../core/http/errors';

/** Única fuente de textos y clasificación de fallos clínicos (D4). Nunca se leen `title`, `detail` ni valores rechazados. */

export type ClinicalFailure =
  | { readonly kind: 'connection' }
  | { readonly kind: 'unauthorized' }
  | { readonly kind: 'validation' }
  | { readonly kind: 'patient-not-found' }
  | { readonly kind: 'patient-inactive' }
  | { readonly kind: 'entry-not-found' }
  | { readonly kind: 'already-corrected' }
  | { readonly kind: 'not-current' }
  | { readonly kind: 'concurrent-write' }
  | { readonly kind: 'appointment-not-started' }
  | { readonly kind: 'appointment-not-attended' }
  | { readonly kind: 'appointment-not-found' }
  | { readonly kind: 'surface-not-allowed' };

export interface MessageCopy {
  readonly title: string;
  readonly text: string;
}

const RELOAD = 'Recargá la historia clínica para ver la versión vigente y volvé a intentarlo.';

export const MESSAGES: Readonly<Record<ClinicalFailure['kind'], MessageCopy>> = {
  connection: { title: 'No pudimos completar la operación.', text: 'Revisá tu conexión e intentá de nuevo.' },
  unauthorized: { title: 'Tu sesión venció.', text: 'Ingresá de nuevo para continuar.' },
  validation: { title: 'Revisá el campo señalado.', text: 'El valor no cumple con el formato permitido; corregilo sin repetirlo acá.' },
  'patient-not-found': { title: 'No encontramos este paciente', text: 'Puede que ya no esté disponible. Elegí otro paciente.' },
  'patient-inactive': { title: 'El paciente está inactivo', text: 'No se puede registrar historia clínica para un paciente inactivo.' },
  'entry-not-found': { title: 'No encontramos la entrada', text: 'Puede que ya no esté disponible. Recargá la historia clínica.' },
  'already-corrected': { title: 'Esta entrada ya fue corregida', text: RELOAD },
  'not-current': { title: 'La entrada ya no es la vigente', text: RELOAD },
  'concurrent-write': { title: 'Otra actualización se registró primero', text: RELOAD },
  'appointment-not-started': { title: 'El turno todavía no comenzó', text: 'Elegí un turno iniciado o registrá la evolución sin turno.' },
  'appointment-not-attended': { title: 'El turno no fue marcado como asistido', text: 'Registrá la asistencia del turno antes de asociarlo.' },
  'appointment-not-found': { title: 'No encontramos el turno', text: 'Revisá la selección y volvé a intentarlo.' },
  'surface-not-allowed': { title: 'La cara no corresponde al estado', text: 'Las caras solo se permiten en estados de caries u obturación.' },
};

/** El servidor expone `status` y `code`; `title`, `detail` y valores rechazados nunca se leen. */
export function toClinicalFailure(error: ApiError): ClinicalFailure {
  if (error.kind === 'unauthorized') {
    return { kind: 'unauthorized' };
  }
  if (error.kind !== 'problem') {
    return { kind: 'connection' };
  }
  switch (error.code) {
    case 'VALIDATION_FAILED': return { kind: 'validation' };
    case 'PATIENT_NOT_FOUND': return { kind: 'patient-not-found' };
    case 'PATIENT_INACTIVE': return { kind: 'patient-inactive' };
    case 'CLINICAL_ENTRY_NOT_FOUND': return { kind: 'entry-not-found' };
    case 'CLINICAL_ENTRY_ALREADY_CORRECTED': return { kind: 'already-corrected' };
    case 'CLINICAL_ENTRY_NOT_CURRENT': return { kind: 'not-current' };
    case 'CLINICAL_RECORD_CONCURRENT_WRITE': return { kind: 'concurrent-write' };
    case 'EVOLUTION_APPOINTMENT_NOT_STARTED': return { kind: 'appointment-not-started' };
    case 'EVOLUTION_APPOINTMENT_NOT_ATTENDED': return { kind: 'appointment-not-attended' };
    case 'EVOLUTION_APPOINTMENT_NOT_FOUND': return { kind: 'appointment-not-found' };
    case 'ODONTOGRAM_SURFACE_NOT_ALLOWED': return { kind: 'surface-not-allowed' };
    default: return { kind: 'connection' };
  }
}

export function clinicalFailureCopy(failure: ClinicalFailure): MessageCopy {
  return MESSAGES[failure.kind];
}
