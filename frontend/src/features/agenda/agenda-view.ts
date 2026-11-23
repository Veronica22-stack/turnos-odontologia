import { h, svg } from '../../core/dom/h';
import { alertBox, formIcon } from '../../core/dom/form';
import { confirmAction } from '../../core/dom/confirm';
import { agendaErrorMessage } from './agenda-messages';
import { createAppointmentForm } from './appointment-form';
import type { AgendaGateway } from './agenda-gateway';
import type { AppointmentDto, AppointmentStatus, AvailableSlotDto, AgendaBlock, PatientSummaryDto } from './agenda-types';
import { buildDayGrid, formatMinutes, gridRowSpan, gridRowStart, GRID_ROW_MINUTES, hourMinutes, type DayGrid, type DayGridAppointmentItem } from './day-grid';
import { brandName } from '../../layouts/brand';
import { dayBoundsUtc, formatDate, formatTime, localDateOf, parseInstant, today } from '../../shared/utils/dates';
import type { ServiceItem, WorkingHour } from '../settings/settings-types';

/** Vista `/agenda` (C-20 D1/D3/D4): agenda diaria y semanal sobre turnos, bloqueos y horarios de atención. */

export interface AgendaViewDeps {
  readonly gateway: AgendaGateway;
  readonly clock?: () => Date;
}

const WEEKDAY_NAMES: readonly string[] = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

const STATUS_CLASS: Readonly<Record<AppointmentStatus, string>> = {
  CONFIRMED: 's-confirmado',
  COMPLETED: 's-atendido',
  NO_SHOW: 's-ausente',
  CANCELLED: 's-cancelado',
};
const STATUS_BADGE: Readonly<Record<AppointmentStatus, string>> = {
  CONFIRMED: 'badge-confirmado',
  COMPLETED: 'badge-atendido',
  NO_SHOW: 'badge-ausente',
  CANCELLED: 'badge-cancelado',
};
const STATUS_TEXT: Readonly<Record<AppointmentStatus, string>> = {
  CONFIRMED: 'Confirmado',
  COMPLETED: 'Atendido',
  NO_SHOW: 'Ausente',
  CANCELLED: 'Cancelado',
};

const DAY_MS = 86_400_000;

