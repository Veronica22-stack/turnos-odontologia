import { describeWait } from '../../shared/utils/retry-after';
import { DNI_MESSAGE, EMAIL_MESSAGE } from './validation';

/** Unica fuente de copy de la reserva (design D7): nunca se muestra `title`/`detail` del servidor. */

export interface BookingNotice {
  readonly variant: 'info' | 'warning' | 'danger';
  readonly title: string;
  readonly detail: string;
  /** Marca de dato pendiente (TBD) que se muestra junto al texto. */
  readonly tbd?: string;
}

export interface EmptyMessage {
  readonly title: string;
  /** Sin punto final cuando hay `tbd`: el punto va despues de la marca. */
  readonly detail: string;
  readonly tbd?: string;
}

export type NoticeKind =
  | { readonly kind: 'slot-unavailable' }
  | { readonly kind: 'rate-limited'; readonly retryAfterSeconds?: number }
  | { readonly kind: 'contact-limit' }
  | { readonly kind: 'service-gone' }
  | { readonly kind: 'stale-page' }
  | { readonly kind: 'unknown-outcome' }
  | { readonly kind: 'connection'; readonly what: 'services' | 'slots' };

export function bookingNotice(notice: NoticeKind): BookingNotice {
  switch (notice.kind) {
    case 'slot-unavailable':
      return {
        variant: 'warning',
        title: 'Ese horario se acaba de ocupar.',
        detail: 'Elegí otro. Te mostramos los más cercanos; tus datos quedan guardados.',
      };
    case 'rate-limited':
      return {
        variant: 'warning',
        title: 'Demasiados intentos seguidos.',
        detail: `Por seguridad, esperá un momento. Probá de nuevo ${describeWait(notice.retryAfterSeconds)}.`,
      };
    case 'contact-limit':
      return {
        variant: 'warning',
        title: 'Ya tenés turnos reservados.',
        detail:
          'Con estos datos ya hay varios turnos próximos. Para cambiarlos usá el enlace del email de confirmación o comunicate con el consultorio.',
        tbd: '(TBD · PA-05)',
      };
    case 'service-gone':
      return {
        variant: 'info',
        title: 'Esa prestación ya no está disponible para reservar online.',
        detail: 'Elegí otra.',
      };
    case 'stale-page':
      return {
        variant: 'danger',
        title: 'La página quedó desactualizada.',
        detail: 'Recargala e intentá de nuevo. Tus datos no se enviaron.',
      };
    case 'unknown-outcome':
      return {
        variant: 'danger',
        title: 'No pudimos confirmar tu reserva.',
        detail:
          'Puede que se haya registrado igual. Revisá tu email antes de intentar de nuevo; si no te llegó nada, volvé a confirmar.',
      };
    case 'connection':
      return {
        variant: 'danger',
        title: notice.what === 'slots' ? 'No pudimos cargar los horarios.' : 'No pudimos cargar las prestaciones.',
        detail: 'Revisá tu conexión.',
      };
  }
}

export function noServicesMessage(): EmptyMessage {
  return {
    title: 'Reservas online no disponibles',
    detail: 'Por ahora no se pueden reservar turnos desde acá. Comunicate con el consultorio',
    tbd: '(TBD · datos)',
  };
}

const GENERIC_FIELD = 'Revisá este dato.';
const PRIVACY = 'Tenés que aceptar el aviso de privacidad para reservar.';

const FIELD_MESSAGES: Readonly<Record<string, Readonly<Record<string, string>>>> = {
  firstName: { NotBlank: 'Ingresá tu nombre.', Size: 'Usá como máximo 100 caracteres.' },
  lastName: { NotBlank: 'Ingresá tu apellido.', Size: 'Usá como máximo 100 caracteres.' },
  phone: { NotBlank: 'Ingresá un teléfono con código de área.', Size: 'Usá como máximo 50 caracteres.' },
  email: { NotBlank: 'Ingresá tu email.', Email: EMAIL_MESSAGE, Size: 'Usá como máximo 255 caracteres.' },
  privacyAccepted: { AssertTrue: PRIVACY, NotNull: PRIVACY },
};

/** Mensaje para un error de campo del servidor; `undefined` si el campo no es del formulario. */
export function serverFieldMessage(field: string, code: string): string | undefined {
  if (field === 'dni') {
    // El `code` del DNI es texto libre en el servidor (pedido P4): cualquier codigo, el mismo mensaje.
    return DNI_MESSAGE;
  }
  const byCode = FIELD_MESSAGES[field];
  return byCode === undefined ? undefined : (byCode[code] ?? GENERIC_FIELD);
}
