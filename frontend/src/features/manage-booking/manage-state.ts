import type { Slot } from '../booking/slots';
import type { ManageNoticeKind } from './manage-messages';
import type { CancelResult, ManagedBooking, RescheduleResult, ViewResult } from './manage-types';

/**
 * Maquina de estados de la pantalla `/turno/{token}` (design D4). Pura y sin DOM: cada transicion
 * devuelve un estado nuevo, o el mismo objeto si la accion no corresponde (doble envio, pantalla ajena).
 */

export type ManageState =
  | { readonly screen: 'loading' }
  | { readonly screen: 'invalid' }
  | { readonly screen: 'rate-limited'; readonly retryAfterSeconds?: number }
  | { readonly screen: 'connection' }
  | {
      readonly screen: 'detail';
      readonly booking: ManagedBooking;
      readonly notice?: ManageNoticeKind;
      readonly busy?: 'cancel';
    }
  | {
      readonly screen: 'rescheduling';
      readonly booking: ManagedBooking;
      readonly busy: boolean;
      readonly chosen?: Slot;
      /** Inicio rechazado por el servidor (409): el selector destaca los horarios cercanos. */
      readonly rejectedStartsAt?: string;
      readonly notice?: ManageNoticeKind;
      /** La prestacion no se puede identificar o ya no tiene disponibilidad publica (D5). */
      readonly unavailable?: true;
    }
  | { readonly screen: 'rescheduled'; readonly booking: ManagedBooking; readonly previous: Slot }
  | { readonly screen: 'cancelled'; readonly booking: ManagedBooking };

/** Resultado de una accion: el estado nuevo y si hay que volver a consultar el turno. */
export interface Transition {
  readonly state: ManageState;
  readonly requery: boolean;
}

export function initialState(): ManageState {
  return { screen: 'loading' };
}

export function loaded(result: ViewResult, notice?: ManageNoticeKind): ManageState {
  switch (result.kind) {
    case 'ok':
      return notice === undefined
        ? { screen: 'detail', booking: result.booking }
        : { screen: 'detail', booking: result.booking, notice };
    case 'invalid':
      return { screen: 'invalid' };
    case 'rate-limited':
      return result.retryAfterSeconds === undefined
        ? { screen: 'rate-limited' }
        : { screen: 'rate-limited', retryAfterSeconds: result.retryAfterSeconds };
    case 'connection':
      return { screen: 'connection' };
  }
}

export function openReschedule(state: ManageState): ManageState {
  if (state.screen !== 'detail' || state.busy !== undefined || !state.booking.reschedulable) {
    return state;
  }
  return { screen: 'rescheduling', booking: state.booking, busy: false };
}

export function choose(state: ManageState, slot: Slot): ManageState {
  if (state.screen !== 'rescheduling' || state.busy) {
    return state;
  }
  return { ...state, chosen: slot };
}

/** Cambio de dia en el selector: el horario elegido ya no corresponde. */
export function clearChoice(state: ManageState): ManageState {
  if (state.screen !== 'rescheduling' || state.busy || state.chosen === undefined) {
    return state;
  }
  const { chosen: _chosen, ...rest } = state;
  return rest;
}

export function back(state: ManageState): ManageState {
  return state.screen === 'rescheduling' ? { screen: 'detail', booking: state.booking } : state;
}

export function rescheduleUnavailable(state: ManageState): ManageState {
  if (state.screen !== 'rescheduling') {
    return state;
  }
  const { chosen: _chosen, rejectedStartsAt: _rejected, ...rest } = state;
  return { ...rest, unavailable: true };
}

export function submitReschedule(state: ManageState): ManageState {
  if (state.screen !== 'rescheduling' || state.busy || state.chosen === undefined || state.unavailable === true) {
    return state;
  }
  const { notice: _notice, ...rest } = state;
  return { ...rest, busy: true };
}

export function resolveReschedule(state: ManageState, result: RescheduleResult): Transition {
  if (state.screen !== 'rescheduling' || !state.busy) {
    return { state, requery: false };
  }
  const { booking, chosen } = state;
  const settled = { screen: 'rescheduling', booking, busy: false } as const;
  switch (result.kind) {
    case 'done':
      return {
        state: {
          screen: 'rescheduled',
          booking: result.booking,
          previous: { startsAt: booking.startsAt, endsAt: booking.endsAt },
        },
        requery: false,
      };
    case 'slot-unavailable':
      return {
        state: {
          ...settled,
          ...(chosen === undefined ? {} : { rejectedStartsAt: chosen.startsAt }),
          notice: { kind: 'slot-unavailable', currentStartsAt: booking.startsAt },
        },
        requery: false,
      };
    case 'not-modifiable':
      return { state: { screen: 'detail', booking, notice: { kind: 'not-modifiable' } }, requery: true };
    case 'invalid':
      return { state: { screen: 'invalid' }, requery: false };
    case 'unknown-outcome':
      return { state: { ...settled, ...(chosen === undefined ? {} : { chosen }), notice: { kind: 'unknown-reschedule' } }, requery: false };
    case 'stale-page':
      return { state: { ...settled, ...(chosen === undefined ? {} : { chosen }), notice: { kind: 'stale-page' } }, requery: false };
    case 'rate-limited':
      return {
        state: {
          ...settled,
          ...(chosen === undefined ? {} : { chosen }),
          notice:
            result.retryAfterSeconds === undefined
              ? { kind: 'rate-limited' }
              : { kind: 'rate-limited', retryAfterSeconds: result.retryAfterSeconds },
        },
        requery: false,
      };
  }
}

export function submitCancel(state: ManageState): ManageState {
  if (state.screen !== 'detail' || state.busy !== undefined || !state.booking.cancellable) {
    return state;
  }
  return { screen: 'detail', booking: state.booking, busy: 'cancel' };
}

export function resolveCancel(state: ManageState, result: CancelResult): Transition {
  if (state.screen !== 'detail' || state.busy !== 'cancel') {
    return { state, requery: false };
  }
  const { booking } = state;
  const withNotice = (notice: ManageNoticeKind): ManageState => ({ screen: 'detail', booking, notice });
  switch (result.kind) {
    case 'done':
      return { state: { screen: 'cancelled', booking: result.booking }, requery: false };
    case 'not-modifiable':
      return { state: withNotice({ kind: 'not-modifiable' }), requery: true };
    case 'invalid':
      return { state: { screen: 'invalid' }, requery: false };
    case 'stale-page':
      return { state: withNotice({ kind: 'stale-page' }), requery: false };
    case 'unknown-outcome':
      return { state: withNotice({ kind: 'unknown-cancel' }), requery: false };
    case 'rate-limited':
      return {
        state: withNotice(
          result.retryAfterSeconds === undefined
            ? { kind: 'rate-limited' }
            : { kind: 'rate-limited', retryAfterSeconds: result.retryAfterSeconds },
        ),
        requery: false,
      };
  }
}
