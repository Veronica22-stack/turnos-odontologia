import type { PatientsFailure } from './patients-messages';
import type { FieldErrors } from './patients-validation';
import type { PatientDetail, PatientInput, PatientSummary } from './patients-types';

/** Máquina de estados pura de `/pacientes` (D1): sin DOM ni peticiones. */

export interface ListState {
  readonly kind: 'list';
  readonly phase: 'loading' | 'ready' | 'error';
  readonly query: string;
  readonly patients: readonly PatientSummary[];
  readonly failure?: PatientsFailure;
}

interface DetailState {
  readonly kind: 'detail';
  readonly phase: 'loading' | 'ready' | 'error';
  readonly id: string;
  readonly patient?: PatientDetail;
  readonly failure?: PatientsFailure;
}

interface EditState {
  readonly kind: 'edit';
  readonly phase: 'idle' | 'saving' | 'error';
  readonly patient: PatientDetail;
  readonly draft: PatientInput;
  readonly errors: FieldErrors;
  readonly hasUnknownField: boolean;
  readonly failure?: PatientsFailure;
}

interface DeactivateState {
  readonly kind: 'deactivate';
  readonly phase: 'idle' | 'working' | 'error';
  readonly patient: PatientDetail;
  readonly failure?: PatientsFailure;
}

interface DeactivatedState {
  readonly kind: 'deactivated';
  readonly patient: PatientDetail;
}

export type PatientsState = ListState | DetailState | EditState | DeactivateState | DeactivatedState;

export function initialPatientsState(): ListState {
  return { kind: 'list', phase: 'loading', query: '', patients: [] };
}

function inputFrom(patient: PatientDetail): PatientInput {
  return {
    firstName: patient.firstName,
    lastName: patient.lastName,
    phone: patient.phone,
    email: patient.email,
    birthDate: patient.birthDate,
    address: patient.address,
  };
}

export function searchChanged(state: ListState, query: string): ListState {
  return { ...state, query };
}

export function listLoaded(state: ListState, patients: readonly PatientSummary[]): ListState {
  return { kind: 'list', phase: 'ready', query: state.query, patients };
}

export function listFailed(state: ListState, failure: PatientsFailure): ListState {
  return { kind: 'list', phase: 'error', query: state.query, patients: state.patients, failure };
}

export function openPatient(state: ListState, id: string): DetailState {
  return { kind: 'detail', phase: 'loading', id };
}

export function detailLoaded(state: DetailState, patient: PatientDetail): DetailState {
  return { kind: 'detail', phase: 'ready', id: state.id, patient };
}

export function detailFailed(state: DetailState, failure: PatientsFailure): DetailState {
  return { kind: 'detail', phase: 'error', id: state.id, failure };
}

function detailPatient(state: DetailState | DeactivateState): PatientDetail {
  if (state.patient === undefined) {
    throw new Error('La ficha todavía no se cargó');
  }
  return state.patient;
}

export function beginEdit(state: DetailState | EditState): EditState {
  if (state.kind === 'edit') {
    return state;
  }
  const patient = detailPatient(state);
  return {
    kind: 'edit',
    phase: 'idle',
    patient,
    draft: inputFrom(patient),
    errors: {},
    hasUnknownField: false,
  };
}

export function setDraft(state: EditState, draft: PatientInput): EditState {
  return state.phase === 'saving' ? state : { ...state, draft, errors: {}, hasUnknownField: false };
}

export function setFieldErrors(state: EditState, errors: FieldErrors, hasUnknownField = false): EditState {
  return { ...state, phase: 'error', errors, hasUnknownField };
}

export function saveStarted(state: EditState): EditState {
  return state.phase === 'saving' ? state : { ...state, phase: 'saving', errors: {}, hasUnknownField: false };
}

export function saveSucceeded(state: EditState, patient: PatientDetail): DetailState {
  return { kind: 'detail', phase: 'ready', id: patient.id, patient };
}

export function saveFailed(state: EditState, failure: PatientsFailure, errors?: FieldErrors, hasUnknownField = false): EditState {
  return {
    ...state,
    phase: 'error',
    failure,
    errors: errors ?? state.errors,
    hasUnknownField,
  };
}

export function cancelEdit(state: EditState): DetailState {
  return { kind: 'detail', phase: 'ready', id: state.patient.id, patient: state.patient };
}

export function beginDeactivate(state: DetailState | DeactivateState): DeactivateState {
  if (state.kind === 'deactivate') {
    return state;
  }
  return { kind: 'deactivate', phase: 'idle', patient: detailPatient(state) };
}

export function deactivateStarted(state: DeactivateState): DeactivateState {
  return state.phase === 'working' ? state : { ...state, phase: 'working' };
}

export function deactivateSucceeded(state: DeactivateState): DeactivatedState {
  return { kind: 'deactivated', patient: state.patient };
}

export function deactivateFailed(state: DeactivateState, failure: PatientsFailure): DeactivateState {
  return { ...state, phase: 'error', failure };
}

export function backToList(state: PatientsState, list: ListState = initialPatientsState()): ListState {
  return { kind: 'list', phase: 'ready', query: list.query, patients: list.patients };
}

export function retry(state: PatientsState): PatientsState {
  switch (state.kind) {
    case 'list':
      return { kind: 'list', phase: 'loading', query: state.query, patients: state.patients };
    case 'detail':
      return { kind: 'detail', phase: 'loading', id: state.id };
    case 'edit':
      return {
        kind: 'edit',
        phase: 'idle',
        patient: state.patient,
        draft: state.draft,
        errors: state.errors,
        hasUnknownField: state.hasUnknownField,
      };
    case 'deactivate':
      return { kind: 'deactivate', phase: 'idle', patient: state.patient };
    case 'deactivated':
      return state;
  }
}
