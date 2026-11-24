import { h } from '../../core/dom/h';
import type { ClinicalResult, CorrectionContent, CorrectionInput } from './clinical-record-gateway';
import { clinicalFailureCopy } from './clinical-record-messages';
import type { ClinicalEntryType, ToothCondition } from './clinical-record-types';
import { isSessionExpired } from './session-expiry';

/** Diálogo de corrección: motivo obligatorio, contenido del tipo, foco inicial y doble envío bloqueado (8.1). */

export interface CorrectionDialogDeps {
  readonly entryLabel: string;
  readonly onSubmit: (input: CorrectionInput) => Promise<ClinicalResult<unknown>>;
  readonly onClose: () => void;
  readonly onSessionExpired?: () => void;
  /** Tipo de la entrada corregida; define la forma del contenido enviado. */
  readonly type?: ClinicalEntryType;
  /** Contenido vigente, precargado para corregir sin cambiar pieza, cara ni turno. */
  readonly content?: CorrectionContent;
}

export interface CorrectionDialog {
  readonly element: HTMLElement;
  close(): void;
}

const MAX = 500;
const CONDITIONS: readonly ToothCondition[] = ['HEALTHY', 'CARIES', 'FILLED', 'MISSING', 'CROWN', 'ROOT_CANAL'];
const CONTENT_LABEL: Readonly<Record<ClinicalEntryType, string>> = {
  anamnesis: 'Notas corregidas',
  odontogram: 'Notas corregidas',
  evolutions: 'Descripción corregida',
};

function textOf(content: CorrectionContent | undefined, key: 'notes' | 'description'): string {
  if (content === undefined || !(key in content)) { return ''; }
  const value = (content as unknown as Record<string, unknown>)[key];
  return typeof value === 'string' ? value : '';
}

function conditionOf(content: CorrectionContent | undefined): ToothCondition | undefined {
  if (content === undefined || !('condition' in content)) { return undefined; }
  const value = (content as unknown as Record<string, unknown>)['condition'];
  return typeof value === 'string' && CONDITIONS.includes(value as ToothCondition) ? value as ToothCondition : undefined;
}

export function createCorrectionDialog(deps: CorrectionDialogDeps): CorrectionDialog {
  const type = deps.type;
  const errorSlot = h('div', { class: 'clinical-form__errors' });
  const reason = h('textarea', { class: 'textarea', attrs: { id: 'correction-reason', rows: '3', maxlength: String(MAX) } });
  const correctedContent = h('textarea', {
    class: 'textarea', attrs: { id: 'correction-content', rows: '3', maxlength: '5000' },
  });
  const initialText = textOf(deps.content, type === 'evolutions' ? 'description' : 'notes');
  correctedContent.value = initialText;
  const initialCondition = conditionOf(deps.content);
  const conditionSelect = type === 'odontogram' && initialCondition !== undefined
    ? h('select', { class: 'select', attrs: { id: 'correction-condition' } },
      ...CONDITIONS.map((value) => h('option', { attrs: { value } }, value)))
    : undefined;
  if (conditionSelect !== undefined && initialCondition !== undefined) { conditionSelect.value = initialCondition; }
  const confirm = h('button', { class: 'btn btn-primary', attrs: { type: 'button', 'data-action': 'confirm' } }, 'Confirmar corrección');
  const cancel = h('button', { class: 'btn btn-secondary', attrs: { type: 'button', 'data-action': 'cancel' } }, 'Cancelar');

  const element = h(
    'div',
    { class: 'dialog-backdrop' },
    h(
      'div',
      { class: 'dialog-static', attrs: { role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': 'correction-title' } },
      h('div', { class: 'dialog-head' }, h('h2', { attrs: { id: 'correction-title' } }, 'Corregir entrada'), cancel),
      h('div', { class: 'dialog-body' },
        h('p', { class: 'help' }, deps.entryLabel),
        errorSlot,
        h('div', { class: 'clinical-field' },
          h('label', { attrs: { for: 'correction-reason' } }, 'Motivo de la corrección'), reason),
        ...(conditionSelect === undefined ? [] : [
          h('div', { class: 'clinical-field' },
            h('label', { attrs: { for: 'correction-condition' } }, 'Estado corregido'), conditionSelect),
        ]),
        h('div', { class: 'clinical-field' },
          h('label', { attrs: { for: 'correction-content' } },
            type === undefined ? 'Contenido corregido' : CONTENT_LABEL[type]),
          correctedContent),
        h('p', { class: 'help', attrs: { role: 'status' } }, 'La entrada original no se modifica: se crea una nueva versión.'),
      ),
      h('div', { class: 'dialog-foot' }, confirm),
    ),
  );

  function close(): void {
    document.removeEventListener('keydown', onKeydown);
    element.remove();
    deps.onClose();
  }

  function onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      close();
    }
  }

  function error(text: string): void {
    errorSlot.replaceChildren(h('p', { attrs: { role: 'alert' } }, text));
  }

  function correctedContentValue(): CorrectionContent {
    if (type === undefined) { return deps.content ?? {}; }
    if (type === 'evolutions') { return { description: correctedContent.value }; }
    if (type === 'odontogram') {
      const condition = conditionSelect?.value;
      return {
        ...(condition !== undefined && CONDITIONS.includes(condition as ToothCondition) ? { condition: condition as ToothCondition } : {}),
        notes: correctedContent.value,
      };
    }
    return { notes: correctedContent.value };
  }

  function discardDraft(): void {
    reason.value = '';
    correctedContent.value = initialText;
  }

  async function submit(): Promise<void> {
    const value = reason.value.trim();
    if (value === '') {
      error('El motivo es obligatorio.');
      return;
    }
    if (value.length > MAX) {
      error(`El motivo admite hasta ${MAX} caracteres.`);
      return;
    }
    errorSlot.replaceChildren();
    confirm.setAttribute('disabled', '');
    try {
      const result = await deps.onSubmit({ reason: value, content: correctedContentValue() });
      if (isSessionExpired(result)) {
        discardDraft();
        deps.onSessionExpired?.();
        return;
      }
      if (result.ok) {
        close();
      } else {
        const copy = clinicalFailureCopy(result.failure);
        error(`${copy.title} ${copy.text}`);
      }
    } finally {
      confirm.removeAttribute('disabled');
    }
  }

  cancel.addEventListener('click', close);
  confirm.addEventListener('click', () => { void submit(); });
  document.addEventListener('keydown', onKeydown);
  queueMicrotask(() => reason.focus());

  return { element, close };
}
