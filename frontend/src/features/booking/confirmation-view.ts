import { alertBox, formIcon } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import { dayLabels, formatTime, localDateOf } from '../../shared/utils/dates';
import { consumeConfirmation, type ConfirmationData } from './confirmation-store';

function metaRow(label: string, value: string): HTMLElement[] {
  return [h('dt', {}, label), h('dd', {}, value)];
}

function withData(data: ConfirmationData): HTMLElement {
  const rows = [
    ...metaRow('Día', dayLabels(localDateOf(data.startsAt)).long),
    ...metaRow('Horario', `${formatTime(data.startsAt)} – ${formatTime(data.endsAt)}`),
    ...(data.practiceName === '' ? [] : metaRow('Consultorio', data.practiceName)),
  ];
  const notice = alertBox({ variant: 'info', role: 'status', icon: 'info' });
  notice.lastElementChild?.append(
    h('strong', {}, 'Te enviamos la confirmación a ', h('span', { class: 'email-strong' }, data.maskedEmail), '.'),
    ' Ahí está el enlace para cancelar o reprogramar el turno; guardalo. Si no llega en unos minutos, revisá la carpeta de spam.',
  );
  return h(
    'section',
    { class: 'booking', attrs: { 'aria-labelledby': 'confirm-title' } },
    h(
      'div',
      { class: 'confirm-hero' },
      h('div', { class: 'confirm-icon' }, formIcon('check')),
      h('h1', { attrs: { id: 'confirm-title', tabindex: '-1' } }, '¡Turno reservado!'),
    ),
    h(
      'div',
      { class: 'confirm-card' },
      h(
        'div',
        { class: 'confirm-card-head' },
        h('h2', {}, data.serviceName),
        h('span', { class: 'badge badge-confirmado' }, formIcon('check'), 'Confirmado'),
      ),
      h('dl', { class: 'meta-list' }, ...rows),
    ),
    notice,
    h('div', { class: 'actions' }, h('a', { class: 'btn btn-secondary btn-lg', attrs: { href: '/reservar' } }, 'Reservar otro turno')),
  );
}

function generic(): HTMLElement {
  return h(
    'section',
    { class: 'booking', attrs: { 'aria-labelledby': 'confirm-title' } },
    h(
      'div',
      { class: 'confirm-hero' },
      h('div', { class: 'confirm-icon' }, formIcon('info')),
      h('h1', { attrs: { id: 'confirm-title', tabindex: '-1' } }, 'Revisá tu email'),
      h('p', { class: 'lead' }, 'Si completaste una reserva, te enviamos la confirmación con el enlace para gestionarla.'),
    ),
    h('div', { class: 'actions' }, h('a', { class: 'btn btn-secondary btn-lg', attrs: { href: '/reservar' } }, 'Reservar un turno')),
  );
}

/** Vista `/reservar/confirmado`: consume la confirmacion en memoria; sin datos, la generica. */
export function createConfirmationView(): () => HTMLElement {
  return () => {
    const data = consumeConfirmation();
    return data === null ? generic() : withData(data);
  };
}
