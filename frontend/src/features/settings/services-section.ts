import { confirmAction } from '../../core/dom/confirm';
import { alertBox, clearFieldErrors, field, formIcon, setFieldError } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import { failureCopy, fieldMessage, MESSAGES } from './settings-messages';
import type { ServiceInput, SettingsGateway } from './settings-gateway';
import type { ServiceItem } from './settings-types';
import { validateService } from './validation';

/** Alta, edición, baja lógica y reactivación de prestaciones (C-19 D6/D8). */

export interface ServicesSectionDeps {
  readonly gateway: Pick<SettingsGateway, 'getServices' | 'createService' | 'updateService' | 'deleteService'>;
  /** Granularidad vigente, obtenida de `GET /practice/settings` (D3). */
  readonly granularity?: number;
  /** Proveedor diferido usado por la vista mientras llega `GET /practice/settings`. */
  readonly getGranularity?: () => number;
}

const FALLBACK_GRANULARITY = 15;

function currentGranularity(deps: ServicesSectionDeps): number {
  return deps.getGranularity?.() ?? deps.granularity ?? FALLBACK_GRANULARITY;
}

interface ServiceFormRefs {
  readonly form: HTMLElement;
  readonly fields: Readonly<Record<string, HTMLInputElement | HTMLTextAreaElement>>;
}

/** Mapeo único DTO → cuerpo PUT, reutilizado por la edición y la reactivación. */
function toServiceInput(service: ServiceItem): ServiceInput {
  return {
    name: service.name,
    description: service.description,
    durationMin: service.durationMin,
    publicVisible: service.publicVisible,
  };
}

