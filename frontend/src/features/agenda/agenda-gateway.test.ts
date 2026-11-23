import { describe, expect, it, vi } from 'vitest';
import { createHttpClient } from '../../core/http/client';
import { cookieCsrfTransport } from '../../core/http/transport';
import { createAgendaGateway, type AgendaGateway } from './agenda-gateway';
import type { AppointmentDto } from './agenda-types';

const PROBLEM = 'application/problem+json';

interface Recorded {
  url: string;
  init: RequestInit;
}

function setup(respond: (call: Recorded) => Response): { gateway: AgendaGateway; calls: Recorded[] } {
  const calls: Recorded[] = [];
  const client = createHttpClient({
    fetch: async (url, init) => {
      const call = { url, init };
      calls.push(call);
      return respond(call);
    },
    transport: cookieCsrfTransport({ readCookies: () => 'XSRF-TOKEN=tok' }),
    onUnauthorized: vi.fn(),
  });
  return { gateway: createAgendaGateway({ client }), calls };
}

function problem(status: number, code: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ status, code, ...extra }), { status, headers: { 'Content-Type': PROBLEM } });
}

function json(body: string, status = 200): Response {
  return new Response(body, { status, headers: { 'Content-Type': 'application/json' } });
}

const APPOINTMENT: AppointmentDto = {
  id: '11111111-1111-1111-1111-111111111111',
  patientId: '22222222-2222-2222-2222-222222222222',
  serviceId: '33333333-3333-3333-3333-333333333333',
  startsAt: '2026-11-23T13:00:00Z',
  endsAt: '2026-11-23T13:30:00Z',
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

describe('agendaGateway.listAppointments', () => {
  it('GET /appointments?from&to -> 200 con turnos decodificados', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify([APPOINTMENT])));

    const result = await gateway.listAppointments('2026-11-23T00:00:00Z', '2026-11-30T00:00:00Z');

    expect(calls[0]?.url).toBe('/api/v1/appointments?from=2026-11-23T00%3A00%3A00Z&to=2026-11-30T00%3A00%3A00Z');
    expect(calls[0]?.init.method).toBe('GET');
    expect(result).toEqual({ ok: true, data: [APPOINTMENT] });
  });

  it('sin params -> GET /appointments sin query', async () => {
    const { gateway, calls } = setup(() => json('[]'));

    const result = await gateway.listAppointments();

    expect(calls[0]?.url).toBe('/api/v1/appointments');
    expect(result).toEqual({ ok: true, data: [] });
  });
});

describe('agendaGateway.createAppointment', () => {
  it('POST /appointments con el payload -> 201 con turno decodificado', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify(APPOINTMENT), 201));
    const payload = { patientId: 'p1', serviceId: 's1', startsAt: '2026-11-23T13:00:00Z', overbooking: true };

    const result = await gateway.createAppointment(payload);

    expect(calls[0]?.url).toBe('/api/v1/appointments');
    expect(calls[0]?.init.method).toBe('POST');
    expect(JSON.parse(String(calls[0]?.init.body))).toEqual(payload);
    expect(result).toEqual({ ok: true, data: APPOINTMENT });
  });
});

describe('errores de turno', () => {
  const conflicts: ReadonlyArray<readonly [string, number]> = [
    ['APPOINTMENT_SLOT_TAKEN', 409],
    ['APPOINTMENT_SLOT_BLOCKED', 409],
    ['OUTSIDE_WORKING_HOURS', 422],
    ['APPOINTMENT_IN_PAST', 422],
    ['SERVICE_NOT_FOUND', 404],
    ['PATIENT_NOT_FOUND', 404],
    ['PATIENT_INACTIVE', 409],
    ['APPOINTMENT_NOT_STARTED', 422],
    ['INVALID_STATUS_TRANSITION', 409],
  ];

  it.each(conflicts)('%s -> {ok:false, error:{code}}', async (code, status) => {
    const { gateway } = setup(() => problem(status, code));

    const result = await gateway.createAppointment({ patientId: 'p1', serviceId: 's1', startsAt: '2026-11-23T13:00:00Z' });

    expect(result).toEqual({ ok: false, error: { kind: 'problem', status, code, errors: [] } });
  });

  it('no expone instantes ni datos del turno ocupante', async () => {
    const { gateway } = setup(() =>
      problem(409, 'APPOINTMENT_SLOT_TAKEN', {
        ocupante: { id: APPOINTMENT.id, startsAt: '2026-11-23T10:00:00Z', endsAt: '2026-11-23T10:30:00Z' },
      }),
    );

    const result = await gateway.createAppointment({ patientId: 'p1', serviceId: 's1', startsAt: '2026-11-23T13:00:00Z' });

    if (!result.ok) {
      expect(result.error).toEqual({ kind: 'problem', status: 409, code: 'APPOINTMENT_SLOT_TAKEN', errors: [] });
      expect(result.error).not.toHaveProperty('ocupante');
      expect(result.error).not.toHaveProperty('startsAt');
    } else {
      expect.unreachable('el conflictivo no puede ser ok');
    }
  });
});

describe('agendaGateway.cancelAppointment', () => {
  it('POST /appointments/{id}/cancel -> turno CANCELLED decodificado', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify(APPOINTMENT)));

    const result = await gateway.cancelAppointment(APPOINTMENT.id);

    expect(calls[0]?.url).toBe('/api/v1/appointments/11111111-1111-1111-1111-111111111111/cancel');
    expect(calls[0]?.init.method).toBe('POST');
    expect(result).toEqual({ ok: true, data: APPOINTMENT });
  });
});

