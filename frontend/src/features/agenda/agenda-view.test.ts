import { afterEach, describe, expect, it, vi } from 'vitest';
import type { HttpResult } from '../../core/http/errors';
import { dayBoundsUtc } from '../../shared/utils/dates';
import type { AgendaGateway } from './agenda-gateway';
import { createAgendaView } from './agenda-view';
import type { AppointmentDto, AgendaBlock, AvailableSlotDto, PatientSummaryDto } from './agenda-types';
import type { ServiceItem, WorkingHour } from '../settings/settings-types';

/** Vista /agenda: agenda diaria y semanal sobre turnos, bloqueos y horarios de atención (C-20). */

const DATE = '2026-11-23'; // lunes
const HOURS: readonly WorkingHour[] = [{ weekday: 1, startTime: '09:00', endTime: '13:00' }];

/** Instant ISO Z tal que en hora de Buenos Aires sea `hhmm` del día local `date`. */
function instantWithLocalTime(date: string, hhmm: string): string {
  const parts = hhmm.split(':').map(Number);
  const hours = parts[0] ?? 0;
  const minutes = parts[1] ?? 0;
  return new Date(dayBoundsUtc(date).start.getTime() + (hours * 60 + minutes) * 60_000).toISOString();
}

const CLOCK = (): Date => new Date(dayBoundsUtc(DATE).start.getTime() + 630 * 60_000); // 10:30 hora del consultorio

const APPOINTMENT: AppointmentDto = {
  id: '11111111-1111-1111-1111-111111111111',
  patientId: '22222222-2222-2222-2222-222222222222',
  serviceId: '33333333-3333-3333-3333-333333333333',
  startsAt: instantWithLocalTime(DATE, '10:00'),
  endsAt: instantWithLocalTime(DATE, '10:30'),
  durationMin: 30,
  status: 'CONFIRMED',
  origin: 'INTERNAL',
  overbooking: false,
  cancelledBy: null,
  cancelledAt: null,
  notes: null,
  createdAt: '2026-11-22T09:00:00Z',
  updatedAt: '2026-11-22T09:00:00Z',
};

const BLOCK: AgendaBlock = { startsAt: instantWithLocalTime(DATE, '11:00'), endsAt: instantWithLocalTime(DATE, '12:00') };
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
    listAppointments: async () => ({ ok: true, data: [APPOINTMENT] }),
    createAppointment: async () => ({ ok: true, data: APPOINTMENT }),
    cancelAppointment: async () => ({ ok: true, data: APPOINTMENT }),
    rescheduleAppointment: async () => ({ ok: true, data: APPOINTMENT }),
    completeAppointment: async () => ({ ok: true, data: APPOINTMENT }),
    noShowAppointment: async () => ({ ok: true, data: APPOINTMENT }),
    listServices: async () => ({ ok: true, data: [SERVICE] }),
    searchPatients: async () => ({ ok: true, data: [PATIENT] }),
    createPatient: async () => ({ ok: true, data: PATIENT }),
    getWorkingHours: async () => ({ ok: true, data: HOURS }),
    listAvailability: async () => ({ ok: true, data: [SLOT] }),
    listBlocks: async () => ({ ok: true, data: [BLOCK] }),
    ...overrides,
  } as AgendaGateway;
}

function mount(root: HTMLElement): void {
  document.body.replaceChildren(root);
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('agenda-view - formulario "Nuevo turno" no queda en el documento', () => {
  function newAppointmentForms(): HTMLDialogElement[] {
    return Array.from(document.body.querySelectorAll('dialog')).filter((d) => d.querySelector('#nuevo-turno-t') !== null);
  }

  function openNewAppointment(root: HTMLElement): void {
    const button = Array.from(root.querySelectorAll('button')).find((b) => b.textContent === 'Nuevo turno');
    button?.click();
  }

  it('al cerrarse, el formulario sale del documento', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());

    openNewAppointment(root);
    const form = newAppointmentForms()[0];
    expect(form).toBeDefined();

    form?.dispatchEvent(new Event('close'));

    expect(newAppointmentForms()).toHaveLength(0);
  });

  it('al tocar "Cancelar" (se quita el atributo open) el formulario sale del documento', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());

    openNewAppointment(root);
    const cancel = Array.from(newAppointmentForms()[0]?.querySelectorAll('button') ?? []).find((b) => b.textContent === 'Cancelar');
    cancel?.click();

    await vi.waitFor(() => expect(newAppointmentForms()).toHaveLength(0));
  });

  it('abrirlo varias veces deja un solo formulario en el documento', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());

    openNewAppointment(root);
    openNewAppointment(root);
    openNewAppointment(root);

    expect(newAppointmentForms()).toHaveLength(1);
  });
});

