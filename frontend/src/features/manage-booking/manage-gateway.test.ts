import { describe, expect, it } from 'vitest';
import { createHttpClient, type FetchFn } from '../../core/http/client';
import { cookieCsrfTransport } from '../../core/http/transport';
import { createManageGateway } from './manage-gateway';

const TOKEN = 'tok_Abc-123_xyz456789012345';

interface Call {
  readonly url: string;
  readonly init: RequestInit;
}

interface Reply {
  readonly status?: number;
  readonly body?: unknown;
  readonly rawBody?: string;
  readonly headers?: Record<string, string>;
  readonly problem?: boolean;
  readonly throws?: 'network' | 'timeout';
}

function setup(reply: Reply, cookies = 'XSRF-TOKEN=csrf-abc'): { gateway: ReturnType<typeof createManageGateway>; calls: Call[] } {
  const calls: Call[] = [];
  const fetchFn: FetchFn = async (url, init) => {
    calls.push({ url, init });
    if (reply.throws === 'network') {
      throw new TypeError('network');
    }
    if (reply.throws === 'timeout') {
      await new Promise<never>((_resolve, reject) => {
        init.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
      });
    }
    const contentType = reply.problem === true ? 'application/problem+json' : 'application/json';
    const payload = reply.rawBody ?? (reply.body === undefined ? null : JSON.stringify(reply.body));
    return new Response(payload, {
      status: reply.status ?? 200,
      headers: { 'Content-Type': contentType, ...(reply.headers ?? {}) },
    });
  };
  const client = createHttpClient({
    fetch: fetchFn,
    transport: cookieCsrfTransport({ readCookies: () => cookies }),
    timeoutMs: 5,
  });
  return { gateway: createManageGateway({ client }), calls };
}

function problem(status: number, code: string, headers?: Record<string, string>): Reply {
  return { status, problem: true, body: { code, title: 'T', detail: 'D' }, ...(headers ? { headers } : {}) };
}

const VIEW = {
  bookingId: '7b1e3a52-1111-4222-8333-444455556666',
  serviceName: 'Limpieza',
  practiceName: 'Consultorio La Muela',
  startsAt: '2026-11-24T13:30:00Z',
  endsAt: '2026-11-24T14:15:00Z',
  status: 'CONFIRMED',
  cancellable: true,
  reschedulable: true,
};

const BOOKING = {
  serviceName: 'Limpieza',
  practiceName: 'Consultorio La Muela',
  startsAt: '2026-11-24T13:30:00Z',
  endsAt: '2026-11-24T14:15:00Z',
  status: 'CONFIRMED',
  cancellable: true,
  reschedulable: true,
};

