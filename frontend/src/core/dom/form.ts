import { h, svg } from './h';

/** Helpers de formulario accesible: campo con etiqueta, ayuda, error vinculado y boton "Mostrar". */

export type FormIconName = 'error' | 'warning' | 'info' | 'lock' | 'eye' | 'check' | 'cross' | 'dot';

// Trazos de los mockups aprobados de C-06 (viewBox 20x20, trazo 1.6).
const ICON_SHAPES: Readonly<Record<FormIconName, ReadonlyArray<readonly [string, Readonly<Record<string, string>>]>>> = {
  error: [
    ['circle', { cx: '10', cy: '10', r: '8.25' }],
    ['path', { d: 'M10 5.8v5M10 13.8v.1', 'stroke-width': '1.9' }],
  ],
  warning: [
    ['path', { d: 'M10 2.8l8 14H2z', 'stroke-linejoin': 'round' }],
    ['path', { d: 'M10 8v4M10 14.6v.1', 'stroke-width': '1.9' }],
  ],
  info: [
    ['circle', { cx: '10', cy: '10', r: '8.25' }],
    ['path', { d: 'M10 9v5M10 6.2v.1', 'stroke-width': '1.9' }],
  ],
  lock: [
    ['rect', { x: '4', y: '9', width: '12', height: '8.5', rx: '1.8' }],
    ['path', { d: 'M7 9V6.5a3 3 0 0 1 6 0V9' }],
  ],
  eye: [
    ['path', { d: 'M1.8 10S4.8 4.5 10 4.5 18.2 10 18.2 10 15.2 15.5 10 15.5 1.8 10 1.8 10z', 'stroke-linejoin': 'round' }],
    ['circle', { cx: '10', cy: '10', r: '2.6' }],
  ],
  check: [['path', { d: 'M4.5 10.5l3.5 3.5 7.5-8', 'stroke-width': '1.9', 'stroke-linejoin': 'round' }]],
  cross: [['path', { d: 'M5.5 5.5l9 9M14.5 5.5l-9 9', 'stroke-width': '1.9' }]],
  dot: [['circle', { cx: '10', cy: '10', r: '2.2' }]],
};

/** Icono decorativo (aria-hidden): siempre acompanado de texto visible o de nombre accesible en el control. */
export function formIcon(name: FormIconName): SVGElement {
  return svg(
    'svg',
    { attrs: { viewBox: '0 0 20 20', focusable: 'false' }, aria: { hidden: 'true' } },
    ...ICON_SHAPES[name].map(([tag, attrs]) =>
      svg(tag, {
        attrs: { fill: 'none', stroke: 'currentColor', 'stroke-width': '1.6', 'stroke-linecap': 'round', ...attrs },
      }),
    ),
  );
}

export interface FieldOptions {
  readonly id: string;
  readonly label: string;
  readonly type?: 'text' | 'email' | 'password' | 'tel' | 'time' | 'number' | 'datetime-local';
  readonly autocomplete: string;
  readonly inputmode?: string;
  readonly min?: string;
  readonly max?: string;
  readonly step?: string;
  readonly help?: string;
  readonly value?: string;
  /** Agrega el boton "Mostrar contraseña" (solo para `type: 'password'`). */
  readonly reveal?: boolean;
}

export interface Field {
  readonly element: HTMLElement;
  readonly input: HTMLInputElement;
  /** Vuelve a ocultar la contraseña (tras enviar). No hace nada si no hay boton. */
  conceal(): void;
}

type FormControl = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;

function describedBy(input: FormControl): string[] {
  return (input.getAttribute('aria-describedby') ?? '').split(/\s+/).filter((id) => id !== '');
}

function setDescribedBy(input: FormControl, ids: readonly string[]): void {
  if (ids.length === 0) {
    input.removeAttribute('aria-describedby');
  } else {
    input.setAttribute('aria-describedby', ids.join(' '));
  }
}

