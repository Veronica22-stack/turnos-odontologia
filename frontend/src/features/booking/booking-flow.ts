import type { BookResult, PublicService } from './booking-types';
import type { BookingStep } from './booking-step-url';
import type { Slot } from './slots';
import { EMPTY_BOOKING_FORM, type BookingFormValues } from './validation';

/** Estado del flujo de reserva (design D3): puro, sin DOM ni red. Las vistas lo renderizan. */

export type FlowStep = 'service' | 'schedule' | 'details' | 'submitting';

export interface BookingFlowState {
  readonly step: FlowStep;
  readonly service?: PublicService;
  readonly date: string;
  readonly slot?: Slot;
  readonly form: BookingFormValues;
  readonly notice?: 'slot-taken';
  /** Inicio rechazado por el servidor: base de las sugerencias de horarios cercanos. */
  readonly takenStartsAt?: string;
}

export function createFlow(): BookingFlowState {
  return { step: 'service', date: '', form: EMPTY_BOOKING_FORM };
}

const STEP_ORDER: readonly FlowStep[] = ['service', 'schedule', 'details'];

/** Paso mas alto permitido por lo elegido hasta ahora. */
function highestStep(state: BookingFlowState): FlowStep {
  if (state.service === undefined) {
    return 'service';
  }
  return state.slot === undefined ? 'schedule' : 'details';
}

function without<K extends keyof BookingFlowState>(state: BookingFlowState, ...keys: K[]): BookingFlowState {
  const copy: Partial<Record<keyof BookingFlowState, unknown>> = { ...state };
  for (const key of keys) {
    delete copy[key];
  }
  return copy as unknown as BookingFlowState;
}

/** Elegir otra prestacion descarta dia y horario; la misma conserva todo. */
export function selectService(state: BookingFlowState, service: PublicService, today: string): BookingFlowState {
  if (state.service?.id === service.id) {
    return { ...state, service };
  }
  return { ...without(state, 'slot', 'notice', 'takenStartsAt'), service, date: today };
}

/** Cambiar de dia descarta el horario; el mismo dia no cambia nada. */
export function selectDate(state: BookingFlowState, date: string): BookingFlowState {
  if (state.date === date) {
    return state;
  }
  return { ...without(state, 'slot', 'notice', 'takenStartsAt'), date };
}

export function selectSlot(state: BookingFlowState, slot: Slot): BookingFlowState {
  return { ...without(state, 'notice', 'takenStartsAt'), slot };
}

export function setForm(state: BookingFlowState, form: BookingFormValues): BookingFlowState {
  return { ...state, form };
}

export function next(state: BookingFlowState): BookingFlowState {
  if (state.step === 'service' && highestStep(state) !== 'service') {
    return { ...state, step: 'schedule' };
  }
  if (state.step === 'schedule' && highestStep(state) === 'details') {
    return { ...without(state, 'notice', 'takenStartsAt'), step: 'details' };
  }
  return state;
}

export function back(state: BookingFlowState): BookingFlowState {
  if (state.step === 'details') {
    return { ...state, step: 'schedule' };
  }
  if (state.step === 'schedule') {
    return { ...state, step: 'service' };
  }
  return state;
}

/** Va al paso pedido por la URL, recortado al mas alto que el estado permite. */
export function goTo(state: BookingFlowState, requested: FlowStep): BookingFlowState {
  const limit = STEP_ORDER.indexOf(highestStep(state));
  const wanted = Math.min(STEP_ORDER.indexOf(requested), limit);
  const step = STEP_ORDER[wanted] ?? 'service';
  return step === state.step ? state : { ...state, step };
}

export function submit(state: BookingFlowState): BookingFlowState {
  return state.step === 'details' ? { ...state, step: 'submitting' } : state;
}

/** Aplica el resultado del envio. Ante exito (`confirmed`/`accepted`) descarta todo lo cargado. */
export function resolve(state: BookingFlowState, result: BookResult): BookingFlowState {
  switch (result.kind) {
    case 'confirmed':
    case 'accepted':
      return createFlow();
    case 'slot-unavailable':
      return {
        ...without(state, 'slot'),
        step: 'schedule',
        notice: 'slot-taken',
        ...(state.slot === undefined ? {} : { takenStartsAt: state.slot.startsAt }),
      };
    case 'service-gone':
      return { ...without(state, 'service', 'slot', 'notice', 'takenStartsAt'), step: 'service', date: '' };
    default:
      return { ...state, step: 'details' };
  }
}

/** Paso de la URL que corresponde al paso actual del flujo. */
export function urlStepOf(step: FlowStep): BookingStep {
  return step === 'service' ? 1 : step === 'schedule' ? 2 : 3;
}

export function flowStepOf(step: BookingStep): FlowStep {
  return step === 1 ? 'service' : step === 2 ? 'schedule' : 'details';
}
