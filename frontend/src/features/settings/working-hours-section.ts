import { alertBox, clearFieldErrors, field, setFieldError } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import { failureCopy, MESSAGES, type SettingsFailure } from './settings-messages';
import type { SettingsGateway } from './settings-gateway';
import type { WorkingHour } from './settings-types';
import { validateWorkingHours } from './validation';

/** Grilla semanal de horarios (C-19 D4/D5). Cada fila se edita en `HH:mm` local del consultorio. */

export interface WorkingHoursSectionDeps {
  readonly gateway: Pick<SettingsGateway, 'getWorkingHours' | 'saveWorkingHours'>;
}

interface RowModel {
  readonly weekday: number;
  readonly startTime: string;
  readonly endTime: string;
}

interface Row {
  readonly weekday: number;
  readonly startInput: HTMLInputElement;
  readonly endInput: HTMLInputElement;
  readonly element: HTMLElement;
}

const WEEKDAYS: readonly string[] = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

/** Función pura: DTO del contrato (`HH:mm:ss`) → modelos de fila (`HH:mm`), ordenados por día. Sin DOM ni HTTP. */
function toRowModels(hours: readonly WorkingHour[]): readonly RowModel[] {
  return [...hours]
    .sort((a, b) => a.weekday - b.weekday)
    .map((hour) => ({
      weekday: hour.weekday,
      // El decoder ya entrega HH:mm; slice protege si el dato viene directo del contrato HH:mm:ss.
      startTime: hour.startTime.slice(0, 5),
      endTime: hour.endTime.slice(0, 5),
    }));
}

/** Función pura: agrupa modelos por día para el contador «N tramos». */
function groupByWeekday(models: readonly RowModel[]): ReadonlyMap<number, number> {
  const counts = new Map<number, number>();
  for (const model of models) {
    counts.set(model.weekday, (counts.get(model.weekday) ?? 0) + 1);
  }
  return counts;
}