describe('agendaGateway.rescheduleAppointment', () => {
  it('POST /appointments/{id}/reschedule con startsAt y overbooking', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify(APPOINTMENT)));

    const result = await gateway.rescheduleAppointment(APPOINTMENT.id, '2026-11-23T15:00:00Z', true);

    expect(calls[0]?.url).toBe('/api/v1/appointments/11111111-1111-1111-1111-111111111111/reschedule');
    expect(JSON.parse(String(calls[0]?.init.body))).toEqual({ startsAt: '2026-11-23T15:00:00Z', overbooking: true });
    expect(result).toEqual({ ok: true, data: APPOINTMENT });
  });

  it('overbooking omitido cuando no se pasa', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify(APPOINTMENT)));

    await gateway.rescheduleAppointment(APPOINTMENT.id, '2026-11-23T15:00:00Z');

    expect(JSON.parse(String(calls[0]?.init.body))).toEqual({ startsAt: '2026-11-23T15:00:00Z' });
  });
});

describe('agendaGateway.completeAppointment / noShowAppointment', () => {
  it.each(['complete', 'no-show'] as const)('POST /appointments/{id}/%s', async (verb) => {
    const { gateway, calls } = setup(() => json(JSON.stringify(APPOINTMENT)));

    const result = verb === 'complete' ? await gateway.completeAppointment('p1') : await gateway.noShowAppointment('p1');

    expect(calls[0]?.url).toBe('/api/v1/appointments/p1/' + verb);
    expect(result).toEqual({ ok: true, data: APPOINTMENT });
  });
});

describe('agendaGateway.listServices', () => {
  it('GET /services -> prestaciones decodificadas', async () => {
    const service = { id: 's1', name: 'Limpieza', description: null, durationMin: 45, active: true, publicVisible: true };
    const { gateway, calls } = setup(() => json(JSON.stringify([service])));

    const result = await gateway.listServices();

    expect(calls[0]?.url).toBe('/api/v1/services');
    expect(result).toEqual({ ok: true, data: [service] });
  });
});

describe('agendaGateway.searchPatients', () => {
  it('GET /patients?q=Lucia -> pacientes activos decodificados', async () => {
    const patient = { id: 'p1', firstName: 'Lucía', lastName: 'Fernández', dni: '30456789', phone: '11 2345 6789', active: true };
    const { gateway, calls } = setup(() => json(JSON.stringify([patient])));

    const result = await gateway.searchPatients('Lucia');

    expect(calls[0]?.url).toBe('/api/v1/patients?q=Lucia');
    expect(result).toEqual({ ok: true, data: [patient] });
  });

  it('sin q -> GET /patients', async () => {
    const { gateway, calls } = setup(() => json('[]'));

    await gateway.searchPatients();

    expect(calls[0]?.url).toBe('/api/v1/patients');
  });
});

describe('agendaGateway.createPatient', () => {
  it('POST /patients -> 201 con paciente decodificado', async () => {
    const patient = { id: 'p1', firstName: 'Lucía', lastName: 'Fernández', dni: '30456789', phone: '11 2345 6789', active: true };
    const { gateway, calls } = setup(() => json(JSON.stringify(patient), 201));

    const result = await gateway.createPatient({
      firstName: 'Lucía',
      lastName: 'Fernández',
      dni: '30456789',
      phone: '11 2345 6789',
      email: 'lucia@ejemplo.com',
    });

    expect(calls[0]?.url).toBe('/api/v1/patients');
    expect(calls[0]?.init.method).toBe('POST');
    expect(JSON.parse(String(calls[0]?.init.body))).toEqual({
      firstName: 'Lucía',
      lastName: 'Fernández',
      dni: '30456789',
      phone: '11 2345 6789',
      email: 'lucia@ejemplo.com',
    });
    expect(result).toEqual({ ok: true, data: patient });
  });
});

describe('agendaGateway.getWorkingHours', () => {
  it('GET /working-hours -> tramos decodificados', async () => {
    const hours = [{ weekday: 1, startTime: '09:00', endTime: '13:00' }];
    const { gateway, calls } = setup(() => json(JSON.stringify(hours)));

    const result = await gateway.getWorkingHours();

    expect(calls[0]?.url).toBe('/api/v1/working-hours');
    expect(result).toEqual({ ok: true, data: hours });
  });
});

describe('agendaGateway.listAvailability', () => {
  it('GET /availability?serviceId&date -> slots libres decodificados', async () => {
    const slots = [{ startsAt: '2026-11-23T13:30:00Z', endsAt: '2026-11-23T13:45:00Z' }];
    const { gateway, calls } = setup(() => json(JSON.stringify({ status: 'OK', slots })));

    const result = await gateway.listAvailability('s1', '2026-11-23');

    expect(calls[0]?.url).toBe('/api/v1/availability?serviceId=s1&date=2026-11-23');
    expect(result).toEqual({ ok: true, data: slots });
  });
});

describe('agendaGateway.listBlocks', () => {
  it('GET /schedule-blocks -> bloqueos decodificados', async () => {
    const blocks = [{ startsAt: '2026-11-23T11:00:00Z', endsAt: '2026-11-23T12:00:00Z' }];
    const { gateway, calls } = setup(() => json(JSON.stringify(blocks)));

    const result = await gateway.listBlocks();

    expect(calls[0]?.url).toBe('/api/v1/schedule-blocks');
    expect(result).toEqual({ ok: true, data: blocks });
  });
});
