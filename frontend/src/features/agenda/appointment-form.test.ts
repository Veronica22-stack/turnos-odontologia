import { afterEach, describe, expect, it, vi } from 'vitest';
import type { HttpResult } from '../../core/http/errors';
import { dayBoundsUtc } from '../../shared/utils/dates';
import type { AgendaGateway } from './agenda-gateway';
import { createAppointmentForm } from './appointment-form';
import type { AppointmentDto, AvailableSlotDto, PatientSummaryDto } from './agenda-types';
import type { ServiceItem } from '../settings/settings-types';

/** Formulario de alta de turno interno (C-20 D7/US-021). */

const DATE = '2026-11-23';

function instantWithLocalTime(date: string, hhmm: string): string {
  const parts = hhmm.split(':').map(Number);
  const hours = parts[0] ?? 0;
  const minutes = parts[1] ?? 0;
  return new Date(dayBoundsUtc(date).start.getTime() + (hours * 60 + minutes) * 60_000).toISOString();
}

const APPOINTMENT: AppointmentDto = {
  id: '11111111-1111-1111-1111-111111111111',
  patientId: '22222222-2222-2222-2222-222222222222',
  serviceId: '33333333-3333-3333-3333-333333333333',
  startsAt: instantWithLocalTime(DATE, '13:30'),
  endsAt: instantWithLocalTime(DATE, '14:15'),
  durationMin: 45,
  status: 'CONFIRMED',
  origin: 'INTERNAL',
  overbooking: false,
  cancelledBy: null,
  cancelledAt: null,
  notes: null,
  createdAt: '2026-11-22T09:00:00Z',
  updatedAt: '2026-11-22T09:00:00Z',
};

const PATIENT: PatientSummaryDto = {
  id: '22222222-2222-2222-2222-222222222222',
  firstName: 'Lucía',
  lastName: 'Fernández',
  dni: '30456789',
  phone: '11 2345 6789',
  active: true,
};
const SERVICE: ServiceItem = {
  id: '33333333-3333-3333-3333-333333333333',
  name: 'Limpieza',
  description: null,
  durationMin: 45,
  active: true,
  publicVisible: true,
};
const SLOT: AvailableSlotDto = { startsAt: instantWithLocalTime(DATE, '13:30'), endsAt: instantWithLocalTime(DATE, '14:15') };

function makeGateway(overrides: Readonly<Partial<AgendaGateway>> = {}): AgendaGateway {
  return {
    listAppointments: async () => ({ ok: true, data: [] }),
    createAppointment: async () => ({ ok: true, data: APPOINTMENT }),
    cancelAppointment: async () => ({ ok: true, data: APPOINTMENT }),
    rescheduleAppointment: async () => ({ ok: true, data: APPOINTMENT }),
    completeAppointment: async () => ({ ok: true, data: APPOINTMENT }),
    noShowAppointment: async () => ({ ok: true, data: APPOINTMENT }),
    listServices: async () => ({ ok: true, data: [SERVICE] }),
    searchPatients: async () => ({ ok: true, data: [PATIENT] }),
    createPatient: async () => ({ ok: true, data: PATIENT }),
    getWorkingHours: async () => ({ ok: true, data: [{ weekday: 1, startTime: '09:00', endTime: '13:00' }] }),
    listAvailability: async () => ({ ok: true, data: [SLOT] }),
    listBlocks: async () => ({ ok: true, data: [] }),
    ...overrides,
  } as AgendaGateway;
}

function mountForm(gateway: AgendaGateway): HTMLDialogElement {
  const dialog = createAppointmentForm({ gateway, day: DATE, setTurno: vi.fn() });
  document.body.append(dialog);
  dialog.setAttribute('open', '');
  return dialog;
}

function registerButtonOf(dialog: HTMLDialogElement): HTMLButtonElement {
  const button = Array.from(dialog.querySelectorAll('button')).find((candidate) => candidate.textContent === 'Registrar turno');
  if (button === undefined) {
    throw new Error('No se encontro el boton de registro');
  }
  return button;
}

async function selectPaciente(dialog: HTMLDialogElement): Promise<void> {
  await vi.waitFor(() => expect(dialog.querySelectorAll('.patient-result').length).toBeGreaterThan(0));
  dialog.querySelector('.patient-result')?.dispatchEvent(new Event('click', { bubbles: true }));
  expect(dialog.querySelector('.card h3')?.textContent).toContain('Lucía Fernández');
}

