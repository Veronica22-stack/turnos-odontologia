import { h } from '../../core/dom/h';
import { dayLabels, formatTime, localDateOf, type Clock } from '../../shared/utils/dates';
import type { BookingGateway } from '../booking/booking-types';
import { backButton, bookingHead, loadingStatus, noticeAlert, present } from '../booking/booking-ui';
import { createSchedulePicker, type PickerTerminal } from '../booking/schedule-step';
import type { Slot } from '../booking/slots';
import { whenOf } from './booking-card';
import { manageNotice, type ManageNoticeKind } from './manage-messages';
import type { ManagedBooking } from './manage-types';

/** Pantalla de reprogramacion (design D7) sobre el selector de dia y horario de C-24. */

function durationMin(booking: ManagedBooking): number {
  return Math.round((Date.parse(booking.endsAt) - Date.parse(booking.startsAt)) / 60_000);
}

function head(booking: ManagedBooking, onBack: () => void, withDuration: boolean): HTMLElement[] {
  const { time } = whenOf(booking.startsAt, booking.endsAt);
  const lead = withDuration
    ? `${booking.serviceName} · ${durationMin(booking)} min · horarios de Buenos Aires`
    : `${booking.serviceName} · horarios de Buenos Aires`;
  return [
    backButton(onBack),
    bookingHead('Elegí el nuevo horario', lead),
    h(
      'div',
      { class: 'choice-summary' },
      h('span', { class: 'muted' }, 'Turno actual'),
      h('span', { class: 'what num' }, `${dayLabels(localDateOf(booking.startsAt)).medium} · ${time}`),
    ),
  ];
}

function section(...children: ReadonlyArray<Node | null>): HTMLElement {
  return h('section', { class: 'booking', attrs: { 'aria-labelledby': 'step-title' } }, ...present(...children));
}

/** Mientras se identifica la prestacion publica del turno (D5). */
export function renderRescheduleLoading(booking: ManagedBooking, onBack: () => void): HTMLElement {
  return section(...head(booking, onBack, false), loadingStatus('Buscando horarios…'));
}

/** La prestacion no se puede identificar o ya no tiene disponibilidad publica (spec "no disponible"). */
export function renderRescheduleUnavailable(booking: ManagedBooking, onBack: () => void): HTMLElement {
  return section(
    ...head(booking, onBack, false),
    noticeAlert(manageNotice({ kind: 'reschedule-unavailable' }), 'status'),
    h(
      'div',
      { class: 'actions' },
      h('button', { class: 'btn btn-secondary btn-lg', attrs: { type: 'button' }, on: { click: onBack } }, 'Volver a mi turno'),
    ),
  );
}

export interface RescheduleStepProps {
  readonly booking: ManagedBooking;
  readonly serviceId: string;
  readonly gateway: Pick<BookingGateway, 'availability'>;
  readonly clock: Clock;
  readonly initialDate: string;
  readonly chosen?: Slot;
  /** Inicio rechazado (409): el selector destaca los horarios cercanos. */
  readonly suggestFrom?: string;
  readonly notice?: ManageNoticeKind;
  readonly busy: boolean;
  readonly onBack: () => void;
  readonly onDateChange: (date: string) => void;
  readonly onChoose: (slot: Slot) => void;
  readonly onConfirm: () => void;
  readonly onViewCurrent: () => void;
  readonly onTerminal: (kind: PickerTerminal) => void;
}

export interface RescheduleStep {
  readonly element: HTMLElement;
  /** Alterna el estado de envio sin volver a montar el selector. */
  setBusy(busy: boolean): void;
}

export function renderRescheduleStep(props: RescheduleStepProps): RescheduleStep {
  let chosen: Slot | undefined = props.chosen;
  let busy = props.busy;
  const summary = h('div', { class: 'summary-line' });
  const confirm = h('button', { class: 'btn btn-primary btn-lg btn-block', attrs: { type: 'button' } });
  const wrap = h('div', { class: 'picker-wrap' });

  function refresh(): void {
    if (chosen === undefined) {
      summary.replaceChildren(h('span', { class: 'muted' }, 'Elegí un horario para continuar'));
    } else {
      const medium = dayLabels(localDateOf(chosen.startsAt)).medium;
      summary.replaceChildren(
        h('span', { class: 'muted' }, `Nuevo horario · ${medium}`),
        ' ',
        h('strong', { class: 'num' }, `${formatTime(chosen.startsAt)} – ${formatTime(chosen.endsAt)}`),
      );
    }
    if (busy) {
      confirm.replaceChildren(h('span', { class: 'spinner', aria: { hidden: 'true' } }), 'Reprogramando…');
      confirm.setAttribute('aria-busy', 'true');
      confirm.setAttribute('aria-disabled', 'true');
    } else {
      confirm.replaceChildren('Confirmar cambio');
      confirm.removeAttribute('aria-busy');
      if (chosen === undefined) {
        confirm.setAttribute('aria-disabled', 'true');
      } else {
        confirm.removeAttribute('aria-disabled');
      }
    }
    wrap.classList.toggle('picker-busy', busy);
    if (busy) {
      wrap.setAttribute('inert', '');
    } else {
      wrap.removeAttribute('inert');
    }
  }

  confirm.addEventListener('click', () => {
    if (!busy && chosen !== undefined) {
      props.onConfirm();
    }
  });

  const picker = createSchedulePicker({
    gateway: props.gateway,
    serviceId: props.serviceId,
    clock: props.clock,
    initialDate: props.initialDate,
    ...(chosen === undefined ? {} : { selectedStartsAt: chosen.startsAt }),
    ...(props.suggestFrom === undefined ? {} : { suggestFrom: props.suggestFrom }),
    onDateChange: (date) => {
      chosen = undefined;
      refresh();
      props.onDateChange(date);
    },
    onChoose: (slot) => {
      chosen = slot;
      refresh();
      props.onChoose(slot);
    },
    onTerminal: props.onTerminal,
  });
  wrap.append(picker.element);
  refresh();

  const unknown = props.notice?.kind === 'unknown-reschedule';
  const element = section(
    ...head(props.booking, props.onBack, true),
    props.notice === undefined ? null : noticeAlert(manageNotice(props.notice)),
    unknown
      ? h(
          'div',
          { class: 'actions' },
          h('button', { class: 'btn btn-secondary', attrs: { type: 'button' }, on: { click: props.onViewCurrent } }, 'Ver estado actual'),
        )
      : null,
    wrap,
    h('div', { class: 'sticky-cta' }, summary, confirm),
  );

  return {
    element,
    setBusy(next) {
      busy = next;
      refresh();
    },
  };
}