export function createWorkingHoursSection(deps: WorkingHoursSectionDeps): HTMLElement {
  let rows: Row[] = [];
  let saving = false;
  let nextRowId = 0;

  const section = h(
    'section',
    { class: 'settings-card', attrs: { id: 'hours', 'aria-labelledby': 'h-hours' }, aria: { busy: 'true' } },
    h('div', { class: 'section-head' }, h('div', {}, h('h2', { attrs: { id: 'h-hours' } }, 'Horarios de atención'))),
    h('p', { class: 'section-desc' }, 'Varios tramos por día, en hora del consultorio (Buenos Aires).'),
    alertBox({
      variant: 'warning',
      role: 'status',
      icon: 'info',
      text: 'Los turnos confirmados no se cancelan automáticamente. Si cambiás los horarios, revisalos en la agenda.',
    }),
  );
  const noticeSlot = h('div', { class: 'section-notice' });
  const grid = h('div', { class: 'variant' });
  const saveButton = h('button', { class: 'btn btn-primary', attrs: { type: 'button' } }, 'Guardar horarios');
  const form = h('form', { class: 'form-grid', attrs: { novalidate: '' } }, grid, h('div', { class: 'form-actions' }, saveButton));
  section.append(noticeSlot, form);

  function makeRow(model: RowModel): Row {
    // Id único y estable (no depende de la posición en `rows`, que cambia al agregar/quitar).
    const id = `hours-${model.weekday}-${nextRowId++}`;
    const start = field({ id: `${id}-start`, label: 'Inicio', type: 'time', autocomplete: 'off', value: model.startTime });
    const end = field({ id: `${id}-end`, label: 'Fin', type: 'time', autocomplete: 'off', value: model.endTime });
    const remove = h('button', { class: 'btn btn-sm btn-ghost', attrs: { type: 'button' } }, 'Quitar');
    const element = h(
      'div',
      { class: 'range-row' },
      start.element,
      end.element,
      h('div', { class: 'range-actions' }, remove),
    );
    const rowRef: Row = { weekday: model.weekday, startInput: start.input, endInput: end.input, element };
    remove.addEventListener('click', () => {
      rows = rows.filter((row) => row !== rowRef);
      clearFieldErrors(form);
      renderGrid();
    });
    return rowRef;
  }

  function renderGrid(): void {
    grid.replaceChildren();
    if (rows.length === 0) {
      grid.append(
        h(
          'div',
          { class: 'empty' },
          h('h3', {}, 'Todavía no hay horarios de atención'),
          h('p', {}, 'Agregá tramos por día para habilitar los turnos en la reserva online.'),
        ),
      );
    }

    const counts = groupByWeekday(
      rows.map((row) => ({ weekday: row.weekday, startTime: row.startInput.value, endTime: row.endInput.value })),
    );
    for (const [index, label] of WEEKDAYS.entries()) {
      const weekday = index + 1;
      const addButton = h(
        'button',
        { class: 'btn btn-sm btn-secondary', attrs: { type: 'button', 'aria-label': `Agregar tramo el ${label.toLowerCase()}` } },
        'Agregar tramo',
      );
      addButton.addEventListener('click', () => addRow(weekday));
      const block = h(
        'div',
        { class: 'day-block', dataset: { weekday: String(weekday) } },
        h(
          'div',
          { class: 'day-title' },
          h('span', {}, label),
          h('span', { class: 'muted num' }, `${counts.get(weekday) ?? 0} tramos`),
        ),
      );
      for (const row of rows.filter((item) => item.weekday === weekday)) {
        block.append(row.element);
      }
      block.append(h('div', { class: 'range-actions' }, addButton));
      grid.append(block);
    }
  }

  function addRow(weekday: number): void {
    rows.push(makeRow({ weekday, startTime: '09:00', endTime: '13:00' }));
    // Orden estable por día: `collect()` y el mapeo de errores por índice usan este mismo orden.
    rows.sort((a, b) => a.weekday - b.weekday);
    clearFieldErrors(form);
    renderGrid();
  }

  function collect(): readonly WorkingHour[] {
    return rows.map((row) => ({
      weekday: row.weekday,
      startTime: row.startInput.value,
      endTime: row.endInput.value,
    }));
  }

  function apply(hours: readonly WorkingHour[]): void {
    rows = toRowModels(hours).map((model) => makeRow(model));
    clearFieldErrors(form);
    noticeSlot.replaceChildren();
    renderGrid();
  }

  function showFailure(failure: SettingsFailure): void {
    const copy = failureCopy(failure);
    const retry = h('button', { class: 'btn btn-secondary', attrs: { type: 'button' } }, 'Reintentar');
    retry.addEventListener('click', () => {
      void load();
    });
    grid.replaceChildren(
      alertBox({ variant: 'danger', role: 'alert', icon: 'error', title: copy.title, text: copy.text }),
      retry,
    );
  }

  async function load(): Promise<void> {
    section.setAttribute('aria-busy', 'true');
    const result = await deps.gateway.getWorkingHours();
    if (result.ok) {
      apply(result.data);
    } else {
      showFailure(result.failure);
    }
    section.removeAttribute('aria-busy');
  }

  async function save(): Promise<void> {
    if (saving) {
      return;
    }
    clearFieldErrors(form);
    noticeSlot.replaceChildren();
    const hours = collect();
    const validation = validateWorkingHours(hours);
    if (!validation.ok) {
      for (const [key, message] of Object.entries(validation.fields)) {
        const match = /^(start|end)-(\d+)$/.exec(key);
        const index = match === null ? Number.NaN : Number(match[2]);
        const row = rows[index];
        if (row !== undefined) {
          setFieldError(match?.[1] === 'end' ? row.endInput : row.startInput, message);
        }
      }
      noticeSlot.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', text: validation.message }));
      return;
    }

    saving = true;
    const result = await deps.gateway.saveWorkingHours(hours);
    saving = false;
    if (result.ok) {
      apply(result.data);
      noticeSlot.replaceChildren(copySuccess());
    } else {
      const copy = failureCopy(result.failure);
      noticeSlot.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', title: copy.title, text: copy.text }));
    }
  }

  function copySuccess(): HTMLElement {
    return alertBox({ variant: 'success', role: 'status', icon: 'check', title: MESSAGES.savedHours.title, text: MESSAGES.savedHours.text });
  }

  saveButton.addEventListener('click', () => {
    void save();
  });

  void load();
  return section;
}
