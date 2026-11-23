import { alertBox, field } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import { formatTime } from '../../shared/utils/dates';
import type { AgendaGateway } from './agenda-gateway';
import { agendaErrorMessage } from './agenda-messages';
import type { AppointmentDto, AvailableSlotDto, CreateAppointmentPayload, CreatePatientPayload, PatientSummaryDto } from './agenda-types';
import type { ServiceItem } from '../settings/settings-types';

/** Formulario de alta de turno interno (C-20 D7/US-021): paciente, prestación, slot y sobreturno. */

export interface AppointmentFormDeps {
  readonly gateway: AgendaGateway;
  /** Fecha local del día para la consulta de disponibilidad. */
  readonly day: string;
  /** Se invoca con el turno creado tras el 201 (agenda refresca). */
  readonly setTurno: (appointment: AppointmentDto) => void;
}

export function createAppointmentForm(deps: AppointmentFormDeps): HTMLDialogElement {
  const { gateway, day, setTurno } = deps;

  let patient: PatientSummaryDto | undefined;
  let service: ServiceItem | undefined;
  let slot: AvailableSlotDto | undefined;
  let services: readonly ServiceItem[] = [];
  let patientResults: readonly PatientSummaryDto[] = [];
  let newPatientOpen = false;
  let submitting = false;
  let overbooking = false;

  // ----- Paciente -----

  const pacienteField = field({
    id: 'pac-q',
    label: 'Paciente (buscar)',
    type: 'text',
    autocomplete: 'name',
    help: 'Coincidencia activa: Fernández, Lucía. Si no hay resultados, usá el botón de abajo.',
  });
  pacienteField.input.addEventListener('input', () => {
    void searchPatients(pacienteField.input.value);
  });

  const patientNameEl = h('h3', {}, 'Paciente activo');
  const patientMetaEl = h('p', { class: 'help' }, 'Sin paciente seleccionado.');
  const patientChangeButton = h('button', { class: 'btn btn-secondary btn-sm', attrs: { type: 'button' }, on: { click: () => void changePatient() } }, 'Cambiar');
  const patientCard = h(
    'div',
    { class: 'card' },
    h('div', { class: 'card-header' }, h('div', {}, patientNameEl, patientMetaEl), patientChangeButton),
  );

  function renderPatientCard(): void {
    if (patient === undefined) {
      patientNameEl.replaceChildren('Paciente activo');
      patientMetaEl.replaceChildren('Sin paciente seleccionado.');
      return;
    }
    patientNameEl.replaceChildren(`${patient.firstName} ${patient.lastName}`);
    patientMetaEl.replaceChildren(`${patient.lastName}, ${patient.firstName} — DNI ${patient.dni}`);
  }

  const resultsZone = h('div', { class: 'patient-results', attrs: { role: 'listbox', 'aria-label': 'Resultados de búsqueda de paciente' } });

  async function searchPatients(query: string): Promise<void> {
    const result = await gateway.searchPatients(query.trim() === '' ? undefined : query);
    patientResults = result.ok ? result.data : [];
    renderPatientResults();
  }

  function renderPatientResults(): void {
    resultsZone.replaceChildren(
      ...patientResults.map((candidate) =>
        h(
          'button',
          {
            class: 'patient-result',
            attrs: { type: 'button', 'data-patient-id': candidate.id, 'aria-pressed': patient?.id === candidate.id ? 'true' : 'false' },
            on: { click: () => selectPatient(candidate) },
          },
          h('span', { class: 'patient-result-name' }, `${candidate.firstName} ${candidate.lastName}`),
          h('span', { class: 'patient-result-meta' }, candidate.dni),
        ),
      ),
    );
  }

  function selectPatient(candidate: PatientSummaryDto): void {
    patient = candidate;
    renderPatientCard();
    renderPatientResults();
  }

  async function changePatient(): Promise<void> {
    patient = undefined;
    renderPatientCard();
    pacienteField.input.value = '';
    pacienteField.input.focus();
    await searchPatients('');
  }

  // ----- Paciente nuevo -----

  const nombreField = field({ id: 'nuevo-nombre', label: 'Nombre', type: 'text', autocomplete: 'given-name' });
  const apellidoField = field({ id: 'nuevo-apellido', label: 'Apellido', type: 'text', autocomplete: 'family-name' });
  const dniField = field({ id: 'nuevo-dni', label: 'DNI', type: 'text', autocomplete: 'off' });
  const telefonoField = field({ id: 'nuevo-tel', label: 'Teléfono celular', type: 'tel', inputmode: 'tel', autocomplete: 'tel-national' });
  const emailField = field({ id: 'nuevo-email', label: 'Email', type: 'email', autocomplete: 'email' });

  const registerPatientButton = h(
    'button',
    { class: 'btn btn-primary', attrs: { type: 'button' }, on: { click: () => void registerNewPatient() } },
    'Registrar paciente nuevo',
  );
  const newPatientFields = h(
    'div',
    { class: 'new-patient-fields' },
    nombreField.element,
    apellidoField.element,
    dniField.element,
    telefonoField.element,
    emailField.element,
    registerPatientButton,
  );
  const noteWhenHidden = h('p', { class: 'new-patient-note' }, 'Si hay coincidencias activas, usá la búsqueda arriba; sino, activá los campos del nuevo paciente.');
  const newPatientHolder = h('div', { class: 'new-patient-holder' }, noteWhenHidden);
  const toggleNewPatientButton = h('button', { class: 'btn btn-secondary', attrs: { type: 'button' }, on: { click: () => toggleNewPatient() } }, 'Crear paciente nuevo');

  function toggleNewPatient(): void {
    newPatientOpen = !newPatientOpen;
    newPatientHolder.replaceChildren(newPatientOpen ? newPatientFields : noteWhenHidden);
  }

  async function registerNewPatient(): Promise<void> {
    const messages: string[] = [];
    if (nombreField.input.value.trim() === '') {
      messages.push('Ingresá un nombre.');
    }
    if (apellidoField.input.value.trim() === '') {
      messages.push('Ingresá un apellido.');
    }
    if (dniField.input.value.trim() === '') {
      messages.push('Ingresá el DNI.');
    }
    if (telefonoField.input.value.trim() === '') {
      messages.push('Ingresá el teléfono.');
    }
    if (messages.length > 0) {
      alertsZone.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', text: messages[0] ?? '' }));
      return;
    }
    const email = emailField.input.value.trim();
    const payload: CreatePatientPayload = {
      firstName: nombreField.input.value.trim(),
      lastName: apellidoField.input.value.trim(),
      dni: dniField.input.value.trim(),
      phone: telefonoField.input.value.trim(),
      ...(email !== '' ? { email } : {}),
    };
    const result = await gateway.createPatient(payload);
    if (result.ok) {
      patient = result.data;
      renderPatientCard();
      renderPatientResults();
      newPatientHolder.replaceChildren(noteWhenHidden);
      return;
    }
    alertsZone.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', text: agendaErrorMessage(result.error) }));
  }

  // ----- Prestación -----

  const prestSelect = h('select', { class: 'select', attrs: { id: 'prest' } });

  function renderServices(): void {
    service = undefined;
    prestSelect.replaceChildren(h('option', { attrs: { value: '' } }, 'Seleccioná una prestación'));
    for (const item of services) {
      prestSelect.append(h('option', { attrs: { value: item.id } }, `${item.name} · ${item.durationMin} min`));
    }
    prestSelect.value = '';
  }

  prestSelect.addEventListener('change', () => {
    service = services.find((item) => item.id === prestSelect.value);
    void loadSlots();
  });

  async function loadServices(): Promise<void> {
    const result = await gateway.listServices();
    services = result.ok ? result.data : [];
    renderServices();
  }

  // ----- Slots -----

  const slotGroup = h('div', { class: 'slot-group', attrs: { id: 'nuevo-turno-slots' } });

  async function loadSlots(): Promise<void> {
    slot = undefined;
    if (service === undefined) {
      slotGroup.replaceChildren(h('p', { class: 'note' }, 'Seleccioná una prestación para ver los slots disponibles.'));
      return;
    }
    const result = await gateway.listAvailability(service.id, day);
    const list = result.ok ? result.data : [];
    slotGroup.replaceChildren(...list.map((available) => renderSlotButton(available)));
  }

  function renderSlotButton(available: AvailableSlotDto): HTMLElement {
    const button = h(
      'button',
      { class: 'slot', attrs: { type: 'button', 'aria-label': formatTime(available.startsAt), 'aria-pressed': 'false' }, on: { click: () => selectSlot(button, available) } },
      formatTime(available.startsAt),
    );
    return button;
  }

  function selectSlot(selected: HTMLButtonElement, available: AvailableSlotDto): void {
    slot = available;
    for (const other of slotGroup.querySelectorAll<HTMLButtonElement>('.slot')) {
      other.setAttribute('aria-pressed', other === selected ? 'true' : 'false');
    }
  }

  // ----- Sobreturno -----

  const overbookingInput = h('input', { class: 'check', attrs: { type: 'checkbox', id: 'overbooking' } });
  overbookingInput.addEventListener('change', () => {
    overbooking = overbookingInput.checked;
  });
  const overbookingLabel = h('label', { class: 'check' }, overbookingInput, h('span', {}, 'Cargar el turno fuera del horario de atención (sobreturno)'));

  // ----- Alertas y envío -----

  const alertsZone = h('div', { class: 'alerts', attrs: { id: 'nuevo-turno-alerts' } });

  function showAlert(message: string): void {
    alertsZone.replaceChildren(alertBox({ variant: 'danger', role: 'alert', icon: 'error', text: message }));
  }

  const registerButton = h('button', { class: 'btn btn-primary btn-lg', attrs: { type: 'button' }, on: { click: () => void submitForm() } }, 'Registrar turno');
  const cancelButton = h('button', { class: 'btn btn-secondary btn-lg', attrs: { type: 'button' }, on: { click: () => closeDialog() } }, 'Cancelar');

  async function submitForm(): Promise<void> {
    if (submitting) {
      return;
    }
    if (patient === undefined) {
      showAlert('Seleccioná un paciente.');
      return;
    }
    if (service === undefined) {
      showAlert('Seleccioná una prestación.');
      return;
    }
    if (slot === undefined) {
      showAlert('Seleccioná un slot libre.');
      return;
    }
    submitting = true;
    registerButton.setAttribute('disabled', '');
    const payload: CreateAppointmentPayload = {
      patientId: patient.id,
      serviceId: service.id,
      startsAt: slot.startsAt,
      ...(overbooking ? { overbooking: true } : {}),
    };
    const result = await gateway.createAppointment(payload);
    submitting = false;
    registerButton.removeAttribute('disabled');
    if (result.ok) {
      closeDialog();
      setTurno(result.data);
      return;
    }
    showAlert(agendaErrorMessage(result.error));
  }

  // ----- Diálogo -----

  const dialog = h('dialog', { class: 'dialog', attrs: { id: 'nuevo-turno', 'aria-labelledby': 'nuevo-turno-t' } });
  const prestField = h(
    'div',
    { class: 'field' },
    h('label', { attrs: { for: 'prest' } }, 'Prestación'),
    prestSelect,
    h('p', { class: 'help' }, 'Duración snapshot del servidor (RN-AG-05).'),
  );

  dialog.replaceChildren(
    h('h3', { attrs: { id: 'nuevo-turno-t' } }, 'Nuevo turno'),
    patientCard,
    pacienteField.element,
    resultsZone,
    newPatientHolder,
    toggleNewPatientButton,
    prestField,
    slotGroup,
    overbookingLabel,
    alertsZone,
    h('div', { class: 'form-actions' }, registerButton, cancelButton),
  );

  function closeDialog(): void {
    if (typeof dialog.close === 'function') {
      dialog.close();
    } else {
      dialog.removeAttribute('open');
    }
  }

  void loadServices();
  void searchPatients('');

  return dialog;
}
