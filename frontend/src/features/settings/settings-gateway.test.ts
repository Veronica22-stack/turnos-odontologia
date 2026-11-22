import { describe, expect, it, vi } from 'vitest';
import { createHttpClient } from '../../core/http/client';
import { cookieCsrfTransport } from '../../core/http/transport';
import { createSettingsGateway } from './settings-gateway';

const PROBLEM = 'application/problem+json';

interface Recorded {
  url: string;
  init: RequestInit;
}

function setup(respond: (call: Recorded) => Response) {
  const calls: Recorded[] = [];
  const onUnauthorized = vi.fn();
  const client = createHttpClient({
    fetch: async (url, init) => {
      const call = { url, init };
      calls.push(call);
      return respond(call);
    },
    transport: cookieCsrfTransport({ readCookies: () => 'XSRF-TOKEN=tok' }),
    onUnauthorized,
  });
  return { gateway: createSettingsGateway({ client }), calls, onUnauthorized };
}

function json(body: string, status = 200): Response {
  return new Response(body, { status, headers: { 'Content-Type': 'application/json' } });
}

function problem(status: number, code: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ status, code, ...extra }), {
    status,
    headers: { 'Content-Type': PROBLEM },
  });
}

function noContent(): Response {
  return new Response(null, { status: 204 });
}

function offline(): Response {
  throw new TypeError('offline');
}

function bodyOf(call: Recorded | undefined): unknown {
  return JSON.parse(String(call?.init.body));
}

function csrfOf(call: Recorded | undefined): string | null {
  return new Headers(call?.init.headers).get('X-XSRF-TOKEN');
}

const PRACTICE = {
  id: 'p1',
  name: 'Consultorio Villa Crespo',
  timezone: 'America/Argentina/Buenos_Aires',
  contactEmail: 'recepcion@villacrespo.example',
  contactPhone: '+54 11 4471 0916',
  address: 'Av. Corrientes 3954',
  slotGranularityMin: 15,
  minNoticeMin: 120,
  maxAdvanceDays: 30,
  reminderHoursBefore: 24,
};
const { id: _id, ...PRACTICE_INPUT } = PRACTICE;

const HOURS = [
  { weekday: 1, startTime: '09:00:00', endTime: '13:00:00' },
  { weekday: 3, startTime: '14:00:00', endTime: '18:00:00' },
];

const SERVICE = {
  id: 's1',
  name: 'Limpieza',
  description: 'Sesión de limpieza',
  durationMin: 45,
  active: true,
  publicVisible: true,
};
const { id: _serviceId, active: _active, ...SERVICE_INPUT } = SERVICE;

const BLOCK = {
  id: 'b1',
  startsAt: '2026-03-23T12:00:00Z',
  endsAt: '2026-03-23T14:00:00Z',
  reason: 'Mantenimiento',
  createdAt: '2026-03-20T10:00:00Z',
  updatedAt: '2026-03-20T10:00:00Z',
  affectedAppointments: [],
};
const BLOCK_INPUT = { startsAt: BLOCK.startsAt, endsAt: BLOCK.endsAt, reason: BLOCK.reason };

