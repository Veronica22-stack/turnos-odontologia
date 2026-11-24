import { describe, expect, it, vi } from 'vitest';
import { clinicalFailureCopy, type ClinicalFailure } from './clinical-record-messages';
import type { ClinicalResult, EvolutionInput } from './clinical-record-gateway';
import type { EvolutionEntry } from './clinical-record-types';
import { createEvolutionsForm } from './evolutions-form';

function result(failure: ClinicalFailure): Promise<ClinicalResult<EvolutionEntry>> {
  return Promise.resolve({ ok: false, failure });
}

function factory(resultFn: (input: EvolutionInput) => Promise<ClinicalResult<EvolutionEntry>> = async () => result({ kind: 'connection' })) {
  return vi.fn(resultFn);
}

function submitForm(form: HTMLElement): void {
  form.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

describe('evolutions form: alta', () => {
  it('envía descripción obligatoria y omite appointmentId vacío', async () => {
    const submit = factory();
    const form = createEvolutionsForm({ submit });
    document.body.replaceChildren(form.element);
    const description = form.element.querySelector<HTMLTextAreaElement>('#evo-description');
    if (description !== null) {
      description.value = '  Control老子  ';
    }

    submitForm(form.element);
    await vi.waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
    expect(submit.mock.calls[0]?.[0]).toEqual({ description: 'Control老子' });
  });

  it('envía appointmentId cuando se informa', async () => {
    const submit = factory();
    const form = createEvolutionsForm({ submit });
    document.body.replaceChildren(form.element);
    const description = form.element.querySelector<HTMLTextAreaElement>('#evo-description');
    const appointment = form.element.querySelector<HTMLInputElement>('#evo-appointment');
    if (description !== null) { description.value = 'Evolución'; }
    if (appointment !== null) { appointment.value = 'turno-9'; }

    submitForm(form.element);
    await vi.waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
    expect(submit.mock.calls[0]?.[0]).toEqual({ description: 'Evolución', appointmentId: 'turno-9' });
  });

  it('no envía nada con descripción vacía o mayor a 5000 caracteres, sin eco del valor', () => {
    const submit = factory();
    const form = createEvolutionsForm({ submit });
    document.body.replaceChildren(form.element);

    submitForm(form.element);
    expect(submit).not.toHaveBeenCalled();
    expect(form.element.querySelector('[role="alert"]')?.textContent).toContain('obligatoria');

    const description = form.element.querySelector<HTMLTextAreaElement>('#evo-description');
    if (description !== null) { description.value = 'x'.repeat(5001); }
    submitForm(form.element);
    expect(submit).not.toHaveBeenCalled();
    expect(form.element.querySelector('[role="alert"]')?.textContent).toContain('5000');
    expect(form.element.textContent).not.toContain('x'.repeat(5001));
  });

  it('mapea los tres errores de turno sin reintentar automáticamente', async () => {
    for (const kind of ['appointment-not-started', 'appointment-not-attended', 'appointment-not-found'] as const) {
      const submit = factory(() => result({ kind }));
      const form = createEvolutionsForm({ submit });
      document.body.replaceChildren(form.element);
      const description = form.element.querySelector<HTMLTextAreaElement>('#evo-description');
      if (description !== null) { description.value = 'Evolución'; }

      submitForm(form.element);
      await vi.waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
      await vi.waitFor(() => expect(form.element.querySelector('[role="alert"]')?.textContent).toContain(clinicalFailureCopy({ kind }).title));
      expect(form.element.querySelector('[data-retry]')).toBeNull();
      expect(submit).toHaveBeenCalledTimes(1);
    }
  });
});
