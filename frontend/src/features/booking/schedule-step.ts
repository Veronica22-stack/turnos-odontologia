import { h } from '../../core/dom/h';
import { addLocalDays, dayLabels, formatTime, localDateOf, today, type Clock } from '../../shared/utils/dates';
import { bookingNotice, type BookingNotice } from './booking-messages';
import { backButton, bookingHead, emptyState, loadingStatus, noticeAlert, present, stepper } from './booking-ui';
import type { BookingGateway, PublicService } from './booking-types';
import { groupSlots, suggestNearest, type Slot } from './slots';

/** Paso 2 (design D4): tira de dias + horarios. `createSchedulePicker` se reutiliza en C-25. */

const DAYS_PER_BATCH = 14;
const MAX_DAYS = 98;
const SKELETON_SLOTS = 6;
const SUGGESTIONS = 3;

export type PickerTerminal = 'service-gone' | 'no-services';

export interface SchedulePickerOptions {
  readonly gateway: Pick<BookingGateway, 'availability'>;
  readonly serviceId: string;
  readonly clock: Clock;
  readonly initialDate?: string;
  readonly selectedStartsAt?: string;
  /** Inicio rechazado por el servidor: se destacan los horarios mas cercanos. */
  readonly suggestFrom?: string;
  readonly onDateChange: (date: string) => void;
  readonly onChoose: (slot: Slot) => void;
  readonly onTerminal: (kind: PickerTerminal) => void;
}

export interface SchedulePicker {
  readonly element: HTMLElement;
}

function monthLabel(from: string, to: string): string {
  const first = dayLabels(from).long.split(' de ')[1] ?? '';
  const last = dayLabels(to).long.split(' de ')[1] ?? '';
  return first === last ? `Día · ${first}` : `Día · ${first} – ${last}`;
}

export function createSchedulePicker(options: SchedulePickerOptions): SchedulePicker {
  const { gateway, serviceId, onDateChange, onChoose, onTerminal } = options;
  const start = today(options.clock);
  const lastAllowed = addLocalDays(start, MAX_DAYS - 1);
  let selectedDate = options.initialDate !== undefined && options.initialDate >= start ? options.initialDate : start;
  let selectedStartsAt = options.selectedStartsAt;
  let suggestFrom = options.suggestFrom;
  let dayCount = 0;
  let request = 0;

  const dayButtons = new Map<string, HTMLButtonElement>();
  const label = h('p', { class: 'month-label', attrs: { id: 'days-label' } });
  const moreButton = h('button', { class: 'btn btn-secondary day-more', attrs: { type: 'button' } }, 'Más días');
  const strip = h('div', { class: 'day-strip' });
  const slotArea = h('div', { class: 'slot-area', attrs: { 'aria-live': 'polite' } });

  function appendDays(count: number): void {
    const target = Math.min(MAX_DAYS, dayCount + count);
    for (let i = dayCount; i < target; i += 1) {
      const date = addLocalDays(start, i);
      const labels = dayLabels(date);
      const dayButton = h(
        'button',
        { class: 'day', attrs: { type: 'button', 'aria-label': labels.long }, aria: { pressed: String(date === selectedDate) } },
        h('span', { class: 'dow', aria: { hidden: 'true' } }, labels.short),
        h('span', { class: 'dnum', aria: { hidden: 'true' } }, labels.day),
      );
      dayButton.addEventListener('click', () => choose(date));
      dayButtons.set(date, dayButton);
      strip.insertBefore(dayButton, moreButton);
    }
    dayCount = target;
    label.textContent = monthLabel(start, addLocalDays(start, dayCount - 1));
    moreButton.hidden = dayCount >= MAX_DAYS;
  }

  function ensureVisible(date: string): void {
    while (!dayButtons.has(date) && dayCount < MAX_DAYS) {
      appendDays(DAYS_PER_BATCH);
    }
  }

  function markSelectedDay(): void {
    for (const [date, dayButton] of dayButtons) {
      dayButton.setAttribute('aria-pressed', String(date === selectedDate));
    }
    dayButtons.get(selectedDate)?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
  }

  function choose(date: string): void {
    if (date === selectedDate) {
      return;
    }
    selectedDate = date;
    selectedStartsAt = undefined;
    suggestFrom = undefined;
    markSelectedDay();
    onDateChange(date);
    void load();
  }

  function slotButton(slot: Slot): HTMLButtonElement {
    const slotEl = h(
      'button',
      {
        class: 'slot',
        attrs: { type: 'button', 'data-starts-at': slot.startsAt },
        aria: { pressed: String(slot.startsAt === selectedStartsAt) },
      },
      formatTime(slot.startsAt),
    );
    slotEl.addEventListener('click', () => {
      selectedStartsAt = slot.startsAt;
      for (const other of slotArea.querySelectorAll('button.slot')) {
        other.setAttribute('aria-pressed', String(other.getAttribute('data-starts-at') === slot.startsAt));
      }
      onChoose(slot);
    });
    return slotEl;
  }

  function group(id: string, title: string, slots: readonly Slot[], className: string): HTMLElement {
    return h(
      'div',
      { class: className, attrs: { role: 'group', 'aria-labelledby': id } },
      h('h2', { attrs: { id } }, title),
      h('div', { class: 'slots' }, ...slots.map(slotButton)),
    );
  }

  function showNotice(notice: BookingNotice, withRetry: boolean): void {
    slotArea.replaceChildren(
      ...present(
        noticeAlert(notice),
        withRetry
          ? h('button', { class: 'btn btn-secondary', attrs: { type: 'button' }, on: { click: () => void load() } }, 'Reintentar')
          : null,
      ),
    );
  }

  function showEmpty(): void {
    const next = addLocalDays(selectedDate, 1);
    const empty = emptyState(
      { title: 'No hay horarios disponibles este día.', detail: 'Probá con otro día de la lista.' },
      true,
    );
    if (next <= lastAllowed) {
      empty.append(
        h(
          'button',
          {
            class: 'btn btn-secondary',
            attrs: { type: 'button' },
            on: {
              click: () => {
                ensureVisible(next);
                choose(next);
              },
            },
          },
          'Ver el día siguiente',
        ),
      );
    }
    slotArea.replaceChildren(empty);
  }

  function showSlots(slots: readonly Slot[]): void {
    if (slots.length === 0) {
      showEmpty();
      return;
    }
    const grouped = groupSlots(slots);
    const nearest = suggestFrom === undefined ? [] : suggestNearest(slots, suggestFrom, SUGGESTIONS);
    slotArea.replaceChildren(
      ...present(
        nearest.length === 0 ? null : group('sug-t', 'Horarios cercanos', nearest, 'suggested'),
        grouped.morning.length === 0 ? null : group('sg-m', 'Mañana', grouped.morning, 'slot-group'),
        grouped.afternoon.length === 0 ? null : group('sg-t', 'Tarde', grouped.afternoon, 'slot-group'),
      ),
    );
  }

  async function load(): Promise<void> {
    const id = ++request;
    const date = selectedDate;
    slotArea.replaceChildren(
      loadingStatus('Buscando horarios…'),
      h(
        'div',
        { class: 'slots', aria: { hidden: 'true' } },
        ...Array.from({ length: SKELETON_SLOTS }, () => h('div', { class: 'slot-skeleton' })),
      ),
    );
    const result = await gateway.availability(serviceId, date);
    if (id !== request) {
      return;
    }
    switch (result.kind) {
      case 'ok':
        showSlots(result.slots);
        return;
      case 'no-services':
      case 'service-gone':
        onTerminal(result.kind);
        return;
      case 'rate-limited':
        showNotice(
          bookingNotice(
            result.retryAfterSeconds === undefined
              ? { kind: 'rate-limited' }
              : { kind: 'rate-limited', retryAfterSeconds: result.retryAfterSeconds },
          ),
          false,
        );
        return;
      case 'connection':
        showNotice(bookingNotice({ kind: 'connection', what: 'slots' }), true);
        return;
    }
  }

  moreButton.addEventListener('click', () => appendDays(DAYS_PER_BATCH));
  strip.append(moreButton);
  ensureVisible(selectedDate);
  markSelectedDay();

  const element = h(
    'div',
    { class: 'schedule-picker' },
    h('div', { attrs: { role: 'group', 'aria-labelledby': 'days-label' } }, label, strip),
    slotArea,
  );
  void load();
  return { element };
}

