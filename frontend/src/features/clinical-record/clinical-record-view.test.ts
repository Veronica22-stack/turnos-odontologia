import { describe, expect, it, vi } from 'vitest';
import type { PatientsGateway } from '../patients/patients-gateway';
import type { PatientSummary } from '../patients/patients-types';
import { createClinicalRecordView, type ClinicalRecordViewDeps } from './clinical-record-view';

const PATIENT: PatientSummary = {
  id: 'pac-001', firstName: 'Julieta', lastName: 'Simulada', dni: '30.456.789', phone: '11 5555 0001', active: true,
};
const OTHER: PatientSummary = {
  id: 'pac-002', firstName: 'Martín', lastName: 'Otro', dni: '20.111.222', phone: '11 5555 0002', active: true,
};

function deps(overrides: Partial<ClinicalRecordViewDeps> = {}): ClinicalRecordViewDeps {
  const patients = {
    search: vi.fn(async () => ({ ok: true as const, data: [PATIENT, OTHER] })),
    get: vi.fn(async () => ({ ok: true as const, data: undefined })),
    update: vi.fn(async () => ({ ok: true as const, data: undefined })),
    deactivate: vi.fn(async () => ({ ok: true as const, data: undefined })),
  } as unknown as PatientsGateway;
  return {
    patients,
    getAnamnesis: vi.fn(async () => ({ ok: true as const, data: { current: null, history: [] } })),
    getOdontogram: vi.fn(async () => ({ ok: true as const, data: { current: [], history: [] } })),
    getEvolutions: vi.fn(async () => ({ ok: true as const, data: { entries: [] } })),
    registerAnamnesis: vi.fn(async () => ({ ok: false as const, failure: { kind: 'connection' as const } })),
    registerOdontogram: vi.fn(async () => ({ ok: false as const, failure: { kind: 'connection' as const } })),
    registerEvolution: vi.fn(async () => ({ ok: false as const, failure: { kind: 'connection' as const } })),
    correctEntry: vi.fn(async () => ({ ok: false as const, failure: { kind: 'connection' as const } })),
    navigate: vi.fn(),
    onSessionExpired: vi.fn(),
    ...overrides,
  };
}

function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe('clinical record view: selección de paciente', () => {
  it('arranca pidiendo la selección y carga las secciones al elegir un paciente', async () => {
    const d = deps();
    const view = createClinicalRecordView(d);
    document.body.replaceChildren(view.element);

    await settle();

    expect(view.element.querySelector('h1')?.textContent).toBe('Historia clínica');
    expect(view.element.textContent).toContain('Elegí un paciente');
    expect(d.getAnamnesis).not.toHaveBeenCalled();

    const button = [...view.element.querySelectorAll('button')].find((b) => b.textContent?.includes('Julieta'));
    button?.click();
    await settle();

    expect(d.getAnamnesis).toHaveBeenCalledTimes(1);
    expect(d.getOdontogram).toHaveBeenCalledTimes(1);
    expect(d.getEvolutions).toHaveBeenCalledTimes(1);
    expect(view.element.querySelector('.clinical-selector')?.hasAttribute('hidden')).toBe(true);
  });

  it('mantiene la URL exacta y no escribe datos del paciente en título, consola ni almacenamiento', async () => {
    const d = deps();
    const spy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    const history = vi.spyOn(window.history, 'replaceState');
    const before = window.location.href;

    const view = createClinicalRecordView(d);
    document.body.replaceChildren(view.element);
    await settle();
    [...view.element.querySelectorAll('button')][0]?.click();
    await settle();

    expect(window.location.href).toBe(before);
    expect(document.title).not.toContain(PATIENT.dni);
    expect(document.title).not.toContain(PATIENT.id);
    expect(spy).not.toHaveBeenCalled();
    expect(storage).not.toHaveBeenCalled();
    expect(history).not.toHaveBeenCalled();

    spy.mockRestore();
    storage.mockRestore();
    history.mockRestore();
  });

  it('una nueva instancia (equivalente a recarga) vuelve a pedir selección', async () => {
    const d = deps();
    const first = createClinicalRecordView(d);
    await settle();
    [...first.element.querySelectorAll('button')][0]?.click();
    await settle();

    const second = createClinicalRecordView(d);
    await settle();

    expect(second.element.textContent).toContain('Elegí un paciente');
    expect(d.getAnamnesis).toHaveBeenCalledTimes(1);
  });
});