describe('agenda-view - agenda diaria (C-20 D4)', () => {
  it('renderiza turno confirmado con badge, bloqueo rayado, hueco libre clickeable; selector de día y leyenda', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);

    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());

    const appt = root.querySelector('.appt');
    expect(appt?.className).toContain('s-confirmado');
    expect(appt?.querySelector('.who')?.textContent).toBe('Lucía Fernández');
    expect(appt?.querySelector('.badge')?.textContent).toBe('Confirmado');
    expect(appt?.getAttribute('title')).toBe('Lucía Fernández · Limpieza');
    expect(root.querySelector('.block')).not.toBeNull();
    expect(root.querySelector('.free-slot')).not.toBeNull();
    expect(root.querySelector('.agenda')?.getAttribute('aria-label')).toBe('Turnos del Lunes 23');
    expect(root.querySelector('[aria-label="Día anterior"]')).not.toBeNull();
    expect(root.querySelector('[aria-label="Día siguiente"]')).not.toBeNull();
    expect(root.querySelector('[aria-label="Semana anterior"]')).toBeNull();
    expect(root.querySelector('.legend')?.textContent).toContain('hora actual');
    expect(root.querySelector('.now-line')).not.toBeNull();
    expect(root.querySelector('.now-line')?.getAttribute('style')).toMatch(/--at/);
  });

  it('la grilla usa filas de 15 min con etiquetas cada 30 min y turnos de 30 min en 2 filas', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);

    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());

    // HOURS = 09:00–13:00 → 16 filas de 15 min, etiquetas solo en las filas pares.
    const timeCells = Array.from(root.querySelectorAll('.agenda-times span'));
    expect(timeCells).toHaveLength(16);
    expect(timeCells.filter((cell) => cell.textContent !== '').map((cell) => cell.textContent)).toEqual([
      '09:00',
      '09:30',
      '10:00',
      '10:30',
      '11:00',
      '11:30',
      '12:00',
      '12:30',
    ]);
    expect(root.querySelectorAll('.agenda-col .line')).toHaveLength(16);

    // APPOINTMENT 10:00–10:30 → fila 4, dos filas de alto (mockup day.html: --span:2).
    const style = root.querySelector('.appt')?.getAttribute('style') ?? '';
    expect(style).toContain('--start:4');
    expect(style).toContain('--span:2');

    // Bloqueo 11:00–12:00 → fila 8, cuatro filas; línea del ahora 10:30 → fila 6.
    expect(root.querySelector('.block')?.getAttribute('style')).toContain('--start:8;--span:4');
    expect(root.querySelector('.now-line')?.getAttribute('style')).toContain('--at:6');

    // Huecos libres 09:00–10:00, 10:30–11:00 y 12:00–13:00 en filas de 15 min.
    expect(Array.from(root.querySelectorAll('.free-slot')).map((slot) => slot.getAttribute('style'))).toEqual([
      '--start:0',
      '--start:6',
      '--start:12',
    ]);
  });

  it('apertura el formulario desde el hueco libre clickeable', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.free-slot')).not.toBeNull());

    root.querySelector('.free-slot')?.dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(document.querySelector('dialog#nuevo-turno')).not.toBeNull());
  });

  it('navega día siguiente', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());
    expect(root.textContent).toContain('Agenda del Lunes 23');

    root.querySelector('[aria-label="Día siguiente"]')?.dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(root.textContent).toContain('Agenda del Martes 24'));
  });

  it('navega día anterior', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());
    expect(root.textContent).toContain('Agenda del Lunes 23');

    root.querySelector('[aria-label="Día anterior"]')?.dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(root.textContent).toContain('Agenda del Domingo 22'));
  });
});

