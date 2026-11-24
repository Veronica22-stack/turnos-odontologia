import { h } from '../../core/dom/h';
import type { AnamnesisInput, ClinicalResult } from './clinical-record-gateway';
import type { AnamnesisEntry } from './clinical-record-types';
import { isSessionExpired } from './session-expiry';

/** Alta de anamnesis: validación local y una sola petición (5.2). */

export interface AnamnesisFormDeps {
  readonly submit: (input: AnamnesisInput) => Promise<ClinicalResult<AnamnesisEntry>>;
  readonly onSessionExpired?: () => void;
}

export interface AnamnesisForm {
  readonly element: HTMLElement;
}

const FIELDS: ReadonlyArray<readonly [keyof AnamnesisInput, string]> = [
  ['allergies', 'Alergias'],
  ['medications', 'Medicación'],
  ['medicalConditions', 'Condiciones médicas'],
  ['habits', 'Hábitos'],
  ['notes', 'Notas'],
];

const MAX = 2000;

export function createAnamnesisForm(deps: AnamnesisFormDeps): AnamnesisForm {
  const errorSlot = h('div', { class: 'clinical-form__errors' });
  const inputs = new Map<keyof AnamnesisInput, HTMLTextAreaElement>();
  const controls = FIELDS.map(([key, label]) => {
    const id = `anamnesis-${key}`;
    const input = h('textarea', { attrs: { id, name: key, rows: '2', maxlength: String(MAX) } });
    inputs.set(key, input);
    return h('div', { class: 'clinical-field' }, h('label', { attrs: { for: id } }, label), input);
  });
  const button = h('button', { class: 'btn btn-primary', attrs: { type: 'submit' } }, 'Registrar anamnesis');
  const form = h(
    'form',
    { class: 'anamnesis-form' },
    errorSlot,
    ...controls,
    button,
  );
  const element = h('div', { class: 'anamnesis-form-wrap' }, h('h3', {}, 'Nueva anamnesis'), form);

  function read(): AnamnesisInput {
    const result: Record<keyof AnamnesisInput, string> = {
      allergies: '', medications: '', medicalConditions: '', habits: '', notes: '',
    };
    for (const [key, input] of inputs) {
      result[key] = input.value.trim();
    }
    return result;
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const input = read();
    const values = Object.values(input);
    if (values.every((value) => value === '')) {
      errorSlot.replaceChildren(h('p', { attrs: { role: 'alert' } }, 'Completá al menos un campo.'));
      return;
    }
    const tooLong = FIELDS.find(([key]) => input[key].length > MAX);
    if (tooLong !== undefined) {
      errorSlot.replaceChildren(h('p', { attrs: { role: 'alert' } }, `El campo debe tener hasta ${MAX} caracteres.`));
      return;
    }
    errorSlot.replaceChildren();
    button.setAttribute('disabled', '');
    void deps.submit(input).then((result) => {
      if (isSessionExpired(result)) {
        for (const field of inputs.values()) {
          field.value = '';
        }
        deps.onSessionExpired?.();
      }
    }).finally(() => button.removeAttribute('disabled'));
  });

  return { element };
}
