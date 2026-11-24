import { h } from '../../core/dom/h';
import type { ClinicalResult, EvolutionInput } from './clinical-record-gateway';
import { clinicalFailureCopy } from './clinical-record-messages';
import type { EvolutionEntry } from './clinical-record-types';
import { isSessionExpired } from './session-expiry';

/** Alta de evoluciones: descripción obligatoria, turno opcional y errores propios (7.2). */

export interface EvolutionsFormDeps {
  readonly submit: (input: EvolutionInput) => Promise<ClinicalResult<EvolutionEntry>>;
  readonly onSessionExpired?: () => void;
}

export interface EvolutionsForm {
  readonly element: HTMLElement;
}

const MAX = 5000;

export function createEvolutionsForm(deps: EvolutionsFormDeps): EvolutionsForm {
  const errorSlot = h('div', { class: 'clinical-form__errors' });
  const description = h('textarea', { class: 'textarea', attrs: { id: 'evo-description', name: 'description', rows: '3', maxlength: String(MAX) } });
  const appointment = h('input', { class: 'input', attrs: { id: 'evo-appointment', name: 'appointmentId', type: 'text' } });
  const button = h('button', { class: 'btn btn-primary', attrs: { type: 'submit' } }, 'Registrar evolución');
  const form = h(
    'form',
    { class: 'form-grid' },
    errorSlot,
    h('div', { class: 'clinical-field' }, h('label', { attrs: { for: 'evo-description' } }, 'Descripción'), description),
    h('div', { class: 'clinical-field' },
      h('label', { attrs: { for: 'evo-appointment' } }, 'Turno asociado (opcional)'), appointment),
    button,
  );

  function error(text: string): void {
    errorSlot.replaceChildren(h('p', { attrs: { role: 'alert' } }, text));
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const value = description.value.trim();
    if (value === '') {
      error('La descripción es obligatoria.');
      return;
    }
    if (value.length > MAX) {
      error(`La descripción admite hasta ${MAX} caracteres.`);
      return;
    }
    const appointmentId = appointment.value.trim();
    const input: EvolutionInput = {
      description: value,
      ...(appointmentId === '' ? {} : { appointmentId }),
    };
    errorSlot.replaceChildren();
    button.setAttribute('disabled', '');
    void deps.submit(input).then((result) => {
      if (isSessionExpired(result)) {
        description.value = '';
        appointment.value = '';
        deps.onSessionExpired?.();
        return;
      }
      if (!result.ok) {
        const copy = clinicalFailureCopy(result.failure);
        error(`${copy.title} ${copy.text}`);
      }
    }).finally(() => button.removeAttribute('disabled'));
  });

  return { element: h('div', { class: 'evolutions-form' }, h('h3', {}, 'Nueva evolución'), form) };
}