describe('view', () => {
  it('pide GET /api/v1/public/bookings/<token> y decodifica el turno sin el bookingId', async () => {
    const { gateway, calls } = setup({ body: VIEW });

    const result = await gateway.view(TOKEN);

    expect(calls[0]?.url).toBe(`/api/v1/public/bookings/${TOKEN}`);
    expect(calls[0]?.init.method).toBe('GET');
    expect(result).toEqual({ kind: 'ok', booking: BOOKING });
    expect(JSON.stringify(result)).not.toContain(VIEW.bookingId);
  });

  it('codifica el token como segmento de ruta', async () => {
    const { gateway, calls } = setup({ body: VIEW });

    await gateway.view('a/b c');

    expect(calls[0]?.url).toBe('/api/v1/public/bookings/a%2Fb%20c');
  });

  it('incluye serviceId solo si vino en la respuesta', async () => {
    const withId = await setup({ body: { ...VIEW, serviceId: 's-2' } }).gateway.view(TOKEN);
    const withoutId = await setup({ body: VIEW }).gateway.view(TOKEN);

    expect(withId).toMatchObject({ kind: 'ok', booking: { serviceId: 's-2' } });
    expect(withoutId.kind === 'ok' && 'serviceId' in withoutId.booking).toBe(false);
  });

  it('acepta practiceName vacío', async () => {
    const result = await setup({ body: { ...VIEW, practiceName: '' } }).gateway.view(TOKEN);

    expect(result).toMatchObject({ kind: 'ok', booking: { practiceName: '' } });
  });

  it.each([
    ['status desconocido', { ...VIEW, status: 'PENDING' }],
    ['instante sin zona', { ...VIEW, startsAt: '2026-11-24T10:30:00' }],
    ['banderas no booleanas', { ...VIEW, cancellable: 'yes' }],
    ['falta el nombre de la prestación', { ...VIEW, serviceName: undefined }],
    ['bookingId ausente', { ...VIEW, bookingId: undefined }],
  ])('un cuerpo inválido (%s) se trata como conexión', async (_label, body) => {
    expect(await setup({ body }).gateway.view(TOKEN)).toEqual({ kind: 'connection' });
  });

  it.each([
    ['INVALID_OR_EXPIRED_TOKEN', problem(404, 'INVALID_OR_EXPIRED_TOKEN')],
    ['otro código 404', problem(404, 'NOT_FOUND')],
  ])('404 (%s) -> invalid', async (_label, reply) => {
    expect(await setup(reply).gateway.view(TOKEN)).toEqual({ kind: 'invalid' });
  });

  it('429 con Retry-After -> rate-limited con los segundos', async () => {
    const result = await setup(problem(429, 'RATE_LIMITED', { 'Retry-After': '30' })).gateway.view(TOKEN);

    expect(result).toEqual({ kind: 'rate-limited', retryAfterSeconds: 30 });
  });

  it('429 sin Retry-After -> rate-limited sin segundos', async () => {
    expect(await setup(problem(429, 'RATE_LIMITED')).gateway.view(TOKEN)).toEqual({ kind: 'rate-limited' });
  });

  it.each([
    ['500', problem(500, 'INTERNAL_ERROR')],
    ['red', { throws: 'network' } as Reply],
    ['timeout', { throws: 'timeout' } as Reply],
    ['401', { status: 401 } as Reply],
  ])('%s -> connection', async (_label, reply) => {
    expect(await setup(reply).gateway.view(TOKEN)).toEqual({ kind: 'connection' });
  });
});

describe('cancel', () => {
  it('envía DELETE a la misma ruta con X-XSRF-TOKEN y devuelve el turno cancelado', async () => {
    const cancelled = { ...VIEW, status: 'CANCELLED', cancellable: false, reschedulable: false };
    const { gateway, calls } = setup({ body: cancelled });

    const result = await gateway.cancel(TOKEN);

    expect(calls[0]?.url).toBe(`/api/v1/public/bookings/${TOKEN}`);
    expect(calls[0]?.init.method).toBe('DELETE');
    expect(new Headers(calls[0]?.init.headers).get('X-XSRF-TOKEN')).toBe('csrf-abc');
    expect(result).toEqual({
      kind: 'done',
      booking: { ...BOOKING, status: 'CANCELLED', cancellable: false, reschedulable: false },
    });
  });

  it.each([
    ['404 -> invalid', problem(404, 'INVALID_OR_EXPIRED_TOKEN'), { kind: 'invalid' }],
    ['422 BOOKING_MODIFICATION_NOT_ALLOWED -> not-modifiable', problem(422, 'BOOKING_MODIFICATION_NOT_ALLOWED'), { kind: 'not-modifiable' }],
    ['403 CSRF_REQUIRED -> stale-page', problem(403, 'CSRF_REQUIRED'), { kind: 'stale-page' }],
    ['429 -> rate-limited', problem(429, 'RATE_LIMITED', { 'Retry-After': '12' }), { kind: 'rate-limited', retryAfterSeconds: 12 }],
    ['500 -> unknown-outcome', problem(500, 'INTERNAL_ERROR'), { kind: 'unknown-outcome' }],
    ['red -> unknown-outcome', { throws: 'network' } as Reply, { kind: 'unknown-outcome' }],
    ['timeout -> unknown-outcome', { throws: 'timeout' } as Reply, { kind: 'unknown-outcome' }],
    ['200 ilegible -> unknown-outcome', { rawBody: 'no es json' } as Reply, { kind: 'unknown-outcome' }],
    ['200 con cuerpo inválido -> unknown-outcome', { body: { status: 'CANCELLED' } } as Reply, { kind: 'unknown-outcome' }],
    ['422 de otro código -> unknown-outcome', problem(422, 'OTHER'), { kind: 'unknown-outcome' }],
  ])('%s', async (_label, reply, expected) => {
    expect(await setup(reply).gateway.cancel(TOKEN)).toEqual(expected);
  });
});

