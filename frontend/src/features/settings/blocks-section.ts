import { confirmAction } from '../../core/dom/confirm';
import { alertBox, clearFieldErrors, field, formIcon, setFieldError } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import {
  dayBoundsUtc,
  formatDate,
  formatTime,
  localDateOf,
  PRACTICE_ZONE,
} from '../../shared/utils/dates';
import { failureCopy, fieldMessage, MESSAGES } from './settings-messages';
import type { BlockInput, SettingsGateway } from './settings-gateway';
import type { ScheduleBlock } from './settings-types';
import { validateBlock } from './validation';

/** Bloqueos de agenda: edición en hora del consultorio, envío de instantes ISO con zona (C-19 D4/D5/D8). */

export interface BlocksSectionDeps {
  readonly gateway: Pick<SettingsGateway, 'getBlocks' | 'createBlock' | 'updateBlock' | 'deleteBlock'>;
}

const LOCAL_INPUT_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/;

/** Función pura: alinea un candidato UTC con la hora local deseada en la zona del consultorio. */
function alignToPracticeZone(candidate: Date, desiredMinutes: number): Date {
  let aligned = candidate;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const shown = formatTime(aligned);
    const currentMinutes = Number(shown.slice(0, 2)) * 60 + Number(shown.slice(3, 5));
    const delta = desiredMinutes - currentMinutes;
    if (delta === 0) {
      break;
    }
    aligned = new Date(aligned.getTime() + delta * 60_000);
  }
  return aligned;
}

/** Función pura: `datetime-local` en hora del consultorio → ISO-8601 con zona. `undefined` si el formato no es válido. */
export function localInputToInstant(value: string): string | undefined {
  const match = LOCAL_INPUT_PATTERN.exec(value);
  if (match === null) {
    return undefined;
  }
  const [, year, month, day, hour, minute] = match;
  const { start } = dayBoundsUtc(`${year}-${month}-${day}`);
  const desiredHour = Number(hour);
  const desiredMinute = Number(minute);
  if (desiredHour > 23 || desiredMinute > 59) {
    return undefined;
  }
  const desiredMinutes = desiredHour * 60 + desiredMinute;
  const candidate = new Date(start.getTime() + desiredMinutes * 60_000);
  if (Number.isNaN(candidate.getTime())) {
    return undefined;
  }
  return alignToPracticeZone(candidate, desiredMinutes).toISOString();
}

/** Función pura: instante ISO → valor para `<input type="datetime-local">` en hora del consultorio. */
export function instantToLocalInput(iso: string): string {
  return `${localDateOf(iso)}T${formatTime(iso)}`;
}

function shortWeekday(iso: string): string {
  const formatted = new Intl.DateTimeFormat('es-AR', { weekday: 'short', timeZone: PRACTICE_ZONE }).format(new Date(iso));
  return formatted.replace(/\.$/, '');
}

function rangeLabel(block: ScheduleBlock): string {
  return `${shortWeekday(block.startsAt)} ${formatDate(block.startsAt)} · ${formatTime(block.startsAt)} – ${formatTime(block.endsAt)}`;
}

interface BlockFormRefs {
  readonly form: HTMLElement;
  readonly fields: Readonly<Record<string, HTMLInputElement | HTMLTextAreaElement>>;
}

