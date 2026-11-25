import type { ApiError, FieldError } from '../../core/http/errors';
import { formatDateTime } from '../../shared/utils/dates';

/** Única fuente de textos y de clasificación de fallos de pacientes (D4). */

export type PatientsFailure =
  | { readonly kind: 'connection' }
  | { readonly kind: 'forbidden' }
  | { readonly kind: 'not-found' }
  | { readonly kind: 'stale-page' }
  | { readonly kind: 'release' }
  | { readonly kind: 'invalid'; readonly errors: readonly FieldError[] };

export interface MessageCopy {
  readonly title: string;
  readonly text: string;
}

export const MESSAGES = {
  loadError: { title: 'No pudimos cargar los pacientes.', text: 'Revisá tu conexión e intentá de nuevo.' },
  detailError: { title: 'No pudimos cargar la ficha.', text: 'Revisá tu conexión e intentá de nuevo.' },
  saveError: { title: 'No pudimos guardar los cambios.', text: 'Revisá tu conexión e intentá de nuevo.' },
  deactivateError: {
    title: 'No pudimos confirmar la baja.',
    text: 'La conexión se cortó y no sabemos si se completó. Revisá la ficha actual antes de reintentar.',
  },
  releaseError: {
    title: 'No pudimos revisar los turnos del paciente.',
    text: 'No se dio de baja la ficha ni se canceló ningún turno pendiente. Revisá tu conexión e intentá de nuevo.',
  },
  notFound: { title: 'No encontramos este paciente', text: 'Puede que ya no esté disponible. Volvé al listado para continuar.' },
  stalePage: {
    title: 'La página quedó desactualizada.',
    text: 'Recargala e intentá de nuevo. No se hizo ningún cambio.',
  },
  invalid: { title: 'Revisá los campos marcados.', text: 'Corregí los valores indicados antes de guardar.' },
  unknownField: { title: 'Revisá los campos marcados.', text: 'El servidor rechazó un dato. Revisá la ficha y volvé a intentar.' },
  forbidden: { title: 'No tenés permiso para esta acción.', text: 'Si creés que es un error, recargá la página.' },
  saved: { title: 'Ficha actualizada.', text: 'Los cambios se guardaron correctamente.' },
  deactivated: {
    title: 'Paciente dado de baja.',
    text: 'La ficha quedó inactiva y no aparece en las búsquedas. Su historia clínica se conserva.',
  },
  review: {
    title: 'Datos para revisar',
    text: 'Los datos informados en una reserva no reemplazaron los de esta ficha. Revisalos antes de guardar cambios.',
  },
  noPatients: {
    title: 'Todavía no hay pacientes',
    text: 'Cuando registres un paciente desde la agenda o por una reserva, aparecerá acá.',
  },
  noResults: {
    title: 'No encontramos pacientes con ese criterio',
    text: 'Probá buscar por DNI o teléfono, o revisá la ortografía del nombre.',
  },
} as const;

/** El servidor expone `status`/`code`; `title`/`detail` nunca se leen ni se muestran. */
export function toPatientsFailure(error: ApiError): PatientsFailure {
  if (error.kind !== 'problem') {
    return { kind: 'connection' };
  }
  if (error.status === 404) {
    return { kind: 'not-found' };
  }
  if (error.status === 403 && error.code !== 'CSRF_REQUIRED') {
    return { kind: 'forbidden' };
  }
  switch (error.code) {
    case 'FORBIDDEN':
      return { kind: 'forbidden' };
    case 'CSRF_REQUIRED':
      return { kind: 'stale-page' };
    case 'VALIDATION_FAILED':
      return { kind: 'invalid', errors: error.errors };
    default:
      return { kind: 'connection' };
  }
}

/** Texto según la operación: la baja y el guardado nunca afirman un resultado dudoso. */
export function failureCopy(failure: PatientsFailure, operation: 'load' | 'detail' | 'save' | 'deactivate'): MessageCopy {
  if (failure.kind === 'not-found') {
    return MESSAGES.notFound;
  }
  if (failure.kind === 'release') {
    return MESSAGES.releaseError;
  }
  if (failure.kind === 'stale-page') {
    return MESSAGES.stalePage;
  }
  if (failure.kind === 'forbidden') {
    return MESSAGES.forbidden;
  }
  if (failure.kind === 'invalid') {
    return MESSAGES.invalid;
  }
  switch (operation) {
    case 'load':
      return MESSAGES.loadError;
    case 'detail':
      return MESSAGES.detailError;
    case 'save':
      return MESSAGES.saveError;
    case 'deactivate':
      return MESSAGES.deactivateError;
  }
}

export interface ReleaseSummary {
  readonly released: number;
  readonly failed: readonly { readonly startsAt: string }[];
}

const turnos = (n: number): string => `${n} ${n === 1 ? 'turno' : 'turnos'}`;

/** Mensaje de éxito de la baja según los turnos liberados (sin datos personales). */
export function deactivatedCopy(release?: ReleaseSummary): { readonly copy: MessageCopy; readonly warning: boolean } {
  if (release === undefined) {
    return { copy: MESSAGES.deactivated, warning: false };
  }
  if (release.failed.length > 0) {
    const when = release.failed.map((a) => formatDateTime(a.startsAt)).join(', ');
    return {
      warning: true,
      copy: {
        title: 'Paciente dado de baja, pero quedaron turnos sin liberar.',
        text: `Se dio de baja al paciente y se liberó ${turnos(release.released)}. No pudimos liberar ${turnos(release.failed.length)} (${when}): cancelalos desde la agenda. Su historia clínica se conserva.`,
      },
    };
  }
  if (release.released === 0) {
    return {
      warning: false,
      copy: { title: MESSAGES.deactivated.title, text: `${MESSAGES.deactivated.text} No tenía turnos futuros para liberar.` },
    };
  }
  return {
    warning: false,
    copy: {
      title: MESSAGES.deactivated.title,
      text: `Se dio de baja al paciente y se liberaron ${turnos(release.released)}. Su historia clínica se conserva.`,
    },
  };
}
