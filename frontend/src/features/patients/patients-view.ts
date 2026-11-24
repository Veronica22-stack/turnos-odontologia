import { confirmAction } from '../../core/dom/confirm';
import { alertBox, formIcon } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import type { MessageCopy, PatientsFailure } from './patients-messages';
import { failureCopy, MESSAGES } from './patients-messages';
import type { PatientsGateway } from './patients-gateway';
import {
  backToList,
  beginDeactivate,
  beginEdit,
  deactivateFailed,
  deactivateStarted,
  deactivateSucceeded,
  detailFailed,
  detailLoaded,
  initialPatientsState,
  listFailed,
  listLoaded,
  openPatient,
  saveFailed,
  saveStarted,
  saveSucceeded,
  searchChanged,
  setDraft,
  setFieldErrors,
  type ListState,
  type PatientsState,
} from './patients-state';
import { mapServerErrors, validatePatientInput } from './patients-validation';
import type { PatientDetail, PatientInput } from './patients-types';
import { createPatientCard } from './patient-card';
import { createPatientForm } from './patient-form';
import { createPatientList, createPatientTable } from './patient-row';

/** Vista `/pacientes` (C-21 D6/D7): búsqueda, ficha, edición y baja sin datos personales en la ruta. */

export interface PatientsViewDeps {
  readonly gateway: PatientsGateway;
}

function action(label: string, className: string, onClick: () => void): HTMLButtonElement {
  const button = h('button', { class: `btn ${className}`, attrs: { type: 'button' } }, label);
  button.addEventListener('click', onClick);
  return button;
}

function notice(copy: MessageCopy, variant: 'info' | 'success' | 'warning' | 'danger', icon: 'info' | 'check' | 'warning' | 'error', ...actions: readonly HTMLElement[]): HTMLElement {
  const box = alertBox({ variant, role: variant === 'info' || variant === 'success' ? 'status' : 'alert', icon, title: copy.title, text: copy.text });
  if (actions.length > 0) {
    box.append(h('div', { class: 'alert-actions' }, ...actions));
  }
  return box;
}

