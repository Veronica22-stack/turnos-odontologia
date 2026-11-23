import { describe, expect, it } from 'vitest';
import {
  back,
  createFlow,
  goTo,
  next,
  resolve,
  selectDate,
  selectService,
  selectSlot,
  setForm,
  submit,
  type BookingFlowState,
} from './booking-flow';
import type { BookResult, PublicService } from './booking-types';
import { EMPTY_BOOKING_FORM } from './validation';

const TODAY = '2026-11-23';
const LIMPIEZA: PublicService = { id: 's-2', name: 'Limpieza', description: null, durationMin: 45 };
const CONSULTA: PublicService = { id: 's-1', name: 'Consulta', description: 'Primera visita', durationMin: 30 };
const SLOT = { startsAt: '2026-11-24T13:30:00Z', endsAt: '2026-11-24T14:15:00Z' };
const OTHER_SLOT = { startsAt: '2026-11-24T14:00:00Z', endsAt: '2026-11-24T14:45:00Z' };
const FORM = { ...EMPTY_BOOKING_FORM, firstName: 'Ana', lastName: 'Pérez', privacyAccepted: true };

function atDetails(): BookingFlowState {
  let state = selectService(createFlow(), LIMPIEZA, TODAY);
  state = next(state);
  state = selectDate(state, '2026-11-24');
  state = selectSlot(state, SLOT);
  state = next(state);
  return setForm(state, FORM);
}

describe('createFlow', () => {
  it('empieza en el paso de prestacion sin elecciones', () => {
    const state = createFlow();

    expect(state.step).toBe('service');
    expect(state.service).toBeUndefined();
    expect(state.slot).toBeUndefined();
    expect(state.form).toEqual(EMPTY_BOOKING_FORM);
  });
});

describe('avance', () => {
  it('no avanza del paso 1 sin prestacion elegida', () => {
    expect(next(createFlow()).step).toBe('service');
  });

  it('elegir prestacion y continuar lleva al paso 2 con el dia de hoy', () => {
    const state = next(selectService(createFlow(), LIMPIEZA, TODAY));

    expect(state.step).toBe('schedule');
    expect(state.date).toBe(TODAY);
    expect(state.service).toEqual(LIMPIEZA);
  });

  it('no avanza del paso 2 sin horario y con horario lleva al paso 3', () => {
    const schedule = next(selectService(createFlow(), LIMPIEZA, TODAY));

    expect(next(schedule).step).toBe('schedule');
    expect(next(selectSlot(schedule, SLOT)).step).toBe('details');
  });
});

describe('volver', () => {
  it('desde el paso 3 conserva prestacion, dia, horario y datos', () => {
    const state = back(atDetails());

    expect(state.step).toBe('schedule');
    expect(state.service).toEqual(LIMPIEZA);
    expect(state.date).toBe('2026-11-24');
    expect(state.slot).toEqual(SLOT);
    expect(state.form).toEqual(FORM);
  });

  it('desde el paso 2 vuelve al 1 con la prestacion marcada y desde el 1 no cambia', () => {
    const state = back(back(atDetails()));

    expect(state.step).toBe('service');
    expect(state.service).toEqual(LIMPIEZA);
    expect(back(state).step).toBe('service');
  });
});

describe('cambios de eleccion', () => {
  it('cambiar de prestacion descarta dia y horario y conserva los datos', () => {
    const state = selectService(atDetails(), CONSULTA, '2026-11-25');

    expect(state.service).toEqual(CONSULTA);
    expect(state.date).toBe('2026-11-25');
    expect(state.slot).toBeUndefined();
    expect(state.form).toEqual(FORM);
  });

  it('volver a elegir la misma prestacion conserva dia y horario', () => {
    const state = selectService(atDetails(), LIMPIEZA, '2026-11-30');

    expect(state.date).toBe('2026-11-24');
    expect(state.slot).toEqual(SLOT);
  });

  it('cambiar de dia descarta el horario, pero el mismo dia no', () => {
    const state = atDetails();

    expect(selectDate(state, '2026-11-25').slot).toBeUndefined();
    expect(selectDate(state, '2026-11-24').slot).toEqual(SLOT);
  });
});

describe('goTo (paso pedido por la URL)', () => {
  it('lleva al paso pedido si el estado lo permite', () => {
    expect(goTo(atDetails(), 'schedule').step).toBe('schedule');
    expect(goTo(goTo(atDetails(), 'service'), 'details').step).toBe('details');
  });

  it('recorta al paso mas alto valido si faltan elecciones', () => {
    expect(goTo(createFlow(), 'details').step).toBe('service');
    expect(goTo(next(selectService(createFlow(), LIMPIEZA, TODAY)), 'details').step).toBe('schedule');
  });
});

describe('envio y resultados', () => {
  it('submit pasa de details a submitting y un segundo submit no cambia el estado', () => {
    const submitting = submit(atDetails());

    expect(submitting.step).toBe('submitting');
    expect(submit(submitting)).toBe(submitting);
  });

  it('submit fuera del paso 3 se ignora', () => {
    const state = createFlow();

    expect(submit(state)).toBe(state);
  });

  it('slot-unavailable vuelve al paso 2 del mismo dia con aviso, sin horario y con datos', () => {
    const state = resolve(submit(atDetails()), { kind: 'slot-unavailable' });

    expect(state.step).toBe('schedule');
    expect(state.date).toBe('2026-11-24');
    expect(state.slot).toBeUndefined();
    expect(state.notice).toBe('slot-taken');
    expect(state.takenStartsAt).toBe(SLOT.startsAt);
    expect(state.form).toEqual(FORM);
  });

  it('elegir un horario despues del conflicto limpia el aviso', () => {
    const conflict = resolve(submit(atDetails()), { kind: 'slot-unavailable' });
    const state = selectSlot(conflict, OTHER_SLOT);

    expect(state.notice).toBeUndefined();
    expect(state.takenStartsAt).toBeUndefined();
    expect(next(state).step).toBe('details');
  });

  it('service-gone vuelve al paso 1 sin prestacion ni horario', () => {
    const state = resolve(submit(atDetails()), { kind: 'service-gone' });

    expect(state.step).toBe('service');
    expect(state.service).toBeUndefined();
    expect(state.slot).toBeUndefined();
    expect(state.form).toEqual(FORM);
  });

  const KEEP_AT_DETAILS: BookResult[] = [
    { kind: 'rate-limited' },
    { kind: 'contact-limit' },
    { kind: 'stale-page' },
    { kind: 'unknown-outcome' },
    { kind: 'fields', errors: [] },
  ];

  it.each(KEEP_AT_DETAILS)('$kind devuelve al paso 3 conservando todo', (result) => {
    const state = resolve(submit(atDetails()), result);

    expect(state.step).toBe('details');
    expect(state.slot).toEqual(SLOT);
    expect(state.form).toEqual(FORM);
  });

  const FINISHED: BookResult[] = [
    { kind: 'accepted' },
    { kind: 'confirmed', serviceName: 'Limpieza', startsAt: SLOT.startsAt, endsAt: SLOT.endsAt, practiceName: '' },
  ];

  it.each(FINISHED)('$kind descarta todo lo cargado', (result) => {
    expect(resolve(submit(atDetails()), result)).toEqual(createFlow());
  });
});
