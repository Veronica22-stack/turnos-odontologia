import { describe, expect, it, vi } from 'vitest';
import type { PatientsGateway } from '../patients/patients-gateway';
import type { PatientSummary } from '../patients/patients-types';
import { createAnamnesisForm } from './anamnesis-form';
import { createClinicalRecordView, type ClinicalRecordViewDeps } from './clinical-record-view';
import type { ClinicalResult } from './clinical-record-gateway';
import { createCorrectionDialog } from './correction-dialog';
import { createEvolutionsForm } from './evolutions-form';
import { createOdontogramForm } from './odontogram-form';

const DRAFT = 'TextoClinicoDelBorrador';
const PATIENT: PatientSummary = {
  id: 'pac-001', firstName: 'Julieta', lastName: 'Simulada', dni: '30.456.789', phone: '11 5555 0001', active: true,
};

const expired = (): ClinicalResult<never> => ({ ok: false, failure: { kind: 'unauthorized' } });
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

function viewDeps(overrides: Partial<ClinicalRecordViewDeps> = {}): ClinicalRecordViewDeps {
  const patients = {
    search: vi.fn(async () => ({ ok: true as const, data: [PATIENT] })),
    get: vi.fn(async () => ({ ok: true as const, data: undefined })),
    update: vi.fn(async () => ({ ok: true as const, data: undefined })),
    deactivate: vi.fn(async () => ({ ok: true as const, data: undefined })),
  } as unknown as PatientsGateway;
  return {
    patients,
    getAnamnesis: vi.fn(async () => ({ ok: true as const, data: { current: null, history: [] } })),
    getOdontogram: vi.fn(async () => ({ ok: true as const, data: { current: [], history: [] } })),
    getEvolutions: vi.fn(async () => ({ ok: true as const, data: { entries: [] } })),
    registerAnamnesis: vi.fn(async () => expired()),
    registerOdontogram: vi.fn(async () => expired()),
    registerEvolution: vi.fn(async () => expired()),
    correctEntry: vi.fn(async () => expired()),
    navigate: vi.fn(),
    onSessionExpired: vi.fn(),
    ...overrides,
  };
}

function captureStorage(): { dump(): string; stop(): void } {
  const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {});
  return {
    dump: () => setItem.mock.calls.map((call) => String(call[1])).join(' '),
    stop: () => setItem.mockRestore(),
  };
}

