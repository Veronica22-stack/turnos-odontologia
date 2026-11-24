import { alertBox, clearFieldErrors, field, setFieldError } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import { failureCopy, fieldMessage, MESSAGES, type SettingsFailure } from './settings-messages';
import type { PracticeSettingsInput, SettingsGateway, SettingsResult } from './settings-gateway';
import type { PracticeSettings } from './settings-types';
import { validatePracticeSettings } from './validation';

/** Sección «Consultorio y parámetros de reserva» (C-19 D7). */

export interface PracticeSectionDeps {
  readonly gateway: Pick<SettingsGateway, 'getPractice' | 'savePractice'>;
  /** Origen del despliegue para componer el link público; inyectable en tests. */
  readonly origin?: string;
  /** Datos ya obtenidos por la vista (D3): evita duplicar `GET /practice/settings`. */
  readonly initial?: PracticeSettings;
  /** Promesa compartida con la vista para usar un único `GET /practice/settings` (D3). */
  readonly initialPromise?: Promise<SettingsResult<PracticeSettings>>;
}

const FIELD_IDS: Readonly<Record<string, string>> = {
  name: 'practice-name',
  contactEmail: 'practice-email',
  contactPhone: 'practice-phone',
  address: 'practice-address',
  slotGranularityMin: 'practice-granularity',
  minNoticeMin: 'practice-notice',
  maxAdvanceDays: 'practice-horizon',
  reminderHoursBefore: 'practice-reminder',
};

const GRANULARITIES: readonly number[] = [10, 15, 20, 30, 60];

interface Control {
  readonly element: HTMLElement;
  readonly input: HTMLInputElement | HTMLSelectElement;
}

interface FieldIssue {
  readonly field: string;
  readonly message: string;
}

function numberField(id: string, label: string, help: string, min: string, max: string): Control {
  const input = h('input', {
    class: 'input num',
    attrs: { id, name: id, type: 'number', min, max, required: '' },
  });
  const element = h(
    'div',
    { class: 'field' },
    h('label', { attrs: { for: id } }, label),
    input,
    h('p', { class: 'help', attrs: { id: `${id}-help` } }, help),
  );
  input.setAttribute('aria-describedby', `${id}-help`);
  return { element, input };
}

function granularityField(): Control {
  const id = 'practice-granularity';
  const input = h('select', { class: 'select', attrs: { id, name: id, required: '' } });
  for (const value of GRANULARITIES) {
    input.append(h('option', { attrs: { value: String(value) } }, String(value)));
  }
  const element = h(
    'div',
    { class: 'field span-2' },
    h('label', { attrs: { for: id } }, 'Granularidad de turnos'),
    input,
    h('p', { class: 'help', attrs: { id: `${id}-help` } }, 'Valores permitidos: 10, 15, 20, 30 o 60 minutos.'),
  );
  input.setAttribute('aria-describedby', `${id}-help`);
  return { element, input };
}

function optional(value: string): string | null {
  return value.trim() === '' ? null : value;
}

function toNumber(value: string): number {
  return value.trim() === '' ? Number.NaN : Number(value);
}

function copyAlert(title: string, text: string, variant: 'success' | 'info' = 'success'): HTMLElement {
  return alertBox({ variant, role: 'status', icon: variant === 'success' ? 'check' : 'info', title, text });
}

/** Render del aviso del link: éxito por portapapeles o campo de solo lectura como fallback. */
function renderLinkNotice(slot: HTMLElement, link: string, copied: boolean): void {
  if (copied) {
    slot.replaceChildren(copyAlert(MESSAGES.copied.title, MESSAGES.copied.text));
    return;
  }
  const input = h('input', {
    class: 'input copy-readonly',
    attrs: { id: 'public-link', type: 'text', readonly: '' },
  });
  input.value = link;
  slot.replaceChildren(
    copyAlert(MESSAGES.clipboardFallback.title, MESSAGES.clipboardFallback.text, 'info'),
    h('label', { class: 'sr-only', attrs: { for: 'public-link' } }, 'Enlace público de reserva'),
    input,
  );
}