describe('settingsGateway - rutas, cuerpos y CSRF (C-19 D2)', () => {
  it('GET /practice/settings decodifica la configuración', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify(PRACTICE)));

    expect(await gateway.getPractice()).toEqual({ ok: true, data: PRACTICE });
    expect(calls[0]?.url).toBe('/api/v1/practice/settings');
    expect(calls[0]?.init.method).toBe('GET');
    // El transporte solo agrega CSRF a métodos no seguros (GET no lo necesita).
    expect(csrfOf(calls[0])).toBeNull();
  });

  it('PUT /practice/settings envía el objeto sin id', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify(PRACTICE)));

    expect(await gateway.savePractice(PRACTICE_INPUT)).toEqual({ ok: true, data: PRACTICE });
    expect(calls[0]?.url).toBe('/api/v1/practice/settings');
    expect(calls[0]?.init.method).toBe('PUT');
    expect(bodyOf(calls[0])).toEqual(PRACTICE_INPUT);
    expect(bodyOf(calls[0])).not.toHaveProperty('id');
    expect(csrfOf(calls[0])).toBe('tok');
  });

  it('GET /working-hours normaliza las horas', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify(HOURS)));

    expect(await gateway.getWorkingHours()).toEqual({
      ok: true,
      data: [
        { weekday: 1, startTime: '09:00', endTime: '13:00' },
        { weekday: 3, startTime: '14:00', endTime: '18:00' },
      ],
    });
    expect(calls[0]?.url).toBe('/api/v1/working-hours');
    expect(calls[0]?.init.method).toBe('GET');
  });

  it('PUT /working-hours reemplaza la lista completa enviando HH:mm:ss', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify(HOURS)));
    const hours = [
      { weekday: 1, startTime: '09:00', endTime: '13:00' },
      { weekday: 1, startTime: '14:00', endTime: '18:00' },
    ];

    await gateway.saveWorkingHours(hours);

    expect(calls[0]?.url).toBe('/api/v1/working-hours');
    expect(calls[0]?.init.method).toBe('PUT');
    expect(bodyOf(calls[0])).toEqual({
      workingHours: [
        { weekday: 1, startTime: '09:00:00', endTime: '13:00:00' },
        { weekday: 1, startTime: '14:00:00', endTime: '18:00:00' },
      ],
    });
    expect(csrfOf(calls[0])).toBe('tok');
  });

  it('GET /services incluye prestaciones inactivas', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify([SERVICE, { ...SERVICE, id: 's2', active: false }])));

    const result = await gateway.getServices();

    expect(result.ok && result.data).toHaveLength(2);
    expect(result.ok && result.data[1]?.active).toBe(false);
    expect(calls[0]?.url).toBe('/api/v1/services');
    expect(calls[0]?.init.method).toBe('GET');
  });

  it('POST /services crea con el cuerpo exacto', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify(SERVICE), 201));

    expect(await gateway.createService(SERVICE_INPUT)).toEqual({ ok: true, data: SERVICE });
    expect(calls[0]?.url).toBe('/api/v1/services');
    expect(calls[0]?.init.method).toBe('POST');
    expect(bodyOf(calls[0])).toEqual(SERVICE_INPUT);
    expect(csrfOf(calls[0])).toBe('tok');
  });

  it('PUT /services/{id} envía active para reactivar', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify(SERVICE)));
    const input = { ...SERVICE_INPUT, active: true };

    await gateway.updateService('s1', input);

    expect(calls[0]?.url).toBe('/api/v1/services/s1');
    expect(calls[0]?.init.method).toBe('PUT');
    expect(bodyOf(calls[0])).toEqual(input);
  });

  it('DELETE /services/{id} devuelve ok con 204', async () => {
    const { gateway, calls } = setup(noContent);

    expect(await gateway.deleteService('s1')).toEqual({ ok: true, data: undefined });
    expect(calls[0]?.url).toBe('/api/v1/services/s1');
    expect(calls[0]?.init.method).toBe('DELETE');
    expect(calls[0]?.init.body).toBeUndefined();
  });

  it('GET /schedule-blocks construye el filtro from/to', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify([BLOCK])));

    expect(await gateway.getBlocks('2026-03-01T00:00:00Z', '2026-03-31T00:00:00Z')).toEqual({
      ok: true,
      data: [BLOCK],
    });
    expect(calls[0]?.url).toBe('/api/v1/schedule-blocks?from=2026-03-01T00%3A00%3A00Z&to=2026-03-31T00%3A00%3A00Z');
    expect(calls[0]?.init.method).toBe('GET');
  });

  it('GET /schedule-blocks sin filtros no agrega query', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify([])));

    expect(await gateway.getBlocks()).toEqual({ ok: true, data: [] });
    expect(calls[0]?.url).toBe('/api/v1/schedule-blocks');
  });

  it('POST /schedule-blocks envía startsAt, endsAt y reason sin auditoría', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify(BLOCK), 201));

    expect(await gateway.createBlock(BLOCK_INPUT)).toEqual({ ok: true, data: BLOCK });
    expect(calls[0]?.url).toBe('/api/v1/schedule-blocks');
    expect(calls[0]?.init.method).toBe('POST');
    expect(bodyOf(calls[0])).toEqual(BLOCK_INPUT);
    expect(bodyOf(calls[0])).not.toHaveProperty('createdAt');
    expect(csrfOf(calls[0])).toBe('tok');
  });

  it('PUT y DELETE /schedule-blocks/{id} usan la misma ruta y el mismo cuerpo', async () => {
    const put = setup(() => json(JSON.stringify(BLOCK)));
    await put.gateway.updateBlock('b1', BLOCK_INPUT);
    expect(put.calls[0]?.url).toBe('/api/v1/schedule-blocks/b1');
    expect(put.calls[0]?.init.method).toBe('PUT');
    expect(bodyOf(put.calls[0])).toEqual(BLOCK_INPUT);

    const del = setup(noContent);
    expect(await del.gateway.deleteBlock('b1')).toEqual({ ok: true, data: undefined });
    expect(del.calls[0]?.url).toBe('/api/v1/schedule-blocks/b1');
    expect(del.calls[0]?.init.method).toBe('DELETE');
  });

  it('una respuesta inválida se convierte en failure connection', async () => {
    const { gateway } = setup(() => json('{"name":"sin id"}'));

    expect(await gateway.getPractice()).toEqual({ ok: false, failure: { kind: 'connection' } });
  });

  it('un 403 CSRF_REQUIRED se mapea a stale-page', async () => {
    const { gateway } = setup(() => problem(403, 'CSRF_REQUIRED'));

    expect(await gateway.getPractice()).toEqual({ ok: false, failure: { kind: 'stale-page' } });
  });
});

