import { alertBox, type FormIconName } from '../../core/dom/form';
import { h, svg } from '../../core/dom/h';
import type { BookingNotice, EmptyMessage } from './booking-messages';
import type { BookingStep } from './booking-step-url';

/** Piezas visuales comunes de los pasos (stepper, encabezado, volver, avisos, estados). */

const NOTICE_ICONS: Readonly<Record<BookingNotice['variant'], FormIconName>> = {
  info: 'info',
  warning: 'warning',
  danger: 'error',
};

export function stepper(step: BookingStep): HTMLElement {
  const bars = ([1, 2, 3] as const).map((n) =>
    h('li', { ...(n < step ? { class: 'done' } : {}), ...(n === step ? { attrs: { 'aria-current': 'step' } } : {}) }),
  );
  return h(
    'div',
    { class: 'stepper' },
    h('span', {}, `Paso ${step} de 3`),
    h('ol', { aria: { hidden: 'true' } }, ...bars),
  );
}

export function backButton(onClick: () => void): HTMLButtonElement {
  const icon = svg(
    'svg',
    { attrs: { viewBox: '0 0 16 16', focusable: 'false' }, aria: { hidden: 'true' } },
    svg('path', {
      attrs: {
        d: 'M10 3L5 8l5 5',
        fill: 'none',
        stroke: 'currentColor',
        'stroke-width': '1.8',
        'stroke-linecap': 'round',
        'stroke-linejoin': 'round',
      },
    }),
  );
  return h('button', { class: 'btn btn-ghost booking-back', attrs: { type: 'button' }, on: { click: onClick } }, icon, 'Volver');
}

export function bookingHead(title: string, lead?: string): HTMLElement {
  return h(
    'div',
    { class: 'booking-head' },
    h('h1', { attrs: { id: 'step-title', tabindex: '-1' } }, title),
    lead === undefined ? null : h('p', { class: 'lead' }, lead),
  );
}

export function noticeAlert(notice: BookingNotice, role: 'alert' | 'status' = 'alert'): HTMLElement {
  const box = alertBox({
    variant: notice.variant,
    role,
    icon: NOTICE_ICONS[notice.variant],
    title: notice.title,
    text: notice.detail,
  });
  if (notice.tbd !== undefined) {
    box.lastElementChild?.append(' ', h('span', { class: 'tbd' }, notice.tbd));
  }
  return box;
}

export function emptyState(message: EmptyMessage, compact = false): HTMLElement {
  const icon = svg(
    'svg',
    { attrs: { viewBox: '0 0 20 20', focusable: 'false' }, aria: { hidden: 'true' } },
    svg('circle', { attrs: { cx: '10', cy: '10', r: '8.25', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.6' } }),
    svg('path', {
      attrs: { d: 'M6.5 10h7', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.6', 'stroke-linecap': 'round' },
    }),
  );
  return h(
    'div',
    { class: compact ? 'empty empty-compact' : 'empty' },
    h('div', { class: 'ico' }, icon),
    h('h2', {}, message.title),
    message.tbd === undefined
      ? h('p', {}, message.detail)
      : h('p', {}, message.detail, ' ', h('span', { class: 'tbd' }, message.tbd), '.'),
  );
}

export function loadingStatus(message: string): HTMLElement {
  return h('div', { class: 'loading', attrs: { role: 'status' } }, h('span', { class: 'spinner', aria: { hidden: 'true' } }), message);
}

/** Descarta los `null` (elementos opcionales) para `replaceChildren`. */
export function present(...items: ReadonlyArray<Node | null>): Node[] {
  return items.filter((item): item is Node => item !== null);
}