export function createServicesSection(deps: ServicesSectionDeps): HTMLElement {
  let services: readonly ServiceItem[] = [];
  let editing: ServiceItem | undefined;
  let saving = false;

  const section = h(
    'section',
    { class: 'settings-card', attrs: { id: 'services', 'aria-labelledby': 'h-services' }, aria: { busy: 'true' } },
    h(
      'div',
      { class: 'section-head' },
      h(
        'div',
        {},
        h('h2', { attrs: { id: 'h-services' } }, 'Prestaciones'),
        h('p', { class: 'section-desc' }, 'Se listan activas e inactivas. La baja es lógica: los turnos ya agendados no se modifican.'),
      ),
      h('button', { class: 'btn btn-sm btn-secondary', attrs: { type: 'button' } }, 'Nueva prestación'),
    ),
  );
  const noticeSlot = h('div', { class: 'section-notice' });
  const content = h('div', { class: 'variant' });
  section.append(noticeSlot, content);

  function badge(text: string, variant: 'active' | 'inactive' | 'visible'): HTMLElement {
    return h(
      'span',
      { class: `badge badge-${variant}` },
      formIcon(variant === 'inactive' ? 'lock' : 'check'),
      h('span', {}, text),
    );
  }

  function rowActions(service: ServiceItem): HTMLElement {
    const edit = h('button', { class: 'btn btn-sm btn-secondary', attrs: { type: 'button' } }, 'Editar');
    edit.addEventListener('click', () => openForm(service));
    const toggle = h(
      'button',
      {
        class: `btn btn-sm ${service.active ? 'btn-danger-outline' : 'btn-secondary'}`,
        attrs: { type: 'button' },
      },
      service.active ? 'Dar de baja' : 'Reactivar',
    );
    toggle.addEventListener('click', () => {
      if (service.active) {
        void deactivate(service);
      } else {
        void reactivate(service);
      }
    });
    return h('div', { class: 'row-actions' }, edit, toggle);
  }

  function serviceRow(service: ServiceItem, asTable: boolean): HTMLElement {
    const state = badge(service.active ? 'Activa' : 'Inactiva', service.active ? 'active' : 'inactive');
    const visible = service.publicVisible ? badge('Visible en /reservar', 'visible') : undefined;
    if (asTable) {
      return h(
        'tr',
        {},
        h('td', {}, h('strong', {}, service.name)),
        h('td', { class: 'num' }, `${service.durationMin} min`),
        h('td', {}, state),
        h('td', {}, visible ?? h('span', { class: 'muted' }, 'No visible')),
        h('td', {}, h('div', { class: 'cell-actions' }, rowActions(service))),
      );
    }
    return h(
      'li',
      {},
      h('div', { class: 'row-title' }, service.name, state),
      h('div', { class: 'row-meta' }, h('span', {}, `Duración: ${service.durationMin} min`), visible),
      rowActions(service),
    );
  }

  function renderList(): void {
    content.replaceChildren();
    if (services.length === 0) {
      const add = h('button', { class: 'btn btn-primary', attrs: { type: 'button' } }, 'Nueva prestación');
      add.addEventListener('click', () => openForm());
      content.append(
        h(
          'div',
          { class: 'empty' },
          h('h3', {}, 'Todavía no cargaste prestaciones'),
          h('p', {}, 'Creá la primera para publicarla en la reserva online. Podés darla de baja después sin borrarla.'),
          add,
        ),
      );
      return;
    }

    const mobile = h('div', { class: 'only-mobile' }, h('ul', { class: 'data-list' }, ...services.map((service) => serviceRow(service, false))));
    const rows = services.map((service) => serviceRow(service, true));
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
            h('tr', {}, h('th', {}, 'Prestación'), h('th', {}, 'Duración'), h('th', {}, 'Estado'), h('th', {}, 'Visibilidad pública'), h('th', {}, 'Acciones')),
          ),
          h('tbody', {}, ...rows),
        ),
      ),
    );
    content.append(
      mobile,
      desktop,
      h('p', { class: 'help' }, 'La visibilidad pública puede tardar hasta 60 s en reflejarse en ', h('code', {}, '/reservar'), ' (caché del catálogo público).'),
    );
  }

  function showLoadFailure(): void {
    const retry = h('button', { class: 'btn btn-secondary', attrs: { type: 'button' } }, 'Reintentar');
    retry.addEventListener('click', () => {
      void load();
    });
    content.replaceChildren(
      alertBox({
        variant: 'danger',
        role: 'alert',
        icon: 'error',
        title: 'No se pudo cargar las prestaciones.',
        text: 'Revisá tu conexión e intentá de nuevo. Las otras secciones siguen disponibles.',
      }),
      retry,
    );
  }

  async function load(): Promise<void> {
    section.setAttribute('aria-busy', 'true');
    const result = await deps.gateway.getServices();
    if (result.ok) {
      services = result.data;
      renderList();
    } else {
      showLoadFailure();
    }
    section.removeAttribute('aria-busy');
  }

  function toInput(refs: ServiceFormRefs): ServiceInput {
    const description = refs.fields['svc-desc']?.value ?? '';
    const duration = refs.fields['svc-duration'];
    return {
      name: refs.fields['svc-name']?.value ?? '',
      description: description.trim() === '' ? null : description,
      durationMin: duration === undefined ? Number.NaN : Number(duration.value),
      publicVisible: refs.fields['svc-visible'] instanceof HTMLInputElement ? refs.fields['svc-visible'].checked : false,
    };
  }

  function fieldFor(refs: ServiceFormRefs, fieldName: string): HTMLInputElement | HTMLTextAreaElement | undefined {
    const map: Readonly<Record<string, string>> = {
      name: 'svc-name',
      description: 'svc-desc',
      durationMin: 'svc-duration',
    };
    const id = map[fieldName];
    return id === undefined ? undefined : refs.fields[id];
  }

  function showIssues(refs: ServiceFormRefs, issues: readonly { readonly field: string; readonly message: string }[], fallback: string): void {
    for (const issue of issues) {
      const target = fieldFor(refs, issue.field);
      if (target !== undefined) {
        setFieldError(target, issue.message);
      }
    }
    const first = fieldFor(refs, issues[0]?.field ?? '');
    first?.focus();
    noticeSlot.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', text: fallback }));
  }

  function buildForm(existing: ServiceItem | undefined): ServiceFormRefs {
    const name = field({
      id: 'svc-name',
      label: 'Nombre *',
      autocomplete: 'off',
      value: existing?.name ?? '',
    });
    const description = h('textarea', { class: 'textarea', attrs: { id: 'svc-desc', name: 'svc-desc' } });
    description.value = existing?.description ?? '';
    const descriptionField = h(
      'div',
      { class: 'field span-2' },
      h('label', { attrs: { for: 'svc-desc' } }, 'Descripción (opcional)'),
      description,
    );
    const duration = field({
      id: 'svc-duration',
      label: 'Duración (minutos) *',
      type: 'number',
      autocomplete: 'off',
      min: '1',
      step: '1',
      value: String(existing?.durationMin ?? currentGranularity(deps)),
    });
    const visible = h('input', { class: 'checkbox', attrs: { id: 'svc-visible', name: 'svc-visible', type: 'checkbox' } });
    visible.checked = existing?.publicVisible ?? true;
    const visibleField = h(
      'div',
      { class: 'field' },
      h('label', { class: 'check', attrs: { for: 'svc-visible' } }, visible, h('span', {}, 'Visible en la reserva pública')),
      h('span', { class: 'help' }, 'Una prestación inactiva nunca se publica, aunque quede marcada como visible.'),
    );
    const saveButton = h('button', { class: 'btn btn-primary', attrs: { type: 'button' } }, 'Guardar prestación');
    const cancel = h('button', { class: 'btn btn-secondary', attrs: { type: 'button' } }, 'Cancelar');
    const form = h(
      'form',
      { class: 'form-grid', attrs: { novalidate: '' } },
      h('div', { class: 'field span-2' }, name.element),
      descriptionField,
      duration.element,
      visibleField,
      h('div', { class: 'form-actions' }, saveButton, cancel),
      h(
        'p',
        { class: 'help span-2' },
        'Granularidad vigente: ',
        h('code', {}, `${currentGranularity(deps)} min`),
        '. Si cambiara en el servidor, se mostrará el error correspondiente sin autocorregir el valor.',
      ),
    );
    const refs: ServiceFormRefs = {
      form,
      fields: { 'svc-name': name.input, 'svc-desc': description, 'svc-duration': duration.input, 'svc-visible': visible },
    };
    cancel.addEventListener('click', () => {
      renderList();
      noticeSlot.replaceChildren();
    });
    saveButton.addEventListener('click', () => {
      void save(refs);
    });
    return refs;
  }

  function openForm(existing?: ServiceItem): void {
    editing = existing;
    const refs = buildForm(existing);
    content.replaceChildren(refs.form);
    noticeSlot.replaceChildren();
    content.querySelector<HTMLInputElement>('#svc-name')?.focus();
  }

  async function save(refs: ServiceFormRefs): Promise<void> {
    if (saving) {
      return;
    }
    clearFieldErrors(refs.form);
    noticeSlot.replaceChildren();
    const input = toInput(refs);
    const validation = validateService(input, currentGranularity(deps));
    if (!validation.ok) {
      showIssues(
        refs,
        Object.entries(validation.fields).map(([field, message]) => ({ field, message })),
        validation.message,
      );
      return;
    }

    saving = true;
    const result =
      editing === undefined
        ? await deps.gateway.createService(input)
        : await deps.gateway.updateService(editing.id, { ...input, active: editing.active });
    saving = false;
    if (result.ok) {
      services = editing === undefined ? [...services, result.data] : services.map((item) => (item.id === result.data.id ? result.data : item));
      editing = undefined;
      renderList();
      noticeSlot.replaceChildren(alertBox({ variant: 'success', role: 'status', icon: 'check', title: MESSAGES.savedService.title, text: MESSAGES.savedService.text }));
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
    if (result.failure.kind === 'not-found') {
      await load();
    }
  }

  async function deactivate(service: ServiceItem): Promise<void> {
    const accepted = await confirmAction({
      title: 'Dar de baja la prestación',
      text: 'La prestación quedará inactiva y no aceptará nuevas reservas. Los turnos ya agendados no se modifican. Podés reactivarla cuando quieras y conservará su visibilidad pública.',
      confirmLabel: 'Dar de baja',
    });
    if (!accepted) {
      return;
    }
    noticeSlot.replaceChildren();
    const result = await deps.gateway.deleteService(service.id);
    if (result.ok) {
      services = services.map((item) => (item.id === service.id ? { ...item, active: false } : item));
      renderList();
      noticeSlot.replaceChildren(alertBox({ variant: 'success', role: 'status', icon: 'check', title: 'Prestación dada de baja.', text: MESSAGES.savedService.text }));
    } else {
      const copy = failureCopy(result.failure);
      noticeSlot.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', title: copy.title, text: copy.text }));
    }
  }

  async function reactivate(service: ServiceItem): Promise<void> {
    noticeSlot.replaceChildren();
    const result = await deps.gateway.updateService(service.id, { ...toServiceInput(service), active: true });
    if (result.ok) {
      services = services.map((item) => (item.id === service.id ? result.data : item));
      renderList();
      noticeSlot.replaceChildren(
        alertBox({
          variant: 'success',
          role: 'status',
          icon: 'check',
          title: 'Prestación reactivada.',
          text: `${service.name} volvió a estar activa y conservó su visibilidad pública.`,
        }),
      );
    } else {
      const copy = failureCopy(result.failure);
      noticeSlot.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', title: copy.title, text: copy.text }));
    }
  }

  section.querySelector<HTMLButtonElement>('.section-head button')?.addEventListener('click', () => openForm());

  void load();
  return section;
}