describe('sesión vencida en historia clínica', () => {
  it('un 401 en la carga descarta la selección, notifica y redirige sin reintentar ni persistir', async () => {
    const storage = captureStorage();
    const d = viewDeps({ getEvolutions: vi.fn(async () => expired()) as ClinicalRecordViewDeps['getEvolutions'] });
    const view = createClinicalRecordView(d);
    document.body.replaceChildren(view.element);
    await settle();
    [...view.element.querySelectorAll('button')][0]?.click();
    await settle();

    expect(d.onSessionExpired).toHaveBeenCalledTimes(1);
    expect(d.navigate).toHaveBeenCalledTimes(1);
    expect(d.navigate).toHaveBeenCalledWith('/login?next=%2Fhistoria-clinica');
    expect(view.element.querySelector('.clinical-selector')?.hasAttribute('hidden')).toBe(false);
    expect(view.element.querySelector('.clinical-sections')?.hasAttribute('hidden')).toBe(true);
    expect(d.getAnamnesis).toHaveBeenCalledTimes(1);
    expect(d.getOdontogram).toHaveBeenCalledTimes(1);
    expect(d.getEvolutions).toHaveBeenCalledTimes(1);
    expect(storage.dump()).not.toContain(DRAFT);
    expect(storage.dump()).not.toContain(PATIENT.dni);
    expect(window.location.href).not.toContain(PATIENT.dni);
    storage.stop();
  });

  it('tras la expiración una vista nueva pide selección y no restaura el borrador', async () => {
    const d = viewDeps({ getAnamnesis: vi.fn(async () => expired()) as ClinicalRecordViewDeps['getAnamnesis'] });
    const first = createClinicalRecordView(d);
    document.body.replaceChildren(first.element);
    await settle();
    [...first.element.querySelectorAll('button')][0]?.click();
    await settle();
    expect(d.onSessionExpired).toHaveBeenCalledTimes(1);

    const odontogramCalls = vi.mocked(d.getOdontogram).mock.calls.length;
    const evolutionCalls = vi.mocked(d.getEvolutions).mock.calls.length;
    const second = createClinicalRecordView(d);
    document.body.replaceChildren(second.element);
    await settle();

    expect(second.element.textContent).toContain('Elegí un paciente');
    expect(vi.mocked(d.getOdontogram).mock.calls.length).toBe(odontogramCalls);
    expect(vi.mocked(d.getEvolutions).mock.calls.length).toBe(evolutionCalls);
  });

  it('un 401 al registrar anamnesis descarta el borrador y notifica sin reintentar', async () => {
    const storage = captureStorage();
    const onSessionExpired = vi.fn();
    const submit = vi.fn(async () => expired()) as unknown as Parameters<typeof createAnamnesisForm>[0]['submit'];
    const form = createAnamnesisForm({ submit, onSessionExpired });
    document.body.replaceChildren(form.element);
    const field = form.element.querySelector<HTMLTextAreaElement>('#anamnesis-notes');
    if (field !== null) { field.value = DRAFT; }

    form.element.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(onSessionExpired).toHaveBeenCalledTimes(1));

    expect(submit).toHaveBeenCalledTimes(1);
    expect(field?.value).toBe('');
    expect(storage.dump()).not.toContain(DRAFT);
    storage.stop();
  });

  it('un 401 al corregir descarta el motivo y notifica sin reintentar', async () => {
    const storage = captureStorage();
    const onSessionExpired = vi.fn();
    const onSubmit = vi.fn(async () => expired()) as unknown as Parameters<typeof createCorrectionDialog>[0]['onSubmit'];
    const dialog = createCorrectionDialog({
      entryLabel: 'Versión 2', type: 'evolutions', onSubmit, onClose: vi.fn(), onSessionExpired,
    });
    document.body.replaceChildren(dialog.element);
    const reason = dialog.element.querySelector<HTMLTextAreaElement>('#correction-reason');
    const content = dialog.element.querySelector<HTMLTextAreaElement>('#correction-content');
    if (reason !== null) { reason.value = DRAFT; }
    if (content !== null) { content.value = DRAFT; }

    dialog.element.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();
    await vi.waitFor(() => expect(onSessionExpired).toHaveBeenCalledTimes(1));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(reason?.value).toBe('');
    expect(content?.value).toBe('');
    expect(storage.dump()).not.toContain(DRAFT);
    storage.stop();
  });

  it('un 401 al registrar evolución o lote odontográfico también descarta el borrador', async () => {
    const onSessionExpired = vi.fn();
    const evolutionSubmit = vi.fn(async () => expired()) as unknown as Parameters<typeof createEvolutionsForm>[0]['submit'];
    const evo = createEvolutionsForm({ submit: evolutionSubmit, onSessionExpired });
    document.body.replaceChildren(evo.element);
    const description = evo.element.querySelector<HTMLTextAreaElement>('#evo-description');
    if (description !== null) { description.value = DRAFT; }
    evo.element.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(onSessionExpired).toHaveBeenCalledTimes(1));
    expect(description?.value).toBe('');
    expect(evolutionSubmit).toHaveBeenCalledTimes(1);

    const odoSubmit = vi.fn(async () => expired()) as unknown as Parameters<typeof createOdontogramForm>[0]['submit'];
    const odo = createOdontogramForm({ submit: odoSubmit, selectedTooth: '36', onSessionExpired });
    document.body.replaceChildren(odo.element);
    const notes = odo.element.querySelector<HTMLInputElement>('#odo-notas');
    if (notes !== null) { notes.value = DRAFT; }
    odo.element.querySelector<HTMLButtonElement>('[data-action="add"]')?.click();
    odo.element.querySelector<HTMLButtonElement>('[data-action="submit"]')?.click();
    await vi.waitFor(() => expect(onSessionExpired).toHaveBeenCalledTimes(2));
    expect(notes?.value).toBe('');
    expect(odoSubmit).toHaveBeenCalledTimes(1);
  });
});