export function createAgendaView(deps: AgendaViewDeps): HTMLElement {
  const { gateway } = deps;
  const clock = deps.clock ?? (() => new Date());

  const patientNames = new Map<string, string>();
  let services: readonly ServiceItem[] = [];
  const state: {
    day: string;
    mode: 'day' | 'week';
    appointments: readonly AppointmentDto[];
    hours: readonly WorkingHour[];
    blocks: readonly AgendaBlock[];
  } = {
    day: today(clock),
    mode: 'day',
    appointments: [],
    hours: [],
    blocks: [],
  };

  function jsDay(date: string): number {
    return dayBoundsUtc(date).start.getUTCDay();
  }

  /** Día de la semana según la zona del consultorio: 1 = lunes ... 7 = domingo. */
  function weekdayOf(date: string): number {
    const jsDayOfWeek = jsDay(date);
    return jsDayOfWeek === 0 ? 7 : jsDayOfWeek;
  }

  function weekdayName(date: string): string {
    return WEEKDAY_NAMES[jsDay(date)] ?? 'Domingo';
  }

  function dayOfMonth(date: string): string {
    return date.slice(8, 10);
  }

  function rangeBounds(): { start: Date; end: Date } {
    if (state.mode === 'day') {
      return dayBoundsUtc(state.day);
    }
    const days = weekDates(state.day);
    return { start: dayBoundsUtc(days[0] ?? state.day).start, end: dayBoundsUtc(days[6] ?? state.day).end };
  }

  async function loadAppointments(): Promise<void> {
    const bounds = rangeBounds();
    const result = await gateway.listAppointments(bounds.start.toISOString(), bounds.end.toISOString());
    state.appointments = result.ok ? (result.data ?? []) : [];
    render();
  }

  function init(): void {
    void (async () => {
      const [hours, blocks, patients, listServices] = await Promise.all([
        gateway.getWorkingHours(),
        gateway.listBlocks(),
        gateway.searchPatients(),
        gateway.listServices(),
      ]);
      state.hours = hours.ok ? (hours.data ?? []) : [];
      state.blocks = blocks.ok ? (blocks.data ?? []) : [];
      if (patients.ok) {
        for (const patient of patients.data ?? []) {
          patientNames.set(patient.id, `${patient.firstName} ${patient.lastName}`);
        }
      }
      services = listServices.ok ? (listServices.data ?? []) : [];
      await loadAppointments();
    })();
  }

  init();

  // ----- Toolbar -----

  const toolbarTitle = h('h3', { attrs: { id: 'agenda-toolbar-title' } });

  function dayTitle(): string {
    if (state.mode === 'day') {
      return `Agenda del ${weekdayName(state.day)} ${dayOfMonth(state.day)}`;
    }
    const days = weekDates(state.day);
    return `Agenda de la semana del ${formatLocalDate(days[0] ?? state.day)} al ${formatLocalDate(days[6] ?? state.day)}`;
  }

  /** 'YYYY-MM-DD' local → 'DD/MM/YYYY' (formato de lectura, sin parseInstant). */
  function formatLocalDate(date: string): string {
    return `${date.slice(8, 10)}/${date.slice(5, 7)}/${date.slice(0, 4)}`;
  }

  function updateToolbarTitle(): void {
    toolbarTitle.replaceChildren(`${brandName()} — Agenda — ${dayTitle()}`);
  }

  const prevButton = h(
    'button',
    { class: 'btn btn-secondary btn-icon', attrs: { type: 'button' }, on: { click: () => shiftDayBy(-1) } },
    chevLeft(),
  );
  const nextButton = h(
    'button',
    { class: 'btn btn-secondary btn-icon', attrs: { type: 'button' }, on: { click: () => shiftDayBy(1) } },
    chevRight(),
  );

  function updateDayNavLabels(): void {
    const label = state.mode === 'day' ? 'Día' : 'Semana';
    prevButton.setAttribute('aria-label', `${label} anterior`);
    nextButton.setAttribute('aria-label', `${label} siguiente`);
  }

  updateDayNavLabels();

  const newTurnoButton = h(
    'button',
    { class: 'btn btn-primary', attrs: { type: 'button' }, on: { click: () => openForm() } },
    plusIcon(),
    'Nuevo turno',
  );

  function setMode(mode: 'day' | 'week'): void {
    state.mode = mode;
    dayButton.setAttribute('aria-pressed', mode === 'day' ? 'true' : 'false');
    weekButton.setAttribute('aria-pressed', mode === 'week' ? 'true' : 'false');
    updateDayNavLabels();
    void loadAppointments();
  }

  const dayButton = h(
    'button',
    { class: 'seg-button', attrs: { type: 'button', 'aria-pressed': 'true' }, on: { click: () => setMode('day') } },
    'Día',
  );
  const weekButton = h(
    'button',
    { class: 'seg-button', attrs: { type: 'button', 'aria-pressed': 'false' }, on: { click: () => setMode('week') } },
    'Semana',
  );
  const segmented = h('div', { class: 'seg', attrs: { role: 'group', 'aria-label': 'Vista' } }, dayButton, weekButton);

  const toolbar = h(
    'div',
    { class: 'agenda-toolbar' },
    h('div', { class: 'toolbar-left' }, prevButton, toolbarTitle, nextButton),
    h('div', { class: 'toolbar-right' }, segmented, newTurnoButton),
  );

  const content = h('div', { class: 'agenda-content' });

  // ----- Render -----

  function render(): void {
    updateToolbarTitle();
    content.replaceChildren(state.mode === 'day' ? renderDayView() : renderWeekView());
  }

  function renderDayView(): HTMLElement {
    const appointments = state.appointments.filter((appointment) => localDateOf(parseInstant(appointment.startsAt)) === state.day);
    if (appointments.length === 0) {
      return emptyState('No hay turnos para este día', 'El día está vacío — cargá el primero.');
    }
    const hours = state.hours.filter((hour) => hour.weekday === weekdayOf(state.day));
    const grid = buildDayGrid({ workingHours: hours, appointments, blocks: state.blocks, date: state.day });
    return renderAgendaGrid(grid, `Turnos del ${weekdayName(state.day)} ${dayOfMonth(state.day)}`);
  }

  function renderWeekView(): HTMLElement {
    const days = weekDates(state.day);
    const region = h(
      'div',
      { class: 'week-grid', attrs: { role: 'region', 'aria-label': `Agenda de la semana ${formatLocalDate(days[0] ?? state.day)}` } },
    );
    for (const date of days) {
      region.append(renderWeekColumn(date));
    }
    return region;
  }

  function renderWeekColumn(date: string): HTMLElement {
    const column = h('article', { class: 'week-col', attrs: { 'aria-label': `${weekdayName(date)} ${dayOfMonth(date)}` } });
    column.append(h('h4', {}, `${weekdayName(date)} ${dayOfMonth(date)}`));
    const appointments = state.appointments.filter((appointment) => localDateOf(parseInstant(appointment.startsAt)) === date);
    if (appointments.length === 0) {
      column.append(emptyState(`No hay turnos para ${weekdayName(date)} ${dayOfMonth(date)}`, 'El día está vacío — cargá el primero.'));
      return column;
    }
    const hours = state.hours.filter((hour) => hour.weekday === weekdayOf(date));
    const blocks = state.blocks.filter((block) => localDateOf(parseInstant(block.startsAt)) === date);
    const grid = buildDayGrid({ workingHours: hours, appointments, blocks, date });
    column.append(renderAgendaGrid(grid, `Turnos del ${weekdayName(date)} ${dayOfMonth(date)}`));
    return column;
  }

  function renderAgendaGrid(grid: DayGrid, regionLabel: string): HTMLElement {
    const region = h('div', { class: 'agenda', attrs: { role: 'region', 'aria-label': regionLabel } });
    region.append(renderAgendaTimes(grid), renderAgendaCol(grid));
    return region;
  }

  function renderAgendaTimes(grid: DayGrid): HTMLElement {
    const cells: HTMLElement[] = [];
    for (let row = 0; row < grid.rows; row += 1) {
      // Filas de 15 min; la etiqueta de la hora va en las filas pares (cada 30 min).
      cells.push(h('span', { class: 'num' }, row % 2 === 0 ? (grid.times[row / 2] ?? '') : ''));
    }
    return h('div', { class: 'agenda-times', attrs: { 'aria-hidden': 'true' } }, ...cells);
  }

  function renderAgendaCol(grid: DayGrid): HTMLElement {
    const base = hourMinutes(grid.times[0] ?? '') ?? 0;
    const col = h('div', { class: 'agenda-col' });
    col.append(...Array.from({ length: grid.rows }, () => h('div', { class: 'line' })));
    for (const item of grid.items) {
      if (item.kind === 'appointment') {
        col.append(renderAppointment(item, base));
      } else if (item.kind === 'block') {
        col.append(
          h(
            'div',
            {
              class: 'block',
              attrs: {
                style: `--start:${gridRowStart(item.startMin, base)};--span:${gridRowSpan(item.startMin, item.endMin)}`,
              },
            },
            formIcon('lock'),
            h('span', {}, 'Bloqueado'),
          ),
        );
      } else {
        col.append(
          h(
            'button',
            {
              class: 'free-slot',
              attrs: { type: 'button', style: `--start:${gridRowStart(item.startMin, base)}` },
              on: { click: () => openForm() },
            },
            h('span', {}, `${formatMinutes(item.startMin)} libre · agregar turno`),
          ),
        );
      }
    }
    const nowLine = renderNowLine(grid, base);
    if (nowLine !== null) {
      col.append(nowLine);
    }
    return col;
  }

  function renderNowLine(grid: DayGrid, base: number): HTMLElement | null {
    if (grid.rows === 0) {
      return null;
    }
    const now = clock();
    const nowMinutes = hourMinutes(formatTime(now)) ?? 0;
    const rangeEnd = base + grid.rows * GRID_ROW_MINUTES;
    if (nowMinutes < base || nowMinutes >= rangeEnd) {
      return null;
    }
    return h(
      'div',
      { class: 'now-line', attrs: { role: 'presentation', style: `--at:${gridRowStart(nowMinutes, base)}` } },
      h('span', { class: 'sr-only' }, `Hora actual ${formatTime(now)}`),
    );
  }

  function renderAppointment(item: DayGridAppointmentItem, base: number): HTMLElement {
    const appointment = item.appointment;
    const start = parseInstant(appointment.startsAt);
    const end = parseInstant(appointment.endsAt);
    const displayName = patientNames.get(appointment.patientId) ?? appointment.patientId;
    const serviceName = services.find((service) => service.id === appointment.serviceId)?.name ?? appointment.serviceId;
    const badge =
      item.overbooking && !item.outsideHours
        ? h('span', { class: 'badge badge-sobreturno' }, 'Sobreturno')
        : h('span', { class: `badge ${STATUS_BADGE[appointment.status]}` }, STATUS_TEXT[appointment.status]);
    return h(
      'button',
      {
        class: `appt ${item.outsideHours ? 's-cancelado' : STATUS_CLASS[appointment.status]}`,
        attrs: {
          type: 'button',
          title: `${displayName} · ${serviceName}`,
          style: `--start:${gridRowStart(item.startMin, base)};--span:${gridRowSpan(item.startMin, item.endMin)}`,
        },
        on: { click: () => openDetail(appointment) },
      },
      h('span', { class: 'row1' }, h('span', { class: 'who' }, displayName), badge),
      h('span', { class: 'what num' }, `${formatMinutes(item.startMin)} – ${formatMinutes(item.endMin)} · ${serviceName}`),
    );
  }

  function emptyState(title: string, text: string): HTMLElement {
    return h(
      'div',
      { class: 'empty' },
      h('span', { class: 'ico', attrs: { 'aria-hidden': 'true' } }, formIcon('dot')),
      h('h3', {}, title),
      h('p', {}, text),
      h('button', { class: 'btn btn-primary', attrs: { type: 'button' }, on: { click: () => openForm() } }, 'Nuevo turno'),
    );
  }

  // ----- Estado vacío -----

  // ----- Diálogo de detalle del turno (C-20 D8) -----

  const detailDialog = h('dialog', { class: 'dialog', attrs: { id: 'agenda-detail', 'aria-labelledby': 'agenda-detail-head' } });
  const detailErrorEl = h('div', { class: 'alerts' });
  let currentAppointment: AppointmentDto | undefined;

  function showDetailError(message: string): void {
    detailErrorEl.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', text: message }));
  }

  function renderDetail(): void {
    if (currentAppointment === undefined) {
      return;
    }
    const appointment = currentAppointment;
    const start = parseInstant(appointment.startsAt);
    const end = parseInstant(appointment.endsAt);
    const displayName = patientNames.get(appointment.patientId) ?? appointment.patientId;
    const serviceName = services.find((service) => service.id === appointment.serviceId)?.name ?? appointment.serviceId;
    const canMark = start.getTime() <= clock().getTime();
    detailErrorEl.replaceChildren();
    detailDialog.replaceChildren(
      h(
        'div',
        { class: 'dialog-head' },
        h('h3', { attrs: { id: 'agenda-detail-head' } }, `Turno — ${displayName}`),
        h('span', { class: `badge ${STATUS_BADGE[appointment.status]}` }, STATUS_TEXT[appointment.status]),
        h('button', { class: 'btn btn-ghost btn-icon', attrs: { type: 'button', 'aria-label': 'Cerrar' }, on: { click: () => closeDialog(detailDialog) } }, formIcon('cross')),
      ),
      h(
        'div',
        { class: 'dialog-body' },
        h(
          'dl',
          { class: 'meta-list' },
          h('dt', {}, 'Fecha'),
          h('dd', { class: 'num' }, `${weekdayName(localDateOf(start))} ${formatDate(start)} · ${formatTime(start)} – ${formatTime(end)}`),
          h('dt', {}, 'Prestación'),
          h('dd', {}, serviceName),
          h('dt', {}, 'Paciente'),
          h('dd', {}, displayName),
          h('dt', {}, 'Origen'),
          h('dd', {}, appointment.origin === 'INTERNAL' ? 'Carga manual' : 'Reserva pública'),
        ),
        h(
          'div',
          { class: 'action-row' },
          h('button', { class: 'btn btn-secondary', attrs: { type: 'button' }, on: { click: () => openReschedule() } }, 'Reprogramar'),
          h('button', { class: 'btn btn-danger-outline', attrs: { type: 'button' }, on: { click: () => void cancelAppointmentFlow() } }, 'Cancelar turno'),
          h('button', { class: 'btn btn-secondary', attrs: { type: 'button', ...(canMark ? {} : { disabled: '' }) }, on: { click: () => void markFlow('complete') } }, 'Marcar atendido'),
          h('button', { class: 'btn btn-secondary', attrs: { type: 'button', ...(canMark ? {} : { disabled: '' }) }, on: { click: () => void markFlow('no-show') } }, 'Marcar ausente'),
          h('button', { class: 'btn btn-ghost btn-icon', attrs: { type: 'button', 'aria-label': 'Volver a la agenda' }, on: { click: () => closeDialog(detailDialog) } }, chevLeft()),
        ),
      ),
      detailErrorEl,
    );
  }

  function openDetail(appointment: AppointmentDto): void {
    currentAppointment = appointment;
    renderDetail();
    openDialog(detailDialog);
  }

  async function cancelAppointmentFlow(): Promise<void> {
    const appointment = currentAppointment;
    if (appointment === undefined) {
      return;
    }
    const accepted = await confirmAction({
      title: '¿Cancelar este turno?',
      text: 'Vas a cancelar el turno: el horario quedará libre para otra persona.',
      confirmLabel: 'Sí, cancelar turno',
      cancelLabel: 'Mantener turno',
    });
    if (!accepted) {
      return;
    }
    const result = await gateway.cancelAppointment(appointment.id);
    if (result.ok) {
      closeDialog(detailDialog);
      await loadAppointments();
      return;
    }
    showDetailError(agendaErrorMessage(result.error));
  }

  async function markFlow(kind: 'complete' | 'no-show'): Promise<void> {
    const appointment = currentAppointment;
    if (appointment === undefined) {
      return;
    }
    const result = kind === 'complete' ? await gateway.completeAppointment(appointment.id) : await gateway.noShowAppointment(appointment.id);
    if (result.ok) {
      closeDialog(detailDialog);
      await loadAppointments();
      return;
    }
    showDetailError(agendaErrorMessage(result.error));
  }

  // ----- Diálogo de reprogramación -----

  const rescheduleDialog = h('dialog', { class: 'dialog', attrs: { id: 'agenda-reschedule', 'aria-labelledby': 'agenda-reschedule-head' } });
  const rescheduleErrorEl = h('div', { class: 'alerts' });
  let rescheduleSelected: AvailableSlotDto | undefined;

  function openReschedule(): void {
    const appointment = currentAppointment;
    if (appointment === undefined) {
      return;
    }
    rescheduleSelected = undefined;
    rescheduleDialog.replaceChildren(
      h(
        'div',
        { class: 'dialog-head' },
        h('h3', { attrs: { id: 'agenda-reschedule-head' } }, 'Reprogramar turno'),
        h('button', { class: 'btn btn-ghost btn-icon', attrs: { type: 'button', 'aria-label': 'Cerrar' }, on: { click: () => closeDialog(rescheduleDialog) } }, formIcon('cross')),
      ),
      h(
        'div',
        { class: 'dialog-body' },
        h('p', { class: 'muted' }, 'Elegí el nuevo horario. El turno deja el rango actual.'),
        h('div', { class: 'slot-group', attrs: { id: 'agenda-reschedule-slots' } }),
        h('p', { class: 'note', attrs: { id: 'agenda-reschedule-note' } }),
        rescheduleErrorEl,
      ),
      h(
        'div',
        { class: 'dialog-foot' },
        h('button', { class: 'btn btn-secondary', attrs: { type: 'button' }, on: { click: () => closeDialog(rescheduleDialog) } }, 'Cancelar'),
        h('button', { class: 'btn btn-primary', attrs: { type: 'button' }, on: { click: () => void submitReschedule() } }, 'Reprogramar'),
      ),
    );
    openDialog(rescheduleDialog);
    void loadRescheduleSlots(appointment);
  }

  async function loadRescheduleSlots(appointment: AppointmentDto): Promise<void> {
    const result = await gateway.listAvailability(appointment.serviceId, localDateOf(parseInstant(appointment.startsAt)));
    const slots = result.ok ? result.data : [];
    const slotsEl = rescheduleDialog.querySelector('#agenda-reschedule-slots');
    if (slotsEl === null) {
      return;
    }
    slotsEl.replaceChildren(...slots.map((slot) => renderRescheduleSlot(slot)));
    const noteEl = rescheduleDialog.querySelector<HTMLParagraphElement>('#agenda-reschedule-note');
    if (noteEl !== null) {
      noteEl.replaceChildren(slots.length === 0 ? 'Sin slots libres para reprogramar en este día.' : '');
    }
  }

  function renderRescheduleSlot(slot: AvailableSlotDto): HTMLElement {
    const button = h(
      'button',
      { class: 'slot', attrs: { type: 'button', 'data-slot-starts': slot.startsAt, 'aria-pressed': 'false' }, on: { click: () => selectRescheduleSlot(button, slot) } },
      formatTime(slot.startsAt),
    );
    return button;
  }

  function selectRescheduleSlot(selected: HTMLButtonElement, slot: AvailableSlotDto): void {
    rescheduleSelected = slot;
    for (const other of rescheduleDialog.querySelectorAll<HTMLButtonElement>('.slot')) {
      other.setAttribute('aria-pressed', other === selected ? 'true' : 'false');
    }
  }

  async function submitReschedule(): Promise<void> {
    const appointment = currentAppointment;
    const slot = rescheduleSelected;
    if (appointment === undefined || slot === undefined) {
      rescheduleErrorEl.replaceChildren(alertBox({ variant: 'warning', role: 'alert', icon: 'info', text: 'Seleccioná un slot libre.' }));
      return;
    }
    const result = await gateway.rescheduleAppointment(appointment.id, slot.startsAt, appointment.overbooking);
    if (result.ok) {
      closeDialog(rescheduleDialog);
      closeDialog(detailDialog);
      await loadAppointments();
      return;
    }
    rescheduleErrorEl.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', text: agendaErrorMessage(result.error) }));
  }

  // ----- Formulario de alta (C-20 D7, US-021) -----

  function openForm(): void {
    const dialog = createAppointmentForm({
      gateway,
      day: state.day,
      setTurno: () => {
        closeDialog(dialog);
        void loadAppointments();
      },
    });
    document.body.append(dialog);
    openDialog(dialog);
    dialog.querySelector<HTMLElement>('#pac-q')?.focus();
  }

  // ----- Utilidades de diálogo y fechas -----

  function openDialog(dialog: HTMLDialogElement): void {
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
  }

  function closeDialog(dialog: HTMLDialogElement): void {
    if (typeof dialog.close === 'function') {
      dialog.close();
    } else {
      dialog.removeAttribute('open');
    }
  }

  function shiftDayBy(delta: number): void {
    const step = state.mode === 'day' ? delta : delta * 7;
    state.day = shiftDay(state.day, step);
    updateDayNavLabels();
    void loadAppointments();
  }

  // ----- Iconos -----

  function chevLeft(): SVGElement {
    return svg(
      'svg',
      { attrs: { width: '16', height: '16', viewBox: '0 0 16 16', focusable: 'false' }, aria: { hidden: 'true' } },
      svg('path', { attrs: { d: 'M10 3L5 8l5 5', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.8', 'stroke-linejoin': 'round' } }),
    );
  }

  function chevRight(): SVGElement {
    return svg(
      'svg',
      { attrs: { width: '16', height: '16', viewBox: '0 0 16 16', focusable: 'false' }, aria: { hidden: 'true' } },
      svg('path', { attrs: { d: 'M6 3l5 5-5 5', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.8', 'stroke-linejoin': 'round' } }),
    );
  }

  function plusIcon(): SVGElement {
    return svg(
      'svg',
      { attrs: { width: '16', height: '16', viewBox: '0 0 16 16', focusable: 'false' }, aria: { hidden: 'true' } },
      svg('path', { attrs: { d: 'M8 3v10M3 8h10', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.8' } }),
    );
  }

  // ----- Fechas -----

  function shiftDay(day: string, delta: number): string {
    return localDateOf(new Date(dayBoundsUtc(day).start.getTime() + delta * DAY_MS));
  }

  function weekDates(day: string): readonly string[] {
    const startMs = dayBoundsUtc(day).start.getTime();
    const jsDayOfWeek = new Date(startMs).getUTCDay();
    const mondayOffset = jsDayOfWeek === 0 ? 6 : jsDayOfWeek - 1;
    const mondayMs = startMs - mondayOffset * DAY_MS;
    return [0, 1, 2, 3, 4, 5, 6].map((index) => localDateOf(new Date(mondayMs + index * DAY_MS)));
  }

  // ----- Montaje -----

  const legend = h(
    'div',
    { class: 'legend', attrs: { 'aria-label': 'Referencias' } },
    h('span', {}, 'Barra izquierda = estado'),
    h('span', {}, 'Rayado = bloqueado'),
    h('span', {}, 'Línea roja = hora actual'),
    h('span', {}, 'Recuadro punteado = libre'),
    h('span', {}, h('b', {}, 'Sobreturno'), ' = turno fuera del tramo de atención'),
  );

  const root = h('div', { class: 'agenda-page' }, h('h1', {}, 'Agenda'), toolbar, content, legend, detailDialog, rescheduleDialog);
  return root;
}
