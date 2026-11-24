import { alertBox, formIcon } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import { formatDateTime } from '../../shared/utils/dates';
import { MESSAGES } from './patients-messages';
import type { PatientDetail } from './patients-types';

/** Ficha individual con insignias e indicador RN-PA-03 (D3/D6). */

export interface PatientCardCallbacks {
  readonly onBack: () => void;
  readonly onEdit: () => void;
  readonly onDeactivate: () => void;
}

const EMPTY = '—';

function button(label: string, className: string, action: string, onClick: () => void): HTMLButtonElement {
  const el = h('button', { class: `btn ${className}`, attrs: { type: 'button', 'data-action': action } }, label);
  el.addEventListener('click', onClick);
  return el;
}

function badge(label: string, className: string): HTMLElement {
  return h('span', { class: `badge ${className}` }, formIcon('check'), label);
}

export function createPatientCard(patient: PatientDetail, callbacks: PatientCardCallbacks): HTMLElement {
  const meta = h(
    'dl',
    { class: 'meta-list' },
    h('dt', {}, 'DNI'),
    h('dd', { class: 'readonly num' }, patient.dni, ' ', h('span', { class: 'help' }, '(solo lectura)')),
    h('dt', {}, 'Teléfono'),
    h('dd', { class: 'num' }, patient.phone),
    h('dt', {}, 'Email'),
    h('dd', {}, patient.email ?? EMPTY),
    h('dt', {}, 'Fecha de nacimiento'),
    h('dd', { class: 'num' }, patient.birthDate ?? EMPTY),
    h('dt', {}, 'Dirección'),
    h('dd', {}, patient.address ?? EMPTY),
    h('dt', {}, 'Origen'),
    h('dd', {}, h('span', { class: 'badge badge-source' }, formIcon('info'), patient.source)),
  );

  const head = h(
    'div',
    { class: 'detail-head' },
    h(
      'div',
      {},
      h('h2', { attrs: { id: 'ficha-t' } }, 'Datos de contacto'),
      h('p', { class: 'sub' }, 'Ficha de paciente · solo datos de filiación y contacto'),
    ),
    badge(patient.active ? 'Activo' : 'Inactivo', patient.active ? 'badge-active' : 'badge-inactive'),
  );

  const review =
    patient.reviewRequired === true
      ? h(
          'div',
          { class: 'review-notice' },
          h('span', { class: 'badge badge-review', attrs: { role: 'status' } }, formIcon('warning'), MESSAGES.review.title),
          h('p', { class: 'help' }, MESSAGES.review.text),
        )
      : null;

  const card = h(
    'div',
    { class: 'detail-card', attrs: { 'aria-labelledby': 'ficha-t' } },
    head,
    review,
    meta,
    h(
      'div',
      { class: 'detail-section' },
      h('h3', {}, 'Registro'),
      h(
        'p',
        { class: 'audit-meta' },
        `Creada el ${formatDateTime(patient.createdAt)} · Actualizada el ${formatDateTime(patient.updatedAt)} (America/Argentina/Buenos_Aires).`,
      ),
    ),
    h(
      'div',
      { class: 'detail-actions' },
      patient.active ? null : alertBox({ variant: 'info', role: 'status', icon: 'info', text: 'Esta ficha está inactiva; sus turnos y su historia clínica se conservan.' }),
      button('Editar ficha', 'btn-primary', 'edit', callbacks.onEdit),
      patient.active ? button('Dar de baja', 'btn-danger-outline', 'deactivate', callbacks.onDeactivate) : null,
    ),
  );

  return h(
    'section',
    { class: 'patients-page detail' },
    button('Volver al listado', 'btn-ghost back-link', 'back', callbacks.onBack),
    h(
      'div',
      { class: 'page-head' },
      h('h1', { attrs: { tabindex: '-1' } }, `${patient.firstName} ${patient.lastName}`),
      h('p', { class: 'lead' }, 'Ficha individual · solo datos de contacto. La historia clínica se gestiona en otro módulo.'),
    ),
    card,
  );
}