describe('settingsGateway - errores de negocio y de transporte (C-19 D3)', () => {
  it('401 delega en onUnauthorized una sola vez y no expone datos parciales', async () => {
    const { gateway, onUnauthorized } = setup(() => problem(401, 'UNAUTHENTICATED'));

    expect(await gateway.getPractice()).toEqual({ ok: false, failure: { kind: 'connection' } });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('400 VALIDATION_FAILED conserva errores por campo', async () => {
    const errors = [
      { field: 'maxAdvanceDays', code: 'Min' },
      { field: 'name', code: 'NotBlank' },
    ];
    const { gateway } = setup(() => problem(400, 'VALIDATION_FAILED', { errors }));

    expect(await gateway.savePractice(PRACTICE_INPUT)).toEqual({ ok: false, failure: { kind: 'fields', errors } });
  });

  it('400 VALIDATION_FAILED sin errores por campo cae a connection', async () => {
    const { gateway } = setup(() => problem(400, 'VALIDATION_FAILED'));

    expect(await gateway.getPractice()).toEqual({ ok: false, failure: { kind: 'connection' } });
  });

  it('422, 409 y 404 se maplean a su fallo de negocio', async () => {
    expect(await setup(() => problem(422, 'DURATION_NOT_MULTIPLE_OF_GRANULARITY')).gateway.createService(SERVICE_INPUT)).toEqual({
      ok: false,
      failure: { kind: 'duration' },
    });
    expect(await setup(() => problem(409, 'SCHEDULE_BLOCK_OVERLAP')).gateway.createBlock(BLOCK_INPUT)).toEqual({
      ok: false,
      failure: { kind: 'conflict' },
    });
    expect(await setup(() => problem(404, 'SERVICE_NOT_FOUND')).gateway.deleteService('s1')).toEqual({
      ok: false,
      failure: { kind: 'not-found' },
    });
    expect(await setup(() => problem(404, 'SCHEDULE_BLOCK_NOT_FOUND')).gateway.deleteBlock('b1')).toEqual({
      ok: false,
      failure: { kind: 'not-found' },
    });
  });

  it('400 INVALID_RANGE se mapea a range', async () => {
    const { gateway } = setup(() => problem(400, 'INVALID_RANGE'));

    expect(await gateway.createBlock(BLOCK_INPUT)).toEqual({ ok: false, failure: { kind: 'range' } });
  });

  it('red, timeout y respuesta inválida se mapean a connection', async () => {
    expect(await setup(offline).gateway.getServices()).toEqual({ ok: false, failure: { kind: 'connection' } });
    expect(await setup(() => json('{')).gateway.getBlocks()).toEqual({ ok: false, failure: { kind: 'connection' } });

    const client = createHttpClient({
      fetch: (_url, init) =>
        new Promise<Response>((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => reject(new Error('abortada')));
        }),
      transport: cookieCsrfTransport({ readCookies: () => '' }),
      timeoutMs: 5,
    });

    expect(await createSettingsGateway({ client }).getPractice()).toEqual({ ok: false, failure: { kind: 'connection' } });
  });
});
