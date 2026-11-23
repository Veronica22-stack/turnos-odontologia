import { formIcon } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import { bookingNotice } from './booking-messages';
import { bookingHead, noticeAlert, stepper } from './booking-ui';
import type { PublicService } from './booking-types';

export interface ServiceStepProps {
  readonly services: readonly PublicService[];
  readonly selected: PublicService | undefined;
  /** Aviso "la prestacion ya no esta disponible" tras volver desde otro paso. */
  readonly serviceGone: boolean;
  readonly onSelect: (service: PublicService) => void;
  readonly onContinue: () => void;
}

const ERROR_ID = 'service-error';

function option(service: PublicService, checked: boolean, onSelect: () => void): HTMLElement {
  const input = h('input', { attrs: { type: 'radio', name: 'service', value: service.id } });
  input.checked = checked;
  input.addEventListener('change', onSelect);
  return h(
    'label',
    { class: 'service-option' },
    input,
    h('span', { class: 'name' }, service.name),
    h('span', { class: 'dur num' }, `${service.durationMin} min`),
    service.description === null ? null : h('span', { class: 'desc' }, service.description),
  );
}

/** Paso 1 (design D4/D5): prestaciones como grupo de seleccion unica. */
export function renderServiceStep(props: ServiceStepProps): HTMLElement {
  let selected = props.selected;
  const summary = h('div', { class: 'summary-line' });
  const fieldset = h('fieldset', { class: 'service-list' }, h('legend', {}, 'Prestación'));
  const errorSlot = h('div', {});

  function refreshSummary(): void {
    summary.replaceChildren(
      ...(selected === undefined
        ? [h('span', { class: 'muted' }, 'Elegí una prestación para continuar')]
        : [h('span', { class: 'muted' }, 'Elegiste'), ' ', h('strong', {}, `${selected.name} · ${selected.durationMin} min`)]),
    );
  }

  function clearError(): void {
    errorSlot.replaceChildren();
    fieldset.removeAttribute('aria-describedby');
    fieldset.removeAttribute('aria-invalid');
  }

  for (const service of props.services) {
    fieldset.append(
      option(service, selected?.id === service.id, () => {
        selected = service;
        clearError();
        refreshSummary();
        props.onSelect(service);
      }),
    );
  }
  refreshSummary();

  function onContinue(): void {
    if (selected === undefined) {
      errorSlot.replaceChildren(h('p', { class: 'error-msg', attrs: { id: ERROR_ID } }, formIcon('error'), h('span', {}, 'Elegí una prestación.')));
      fieldset.setAttribute('aria-describedby', ERROR_ID);
      fieldset.setAttribute('aria-invalid', 'true');
      return;
    }
    props.onContinue();
  }

  return h(
    'section',
    { class: 'booking', attrs: { 'aria-labelledby': 'step-title' } },
    stepper(1),
    bookingHead('¿Qué turno necesitás?', 'Reservá online sin crear una cuenta. Te confirmamos por email.'),
    props.serviceGone ? noticeAlert(bookingNotice({ kind: 'service-gone' }), 'status') : null,
    fieldset,
    errorSlot,
    h(
      'div',
      { class: 'sticky-cta' },
      summary,
      h('button', { class: 'btn btn-primary btn-lg btn-block', attrs: { type: 'button' }, on: { click: onContinue } }, 'Continuar'),
    ),
  );
}
