import type { ApiError } from '../../core/http/errors';

/** Única fuente de copy y de mapeo `code` → fallo de negocio (C-19 D3). */

export type SettingsFailure =
  | { readonly kind: 'fields'; readonly errors: readonly { readonly field: string; readonly code: string }[] }
  | { readonly kind: 'duration' }
  | { readonly kind: 'range' }
  | { readonly kind: 'conflict' }
  | { readonly kind: 'not-found' }
  | { readonly kind: 'stale-page' }
  | { readonly kind: 'connection' };

export interface MessageCopy {
  readonly title: string;
  readonly text: string;
}

export const MESSAGES = {
  saved: { title: 'Cambios guardados.', text: 'Los valores se actualizaron correctamente.' },
  savedPractice: {
    title: 'Cambios guardados.',
    text: 'Los valores del consultorio se actualizaron. Las demás secciones conservaron sus borradores.',
  },
  savedHours: {
    title: 'Horarios guardados.',
    text: 'Los tramos se actualizaron sin tocar los borradores de otras secciones.',
  },
  savedService: { title: 'Prestación guardada.', text: 'El cambio se aplicó sin tocar las otras secciones.' },
  savedBlock: { title: 'Bloqueo guardado.', text: 'El cambio se aplicó sin tocar las otras secciones.' },
  copied: { title: 'Link copiado.', text: 'Ya podés pegarlo donde quieras compartirlo.' },
  clipboardFallback: {
    title: 'No pudimos copiar automáticamente.',
    text: 'Seleccioná y copiá el enlace desde el campo.',
  },
  loadError: { title: 'No se pudo cargar esta sección.', text: 'Revisá tu conexión e intentá de nuevo.' },
  stalePage: { title: 'La página quedó desactualizada.', text: 'Recargá la página y volvé a intentarlo.' },
  connection: { title: 'No se pudo completar la operación.', text: 'Revisá tu conexión e intentá de nuevo.' },
  duration: {
    title: 'No se guardó la prestación.',
    text: 'La duración debe ser múltiplo de la granularidad vigente.',
  },
  range: { title: 'El rango no es válido.', text: 'Revisá la hora de inicio y la de fin.' },
  conflict: {
    title: 'Conflicto: el rango se superpone con otro bloqueo.',
    text: 'Elegí otro horario. No se creó el bloqueo.',
  },
  notFound: { title: 'El registro ya no existe.', text: 'Actualizá el listado para continuar.' },
} as const;

/** El servidor expone `code`; `title`/`detail` nunca se usan como texto de la UI. */
export function toSettingsFailure(error: ApiError): SettingsFailure {
  if (error.kind !== 'problem') {
    return { kind: 'connection' };
  }
  switch (error.code) {
    case 'VALIDATION_FAILED':
      return error.errors.length > 0 ? { kind: 'fields', errors: error.errors } : { kind: 'connection' };
    case 'DURATION_NOT_MULTIPLE_OF_GRANULARITY':
      return { kind: 'duration' };
    case 'INVALID_RANGE':
      return { kind: 'range' };
    case 'SCHEDULE_BLOCK_OVERLAP':
      return { kind: 'conflict' };
    case 'SERVICE_NOT_FOUND':
    case 'SCHEDULE_BLOCK_NOT_FOUND':
      return { kind: 'not-found' };
    case 'CSRF_REQUIRED':
      return { kind: 'stale-page' };
    default:
      return { kind: 'connection' };
  }
}

const FAILURE_COPY: Readonly<Record<SettingsFailure['kind'], MessageCopy>> = {
  fields: { title: 'Revisá los campos marcados.', text: 'Corregí los valores indicados antes de guardar.' },
  duration: MESSAGES.duration,
  range: MESSAGES.range,
  conflict: MESSAGES.conflict,
  'not-found': MESSAGES.notFound,
  'stale-page': MESSAGES.stalePage,
  connection: MESSAGES.connection,
};

export function failureCopy(failure: SettingsFailure): MessageCopy {
  return FAILURE_COPY[failure.kind];
}

const FIELD_COPY: Readonly<Record<string, string>> = {
  NotBlank: 'Este campo es obligatorio.',
  NotNull: 'Este campo es obligatorio.',
  Size: 'El texto supera la longitud permitida.',
  Min: 'El valor es menor al permitido.',
  Max: 'El valor supera el permitido.',
  VALIDATION_FAILED: 'Revisá este valor.',
};

/** Mensaje por campo a partir del `code` del servidor; nunca devuelve texto técnico crudo. */
export function fieldMessage(code: string, field: string): string {
  return FIELD_COPY[code] ?? `Revisá el valor de ${field}.`;
}