export function createBlocksSection(deps: BlocksSectionDeps): HTMLElement {
  let blocks: readonly ScheduleBlock[] = [];
  let editing: ScheduleBlock | undefined;
  let saving = false;
  let formRefs: BlockFormRefs | undefined;

  const section = h(
    'section',
    { class: 'settings-card', attrs: { id: 'blocks', 'aria-labelledby': 'h-blocks' }, aria: { busy: 'true' } },
    h(
      'div',
      { class: 'section-head' },
      h(
        'div',
        {},
        h('h2', { attrs: { id: 'h-blocks' } }, 'Bloqueos de agenda'),
        h('p', { class: 'section-desc' }, 'Rangos en hora del consultorio (Buenos Aires). No ingreses datos de pacientes en el motivo.'),
      ),
      h('button', { class: 'btn btn-sm btn-secondary', attrs: { type: 'button' } }, 'Nuevo bloqueo'),
    ),
  );
  const noticeSlot = h('div', { class: 'section-notice' });
  const listSlot = h('div', { class: 'variant' });
  const formSlot = h('div', { class: 'variant' });
  section.append(noticeSlot, listSlot, formSlot);

  function rowActions(block: ScheduleBlock): HTMLElement {
    const edit = h('button', { class: 'btn btn-sm btn-secondary', attrs: { type: 'button' } }, 'Editar');
    edit.addEventListener('click', () => openForm(block));
    const remove = h('button', { class: 'btn btn-sm btn-danger-outline', attrs: { type: 'button' } }, 'Eliminar');
    remove.addEventListener('click', () => {
      void erase(block);
    });
    return h('div', { class: 'row-actions' }, edit, remove);
  }

  function blockRow(block: ScheduleBlock, asTable: boolean): HTMLElement {
    const label = rangeLabel(block);
    const affected = block.affectedAppointments.length;
    if (asTable) {
      return h(
        'tr',
        {},
        h('td', {}, h('strong', {}, label)),
        h('td', {}, block.reason),
        h('td', { class: 'num' }, String(affected)),
        h('td', {}, h('div', { class: 'cell-actions' }, rowActions(block))),
      );
    }
    return h(
      'li',
      {},
      h('div', { class: 'row-title' }, formIcon('lock'), label),
      h('div', { class: 'row-meta' }, h('span', {}, `Motivo: ${block.reason}`), h('span', {}, `${affected} turnos afectados`)),
      rowActions(block),
    );
  }

  function renderList(): void {
    listSlot.replaceChildren();
    if (blocks.length === 0) {
      const add = h('button', { class: 'btn btn-primary', attrs: { type: 'button' } }, 'Nuevo bloqueo');
      add.addEventListener('click', () => openForm());
      listSlot.append(
        h(
          'div',
          { class: 'empty' },
          h('h3', {}, 'Todavía no hay bloqueos'),
          h('p', {}, 'Bloqueá un rango cuando no quieras ofrecer turnos (vacaciones, mantenimiento, capacitación).'),
          add,
        ),
      );
      return;
    }
    const mobile = h('div', { class: 'only-mobile' }, h('ul', { class: 'data-list' }, ...blocks.map((block) => blockRow(block, false))));
    const desktop = h(
      'div',
      { class: 'only-desktop' },
      h(
        'div',
        { class: 'table-wrap' },
        h(
          'table',
          { class: 'data' },
          h(
            'thead',
            {},
            h('tr', {}, h('th', {}, 'Rango (hora del consultorio)'), h('th', {}, 'Motivo'), h('th', {}, 'Turnos afectados'), h('th', {}, 'Acciones')),
          ),
          h('tbody', {}, ...blocks.map((block) => blockRow(block, true))),
        ),
      ),
    );
    listSlot.append(mobile, desktop);
  }

  function buildForm(existing: ScheduleBlock | undefined): BlockFormRefs {
    const start = field({
      id: 'block-start',
      label: 'Inicio *',
      type: 'datetime-local',
      autocomplete: 'off',
      value: existing === undefined ? '' : instantToLocalInput(existing.startsAt),
    });
    const end = field({
      id: 'block-end',
      label: 'Fin *',
      type: 'datetime-local',
      autocomplete: 'off',
      value: existing === undefined ? '' : instantToLocalInput(existing.endsAt),
    });
    const reason = h('textarea', { class: 'textarea', attrs: { id: 'block-reason', name: 'block-reason', placeholder: 'Ej.: Mantenimiento del consultorio' } });
    reason.value = existing?.reason ?? '';
    const reasonField = h(
      'div',
      { class: 'field span-2' },
      h('label', { attrs: { for: 'block-reason' } }, 'Motivo *'),
      reason,
      h('span', { class: 'help' }, 'No incluyas nombres ni datos de pacientes. De 1 a 500 caracteres.'),
    );
    const saveButton = h('button', { class: 'btn btn-primary', attrs: { type: 'button' } }, 'Guardar bloqueo');
    const cancel = h('button', { class: 'btn btn-secondary', attrs: { type: 'button' } }, 'Cancelar');
    const form = h(
      'form',
      { class: 'form-grid', attrs: { novalidate: '' } },
      start.element,
      end.element,
      reasonField,
      h('div', { class: 'form-actions' }, saveButton, cancel),
    );
    const refs: BlockFormRefs = { form, fields: { 'block-start': start.input, 'block-end': end.input, 'block-reason': reason } };
    cancel.addEventListener('click', () => {
      editing = undefined;
      renderForm();
      noticeSlot.replaceChildren();
    });
    saveButton.addEventListener('click', () => {
      void save(refs);
    });
    return refs;
  }

  function renderForm(existing?: ScheduleBlock): void {
    formRefs = buildForm(existing);
    formSlot.replaceChildren(formRefs.form);
  }

  function openForm(existing?: ScheduleBlock): void {
    editing = existing;
    noticeSlot.replaceChildren();
    renderForm(existing);
    formSlot.querySelector<HTMLInputElement>('#block-start')?.focus();
  }

  function readInput(refs: BlockFormRefs): BlockInput {
    const startValue = refs.fields['block-start']?.value ?? '';
    const endValue = refs.fields['block-end']?.value ?? '';
    return {
      // Un valor inválido se reenvía tal cual para que `validateBlock` marque el campo.
      startsAt: localInputToInstant(startValue) ?? startValue,
      endsAt: localInputToInstant(endValue) ?? endValue,
      reason: refs.fields['block-reason']?.value ?? '',
    };
  }

  function showIssues(refs: BlockFormRefs, issues: readonly { readonly field: string; readonly message: string }[], fallback: string): void {
    for (const issue of issues) {
      const id = issue.field === 'startsAt' ? 'block-start' : issue.field === 'endsAt' ? 'block-end' : 'block-reason';
      const target = refs.fields[id];
      if (target !== undefined) {
        setFieldError(target, issue.message);
      }
    }
    const first = issues[0]?.field;
    const firstId = first === 'startsAt' ? 'block-start' : first === 'endsAt' ? 'block-end' : 'block-reason';
    refs.fields[firstId]?.focus();
    noticeSlot.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', text: fallback }));
  }

  function showAffected(block: ScheduleBlock): void {
    const count = block.affectedAppointments.length;
    if (count === 0) {
      noticeSlot.replaceChildren(
        alertBox({
          variant: 'warning',
          role: 'status',
          icon: 'info',
          title: MESSAGES.savedBlock.title,
          text: 'No hay turnos confirmados dentro del rango según la información disponible.',
        }),
      );
      return;
    }
    const details = h(
      'ul',
      { class: 'data-list' },
      ...block.affectedAppointments.map((appointment) =>
        h(
          'li',
          { class: 'row-meta' },
          h('span', { class: 'num' }, `${formatTime(appointment.startsAt)} – ${formatTime(appointment.endsAt)}`),
          h('span', { class: 'badge badge-visible' }, 'Confirmado'),
        ),
      ),
    );
    noticeSlot.replaceChildren(
      alertBox({
        variant: 'warning',
        role: 'status',
        icon: 'info',
        title: `Bloqueo guardado: ${count} turnos quedan dentro del rango.`,
        text: 'No se cancelaron ni se modificaron. Revisalos en la agenda antes del guardado.',
      }),
      h(
        'div',
        { class: 'link-box' },
        h('p', { class: 'subhead' }, `Turnos afectados · ${rangeLabel(block)}`),
        details,
        h('p', { class: 'help' }, 'Los identificadores de turno son internos y no se muestran.'),
      ),
    );
  }

  async function load(): Promise<void> {
    section.setAttribute('aria-busy', 'true');
    const result = await deps.gateway.getBlocks();
    if (result.ok) {
      blocks = result.data;
      renderList();
      renderForm();
    } else {
      const copy = failureCopy(result.failure);
      const retry = h('button', { class: 'btn btn-secondary', attrs: { type: 'button' } }, 'Reintentar');
      retry.addEventListener('click', () => {
        void load();
      });
      listSlot.replaceChildren(
        alertBox({ variant: 'danger', role: 'alert', icon: 'error', title: copy.title, text: copy.text }),
        retry,
      );
      formSlot.replaceChildren();
    }
    section.removeAttribute('aria-busy');
  }

  async function save(refs: BlockFormRefs): Promise<void> {
    if (saving) {
      return;
    }
    clearFieldErrors(refs.form);
    noticeSlot.replaceChildren();
    const input = readInput(refs);
    const validation = validateBlock(input);
    if (!validation.ok) {
      showIssues(
        refs,
        Object.entries(validation.fields).map(([field, message]) => ({ field, message })),
        validation.message,
      );
      return;
    }

    saving = true;
    const result = editing === undefined ? await deps.gateway.createBlock(input) : await deps.gateway.updateBlock(editing.id, input);
    saving = false;
    if (result.ok) {
      blocks = editing === undefined ? [...blocks, result.data] : blocks.map((item) => (item.id === result.data.id ? result.data : item));
      editing = undefined;
      renderList();
      renderForm();
      showAffected(result.data);
      return;
    }

    const copy = failureCopy(result.failure);
    if (result.failure.kind === 'fields') {
      showIssues(
        refs,
        result.failure.errors.map((error) => ({ field: error.field, message: fieldMessage(error.code, error.field) })),
        copy.text,
      );
      return;
    }
    noticeSlot.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', title: copy.title, text: copy.text }));
  }

  async function erase(block: ScheduleBlock): Promise<void> {
    const accepted = await confirmAction({
      title: 'Eliminar el bloqueo',
      text: `Se eliminará el rango ${rangeLabel(block)}. Los turnos no se modifican.`,
      confirmLabel: 'Eliminar',
    });
    if (!accepted) {
      return;
    }
    noticeSlot.replaceChildren();
    const result = await deps.gateway.deleteBlock(block.id);
    if (result.ok) {
      blocks = blocks.filter((item) => item.id !== block.id);
      renderList();
      noticeSlot.replaceChildren(alertBox({ variant: 'success', role: 'status', icon: 'check', title: 'Bloqueo eliminado.', text: MESSAGES.savedBlock.text }));
    } else {
      const copy = failureCopy(result.failure);
      noticeSlot.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', title: copy.title, text: copy.text }));
      if (result.failure.kind === 'not-found') {
        await load();
      }
    }
  }

  section.querySelector<HTMLButtonElement>('.section-head button')?.addEventListener('click', () => openForm());

  void load();
  return section;
}
