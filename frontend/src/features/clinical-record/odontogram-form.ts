import { h } from '../../core/dom/h';
import type { ClinicalResult, OdontogramEntryInput } from './clinical-record-gateway';
import type { OdontogramEntry, ToothCondition, ToothSurface } from './clinical-record-types';
import { isSessionExpired } from './session-expiry';

/** Registro por lotes de estados del odontograma (6.2): validación local y una sola petición. */

export interface OdontogramFormDeps {
  readonly submit: (entries: readonly OdontogramEntryInput[]) => Promise<ClinicalResult<readonly OdontogramEntry[]>>;
  readonly selectedTooth: string;
  readonly onSessionExpired?: () => void;
}

export interface OdontogramForm {
  readonly element: HTMLElement;
}

const CONDITIONS: ReadonlySet<string> = new Set<ToothCondition>([
  'HEALTHY', 'CARIES', 'FILLED', 'MISSING', 'CROWN', 'ROOT_CANAL',
]);
const SURFACES: ReadonlySet<string> = new Set<ToothSurface>([
  'OCCLUSAL', 'MESIAL', 'DISTAL', 'VESTIBULAR', 'LINGUAL',
]);
const SURFACE_ONLY: ReadonlySet<string> = new Set<ToothCondition>(['CARIES', 'FILLED']);
const MAX_ENTRIES = 64;

export function createOdontogramForm(deps: OdontogramFormDeps): OdontogramForm {
  const errorSlot = h('div', { class: 'clinical-form__errors' });
  const tooth = h('input', { class: 'input num', attrs: { id: 'odo-pieza', type: 'text', readonly: '' } });
  tooth.value = deps.selectedTooth;
  const condition = h('select', { class: 'select', attrs: { id: 'odo-estado' } },
    ...[...CONDITIONS].map((value) => h('option', { attrs: { value } }, value)));
  const surface = h('select', { class: 'select', attrs: { id: 'odo-cara' } },
    ...[...SURFACES].map((value) => h('option', { attrs: { value } }, value)));
  const notes = h('input', { class: 'input', attrs: { id: 'odo-notas', type: 'text', maxlength: '2000' } });

  const count = h('p', { class: 'help', attrs: { role: 'status' } }, '0 estados en el lote');
  const submitButton = h('button', { class: 'btn btn-primary', attrs: { type: 'button', 'data-action': 'submit' } }, 'Registrar lote');
  let batch: OdontogramEntryInput[] = [];

  function error(message: string): void {
    errorSlot.replaceChildren(h('p', { attrs: { role: 'alert' } }, message));
  }

  function refreshCount(): void {
    count.textContent = `${batch.length} ${batch.length === 1 ? 'estado' : 'estados'} en el lote`;
  }

  function add(): void {
    errorSlot.replaceChildren();
    const value = condition.value;
    if (!CONDITIONS.has(value)) {
      error('Seleccioná un estado válido.');
      return;
    }
    const chosenSurface = surface.value;
    if (SURFACE_ONLY.has(value) && !SURFACES.has(chosenSurface)) {
      error('Seleccioná una cara válida.');
      return;
    }
    if (batch.length >= MAX_ENTRIES) {
      error('El lote admite hasta 64 estados.');
      return;
    }
    const entry: OdontogramEntryInput = {
      toothCode: deps.selectedTooth,
      condition: value as ToothCondition,
      ...(SURFACE_ONLY.has(value) ? { surface: chosenSurface as ToothSurface } : {}),
      ...(notes.value.trim() === '' ? {} : { notes: notes.value.trim() }),
    };
    batch = [...batch, entry];
    refreshCount();
  }

  async function submit(): Promise<void> {
    errorSlot.replaceChildren();
    if (batch.length === 0) {
      error('Agregá al menos un estado.');
      return;
    }
    const pending = batch;
    batch = [];
    refreshCount();
    submitButton.setAttribute('disabled', '');
    try {
      const result = await deps.submit(pending);
      if (isSessionExpired(result)) {
        notes.value = '';
        deps.onSessionExpired?.();
      }
    } finally {
      submitButton.removeAttribute('disabled');
    }
  }

  const form = h(
    'form',
    { class: 'form-grid', attrs: { novalidate: '' } },
    errorSlot,
    h('div', { class: 'field' }, h('label', { attrs: { for: 'odo-pieza' } }, 'Pieza seleccionada'), tooth),
    h('div', { class: 'field' }, h('label', { attrs: { for: 'odo-estado' } }, 'Estado'), condition),
    h('div', { class: 'field' }, h('label', { attrs: { for: 'odo-cara' } }, 'Cara (solo CARIES / FILLED)'), surface),
    h('div', { class: 'field' }, h('label', { attrs: { for: 'odo-notas' } }, 'Notas (opcional)'), notes),
    h('div', { class: 'field span-2' },
      h('button', { class: 'btn btn-secondary', attrs: { type: 'button', 'data-action': 'add' }, on: { click: add } }, 'Agregar al lote')),
    h('div', { class: 'field span-2' }, count, submitButton),
  );
  submitButton.addEventListener('click', () => { void submit(); });
  form.addEventListener('submit', (event) => event.preventDefault());

  return { element: h('div', { class: 'odontogram-form' }, h('h3', {}, 'Registro por lotes'), form) };
}
