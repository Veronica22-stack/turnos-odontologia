import { clearFieldErrors, field, setFieldError, type Field } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import type { FieldError } from '../../core/http/errors';
import { dayLabels, formatTime, localDateOf } from '../../shared/utils/dates';
import type { BookingNotice } from './booking-messages';
import { serverFieldMessage } from './booking-messages';
import type { PublicService } from './booking-types';
import { backButton, bookingHead, noticeAlert, stepper } from './booking-ui';
import type { Slot } from './slots';
import { validateBookingForm, type BookingFormValues } from './validation';

/** Paso 3 (design D5): formulario, validacion local, honeypot y estado de envio. */

export interface DetailsStepProps {
  readonly service: PublicService;
  readonly slot: Slot;
  readonly values: BookingFormValues;
  readonly submitting?: boolean;
  readonly onChange: (values: BookingFormValues) => void;
  readonly onSubmit: (values: BookingFormValues) => void;
  readonly onBack: () => void;
}

export interface DetailsStep {
  readonly element: HTMLElement;
  setSubmitting(submitting: boolean): void;
  showNotice(notice: BookingNotice | null): void;
  showServerErrors(errors: readonly FieldError[]): void;
}

const SUBMIT_LABEL = 'Confirmar reserva';
const FIELD_IDS = ['firstName', 'lastName', 'dni', 'phone', 'email', 'privacyAccepted'] as const;

export function renderDetailsStep(props: DetailsStepProps): DetailsStep {
  const { values } = props;
  const firstName = field({ id: 'firstName', label: 'Nombre', autocomplete: 'given-name', value: values.firstName });
  const lastName = field({ id: 'lastName', label: 'Apellido', autocomplete: 'family-name', value: values.lastName });
  const dni = field({
    id: 'dni',
    label: 'DNI',
    autocomplete: 'off',
    inputmode: 'numeric',
    help: 'Como figura en el documento; podés usar puntos.',
    value: values.dni,
  });
  const phone = field({
    id: 'phone',
    label: 'Teléfono',
    type: 'tel',
    autocomplete: 'tel',
    help: 'Con código de área, por ejemplo 11 2345 6789.',
    value: values.phone,
  });
  const email = field({
    id: 'email',
    label: 'Email',
    type: 'email',
    autocomplete: 'email',
    inputmode: 'email',
    help: 'Te enviamos ahí la confirmación y el enlace para cancelar o reprogramar.',
    value: values.email,
  });

  const trap = h('input', { attrs: { id: 'website', name: 'website', type: 'text', tabindex: '-1', autocomplete: 'off' } });
  trap.value = values.website;
  const honeypot = h(
    'div',
    { class: 'hp', aria: { hidden: 'true' } },
    h('label', { attrs: { for: 'website' } }, 'Sitio web'),
    trap,
  );

  const privacy = h('input', { attrs: { id: 'privacyAccepted', name: 'privacyAccepted', type: 'checkbox', required: '' } });
  privacy.checked = values.privacyAccepted;
  const privacyField = h(
    'div',
    { class: 'field check-field' },
    h(
      'label',
      { class: 'check' },
      privacy,
      h(
        'span',
        {},
        'Leí y acepto el ',
        h('a', { attrs: { href: '#', target: '_blank', rel: 'noopener noreferrer' } }, 'aviso de privacidad'),
        ' ',
        h('span', { class: 'tbd' }, '(TBD · PA-10)'),
        '.',
      ),
    ),
  );

  const inputs: Readonly<Record<(typeof FIELD_IDS)[number], HTMLInputElement>> = {
    firstName: firstName.input,
    lastName: lastName.input,
    dni: dni.input,
    phone: phone.input,
    email: email.input,
    privacyAccepted: privacy,
  };
  const textFields: readonly Field[] = [firstName, lastName, dni, phone, email];

  const button = h('button', { class: 'btn btn-primary btn-lg btn-block', attrs: { type: 'submit' } }, SUBMIT_LABEL);
  const alertSlot = h('div', { class: 'booking-alert' });
  const form = h(
    'form',
    { class: 'booking-form', attrs: { novalidate: '' } },
    h('div', { class: 'field-row' }, firstName.element, lastName.element),
    dni.element,
    phone.element,
    email.element,
    honeypot,
    privacyField,
    button,
  );

  let submitting = false;

  function current(): BookingFormValues {
    return {
      firstName: firstName.input.value,
      lastName: lastName.input.value,
      dni: dni.input.value,
      phone: phone.input.value,
      email: email.input.value,
      website: trap.value,
      privacyAccepted: privacy.checked,
    };
  }

  function setSubmitting(value: boolean): void {
    submitting = value;
    for (const item of textFields) {
      item.input.readOnly = value;
    }
    trap.readOnly = value;
    if (value) {
      privacy.setAttribute('aria-disabled', 'true');
      button.setAttribute('aria-busy', 'true');
      button.setAttribute('aria-disabled', 'true');
      button.replaceChildren(h('span', { class: 'spinner', aria: { hidden: 'true' } }), 'Reservando…');
    } else {
      privacy.removeAttribute('aria-disabled');
      button.removeAttribute('aria-busy');
      button.removeAttribute('aria-disabled');
      button.replaceChildren(SUBMIT_LABEL);
    }
  }

  function markErrors(errors: ReadonlyArray<{ field: string; message: string }>): void {
    for (const error of errors) {
      const target = inputs[error.field as (typeof FIELD_IDS)[number]];
      if (target !== undefined) {
        setFieldError(target, error.message);
      }
    }
    const first = errors.map((error) => inputs[error.field as (typeof FIELD_IDS)[number]]).find((el) => el !== undefined);
    first?.focus();
  }

  function onSubmit(event: Event): void {
    event.preventDefault();
    if (submitting) {
      return;
    }
    clearFieldErrors(form);
    const validation = validateBookingForm(current());
    if (!validation.ok) {
      markErrors(validation.errors);
      return;
    }
    props.onSubmit(validation.value);
  }

  form.addEventListener('submit', onSubmit);
  form.addEventListener('input', () => props.onChange(current()));
  form.addEventListener('change', () => props.onChange(current()));
  privacy.addEventListener('click', (event) => {
    if (submitting) {
      event.preventDefault();
    }
  });
  if (props.submitting === true) {
    setSubmitting(true);
  }

  const slotDay = dayLabels(localDateOf(props.slot.startsAt)).long;
  const element = h(
    'section',
    { class: 'booking', attrs: { 'aria-labelledby': 'step-title' } },
    stepper(3),
    backButton(props.onBack),
    bookingHead('Tus datos', 'Todos los campos son obligatorios.'),
    h(
      'div',
      { class: 'choice-summary', attrs: { 'aria-label': 'Turno elegido' } },
      h('span', {}, h('span', { class: 'what' }, props.service.name), ` · ${props.service.durationMin} min`),
      h('span', { class: 'num' }, `${slotDay} · ${formatTime(props.slot.startsAt)} – ${formatTime(props.slot.endsAt)}`),
    ),
    alertSlot,
    form,
  );

  return {
    element,
    setSubmitting,
    showNotice(notice) {
      alertSlot.replaceChildren(...(notice === null ? [] : [noticeAlert(notice)]));
    },
    showServerErrors(errors) {
      clearFieldErrors(form);
      const messages: Array<{ field: string; message: string }> = [];
      for (const error of errors) {
        const message = serverFieldMessage(error.field, error.code);
        if (message !== undefined && !messages.some((m) => m.field === error.field)) {
          messages.push({ field: error.field, message });
        }
      }
      markErrors(messages);
    },
  };
}
