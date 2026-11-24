import { alertBox, clearFieldErrors, setFieldError } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import { MESSAGES } from './patients-messages';
import { validatePatientInput, type FieldErrors } from './patients-validation';
import type { PatientDetail, PatientInput } from './patients-types';

/** Formulario de edición (D5/D6): DNI y origen como texto; solo campos aceptados por la API. */

export interface PatientFormState {
  readonly patient: PatientDetail;
  readonly draft: PatientInput;
  readonly errors: FieldErrors;
  readonly hasUnknownField: boolean;
  readonly busy?: boolean;
}

export interface PatientFormCallbacks {
  readonly onSave: (input: PatientInput) => void;
  readonly onCancel: () => void;
}

interface FieldSpec {
  readonly name: keyof PatientInput;
  readonly label: string;
  readonly type: 'text' | 'tel' | 'email';
  readonly help?: string;
  readonly optional?: boolean;
}

const FIELDS: readonly FieldSpec[] = [
  { name: 'firstName', label: 'Nombre', type: 'text' },
  { name: 'lastName', label: 'Apellido', type: 'text' },
  { name: 'phone', label: 'Teléfono', type: 'tel', help: 'Sin 0 ni 15.' },
  { name: 'email', label: 'Email', type: 'email', optional: true },
  { name: 'birthDate', label: 'Fecha de nacimiento', type: 'text', help: 'Formato aaaa-mm-dd.', optional: true },
  { name: 'address', label: 'Dirección', type: 'text', optional: true },
];

function buildField(spec: FieldSpec, value: string): HTMLElement {
  const input = h('input', {
    class: `input${spec.name === 'phone' || spec.name === 'birthDate' ? ' num' : ''}`,
    attrs: {
      id: spec.name,
      name: spec.name,
      type: spec.type,
      ...(spec.optional === true ? {} : { required: '' }),
    },
  });
  input.value = value;
  return h(
    'div',
    { class: `field${spec.name === 'address' ? ' span-2' : ''}` },
    h('label', { attrs: { for: spec.name } }, spec.label, ' ',
      spec.optional === true ? h('span', { class: 'opt' }, '(opcional)') : h('span', { class: 'req', attrs: { 'aria-hidden': 'true' } }, '*')),
    input,
    spec.help === undefined ? null : h('span', { class: 'help' }, spec.help),
  );
}

export function createPatientForm(state: PatientFormState, callbacks: PatientFormCallbacks): HTMLElement {
  const fields = new Map<keyof PatientInput, HTMLInputElement>();
  const form = h('form', { class: 'form-grid', attrs: { novalidate: '' } });

  for (const spec of FIELDS) {
    const field = buildField(spec, state.draft[spec.name] ?? '');
    const input = field.querySelector<HTMLInputElement>(`[name="${spec.name}"]`);
    if (input !== null) {
      fields.set(spec.name, input);
    }
    form.append(field);
  }

  const save = h(
    'button',
    {
      class: 'btn btn-primary',
      attrs: { type: 'button', 'data-action': 'save', ...(state.busy === true ? { disabled: '', 'aria-busy': 'true' } : {}) },
    },
    'Guardar cambios',
  );
  const cancel = h('button', { class: 'btn btn-secondary', attrs: { type: 'button', 'data-action': 'cancel' } }, 'Cancelar');
  form.append(h('div', { class: 'form-actions span-2' }, save, cancel));

  form.addEventListener('submit', (event) => event.preventDefault());
  cancel.addEventListener('click', callbacks.onCancel);
  save.addEventListener('click', () => {
    if (state.busy === true) {
      return;
    }
    clearFieldErrors(form);
    const draft: PatientInput = {
      firstName: fields.get('firstName')?.value ?? '',
      lastName: fields.get('lastName')?.value ?? '',
      phone: fields.get('phone')?.value ?? '',
      email: fields.get('email')?.value ?? '',
      birthDate: fields.get('birthDate')?.value ?? '',
      address: fields.get('address')?.value ?? '',
    };
    const errors = validatePatientInput(draft);
    const count = Object.keys(errors).length;
    if (count > 0) {
      for (const [field, message] of Object.entries(errors)) {
        const input = fields.get(field as keyof PatientInput);
        if (input !== undefined && message !== undefined) {
          setFieldError(input, message);
        }
      }
      return;
    }
    callbacks.onSave({
      ...draft,
      email: draft.email === '' ? null : draft.email,
      birthDate: draft.birthDate === '' ? null : draft.birthDate,
      address: draft.address === '' ? null : draft.address,
    });
  });

  for (const [field, message] of Object.entries(state.errors)) {
    const input = fields.get(field as keyof PatientInput);
    if (input !== undefined && message !== undefined) {
      setFieldError(input, message);
    }
  }

  const notice = state.hasUnknownField
    ? alertBox({ variant: 'warning', role: 'alert', icon: 'warning', text: MESSAGES.unknownField.text })
    : null;

  return h(
    'section',
    { class: 'patients-page detail' },
    h(
      'div',
      { class: 'page-head' },
      h('h1', { attrs: { tabindex: '-1' } }, 'Editar ficha'),
      h('p', { class: 'lead' }, 'Solo se editan los campos aceptados por la API. El DNI y el origen son de solo lectura.'),
    ),
    h(
      'div',
      { class: 'detail-card' },
      h(
        'p',
        { class: 'help' },
        'Ficha de ',
        h('strong', {}, `${state.patient.firstName} ${state.patient.lastName}`),
        ' · DNI ',
        h('span', { class: 'num' }, state.patient.dni),
        ' (solo lectura) · Origen: ',
        h('span', { class: 'badge badge-source' }, state.patient.source),
      ),
      notice,
      form,
    ),
  );
}
