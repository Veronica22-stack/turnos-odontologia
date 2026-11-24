import { describe, expect, it } from 'vitest';
import type { PatientsFailure } from './patients-messages';
import type { PatientDetail, PatientInput, PatientSummary } from './patients-types';
import {
  backToList,
  beginDeactivate,
  beginEdit,
  deactivateFailed,
  deactivateSucceeded,
  detailFailed,
  detailLoaded,
  initialPatientsState,
  listFailed,
  listLoaded,
  openPatient,
  retry,
  saveFailed,
  saveStarted,
  saveSucceeded,
  searchChanged,
  type PatientsState,
} from './patients-state';

const SUMMARY: PatientSummary = {
  id: 'pac-001',
  firstName: 'Julieta',
  lastName: 'Simulada',
  dni: '30.456.789',
  phone: '11 5555 0001',
  active: true,
};
const DETAIL: PatientDetail = {
  ...SUMMARY,
  email: null,
  birthDate: null,
  address: null,
  source: 'PUBLIC_BOOKING',
  createdAt: '2025-03-12T12:41:00Z',
  updatedAt: '2026-11-18T17:07:00Z',
};
const INPUT: PatientInput = {
  firstName: 'Julieta',
  lastName: 'Simulada',
  phone: '11 5555 0002',
  email: null,
  birthDate: null,
  address: null,
};
const CONNECTION: PatientsFailure = { kind: 'connection' };
const NOT_FOUND: PatientsFailure = { kind: 'not-found' };

describe('patients-state - transiciones puras (D1)', () => {
  it('2.3 listado → ficha → edición → baja y vuelve al listado con el término', () => {
    const loading = initialPatientsState();
    expect(loading).toEqual({ kind: 'list', phase: 'loading', query: '', patients: [] });
    const list = listLoaded(loading, [SUMMARY]);
    const searched = listLoaded(searchChanged(list, '30.456'), [SUMMARY]);
    expect(searched.query).toBe('30.456');

    const detail = detailLoaded(openPatient(searched, SUMMARY.id), DETAIL);
    expect(detail.kind).toBe('detail');
    const editing = beginEdit(detail);
    expect(editing.kind).toBe('edit');
    expect(beginEdit(editing)).toBe(editing);

    const saving = saveStarted(editing);
    expect(saving.kind).toBe('edit');
    const saved = saveSucceeded(saving, { ...DETAIL, phone: INPUT.phone });
    expect(saved.kind).toBe('detail');
    const deactivating = beginDeactivate(saved);
    expect(deactivating.kind).toBe('deactivate');
    const deactivated = deactivateSucceeded(deactivating);
    expect(deactivated.kind).toBe('deactivated');

    const back = backToList(deactivated, searched);
    expect(back).toEqual({ kind: 'list', phase: 'ready', query: '30.456', patients: [SUMMARY] });
  });

  it('2.3 busy ignora un segundo envío y retry desde error', () => {
    const list = listLoaded(initialPatientsState(), [SUMMARY]);
    const editing = beginEdit(detailLoaded(openPatient(list, SUMMARY.id), DETAIL));
    const saving = saveStarted(editing);
    expect(saveStarted(saving)).toBe(saving);
    expect(saveSucceeded(saving, DETAIL).kind).toBe('detail');

    const deactivating = beginDeactivate(detailLoaded(openPatient(list, SUMMARY.id), DETAIL));
    expect(beginDeactivate(deactivating)).toBe(deactivating);
    expect(deactivateFailed(deactivating, CONNECTION)).toHaveProperty('phase', 'error');

    const failedList = listFailed(initialPatientsState(), CONNECTION);
    expect(failedList).toHaveProperty('phase', 'error');
    expect(retry(failedList)).toEqual({ kind: 'list', phase: 'loading', query: '', patients: [] });
    const failedDetail = detailFailed(openPatient(list, SUMMARY.id), CONNECTION);
    expect(retry(failedDetail)).toHaveProperty('kind', 'detail');
    expect(retry(failedDetail)).toHaveProperty('phase', 'loading');
  });

  it('2.3 triangula fallo de carga y 404 de ficha conservando el término', () => {
    const searched = searchChanged(listFailed(initialPatientsState(), CONNECTION), 'Julieta');
    expect(searched).toMatchObject({ kind: 'list', query: 'Julieta', phase: 'error', failure: CONNECTION });

    const missing = detailFailed(openPatient(listLoaded(initialPatientsState(), [SUMMARY]), SUMMARY.id), NOT_FOUND);
    expect(missing).toMatchObject({ kind: 'detail', phase: 'error', failure: NOT_FOUND });
    expect(backToList(missing)).toMatchObject({ kind: 'list', query: '', phase: 'ready' });

    const list = listLoaded(initialPatientsState(), [SUMMARY]);
    const saveError = saveFailed(saveStarted(beginEdit(detailLoaded(openPatient(list, SUMMARY.id), DETAIL))), NOT_FOUND);
    expect(saveError).toMatchObject({ kind: 'edit', phase: 'error', failure: NOT_FOUND });
  });
});
