import { dayLabels, formatTime, localDateOf } from '../../shared/utils/dates';
import { bookingNotice, type BookingNotice, type EmptyMessage } from '../booking/booking-messages';
import type { ManagedBooking } from './manage-types';

/** Unica fuente de copy de la gestion (design D8): nunca se muestra `title`/`detail` del servidor. */

export type ManageNoticeKind =
  | { readonly kind: 'not-modifiable' }
  | { readonly kind: 'slot-unavailable'; readonly currentStartsAt: string }
  | { readonly kind: 'stale-page' }
  | { readonly kind: 'unknown-cancel' }
  | { readonly kind: 'unknown-reschedule' }
  | { readonly kind: 'reschedule-unavailable' }
  | { readonly kind: 'connection' }
  | { readonly kind: 'rate-limited'; readonly retryAfterSeconds?: number };

export function manageNotice(notice: ManageNoticeKind): BookingNotice {
  switch (notice.kind) {
    case 'not-modifiable':
      return {
        variant: 'warning',
        title: 'Este turno ya no se puede modificar.',
        detail: 'Puede que ya haya empezado o que el consultorio lo haya cambiado. Te mostramos su estado actual.',
      };
    case 'slot-unavailable': {
      const day = dayLabels(localDateOf(notice.currentStartsAt)).long;
      return {
        variant: 'warning',
        title: 'Ese horario se acaba de ocupar.',
        detail: `Tu turno sigue el ${day} a las ${formatTime(notice.currentStartsAt)}. Elegí otro horario.`,
      };
    }
    case 'stale-page':
      return {
        variant: 'danger',
        title: 'La página quedó desactualizada.',
        detail: 'Recargala e intentá de nuevo. No se hizo ningún cambio.',
      };
    case 'unknown-cancel':
      return {
        variant: 'danger',
        title: 'No pudimos confirmar la cancelación.',
        detail:
          'Puede que se haya registrado igual: si se canceló, te va a llegar un email y este enlace va a dejar de funcionar. Si no, probá de nuevo.',
      };
    case 'unknown-reschedule':
      return {
        variant: 'danger',
        title: 'No pudimos confirmar el cambio de horario.',
        detail: 'Puede que se haya registrado igual. Revisá el estado antes de intentar de nuevo.',
      };
    case 'reschedule-unavailable':
      return {
        variant: 'info',
        title: 'No se puede reprogramar este turno online.',
        detail:
          'La prestación ya no está disponible para reservar desde la web. Comunicate con el consultorio para cambiarlo o cancelalo desde acá.',
        tbd: '(TBD · datos)',
      };
    case 'connection':
      return { variant: 'danger', title: 'No pudimos cargar tu turno.', detail: 'Revisá tu conexión.' };
    case 'rate-limited':
      return bookingNotice(
        notice.retryAfterSeconds === undefined
          ? { kind: 'rate-limited' }
          : { kind: 'rate-limited', retryAfterSeconds: notice.retryAfterSeconds },
      );
  }
}

/** Mismo texto para token inexistente, vencido, revocado o con forma imposible (no revela el motivo). */
export function invalidLinkMessage(): EmptyMessage {
  return {
    title: 'Este enlace no es válido',
    detail:
      'Puede que haya vencido, que el turno ya se haya cancelado o que el enlace esté incompleto. Si necesitás ayuda con tu turno, comunicate con el consultorio',
    tbd: '(TBD · datos)',
  };
}

export interface StatusBadge {
  readonly tone: 'confirmed' | 'cancelled' | 'past';
  readonly label: string;
}

/** La insignia sale de las banderas del servidor; asistencia y ausencia no se distinguen (design D6). */
export function statusBadge(booking: ManagedBooking): StatusBadge {
  if (booking.status === 'CANCELLED') {
    return { tone: 'cancelled', label: 'Cancelado' };
  }
  if (booking.status === 'CONFIRMED' && (booking.cancellable || booking.reschedulable)) {
    return { tone: 'confirmed', label: 'Confirmado' };
  }
  return { tone: 'past', label: 'Turno pasado' };
}

/** Aviso de un turno que no admite cambios desde el enlace. */
export function readOnlyNotice(booking: ManagedBooking): BookingNotice {
  if (booking.status === 'CANCELLED') {
    return {
      variant: 'info',
      title: 'Este turno está cancelado.',
      detail: 'Si necesitás otro, podés reservarlo online.',
    };
  }
  return {
    variant: 'info',
    title: 'Este turno ya pasó.',
    detail: 'Por eso no se puede cancelar ni reprogramar desde acá.',
  };
}

export interface CancelConfirmation {
  readonly title: string;
  readonly text: string;
  readonly confirmLabel: string;
  readonly cancelLabel: string;
}

export function cancelConfirmation(booking: ManagedBooking): CancelConfirmation {
  const day = dayLabels(localDateOf(booking.startsAt)).long;
  return {
    title: '¿Cancelar este turno?',
    text: `${booking.serviceName} · ${day}, ${formatTime(booking.startsAt)}. El horario queda libre para otra persona y este enlace deja de funcionar. No se puede deshacer.`,
    confirmLabel: 'Sí, cancelar turno',
    cancelLabel: 'No, mantener turno',
  };
}

export function cancelledNotice(): BookingNotice {
  return {
    variant: 'info',
    title: 'Te enviamos un email con la cancelación.',
    detail: 'Este enlace ya no sirve para gestionar el turno.',
  };
}

export function rescheduledNotice(): BookingNotice {
  return {
    variant: 'info',
    title: 'Te enviamos un email con los nuevos datos.',
    detail: 'Podés seguir usando este enlace para cancelar o volver a cambiar el turno.',
  };
}
