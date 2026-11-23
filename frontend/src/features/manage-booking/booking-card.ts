import { formIcon } from '../../core/dom/form';
import { h, svg } from '../../core/dom/h';
import { dayLabels, formatTime, localDateOf } from '../../shared/utils/dates';
import type { Slot } from '../booking/slots';
import { statusBadge } from './manage-messages';
import type { ManagedBooking } from './manage-types';

/** Tarjeta del turno (design D6): prestacion, insignia con texto e icono, dia, horario y consultorio. */

function icon(path: string, viewBox = '0 0 16 16'): SVGElement {
  return svg(
    'svg',
    { attrs: { viewBox, focusable: 'false' }, aria: { hidden: 'true' } },
    svg('path', {
      attrs: { d: path, fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' },
    }),
  );
}

function badge(booking: ManagedBooking): HTMLElement {
  const { tone, label } = statusBadge(booking);
  if (tone === 'confirmed') {
    return h('span', { class: 'badge badge-confirmado' }, formIcon('check'), label);
  }
  if (tone === 'cancelled') {
    return h('span', { class: 'badge badge-cancelado' }, icon('M4 4l8 8M12 4l-8 8'), h('span', {}, label));
  }
  return h('span', { class: 'badge badge-neutral' }, icon('M8 4.4v3.8l2.2 1.4M8 2a6 6 0 100 12A6 6 0 008 2z'), label);
}

function metaRow(label: string, value: string, numeric = false): HTMLElement[] {
  return [h('dt', {}, label), h('dd', numeric ? { class: 'num' } : {}, value)];
}

/** Dia largo y horario de Buenos Aires de un instante ISO. */
export function whenOf(startsAt: string, endsAt: string): { day: string; time: string } {
  return { day: dayLabels(localDateOf(startsAt)).long, time: `${formatTime(startsAt)} – ${formatTime(endsAt)}` };
}

export function renderBookingCard(booking: ManagedBooking, options: { readonly previous?: Slot } = {}): HTMLElement {
  const { day, time } = whenOf(booking.startsAt, booking.endsAt);
  const { previous } = options;
  return h(
    'article',
    { class: booking.status === 'CANCELLED' ? 'confirm-card card-cancelled' : 'confirm-card', attrs: { 'aria-labelledby': 'turno-t' } },
    h('div', { class: 'confirm-card-head' }, h('h2', { attrs: { id: 'turno-t' } }, booking.serviceName), badge(booking)),
    h(
      'dl',
      { class: 'meta-list' },
      ...metaRow('Día', day),
      ...metaRow('Horario', time, true),
      ...(booking.practiceName === '' ? [] : metaRow('Consultorio', booking.practiceName)),
    ),
    previous === undefined
      ? null
      : h(
          'p',
          { class: 'previous-time' },
          h('span', { class: 'muted' }, 'Antes:'),
          ' ',
          h('s', { class: 'num' }, `${dayLabels(localDateOf(previous.startsAt)).medium} · ${formatTime(previous.startsAt)}`),
        ),
    h('p', { class: 'zone-note' }, 'Horarios de Buenos Aires.'),
  );
}
