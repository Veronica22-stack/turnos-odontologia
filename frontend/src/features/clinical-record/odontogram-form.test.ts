import { describe, expect, it, vi } from 'vitest';
import type { ClinicalResult, OdontogramEntryInput } from './clinical-record-gateway';
import type { OdontogramEntry } from './clinical-record-types';
import { createOdontogramForm } from './odontogram-form';

function submitFactory() {
  return vi.fn(async (_entries: readonly OdontogramEntryInput[]): Promise<ClinicalResult<readonly OdontogramEntry[]>> => ({
    ok: false, failure: { kind: 'connection' },
  }));
}

function set(form: HTMLElement, id: string, value: string): void {
  const el = form.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`);
  if (el !== null) {
    el.value = value;
  }
}

function click(form: HTMLElement, attr: string): void {
  form.querySelector<HTMLButtonElement>(`[data-action="${attr}"]`)?.click();
}

describe('odontogram form: lote 1–64 estados', () => {
  it('agrega estados validados y envía un único POST con el lote y solo campos del contrato', async () => {
    const submit = submitFactory();
    const form = createOdontogramForm({ submit, selectedTooth: '36' });
    document.body.replaceChildren(form.element);

    set(form.element, 'odo-estado', 'CARIES');
    set(form.element, 'odo-cara', 'OCCLUSAL');
    set(form.element, 'odo-notas', ' dolor ');
    click(form.element, 'add');
    set(form.element, 'odo-estado', 'FILLED');
    set(form.element, 'odo-notas', '');
    click(form.element, 'add');
    click(form.element, 'submit');
    await vi.waitFor(() => expect(submit).toHaveBeenCalledTimes(1));

    expect(submit.mock.calls[0]?.[0]).toEqual([
      { toothCode: '36', condition: 'CARIES', surface: 'OCCLUSAL', notes: 'dolor' },
      { toothCode: '36', condition: 'FILLED', surface: 'OCCLUSAL' },
    ]);
  });

  it('ignora la cara en estados que no la admiten y no la envía', async () => {
    const submit = submitFactory();
    const form = createOdontogramForm({ submit, selectedTooth: '36' });
    document.body.replaceChildren(form.element);

    set(form.element, 'odo-estado', 'CROWN');
    set(form.element, 'odo-cara', 'OCCLUSAL');
    click(form.element, 'add');
    click(form.element, 'submit');
    await vi.waitFor(() => expect(submit).toHaveBeenCalledTimes(1));

    expect(submit.mock.calls[0]?.[0]).toEqual([{ toothCode: '36', condition: 'CROWN' }]);
    expect(form.element.querySelector('[role="alert"]')).toBeNull();
  });

  it('rechaza un estado fuera del catálogo sin eco del valor rechazado', () => {
    const submit = submitFactory();
    const form = createOdontogramForm({ submit, selectedTooth: '36' });
    document.body.replaceChildren(form.element);

    set(form.element, 'odo-estado', 'CARIES_MALICIOSO');
    click(form.element, 'add');

    expect(submit).not.toHaveBeenCalled();
    expect(form.element.querySelector('[role="alert"]')?.textContent).not.toContain('CARIES_MALICIOSO');
  });

  it('no acumula más de 64 estados ni envía un lote vacío', async () => {
    const submit = submitFactory();
    const form = createOdontogramForm({ submit, selectedTooth: '36' });
    document.body.replaceChildren(form.element);

    click(form.element, 'submit');
    expect(submit).not.toHaveBeenCalled();

    set(form.element, 'odo-estado', 'HEALTHY');
    for (let i = 0; i < 65; i += 1) {
      click(form.element, 'add');
    }
    click(form.element, 'submit');
    await vi.waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
    expect(submit.mock.calls[0]?.[0]).toHaveLength(64);
  });
});
