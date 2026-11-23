import { describe, expect, it } from 'vitest';
import { createHttpClient, type FetchFn } from '../../core/http/client';
import { cookieCsrfTransport } from '../../core/http/transport';
import { createBookingGateway } from './booking-gateway';
import type { BookingRequest } from './booking-types';
import { EMPTY_BOOKING_FORM } from './validation';

interface Call {
  readonly url: string;
  readonly init: RequestInit;
}

interface Reply {
  readonly status?: number;
  readonly body?: unknown;
  readonly headers?: Record<string, string>;
  readonly problem?: boolean;
  readonly throws?: 'network' | 'timeout';
}

function setup(reply: Reply | (() => Promise<Reply>)): { gateway: ReturnType<typeof createBookingGateway>; calls: Call[] } {
  const calls: Call[] = [];
  const fetchFn: FetchFn = async (url, init) => {
    calls.push({ url, init });
    const current = typeof reply === 'function' ? await reply() : reply;
    if (current.throws === 'network') {
      throw new TypeError('network');
    }
    if (current.throws === 'timeout') {
      throw new DOMException('aborted', 'AbortError');
    }
    const contentType = current.problem === true ? 'application/problem+json' : 'application/json';
    return new Response(current.body === undefined ? null : JSON.stringify(current.body), {
      status: current.status ?? 200,
      headers: { 'Content-Type': contentType, ...(current.headers ?? {}) },
    });
  };
  const client = createHttpClient({
    fetch: fetchFn,
    transport: cookieCsrfTransport({ readCookies: () => 'XSRF-TOKEN=tok-123' }),
  });
  return { gateway: createBookingGateway({ client }), calls };
}

function problem(status: number, code: string, extra: Record<string, unknown> = {}, headers?: Record<string, string>): Reply {
  return { status, problem: true, body: { code, title: 'T', detail: 'D', ...extra }, ...(headers ? { headers } : {}) };
}

const SERVICE_ID = '7b1e3a52-1111-4222-8333-444455556666';

describe('listServices', () => {
  it('pide GET /api/v1/public/services y decodifica las prestaciones', async () => {
    const { gateway, calls } = setup({
      body: [
        { id: SERVICE_ID, name: 'Limpieza', description: null, durationMin: 30 },
        { id: 'b', name: 'Consulta', description: 'Primera visita', durationMin: 45 },
      ],
    });

    const result = await gateway.listServices();

    expect(calls[0]?.url).toBe('/api/v1/public/services');
    expect(calls[0]?.init.method).toBe('GET');
    expect(result).toEqual({
      kind: 'ok',
      services: [
        { id: SERVICE_ID, name: 'Limpieza', description: null, durationMin: 30 },
        { id: 'b', name: 'Consulta', description: 'Primera visita', durationMin: 45 },
      ],
    });
  });

  it('devuelve una lista vacia como ok sin prestaciones', async () => {
    const { gateway } = setup({ body: [] });

    expect(await gateway.listServices()).toEqual({ kind: 'ok', services: [] });
  });

  it.each([
    ['sin id', [{ name: 'X', description: null, durationMin: 30 }]],
    ['duracion no entera', [{ id: 'a', name: 'X', description: null, durationMin: 30.5 }]],
    ['no es una lista', { id: 'a' }],
  ])('un cuerpo invalido (%s) es un error de conexion', async (_label, body) => {
    const { gateway } = setup({ body });

    expect(await gateway.listServices()).toEqual({ kind: 'connection' });
  });

  it('429 con Retry-After', async () => {
    const { gateway } = setup(problem(429, 'RATE_LIMITED', {}, { 'Retry-After': '30' }));

    expect(await gateway.listServices()).toEqual({ kind: 'rate-limited', retryAfterSeconds: 30 });
  });

  it('429 sin Retry-After no inventa la espera', async () => {
    const { gateway } = setup(problem(429, 'RATE_LIMITED'));

    expect(await gateway.listServices()).toEqual({ kind: 'rate-limited' });
  });

  it.each([{ throws: 'network' as const }, { throws: 'timeout' as const }, problem(500, 'INTERNAL_ERROR')])(
    'red, timeout y 5xx son conexion',
    async (reply) => {
      const { gateway } = setup(reply);

      expect(await gateway.listServices()).toEqual({ kind: 'connection' });
    },
  );
});