describe('reschedule', () => {
  it('envía POST .../reschedule con X-XSRF-TOKEN y el startsAt exacto, sin reformatear', async () => {
    const moved = { ...VIEW, startsAt: '2026-11-26T14:15:00Z', endsAt: '2026-11-26T15:00:00Z' };
    const { gateway, calls } = setup({ body: moved });

    const result = await gateway.reschedule(TOKEN, '2026-11-26T14:15:00Z');

    expect(calls[0]?.url).toBe(`/api/v1/public/bookings/${TOKEN}/reschedule`);
    expect(calls[0]?.init.method).toBe('POST');
    expect(calls[0]?.init.body).toBe('{"startsAt":"2026-11-26T14:15:00Z"}');
    expect(new Headers(calls[0]?.init.headers).get('X-XSRF-TOKEN')).toBe('csrf-abc');
    expect(result).toEqual({
      kind: 'done',
      booking: { ...BOOKING, startsAt: '2026-11-26T14:15:00Z', endsAt: '2026-11-26T15:00:00Z' },
    });
  });

  it('conserva el instante con offset tal como llegó', async () => {
    const { gateway, calls } = setup({ body: VIEW });

    await gateway.reschedule(TOKEN, '2026-11-26T11:15:00-03:00');

    expect(calls[0]?.init.body).toBe('{"startsAt":"2026-11-26T11:15:00-03:00"}');
  });

  it.each([
    ['409 SLOT_TAKEN -> slot-unavailable', problem(409, 'SLOT_TAKEN'), { kind: 'slot-unavailable' }],
    ['422 APPOINTMENT_IN_PAST -> slot-unavailable', problem(422, 'APPOINTMENT_IN_PAST'), { kind: 'slot-unavailable' }],
    ['422 BOOKING_MODIFICATION_NOT_ALLOWED -> not-modifiable', problem(422, 'BOOKING_MODIFICATION_NOT_ALLOWED'), { kind: 'not-modifiable' }],
    ['400 VALIDATION_FAILED -> unknown-outcome', problem(400, 'VALIDATION_FAILED'), { kind: 'unknown-outcome' }],
    ['500 -> unknown-outcome', problem(500, 'INTERNAL_ERROR'), { kind: 'unknown-outcome' }],
    ['red -> unknown-outcome', { throws: 'network' } as Reply, { kind: 'unknown-outcome' }],
    ['timeout -> unknown-outcome', { throws: 'timeout' } as Reply, { kind: 'unknown-outcome' }],
    ['404 -> invalid', problem(404, 'INVALID_OR_EXPIRED_TOKEN'), { kind: 'invalid' }],
    ['403 CSRF_REQUIRED -> stale-page', problem(403, 'CSRF_REQUIRED'), { kind: 'stale-page' }],
    ['429 -> rate-limited', problem(429, 'RATE_LIMITED', { 'Retry-After': '40' }), { kind: 'rate-limited', retryAfterSeconds: 40 }],
  ])('%s', async (_label, reply, expected) => {
    expect(await setup(reply).gateway.reschedule(TOKEN, '2026-11-26T14:15:00Z')).toEqual(expected);
  });
});