export interface ScheduleStepProps extends Omit<SchedulePickerOptions, 'serviceId' | 'onChoose'> {
  readonly service: PublicService;
  readonly slotTaken: boolean;
  readonly onChoose: (slot: Slot) => void;
  readonly onBack: () => void;
  readonly onContinue: () => void;
  /** Horario ya elegido (al volver desde el paso 3): habilita Continuar y muestra el resumen. */
  readonly selectedSlot?: Slot;
}

/** Paso 2 completo: encabezado, aviso de conflicto, selector y resumen fijo al pie. */
export function renderScheduleStep(props: ScheduleStepProps): HTMLElement {
  let chosen: Slot | undefined = props.selectedSlot;
  const summary = h('div', { class: 'summary-line' });
  const continueButton = h(
    'button',
    { class: 'btn btn-primary btn-lg btn-block', attrs: { type: 'button' } },
    'Continuar',
  );

  function refresh(): void {
    if (chosen === undefined) {
      summary.replaceChildren(h('span', { class: 'muted' }, 'Elegí un horario para continuar'));
      continueButton.setAttribute('aria-disabled', 'true');
      return;
    }
    summary.replaceChildren(
      h('span', { class: 'muted' }, dayLabels(localDateOf(chosen.startsAt)).medium),
      ' ',
      h('strong', { class: 'num' }, `${formatTime(chosen.startsAt)} – ${formatTime(chosen.endsAt)}`),
    );
    continueButton.removeAttribute('aria-disabled');
  }

  continueButton.addEventListener('click', () => {
    if (chosen !== undefined) {
      props.onContinue();
    }
  });
  refresh();

  const picker = createSchedulePicker({
    gateway: props.gateway,
    serviceId: props.service.id,
    clock: props.clock,
    ...(props.initialDate === undefined ? {} : { initialDate: props.initialDate }),
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

  return h(
    'section',
    { class: 'booking', attrs: { 'aria-labelledby': 'step-title' } },
    stepper(2),
    backButton(props.onBack),
    bookingHead('Elegí día y horario', `${props.service.name} · ${props.service.durationMin} min · horarios de Buenos Aires`),
    props.slotTaken ? noticeAlert(bookingNotice({ kind: 'slot-unavailable' })) : null,
    picker.element,
    h('div', { class: 'sticky-cta' }, summary, continueButton),
  );
}