describe('availability', () => {
  it('pide la query exacta y decodifica los slots con instantes validos', async () => {
    const slots = [
      { startsAt: '2026-11-24T12:30:00Z', endsAt: '2026-11-24T13:00:00Z' },
      { startsAt: '2026-11-24T13:00:00Z', endsAt: '2026-11-24T13:30:00Z' },
    ];
    const { gateway, calls } = setup({ body: { status: 'OK', slots } });

    const result = await gateway.availability(SERVICE_ID, '2026-11-24');

    expect(calls[0]?.url).toBe(`/api/v1/public/availability?serviceId=${SERVICE_ID}&date=2026-11-24`);
    expect(result).toEqual({ kind: 'ok', slots });
  });

  it('codifica los valores de la query', async () => {
    const { gateway, calls } = setup({ body: { status: 'OK', slots: [] } });

    await gateway.availability('a b&c', '2026-11-24');

    expect(calls[0]?.url).toBe('/api/v1/public/availability?serviceId=a%20b%26c&date=2026-11-24');
  });

  it('un dia sin horarios es ok con lista vacia', async () => {
    const { gateway } = setup({ body: { status: 'OK', slots: [] } });

    expect(await gateway.availability(SERVICE_ID, '2026-11-24')).toEqual({ kind: 'ok', slots: [] });
  });

  it('NO_ACTIVE_SERVICES es no-services', async () => {
    const { gateway } = setup({ body: { status: 'NO_ACTIVE_SERVICES', slots: [] } });

    expect(await gateway.availability(SERVICE_ID, '2026-11-24')).toEqual({ kind: 'no-services' });
  });

  it('un instante sin zona es una respuesta invalida (conexion)', async () => {
    const { gateway } = setup({
      body: { status: 'OK', slots: [{ startsAt: '2026-11-24T12:30:00', endsAt: '2026-11-24T13:00:00Z' }] },
    });

    expect(await gateway.availability(SERVICE_ID, '2026-11-24')).toEqual({ kind: 'connection' });
  });

  it.each([
    problem(404, 'SERVICE_NOT_FOUND'),
    problem(422, 'SERVICE_INACTIVE'),
  ])('servicio inexistente o inactivo es service-gone', async (reply) => {
    const { gateway } = setup(reply);

    expect(await gateway.availability(SERVICE_ID, '2026-11-24')).toEqual({ kind: 'service-gone' });
  });

  it('429 es rate-limited con la espera informada', async () => {
    const { gateway } = setup(problem(429, 'RATE_LIMITED', {}, { 'Retry-After': '40' }));

    expect(await gateway.availability(SERVICE_ID, '2026-11-24')).toEqual({ kind: 'rate-limited', retryAfterSeconds: 40 });
  });

  it.each([
    problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'date', code: 'ISO_LOCAL_DATE' }] }),
    { throws: 'timeout' as const },
    { throws: 'network' as const },
  ])('400, timeout y red son conexion', async (reply) => {
    const { gateway } = setup(reply);

    expect(await gateway.availability(SERVICE_ID, '2026-11-24')).toEqual({ kind: 'connection' });
  });
});

