import { describe, expect, it } from 'vitest';
import {
  back,
  choose,
  clearChoice,
  initialState,
  loaded,
  openReschedule,
  rescheduleUnavailable,
  resolveCancel,
  resolveReschedule,
  submitCancel,
  submitReschedule,
  type ManageState,
} from './manage-state';
import type { ManagedBooking } from './manage-types';

const BOOKING: ManagedBooking = {
  serviceName: 'Limpieza',
  practiceName: 'Consultorio La Muela',
  startsAt: '2026-11-24T13:30:00Z',
  endsAt: '2026-11-24T14:15:00Z',
  status: 'CONFIRMED',
  cancellable: true,
  reschedulable: true,
};
const MOVED: ManagedBooking = { ...BOOKING, startsAt: '2026-11-26T14:15:00Z', endsAt: '2026-11-26T15:00:00Z' };
const SLOT = { startsAt: '2026-11-26T14:15:00Z', endsAt: '2026-11-26T15:00:00Z' };

function detail(booking: ManagedBooking = BOOKING): ManageState {
  return loaded({ kind: 'ok', booking });
}

function rescheduling(): ManageState {
  return choose(openReschedule(detail()), SLOT);
}

describe('initialState y loaded', () => {
  it('arranca cargando', () => {
    expect(initialState()).toEqual({ screen: 'loading' });
  });

  it.each([
    [{ kind: 'ok', booking: BOOKING } as const, { screen: 'detail', booking: BOOKING }],
    [{ kind: 'invalid' } as const, { screen: 'invalid' }],
    [{ kind: 'rate-limited', retryAfterSeconds: 40 } as const, { screen: 'rate-limited', retryAfterSeconds: 40 }],
    [{ kind: 'rate-limited' } as const, { screen: 'rate-limited' }],
    [{ kind: 'connection' } as const, { screen: 'connection' }],
  ])('traduce el resultado %#', (result, expected) => {
    expect(loaded(result)).toEqual(expected);
  });

  it('conserva el aviso pasado a loaded cuando hay turno', () => {
    expect(loaded({ kind: 'ok', booking: BOOKING }, { kind: 'not-modifiable' })).toEqual({
      screen: 'detail',
      booking: BOOKING,
      notice: { kind: 'not-modifiable' },
    });
  });
});

describe('openReschedule / choose / clearChoice / back', () => {
  it('abre la reprogramación solo si el servidor la habilita', () => {
    expect(openReschedule(detail())).toMatchObject({ screen: 'rescheduling', booking: BOOKING, busy: false });
    const readOnly = detail({ ...BOOKING, reschedulable: false });
    expect(openReschedule(readOnly)).toBe(readOnly);
  });

  it('no abre desde otra pantalla', () => {
    const invalid = loaded({ kind: 'invalid' });
    expect(openReschedule(invalid)).toBe(invalid);
  });

  it('elige un horario y lo descarta al cambiar de día', () => {
    const chosen = rescheduling();
    expect(chosen).toMatchObject({ screen: 'rescheduling', chosen: SLOT });
    const cleared = clearChoice(chosen);
    expect(cleared.screen === 'rescheduling' && cleared.chosen).toBeUndefined();
  });

  it('volver regresa al detalle con el turno original', () => {
    expect(back(rescheduling())).toEqual({ screen: 'detail', booking: BOOKING });
  });

  it('volver no hace nada fuera de la reprogramación', () => {
    const state = detail();
    expect(back(state)).toBe(state);
  });

  it('no se puede elegir horario durante el envío', () => {
    const submitting = submitReschedule(rescheduling());
    expect(choose(submitting, { startsAt: '2026-11-26T15:00:00Z', endsAt: '2026-11-26T15:45:00Z' })).toBe(submitting);
  });
});