export function field(options: FieldOptions): Field {
  const { id } = options;
  const type = options.type ?? 'text';
  const input = h('input', {
    class: 'input',
    attrs: {
      id,
      name: id,
      type,
      autocomplete: options.autocomplete,
      required: '',
      ...(options.inputmode === undefined ? {} : { inputmode: options.inputmode }),
      ...(options.min === undefined ? {} : { min: options.min }),
      ...(options.max === undefined ? {} : { max: options.max }),
      ...(options.step === undefined ? {} : { step: options.step }),
      ...(type === 'email' ? { autocapitalize: 'none', spellcheck: 'false' } : {}),
    },
  });
  if (options.value !== undefined) {
    input.value = options.value;
  }

  let toggle: HTMLButtonElement | undefined;
  let control: HTMLElement = input;
  if (options.reveal === true) {
    const button = h(
      'button',
      {
        class: 'reveal-btn',
        attrs: { type: 'button', 'aria-label': 'Mostrar contraseña', 'aria-controls': id },
        aria: { pressed: 'false' },
      },
      formIcon('eye'),
    );
    button.addEventListener('click', () => {
      const showing = input.type === 'password';
      input.type = showing ? 'text' : 'password';
      button.setAttribute('aria-pressed', showing ? 'true' : 'false');
    });
    toggle = button;
    control = h('div', { class: 'input-reveal' }, input, button);
  }

  const element = h('div', { class: 'field' }, h('label', { attrs: { for: id } }, options.label), control);
  if (options.help !== undefined) {
    element.append(h('p', { class: 'help', attrs: { id: `${id}-help` } }, options.help));
    setDescribedBy(input, [`${id}-help`]);
  }

  return {
    element,
    input,
    conceal() {
      input.type = 'password';
      toggle?.setAttribute('aria-pressed', 'false');
    },
  };
}

/** Marca el campo como invalido y muestra `message` (como texto) vinculado por `aria-describedby`. */
export function setFieldError(input: FormControl, message: string): void {
  const errorId = `${input.id}-error`;
  const container = input.closest('.field');
  if (container === null) {
    return;
  }
  const existing = container.querySelector<HTMLElement>(`#${CSS.escape(errorId)}`);
  if (existing !== null) {
    existing.querySelector('span')?.replaceChildren(message);
  } else {
    container.append(h('p', { class: 'error-msg', attrs: { id: errorId } }, formIcon('error'), h('span', {}, message)));
  }
  input.setAttribute('aria-invalid', 'true');
  const ids = describedBy(input);
  if (!ids.includes(errorId)) {
    setDescribedBy(input, [...ids, errorId]);
  }
}

/** Quita el error de un campo conservando la ayuda. */
export function clearFieldError(input: FormControl): void {
  const errorId = `${input.id}-error`;
  input.closest('.field')?.querySelector(`#${CSS.escape(errorId)}`)?.remove();
  input.removeAttribute('aria-invalid');
  setDescribedBy(
    input,
    describedBy(input).filter((id) => id !== errorId),
  );
}

export function clearFieldErrors(root: ParentNode): void {
  for (const input of root.querySelectorAll<FormControl>('input[aria-invalid="true"], select[aria-invalid="true"], textarea[aria-invalid="true"]')) {
    clearFieldError(input);
  }
}

export interface AlertOptions {
  readonly variant: 'info' | 'success' | 'warning' | 'danger';
  /** `alert` anuncia de inmediato (errores); `status` es informativo. */
  readonly role: 'alert' | 'status';
  readonly icon: FormIconName;
  readonly title?: string;
  readonly text?: string;
}

/** Aviso del design system (`.alert-*`); titulo en negrita y texto, siempre como nodos de texto. */
export function alertBox(options: AlertOptions): HTMLElement {
  const body = h('div', {});
  if (options.title !== undefined) {
    body.append(h('strong', {}, options.title));
  }
  if (options.title !== undefined && options.text !== undefined) {
    body.append(' ');
  }
  if (options.text !== undefined) {
    body.append(options.text);
  }
  return h(
    'div',
    { class: `alert alert-${options.variant}`, attrs: { role: options.role } },
    formIcon(options.icon),
    body,
  );
}
