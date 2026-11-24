import { describe, expect, it, vi } from 'vitest';
import type { AnamnesisInput, ClinicalResult } from './clinical-record-gateway';
import type { AnamnesisEntry } from './clinical-record-types';
import { createAnamnesisForm } from './anamnesis-form';

function submitFactory() {
  return vi.fn(async (_input: AnamnesisInput): Promise<ClinicalResult<AnamnesisEntry>> => ({
    ok: false, failure: { kind: 'connection' },
  }));
}

function fill(form: HTMLElement, values: Record<string, string>): void {
  for (const [id, value] of Object.entries(values)) {
    const input = form.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`);
    if (input !== null) {
      input.value = value;
    }
  }
}

describe('anamnesis form', () => {
  it('envía una única petición con los cinco campos del contrato', async () => {
    const submit = submitFactory();
    const form = createAnamnesisForm({ submit });
    document.body.replaceChildren(form.element);
    fill(form.element, {
      'anamnesis-allergies': 'Penicilina',
      'anamnesis-medications': 'Ibuprofeno',
      'anamnesis-medicalConditions': 'Asma',
      'anamnesis-habits': 'No fuma',
      'anamnesis-notes': 'Control',
    });

    form.element.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(submit).toHaveBeenCalledTimes(1));

    expect(submit.mock.calls[0]?.[0]).toEqual({
      allergies: 'Penicilina', medications: 'Ibuprofeno', medicalConditions: 'Asma', habits: 'No fuma', notes: 'Control',
    });
  });

  it('no envía nada si todos los campos están vacíos y muestra error junto al campo', () => {
    const submit = submitFactory();
    const form = createAnamnesisForm({ submit });
    document.body.replaceChildren(form.element);

    form.element.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    expect(submit).not.toHaveBeenCalled();
    expect(form.element.querySelector('[role="alert"]')?.textContent).toContain('al menos un campo');
    expect(form.element.textContent).not.toContain('Penicilina');
  });

  it('rechaza un campo de más de 2000 caracteres sin repetir el valor', () => {
    const submit = submitFactory();
    const form = createAnamnesisForm({ submit });
    document.body.replaceChildren(form.element);
    fill(form.element, { 'anamnesis-notes': 'x'.repeat(2001) });

    form.element.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    expect(submit).not.toHaveBeenCalled();
    expect(form.element.querySelector('[role="alert"]')?.textContent).toContain('2000');
    expect(form.element.textContent).not.toContain('x'.repeat(2001));
  });
});