async function seleccionaPrestacionYSlot(dialog: HTMLDialogElement): Promise<void> {
  const select = dialog.querySelector<HTMLSelectElement>('#prest');
  expect(select).not.toBeNull();
  await vi.waitFor(() =>
    expect(Array.from(select?.options ?? []).some((option) => option.value === SERVICE.id)).toBe(true),
  );
  select!.value = SERVICE.id;
  select!.dispatchEvent(new Event('change', { bubbles: true }));
  await vi.waitFor(() => expect(dialog.querySelector('.slot')).not.toBeNull());
  dialog.querySelector('.slot')?.dispatchEvent(new Event('click', { bubbles: true }));
  expect(dialog.querySelector('.slot')?.getAttribute('aria-pressed')).toBe('true');
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('appointment-form', () => {
  it('flujo existente: selecciona paciente activo y crea el turno con patientId', async () => {
    const createAppointment = vi.fn(async (): Promise<HttpResult<AppointmentDto>> => ({ ok: true, data: APPOINTMENT }));
    const setTurno = vi.fn();
    const dialog = createAppointmentForm({ gateway: makeGateway({ createAppointment }), day: DATE, setTurno });
    document.body.append(dialog);
    dialog.setAttribute('open', '');

    await selectPaciente(dialog);
    await seleccionaPrestacionYSlot(dialog);
    registerButtonOf(dialog).dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() =>
      expect(createAppointment).toHaveBeenCalledWith({ patientId: PATIENT.id, serviceId: SERVICE.id, startsAt: SLOT.startsAt }),
    );
    await vi.waitFor(() => expect(setTurno).toHaveBeenCalledWith(APPOINTMENT));
    expect(dialog.hasAttribute('open')).toBe(false);
  });

  it('flujo nuevo paciente: dado de alta y creado antes de registrar el turno', async () => {
    const createPatient = vi.fn(async (): Promise<HttpResult<PatientSummaryDto>> => ({ ok: true, data: PATIENT }));
    const createAppointment = vi.fn(async (): Promise<HttpResult<AppointmentDto>> => ({ ok: true, data: APPOINTMENT }));
    const dialog = createAppointmentForm({
      gateway: makeGateway({ searchPatients: async () => ({ ok: true, data: [] }), createPatient, createAppointment }),
      day: DATE,
      setTurno: vi.fn(),
    });
    document.body.append(dialog);
    dialog.setAttribute('open', '');

    const createNewButton = Array.from(dialog.querySelectorAll('button')).find((candidate) => candidate.textContent === 'Crear paciente nuevo');
    createNewButton?.dispatchEvent(new Event('click', { bubbles: true }));
    await vi.waitFor(() => expect(dialog.querySelector('#nuevo-nombre')).not.toBeNull());

    const inputs = ['nombre', 'apellido', 'dni', 'tel'];
    for (const input of inputs) {
      (dialog.querySelector(`#nuevo-${input}`) as HTMLInputElement).value = {
        nombre: 'Lucía',
        apellido: 'Fernández',
        dni: '30456789',
        tel: '11 2345 6789',
      }[input]!;
    }
    const registerPatientButton = Array.from(dialog.querySelectorAll('button')).find(
      (candidate) => candidate.textContent === 'Registrar paciente nuevo',
    );
    registerPatientButton?.dispatchEvent(new Event('click', { bubbles: true }));
    await vi.waitFor(() => expect(createPatient).toHaveBeenCalledWith({ firstName: 'Lucía', lastName: 'Fernández', dni: '30456789', phone: '11 2345 6789' }));
    await vi.waitFor(() => expect(dialog.querySelector('.card h3')?.textContent).toContain('Lucía Fernández'));

    await seleccionaPrestacionYSlot(dialog);
    registerButtonOf(dialog).dispatchEvent(new Event('click', { bubbles: true }));
    await vi.waitFor(() => expect(createAppointment).toHaveBeenCalledWith({ patientId: PATIENT.id, serviceId: SERVICE.id, startsAt: SLOT.startsAt }));
  });

  it('flujo 409: muestra el mensaje genérico del solapamiento', async () => {
    const dialog = mountForm(
      makeGateway({ createAppointment: async () => ({ ok: false, error: { kind: 'problem', status: 409, code: 'APPOINTMENT_SLOT_TAKEN', errors: [] } }) }),
    );

    await selectPaciente(dialog);
    await seleccionaPrestacionYSlot(dialog);
    registerButtonOf(dialog).dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(dialog.textContent).toContain('Ese horario ya está ocupado, eligí otro'));
    expect(dialog.hasAttribute('open')).toBe(true);
  });

  it('flujo 422 OUTSIDE_WORKING_HOURS: muestra el mensaje del horario de atención', async () => {
    const dialog = mountForm(
      makeGateway({ createAppointment: async () => ({ ok: false, error: { kind: 'problem', status: 422, code: 'OUTSIDE_WORKING_HOURS', errors: [] } }) }),
    );

    await selectPaciente(dialog);
    await seleccionaPrestacionYSlot(dialog);
    registerButtonOf(dialog).dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(dialog.textContent).toContain('Ese horario no está dentro del horario de atención'));
  });

  it('campos obligatorios: sin paciente/prestación/slot no se envía', async () => {
    const createAppointment = vi.fn();
    const dialog = mountForm(makeGateway({ createAppointment }));

    registerButtonOf(dialog).dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(dialog.textContent).toContain('Seleccioná un paciente.'));
    expect(createAppointment).not.toHaveBeenCalled();
  });

  it('doble envío bloqueado mientras está en curso', async () => {
    let resolveAppointment: ((result: { ok: true; data: AppointmentDto }) => void) | undefined;
    const createAppointment = vi.fn(
      () => new Promise<{ ok: true; data: AppointmentDto }>((resolve) => { resolveAppointment = resolve; }),
    );
    const dialog = mountForm(makeGateway({ createAppointment }));

    await selectPaciente(dialog);
    await seleccionaPrestacionYSlot(dialog);
    const button = registerButtonOf(dialog);
    button.dispatchEvent(new Event('click', { bubbles: true }));
    button.dispatchEvent(new Event('click', { bubbles: true }));

    expect(createAppointment).toHaveBeenCalledTimes(1);
    resolveAppointment?.({ ok: true, data: APPOINTMENT });
  });
});
