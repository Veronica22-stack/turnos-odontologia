import { h } from './h';

/** Confirmación con `<dialog>` nativo (C-19 D8): texto como nodos y foco devuelto al origen. */

export interface ConfirmOptions {
  readonly title: string;
  readonly text: string;
  readonly confirmLabel?: string;
  readonly cancelLabel?: string;
}

let sequence = 0;

export function confirmAction(options: ConfirmOptions): Promise<boolean> {
  const opener = document.activeElement instanceof HTMLElement ? document.activeElement : undefined;
  const titleId = `confirm-title-${++sequence}`;
  const accept = h(
    'button',
    { class: 'btn btn-danger', attrs: { type: 'button', 'data-confirm': 'accept' } },
    options.confirmLabel ?? 'Confirmar',
  );
  const cancel = h(
    'button',
    { class: 'btn btn-secondary', attrs: { type: 'button', 'data-confirm': 'cancel' } },
    options.cancelLabel ?? 'Cancelar',
  );
  const dialog = h(
    'dialog',
    { class: 'confirm-dialog', attrs: { 'aria-labelledby': titleId } },
    h('h2', { attrs: { id: titleId } }, options.title),
    h('p', {}, options.text),
    h('div', { class: 'form-actions' }, cancel, accept),
  );

  return new Promise<boolean>((resolve) => {
    let settled = false;
    function finish(result: boolean): void {
      if (settled) {
        return;
      }
      settled = true;
      dialog.remove();
      opener?.focus();
      resolve(result);
    }
    accept.addEventListener('click', () => finish(true));
    cancel.addEventListener('click', () => finish(false));
    dialog.addEventListener('cancel', (event) => {
      event.preventDefault();
      finish(false);
    });
    document.body.append(dialog);
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
    accept.focus();
  });
}