describe('agenda-view - agenda semanal (C-20)', () => {
  async function switchToWeek(root: HTMLElement): Promise<void> {
    const weekToggle = Array.from(root.querySelectorAll('button')).find((button) => button.textContent === 'Semana');
    expect(weekToggle).toBeDefined();
    weekToggle?.dispatchEvent(new Event('click', { bubbles: true }));
    await vi.waitFor(() => expect(root.querySelector('.week-grid')).not.toBeNull());
  }

  it('organiza los días de la semana con turnos por día', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);

    await switchToWeek(root);

    expect(root.querySelectorAll('.week-col')).toHaveLength(7);
    expect(root.querySelector('.week-col[aria-label="Lunes 23"] .appt')).not.toBeNull();
    expect(root.querySelector('.week-col[aria-label="Viernes 27"] .empty')).not.toBeNull();
    expect(root.querySelector('.week-col[aria-label="Domingo 29"] .empty')).not.toBeNull();
    expect(root.querySelector('[aria-label="Semana anterior"]')).not.toBeNull();
    expect(root.textContent).toContain('Agenda de la semana del 23/11/2026 al 29/11/2026');
  });

  it('cada columna usa la misma grilla de 15 min: turno de 30 min en 2 filas', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);

    await switchToWeek(root);

    const column = root.querySelector('.week-col[aria-label="Lunes 23"]');
    expect(column?.querySelectorAll('.agenda-times span')).toHaveLength(16);
    expect(column?.querySelectorAll('.agenda-col .line')).toHaveLength(16);
    const style = column?.querySelector('.appt')?.getAttribute('style') ?? '';
    expect(style).toContain('--start:4');
    expect(style).toContain('--span:2');
  });

  it('navega semana siguiente', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);
    await switchToWeek(root);

    root.querySelector('[aria-label="Semana siguiente"]')?.dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(root.textContent).toContain('Agenda de la semana del 30/11/2026 al 06/12/2026'));
  });
});