export function createPracticeSection(deps: PracticeSectionDeps): HTMLElement {
  const origin = deps.origin ?? window.location.origin;
  let current: PracticeSettings | undefined;
  let saving = false;

  const section = h(
    'section',
    { class: 'settings-card', attrs: { id: 'practice', 'aria-labelledby': 'h-practice' }, aria: { busy: 'true' } },
    h(
      'div',
      { class: 'section-head' },
      h('h2', { attrs: { id: 'h-practice' } }, 'Consultorio y parámetros de reserva'),
      h('p', { class: 'section-desc' }, 'Datos de contacto y reglas de la reserva online. La zona horaria se muestra como dato del consultorio.'),
    ),
  );
  const body = h('div', { class: 'variant' });
  section.append(body);

  const name = field({
    id: 'practice-name',
    label: 'Nombre del consultorio *',
    autocomplete: 'organization',
    help: 'Nombre que ven los pacientes al reservar.',
  });
  const timezone = field({
    id: 'practice-tz',
    label: 'Zona horaria',
    autocomplete: 'off',
    help: 'Se fija en la instalación y no se puede editar.',
  });
  timezone.input.disabled = true;
  timezone.input.readOnly = true;
  const email = field({
    id: 'practice-email',
    label: 'Email de contacto',
    type: 'email',
    autocomplete: 'email',
    help: 'Se usa en las confirmaciones de turno.',
  });
  const phone = field({ id: 'practice-phone', label: 'Teléfono', autocomplete: 'tel', inputmode: 'tel' });
  const address = field({ id: 'practice-address', label: 'Dirección', autocomplete: 'street-address' });
  const granularity = granularityField();
  const notice = numberField('practice-notice', 'Anticipación mínima (minutos)', 'Mínimo 0.', '0', '10080');
  const horizon = numberField('practice-horizon', 'Horizonte máximo (días)', 'Entre 1 y 365 días.', '1', '365');
  const reminder = numberField('practice-reminder', 'Recordatorio (horas antes)', 'Entre 1 y 168 horas.', '1', '168');

  const controls: readonly Control[] = [name, email, phone, address, granularity, notice, horizon, reminder];
  const form = h(
    'form',
    { class: 'form-grid', attrs: { novalidate: '' } },
    h('div', { class: 'field span-2' }, name.element),
    timezone.element,
    email.element,
    phone.element,
    address.element,
    granularity.element,
    notice.element,
    horizon.element,
    reminder.element,
    h(
      'div',
      { class: 'field span-2' },
      h('div', { class: 'form-actions' }, h('button', { class: 'btn btn-primary', attrs: { type: 'button' } }, 'Guardar datos del consultorio')),
    ),
  );
  const noticeSlot = h('div', { class: 'section-notice' });
  const linkNotice = h('div', { class: 'section-notice' });
  const linkBox = h(
    'div',
    { class: 'link-box', aria: { labelledby: 'h-link' } },
    h('div', { class: 'subhead' }, h('span', { attrs: { id: 'h-link' } }, 'Link público de reserva')),
    h('p', { class: 'help' }, 'Se compone con el origen de esta aplicación: ', h('code', {}, '/reservar'), '.'),
    h(
      'div',
      { class: 'form-actions' },
      h('button', { class: 'btn btn-secondary', attrs: { type: 'button' } }, 'Copiar link público de reserva'),
    ),
    linkNotice,
  );

  function apply(data: PracticeSettings): void {
    current = data;
    name.input.value = data.name;
    timezone.input.value = data.timezone;
    email.input.value = data.contactEmail ?? '';
    phone.input.value = data.contactPhone ?? '';
    address.input.value = data.address ?? '';
    granularity.input.value = String(data.slotGranularityMin);
    notice.input.value = String(data.minNoticeMin);
    horizon.input.value = String(data.maxAdvanceDays);
    reminder.input.value = String(data.reminderHoursBefore);
    body.replaceChildren(noticeSlot, form, linkBox);
  }

  function showLoadFailure(failure: SettingsFailure): void {
    const copy = failureCopy(failure);
    const retry = h('button', { class: 'btn btn-secondary', attrs: { type: 'button' } }, 'Reintentar');
    retry.addEventListener('click', () => {
      void load();
    });
    body.replaceChildren(
      alertBox({ variant: 'danger', role: 'alert', icon: 'error', title: copy.title, text: copy.text }),
      retry,
    );
  }

  let pending = deps.initialPromise;

  async function load(): Promise<void> {
    section.setAttribute('aria-busy', 'true');
    const result = await (pending ?? deps.gateway.getPractice());
    pending = undefined;
    if (result.ok) {
      apply(result.data);
    } else {
      showLoadFailure(result.failure);
    }
    section.removeAttribute('aria-busy');
  }

  function inputOf(fieldName: string): (HTMLInputElement | HTMLSelectElement) | undefined {
    const id = FIELD_IDS[fieldName];
    return id === undefined ? undefined : controls.find((control) => control.input.id === id)?.input;
  }

  /** Enlace de errores por campo: misma salida para validación local y respuesta del servidor. */
  function showIssues(issues: readonly FieldIssue[]): void {
    for (const issue of issues) {
      const target = inputOf(issue.field);
      if (target !== undefined) {
        setFieldError(target, issue.message);
      }
    }
    const first = inputOf(issues[0]?.field ?? '');
    first?.focus();
  }

  async function save(): Promise<void> {
    if (saving || current === undefined) {
      return;
    }
    clearFieldErrors(form);
    noticeSlot.replaceChildren();

    const input: PracticeSettingsInput = {
      name: name.input.value,
      timezone: current.timezone,
      contactEmail: optional(email.input.value),
      contactPhone: optional(phone.input.value),
      address: optional(address.input.value),
      slotGranularityMin: toNumber(granularity.input.value),
      minNoticeMin: toNumber(notice.input.value),
      maxAdvanceDays: toNumber(horizon.input.value),
      reminderHoursBefore: toNumber(reminder.input.value),
    };
    const validation = validatePracticeSettings(input);
    if (!validation.ok) {
      showIssues(
        Object.entries(validation.fields).map(([field, message]) => ({ field, message })),
      );
      noticeSlot.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', text: validation.message }));
      return;
    }

    saving = true;
    const result = await deps.gateway.savePractice(input);
    saving = false;
    if (result.ok) {
      current = result.data;
      noticeSlot.replaceChildren(copyAlert(MESSAGES.savedPractice.title, MESSAGES.savedPractice.text));
    } else {
      const copy = failureCopy(result.failure);
      noticeSlot.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', title: copy.title, text: copy.text }));
      if (result.failure.kind === 'fields') {
        showIssues(
          result.failure.errors.map((error) => ({ field: error.field, message: fieldMessage(error.code, error.field) })),
        );
      }
    }
  }

  async function copyLink(): Promise<void> {
    const link = new URL('/reservar', origin).toString();
    const clipboard = navigator.clipboard;
    if (clipboard?.writeText === undefined) {
      renderLinkNotice(linkNotice, link, false);
      return;
    }
    try {
      await clipboard.writeText(link);
      renderLinkNotice(linkNotice, link, true);
    } catch {
      renderLinkNotice(linkNotice, link, false);
    }
  }

  const saveButton = form.querySelector<HTMLButtonElement>('button.btn-primary');
  saveButton?.addEventListener('click', () => {
    void save();
  });
  linkBox.querySelector<HTMLButtonElement>('button.btn-secondary')?.addEventListener('click', () => {
    void copyLink();
  });

  if (deps.initial !== undefined) {
    apply(deps.initial);
    section.removeAttribute('aria-busy');
  } else {
    void load();
  }
  return section;
}