describe('submitReschedule / resolveReschedule', () => {
  it('sin horario elegido no hace nada', () => {
    const state = openReschedule(detail());
    expect(submitReschedule(state)).toBe(state);
  });

  it('pasa a busy y un segundo envío no cambia el estado', () => {
    const submitting = submitReschedule(rescheduling());
    expect(submitting).toMatchObject({ screen: 'rescheduling', busy: true });
    expect(submitReschedule(submitting)).toBe(submitting);
  });

  it('done -> rescheduled con el horario anterior', () => {
    const { state, requery } = resolveReschedule(submitReschedule(rescheduling()), { kind: 'done', booking: MOVED });
    expect(state).toEqual({
      screen: 'rescheduled',
      booking: MOVED,
      previous: { startsAt: BOOKING.startsAt, endsAt: BOOKING.endsAt },
    });
    expect(requery).toBe(false);
  });

  it('slot-unavailable -> sigue reprogramando, sin elección y con el horario rechazado', () => {
    const { state } = resolveReschedule(submitReschedule(rescheduling()), { kind: 'slot-unavailable' });
    expect(state).toEqual({
      screen: 'rescheduling',
      booking: BOOKING,
      busy: false,
      rejectedStartsAt: SLOT.startsAt,
      notice: { kind: 'slot-unavailable', currentStartsAt: BOOKING.startsAt },
    });
  });

  it('not-modifiable -> detalle con aviso y pide nueva consulta', () => {
    const { state, requery } = resolveReschedule(submitReschedule(rescheduling()), { kind: 'not-modifiable' });
    expect(state).toEqual({ screen: 'detail', booking: BOOKING, notice: { kind: 'not-modifiable' } });
    expect(requery).toBe(true);
  });

  it.each([
    [{ kind: 'unknown-outcome' } as const, { kind: 'unknown-reschedule' }],
    [{ kind: 'stale-page' } as const, { kind: 'stale-page' }],
    [{ kind: 'rate-limited', retryAfterSeconds: 30 } as const, { kind: 'rate-limited', retryAfterSeconds: 30 }],
  ])('%j conserva la elección y muestra el aviso', (result, notice) => {
    const { state, requery } = resolveReschedule(submitReschedule(rescheduling()), result);
    expect(state).toMatchObject({ screen: 'rescheduling', busy: false, chosen: SLOT, notice });
    expect(requery).toBe(false);
  });

  it('invalid -> enlace no válido', () => {
    expect(resolveReschedule(submitReschedule(rescheduling()), { kind: 'invalid' }).state).toEqual({ screen: 'invalid' });
  });

  it('resolver fuera del envío no cambia nada', () => {
    const state = detail();
    expect(resolveReschedule(state, { kind: 'done', booking: MOVED })).toEqual({ state, requery: false });
  });
});

describe('submitCancel / resolveCancel', () => {
  it('pasa a busy y un segundo envío no cambia el estado', () => {
    const busy = submitCancel(detail());
    expect(busy).toMatchObject({ screen: 'detail', busy: 'cancel' });
    expect(submitCancel(busy)).toBe(busy);
  });

  it('no cancela un turno que el servidor no habilita', () => {
    const readOnly = detail({ ...BOOKING, cancellable: false });
    expect(submitCancel(readOnly)).toBe(readOnly);
  });

  it('done -> cancelado', () => {
    const cancelled: ManagedBooking = { ...BOOKING, status: 'CANCELLED', cancellable: false, reschedulable: false };
    const { state } = resolveCancel(submitCancel(detail()), { kind: 'done', booking: cancelled });
    expect(state).toEqual({ screen: 'cancelled', booking: cancelled });
  });

  it('not-modifiable -> aviso y nueva consulta', () => {
    const { state, requery } = resolveCancel(submitCancel(detail()), { kind: 'not-modifiable' });
    expect(state).toEqual({ screen: 'detail', booking: BOOKING, notice: { kind: 'not-modifiable' } });
    expect(requery).toBe(true);
  });

  it.each([
    [{ kind: 'stale-page' } as const, { kind: 'stale-page' }],
    [{ kind: 'unknown-outcome' } as const, { kind: 'unknown-cancel' }],
    [{ kind: 'rate-limited' } as const, { kind: 'rate-limited' }],
  ])('%j deja el turno intacto con el aviso', (result, notice) => {
    const { state, requery } = resolveCancel(submitCancel(detail()), result);
    expect(state).toEqual({ screen: 'detail', booking: BOOKING, notice });
    expect(requery).toBe(false);
  });

  it('invalid -> enlace no válido', () => {
    expect(resolveCancel(submitCancel(detail()), { kind: 'invalid' }).state).toEqual({ screen: 'invalid' });
  });
});

describe('rescheduleUnavailable', () => {
  it('marca la reprogramación como no disponible y conserva el turno', () => {
    const state = rescheduleUnavailable(openReschedule(detail()));
    expect(state).toMatchObject({ screen: 'rescheduling', booking: BOOKING, unavailable: true });
  });

  it('no cambia otras pantallas', () => {
    const state = detail();
    expect(rescheduleUnavailable(state)).toBe(state);
  });
});