describe('agenda-view - flujo de detalle del turno (C-20 D8)', () => {
  it('clic en turno abre el <dialog> nativo con el estado del turno', async () => {
    const root = createAgendaView({ gateway: makeGateway(), clock: CLOCK });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());

    root.querySelector('.appt')?.dispatchEvent(new Event('click', { bubbles: true }));

    const dialog = document.querySelector('dialog#agenda-detail') as HTMLDialogElement;
    expect(dialog).not.toBeNull();
    expect(dialog.textContent).toContain('Lucía Fernández');
    expect(dialog.textContent).toContain('Confirmado');
  });

  it('flujo de cancelación OK: confirma y cierra el detalle', async () => {
    const cancelAppointment = vi.fn(async (): Promise<HttpResult<AppointmentDto>> => ({ ok: true, data: APPOINTMENT }));
    const root = createAgendaView({ gateway: makeGateway({ cancelAppointment }), clock: CLOCK });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());

    root.querySelector('.appt')?.dispatchEvent(new Event('click', { bubbles: true }));
    const dialog = document.querySelector('dialog#agenda-detail') as HTMLDialogElement;
    const cancelButton = Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === 'Cancelar turno');
    cancelButton?.dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(document.querySelector('.confirm-dialog')).not.toBeNull());
    document.querySelector('.confirm-dialog button[data-confirm="accept"]')?.dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(cancelAppointment).toHaveBeenCalledWith(APPOINTMENT.id));
    await vi.waitFor(() => expect(document.querySelector('dialog#agenda-detail')?.hasAttribute('open')).toBe(false));
  });

  it('flujo de reprogramar a slot libre OK: reprograma al slot seleccionado', async () => {
    const rescheduleAppointment = vi.fn(async (): Promise<HttpResult<AppointmentDto>> => ({ ok: true, data: APPOINTMENT }));
    const root = createAgendaView({ gateway: makeGateway({ rescheduleAppointment }), clock: CLOCK });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());

    root.querySelector('.appt')?.dispatchEvent(new Event('click', { bubbles: true }));
    const dialog = document.querySelector('dialog#agenda-detail') as HTMLDialogElement;
    const reprogButton = Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === 'Reprogramar');
    reprogButton?.dispatchEvent(new Event('click', { bubbles: true }));

    const rescheduleDialog = document.querySelector('dialog#agenda-reschedule');
    await vi.waitFor(() => expect(rescheduleDialog).not.toBeNull());
    const slot = document.querySelector<HTMLButtonElement>('dialog#agenda-reschedule .slot');
    expect(slot).not.toBeNull();
    slot?.dispatchEvent(new Event('click', { bubbles: true }));
    expect(slot?.getAttribute('aria-pressed')).toBe('true');
    const reprogSubmit = Array.from(document.querySelectorAll('dialog#agenda-reschedule button')).find(
      (button) => button.textContent === 'Reprogramar',
    );
    reprogSubmit?.dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(rescheduleAppointment).toHaveBeenCalledWith(APPOINTMENT.id, SLOT.startsAt, false));
    await vi.waitFor(() => expect(document.querySelector('dialog#agenda-reschedule')?.hasAttribute('open')).toBe(false));
  });

  it('Marcar atendido solo si startsAt <= ahora y lo marca', async () => {
    const completeAppointment = vi.fn(async (): Promise<HttpResult<AppointmentDto>> => ({ ok: true, data: APPOINTMENT }));
    const root = createAgendaView({ gateway: makeGateway({ completeAppointment }), clock: CLOCK });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());

    root.querySelector('.appt')?.dispatchEvent(new Event('click', { bubbles: true }));
    const dialog = document.querySelector('dialog#agenda-detail') as HTMLDialogElement;
    const markButton = Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === 'Marcar atendido');
    expect(markButton?.hasAttribute('disabled')).toBe(false);
    markButton?.dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(completeAppointment).toHaveBeenCalledWith(APPOINTMENT.id));
  });

  it('Marcar antes del inicio deshabilita marcar resultado', async () => {
    const root = createAgendaView({
      gateway: makeGateway(),
      clock: () => new Date(dayBoundsUtc(DATE).start.getTime() + 9 * 60_000), // 9:00, antes del inicio a las 10:00
    });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());

    root.querySelector('.appt')?.dispatchEvent(new Event('click', { bubbles: true }));
    const dialog = document.querySelector('dialog#agenda-detail') as HTMLDialogElement;
    expect(Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === 'Marcar atendido')?.hasAttribute('disabled')).toBe(true);
    expect(Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === 'Marcar ausente')?.hasAttribute('disabled')).toBe(true);
  });

  it('409 amigable al cancelar', async () => {
    const cancelAppointment = vi.fn(
      async (): Promise<HttpResult<AppointmentDto>> => ({
        ok: false,
        error: { kind: 'problem', status: 409, code: 'APPOINTMENT_SLOT_TAKEN', errors: [] },
      }),
    );
    const root = createAgendaView({ gateway: makeGateway({ cancelAppointment }), clock: CLOCK });
    mount(root);
    await vi.waitFor(() => expect(root.querySelector('.appt')).not.toBeNull());

    root.querySelector('.appt')?.dispatchEvent(new Event('click', { bubbles: true }));
    const dialog = document.querySelector('dialog#agenda-detail') as HTMLDialogElement;
    Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === 'Cancelar turno')?.dispatchEvent(
      new Event('click', { bubbles: true }),
    );
    document.querySelector('.confirm-dialog button[data-confirm="accept"]')?.dispatchEvent(new Event('click', { bubbles: true }));

    await vi.waitFor(() => expect(document.body.textContent).toContain('Ese horario ya está ocupado, eligí otro'));
  });
});