export function createPatientsView(deps: PatientsViewDeps): HTMLElement {
  const { gateway } = deps;
  const root = h('section', { class: 'patients-view' });
  let state: PatientsState = initialPatientsState();
  let listState: ListState = state;
  let generation = 0;

  function set(next: PatientsState): void {
    state = next;
    if (next.kind === 'list') {
      listState = next;
    }
    render();
  }

  function header(title: string, lead: string): HTMLElement {
    return h('div', { class: 'page-head' }, h('h1', { attrs: { tabindex: '-1' } }, title), h('p', { class: 'lead' }, lead));
  }

  function searchForm(current: string): HTMLElement {
    const input = h('input', {
      class: 'input',
      attrs: { id: 'q', name: 'q', type: 'search', placeholder: 'Buscar por nombre, DNI o teléfono', autocomplete: 'off' },
    });
    input.value = current;
    const form = h(
      'form',
      { class: 'table-tools', attrs: { role: 'search', novalidate: '' } },
      h(
        'div',
        { class: 'search' },
        h('label', { class: 'sr-only', attrs: { for: 'q' } }, 'Buscar pacientes'),
        input,
      ),
      h('button', { class: 'btn btn-primary', attrs: { type: 'submit' } }, 'Buscar'),
    );
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      const term = input.value.trim();
      set(searchChanged(listState, term));
      void load(term);
    });
    return form;
  }

  async function load(query: string): Promise<void> {
    const sequence = ++generation;
    set({ kind: 'list', phase: 'loading', query, patients: [] });
    const result = await gateway.search(query);
    if (sequence !== generation) {
      return;
    }
    set(
      result.ok && Array.isArray(result.data)
        ? listLoaded(state as ListState, result.data)
        : listFailed(state as ListState, result.ok ? { kind: 'connection' } : result.failure),
    );
  }

  async function loadDetail(id: string): Promise<void> {
    const sequence = ++generation;
    set({ kind: 'detail', phase: 'loading', id });
    const result = await gateway.get(id);
    if (sequence !== generation) {
      return;
    }
    const pending = state as { kind: 'detail'; phase: 'loading'; id: string };
    set(
      result.ok && result.data !== undefined
        ? detailLoaded(pending, result.data)
        : detailFailed(pending, result.ok ? { kind: 'connection' } : result.failure),
    );
  }

  function renderList(current: ListState): HTMLElement {
    const content: HTMLElement[] = [header('Pacientes', 'Buscá por nombre, apellido, DNI o teléfono. Las fichas dadas de baja no aparecen acá.'), searchForm(current.query)];
    if (current.phase === 'loading') {
      content.push(
        h('div', { class: 'loading-panel', attrs: { role: 'status' } }, h('p', {}, 'Cargando pacientes…'), h('div', { class: 'loading-line' }), h('div', { class: 'loading-line short' })),
      );
      return h('div', {}, ...content);
    }
    if (current.phase === 'error') {
      const failure = current.failure ?? { kind: 'connection' } as PatientsFailure;
      content.push(notice(failureCopy(failure, 'load'), 'danger', 'error', action('Reintentar', 'btn-secondary', () => void load(current.query))));
      return h('div', {}, ...content);
    }
    if (current.patients.length === 0) {
      const copy = current.query === '' ? MESSAGES.noPatients : MESSAGES.noResults;
      content.push(
        h(
          'div',
          { class: 'empty' },
          h('div', { class: 'ico', attrs: { 'aria-hidden': 'true' } }, formIcon('info')),
          h('h3', {}, copy.title),
          h('p', {}, copy.text),
        ),
      );
      return h('div', {}, ...content);
    }
    content.push(h('p', { class: 'result-count', attrs: { role: 'status' } }, `${current.patients.length} pacientes activos · orden alfabético del servidor`));
    content.push(createPatientList(current.patients, (id) => void open(id)));
    content.push(h('div', { class: 'table-wrap only-desktop' }, createPatientTable(current.patients, (id) => void open(id))));
    return h('div', {}, ...content);
  }

  function open(id: string): void {
    ++generation;
    set(openPatient(listState, id));
    void loadDetail(id);
  }

  function renderDetail(current: Extract<PatientsState, { kind: 'detail' }>): HTMLElement {
    if (current.phase === 'loading') {
      return h('div', { class: 'loading-panel', attrs: { role: 'status' } }, h('p', {}, 'Cargando ficha…'));
    }
    if (current.phase === 'error' || current.patient === undefined) {
      const failure = current.failure ?? { kind: 'connection' } as PatientsFailure;
      const copy = failureCopy(failure, 'detail');
      const back = action('Volver al listado', 'btn-secondary', () => set(backToList(current, listState)));
      if (failure.kind === 'connection') {
        return notice(copy, 'danger', 'error', action('Reintentar', 'btn-primary', () => void loadDetail(current.id)), back);
      }
      return notice(copy, failure.kind === 'not-found' ? 'warning' : 'danger', failure.kind === 'not-found' ? 'warning' : 'error', back);
    }
    const patient = current.patient;
    return createPatientCard(patient, {
      onBack: () => set(backToList(current, listState)),
      onEdit: () => set(beginEdit(current)),
      onDeactivate: () => confirmDeactivate(patient),
    });
  }

  function renderEdit(current: Extract<PatientsState, { kind: 'edit' }>): HTMLElement {
    const failureNotice = current.failure === undefined
      ? null
      : current.failure.kind === 'not-found'
        ? notice(failureCopy(current.failure, 'save'), 'warning', 'warning', action('Volver al listado', 'btn-secondary', () => set(backToList(current, listState))))
        : notice(
            failureCopy(current.failure, 'save'),
            'danger',
            'error',
            action('Ver ficha actual', 'btn-secondary', () => void loadDetail(current.patient.id)),
          );

    return h(
      'div',
      {},
      failureNotice,
      createPatientForm(
        { patient: current.patient, draft: current.draft, errors: current.errors, hasUnknownField: current.hasUnknownField, busy: current.phase === 'saving' },
        {
          onCancel: () => set({ kind: 'detail', phase: 'ready', id: current.patient.id, patient: current.patient }),
          onSave: (input: PatientInput) => void save(current, input),
        },
      ),
    );
  }

  async function save(current: Extract<PatientsState, { kind: 'edit' }>, input: PatientInput): Promise<void> {
    if (state.kind !== 'edit' || state.phase !== 'idle') {
      return;
    }
    const sequence = generation;
    const errors = validatePatientInput(input);
    if (Object.keys(errors).length > 0) {
      set(setFieldErrors(current, errors));
      return;
    }
    const submitted = setDraft(current, input);
    set(saveStarted(submitted));
    const result = await gateway.update(current.patient.id, input);
    if (sequence !== generation) {
      return;
    }
    if (result.ok && result.data !== undefined) {
      set(saveSucceeded(submitted, result.data));
      return;
    }
    const failure: PatientsFailure = result.ok ? { kind: 'connection' } : result.failure;
    const mapped = failure.kind === 'invalid' ? mapServerErrors(failure.errors) : undefined;
    set(saveFailed(submitted, failure, mapped?.fields, mapped?.hasUnknown === true));
  }

  function confirmDeactivate(patient: PatientDetail): void {
    const sequence = generation;
    const opener = document.activeElement;
    void confirmAction({
      title: '¿Dar de baja a este paciente?',
      text: 'La ficha quedará inactiva y no aparecerá en las búsquedas. Sus turnos y su historia clínica se conservan. No se puede deshacer.',
      confirmLabel: 'Sí, dar de baja',
      cancelLabel: 'No, mantener activo',
      initialFocus: 'cancel',
    }).then(async (confirmed) => {
      if (!confirmed || sequence !== generation) {
        if (opener instanceof HTMLElement) {
          opener.focus();
        }
        return;
      }
      const current: Extract<PatientsState, { kind: 'deactivate' }> = beginDeactivate({ kind: 'detail', phase: 'ready', id: patient.id, patient });
      set(deactivateStarted(current));
      const result = await gateway.deactivate(patient.id);
      if (sequence !== generation) {
        return;
      }
      set(result.ok ? deactivateSucceeded(current) : deactivateFailed(current, result.failure));
    });
  }

  function renderDeactivate(current: Extract<PatientsState, { kind: 'deactivate' }>): HTMLElement {
    const failure = current.failure === undefined ? null : notice(failureCopy(current.failure, 'deactivate'), 'danger', 'error', action('Reintentar', 'btn-primary', () => confirmDeactivate(current.patient)));
    return h('div', {}, failure, createPatientCard(current.patient, {
      onBack: () => set(backToList(current, listState)),
      onEdit: () => set(beginEdit({ kind: 'detail', phase: 'ready', id: current.patient.id, patient: current.patient })),
      onDeactivate: () => confirmDeactivate(current.patient),
    }));
  }

  function render(): void {
    let content: HTMLElement;
    switch (state.kind) {
      case 'list':
        content = renderList(state);
        break;
      case 'detail':
        content = renderDetail(state);
        break;
      case 'edit':
        content = renderEdit(state);
        break;
      case 'deactivate':
        content = renderDeactivate(state);
        break;
      case 'deactivated':
        content = notice(MESSAGES.deactivated, 'info', 'check', action('Volver al listado', 'btn-primary', () => void load(listState.query)));
        break;
    }
    root.replaceChildren(content);
  }

  void load('');
  return root;
}