describe('book', () => {
  const REQUEST: BookingRequest = {
    serviceId: SERVICE_ID,
    startsAt: '2026-11-24T12:30:00Z',
    form: {
      ...EMPTY_BOOKING_FORM,
      firstName: 'Ana',
      lastName: 'Pérez',
      dni: '30.456.789',
      phone: '11 2345 6789',
      email: 'ana@ejemplo.com',
      privacyAccepted: true,
    },
  };

  const CREATED = {
    bookingId: '9f1c0000-0000-4000-8000-000000000001',
    serviceName: 'Limpieza',
    startsAt: '2026-11-24T13:30:00Z',
    endsAt: '2026-11-24T14:15:00Z',
    status: 'CONFIRMED',
    practiceName: 'Consultorio La Muela',
  };

  it('envia el POST con CSRF y el cuerpo exacto, sin fin ni duracion', async () => {
    const { gateway, calls } = setup({ status: 201, body: CREATED });

    await gateway.book(REQUEST);

    const call = calls[0];
    expect(call?.url).toBe('/api/v1/public/bookings');
    expect(call?.init.method).toBe('POST');
    expect(new Headers(call?.init.headers).get('X-XSRF-TOKEN')).toBe('tok-123');
    expect(JSON.parse(String(call?.init.body))).toEqual({
      serviceId: SERVICE_ID,
      startsAt: '2026-11-24T12:30:00Z',
      firstName: 'Ana',
      lastName: 'Pérez',
      dni: '30.456.789',
      phone: '11 2345 6789',
      email: 'ana@ejemplo.com',
      website: '',
      privacyAccepted: true,
    });
  });

  it('el honeypot viaja con el valor que tenga', async () => {
    const { gateway, calls } = setup({ status: 201, body: CREATED });

    await gateway.book({ ...REQUEST, form: { ...REQUEST.form, website: 'http://spam.example' } });

    expect((JSON.parse(String(calls[0]?.init.body)) as Record<string, unknown>)['website']).toBe('http://spam.example');
  });

  it('201 es confirmed sin exponer bookingId', async () => {
    const { gateway } = setup({ status: 201, body: CREATED });

    const result = await gateway.book(REQUEST);

    expect(result).toEqual({
      kind: 'confirmed',
      serviceName: 'Limpieza',
      startsAt: '2026-11-24T13:30:00Z',
      endsAt: '2026-11-24T14:15:00Z',
      practiceName: 'Consultorio La Muela',
    });
    expect(JSON.stringify(result)).not.toContain('9f1c0000');
  });

  it('practiceName nulo se convierte en vacio', async () => {
    const { gateway } = setup({ status: 201, body: { ...CREATED, practiceName: null } });

    expect(await gateway.book(REQUEST)).toMatchObject({ kind: 'confirmed', practiceName: '' });
  });

  it('200 ACCEPTED del honeypot es accepted', async () => {
    const { gateway } = setup({
      status: 200,
      body: { bookingId: null, serviceName: null, startsAt: null, endsAt: null, status: 'ACCEPTED', practiceName: null },
    });

    expect(await gateway.book(REQUEST)).toEqual({ kind: 'accepted' });
  });

  it('201 sin startsAt es un resultado desconocido', async () => {
    const { gateway } = setup({ status: 201, body: { ...CREATED, startsAt: null } });

    expect(await gateway.book(REQUEST)).toEqual({ kind: 'unknown-outcome' });
  });

  it.each([
    [problem(409, 'SLOT_TAKEN'), 'slot-unavailable'],
    [problem(422, 'BOOKING_TOO_SOON'), 'slot-unavailable'],
    [problem(422, 'BOOKING_BEYOND_HORIZON'), 'slot-unavailable'],
    [problem(422, 'APPOINTMENT_IN_PAST'), 'slot-unavailable'],
    [problem(422, 'BOOKING_CONTACT_LIMIT'), 'contact-limit'],
    [problem(404, 'SERVICE_NOT_FOUND'), 'service-gone'],
    [problem(422, 'SERVICE_INACTIVE'), 'service-gone'],
    [problem(403, 'CSRF_REQUIRED'), 'stale-page'],
    [problem(400, 'MALFORMED_REQUEST'), 'unknown-outcome'],
    [problem(500, 'INTERNAL_ERROR', { errorId: 'e-1' }), 'unknown-outcome'],
    [{ throws: 'timeout' as const }, 'unknown-outcome'],
    [{ throws: 'network' as const }, 'unknown-outcome'],
  ])('clasifica el rechazo %# como %s', async (reply, kind) => {
    const { gateway } = setup(reply);

    expect(await gateway.book(REQUEST)).toEqual({ kind });
  });

  it('400 VALIDATION_FAILED devuelve solo los campos conocidos', async () => {
    const { gateway } = setup(
      problem(400, 'VALIDATION_FAILED', {
        errors: [
          { field: 'email', code: 'Email' },
          { field: 'dni', code: 'dni length must be between 6 and 9 digits' },
          { field: 'serviceId', code: 'NotNull' },
        ],
      }),
    );

    expect(await gateway.book(REQUEST)).toEqual({
      kind: 'fields',
      errors: [
        { field: 'email', code: 'Email' },
        { field: 'dni', code: 'dni length must be between 6 and 9 digits' },
      ],
    });
  });

  it('400 VALIDATION_FAILED sin campos conocidos es un resultado desconocido', async () => {
    const { gateway } = setup(problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'serviceId', code: 'NotNull' }] }));

    expect(await gateway.book(REQUEST)).toEqual({ kind: 'unknown-outcome' });
  });

  it('429 conserva la espera informada', async () => {
    const { gateway } = setup(problem(429, 'RATE_LIMITED', {}, { 'Retry-After': '40' }));

    expect(await gateway.book(REQUEST)).toEqual({ kind: 'rate-limited', retryAfterSeconds: 40 });
  });

  it('nunca refleja title ni detail del servidor', async () => {
    const { gateway } = setup(problem(409, 'SLOT_TAKEN', { title: 'Texto del servidor', detail: 'Otro texto' }));

    expect(JSON.stringify(await gateway.book(REQUEST))).not.toContain('servidor');
  });
});
