import { h } from '../../core/dom/h';
import type { PatientSummary } from './patients-types';

/** Tabla desktop y lista apilada móvil del listado (D6). Todo el dato del paciente se inserta como texto. */

export type OpenPatient = (id: string) => void;

function fullName(patient: PatientSummary): string {
  return `${patient.firstName} ${patient.lastName}`;
}

function openLink(patient: PatientSummary, className: string, label: string | undefined, onOpen: OpenPatient): HTMLAnchorElement {
  const link = h(
    'a',
    { class: className, attrs: { href: '#', ...(label === undefined ? {} : { 'aria-label': label }) } },
    label === undefined ? fullName(patient) : undefined,
  );
  link.addEventListener('click', (event) => {
    event.preventDefault();
    onOpen(patient.id);
  });
  return link;
}

export function createPatientTable(patients: readonly PatientSummary[], onOpen: OpenPatient): HTMLTableElement {
  const body = h('tbody');
  for (const patient of patients) {
    body.append(
      h(
        'tr',
        {},
        h('td', {}, openLink(patient, 'pname', undefined, onOpen)),
        h('td', { class: 'num' }, patient.dni),
        h('td', { class: 'num' }, patient.phone),
      ),
    );
  }
  return h(
    'table',
    { class: 'data' },
    h('caption', { class: 'sr-only' }, 'Pacientes activos: nombre, DNI y teléfono. Cada nombre abre la ficha.'),
    h(
      'thead',
      {},
      h('tr', {}, h('th', { attrs: { scope: 'col' } }, 'Paciente'), h('th', { attrs: { scope: 'col' } }, 'DNI'), h('th', { attrs: { scope: 'col' } }, 'Teléfono')),
    ),
    body,
  );
}

export function createPatientList(patients: readonly PatientSummary[], onOpen: OpenPatient): HTMLUListElement {
  const list = h('ul', { class: 'patient-list only-mobile', attrs: { 'aria-label': 'Pacientes activos' } });
  for (const patient of patients) {
    const link = openLink(patient, '', `Ver ficha de ${fullName(patient)}`, onOpen);
    list.append(
      h(
        'li',
        {},
        link,
      ),
    );
    link.append(
      h('span', { class: 'name' }, fullName(patient)),
      h('span', { class: 'sub' }, `DNI ${patient.dni} · Tel. ${patient.phone}`),
    );
  }
  return list;
}
