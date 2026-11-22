import { describe, expect, it, vi } from 'vitest';
import { createHttpClient } from '../../core/http/client';
import { cookieCsrfTransport } from '../../core/http/transport';
import { createAuthGateway } from './auth-gateway';

const PROBLEM = 'application/problem+json';

interface Recorded {
  url: string;
  init: RequestInit;
}

function setup(respond: (call: Recorded) => Response) {
  const calls: Recorded[] = [];
  const onUnauthorized = vi.fn();
  const onSessionExpired = vi.fn();
  const client = createHttpClient({
    fetch: async (url, init) => {
      const call = { url, init };
      calls.push(call);
      return respond(call);
    },
    transport: cookieCsrfTransport({ readCookies: () => 'XSRF-TOKEN=tok' }),
    onUnauthorized,
  });
  return { gateway: createAuthGateway({ client, onSessionExpired }), calls, onUnauthorized, onSessionExpired };
}

function problem(
  status: number,
  code: string,
  extra: Record<string, unknown> = {},
  headers: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify({ status, code, ...extra }), {
    status,
    headers: { 'Content-Type': PROBLEM, ...headers },
  });
}

function json(body: string, status = 200): Response {
  return new Response(body, { status, headers: { 'Content-Type': 'application/json' } });
}

function offline(): Response {
  throw new TypeError('offline');
}

const ME = '{"userId":"u1","role":"DENTIST","mustChangePassword":false,"expiresAt":"2026-11-21T22:00:00Z"}';
const CREDENTIALS = { email: 'romero@ejemplo.com', password: 'una contraseña' };

describe('authGateway.login', () => {
  it('envia POST /api/v1/auth/login con el cuerpo JSON exacto y la cabecera CSRF', async () => {
    const { gateway, calls } = setup(() => json(ME));

    await gateway.login(CREDENTIALS);

    expect(calls[0]?.url).toBe('/api/v1/auth/login');
    expect(calls[0]?.init.method).toBe('POST');
    expect(JSON.parse(String(calls[0]?.init.body))).toEqual({ email: 'romero@ejemplo.com', password: 'una contraseña' });
    expect(new Headers(calls[0]?.init.headers).get('X-XSRF-TOKEN')).toBe('tok');
  });

  it('200 valido -> ok con el usuario de la forma real', async () => {
    const { gateway } = setup(() => json(ME));

    expect(await gateway.login(CREDENTIALS)).toEqual({
      ok: true,
      user: { userId: 'u1', role: 'DENTIST', mustChangePassword: false, expiresAt: '2026-11-21T22:00:00Z' },
    });
  });

  it('200 con mustChangePassword true lo conserva', async () => {
    const { gateway } = setup(() => json('{"userId":"u1","role":"DENTIST","mustChangePassword":true}'));

    expect(await gateway.login(CREDENTIALS)).toMatchObject({ ok: true, user: { mustChangePassword: true } });
  });

  it('200 sin userId es una respuesta invalida: falla de conexion', async () => {
    const { gateway } = setup(() => json('{"role":"DENTIST"}'));

    expect(await gateway.login(CREDENTIALS)).toEqual({ ok: false, failure: { kind: 'connection' } });
  });

  it('401 INVALID_CREDENTIALS -> invalid-credentials sin tocar la sesion', async () => {
    const { gateway, onUnauthorized, onSessionExpired } = setup(() => problem(401, 'INVALID_CREDENTIALS'));

    expect(await gateway.login(CREDENTIALS)).toEqual({ ok: false, failure: { kind: 'invalid-credentials' } });
    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it('400 VALIDATION_FAILED -> el mismo invalid-credentials', async () => {
    const { gateway } = setup(() => problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'email', code: 'Email' }] }));

    expect(await gateway.login(CREDENTIALS)).toEqual({ ok: false, failure: { kind: 'invalid-credentials' } });
  });

  it('429 RATE_LIMITED con Retry-After -> locked con los segundos', async () => {
    const { gateway } = setup(() => problem(429, 'RATE_LIMITED', {}, { 'Retry-After': '45' }));

    expect(await gateway.login(CREDENTIALS)).toEqual({ ok: false, failure: { kind: 'locked', retryAfterSeconds: 45 } });
  });

  it('429 sin Retry-After -> locked sin segundos', async () => {
    const { gateway } = setup(() => problem(429, 'RATE_LIMITED'));

    expect(await gateway.login(CREDENTIALS)).toEqual({ ok: false, failure: { kind: 'locked' } });
  });

  it('403 CSRF_REQUIRED -> stale-page', async () => {
    const { gateway } = setup(() => problem(403, 'CSRF_REQUIRED'));

    expect(await gateway.login(CREDENTIALS)).toEqual({ ok: false, failure: { kind: 'stale-page' } });
  });

  it('red, 500 y codigos desconocidos -> connection', async () => {
    expect(await setup(offline).gateway.login(CREDENTIALS)).toEqual({ ok: false, failure: { kind: 'connection' } });
    expect(await setup(() => problem(500, 'INTERNAL_ERROR')).gateway.login(CREDENTIALS)).toEqual({
      ok: false,
      failure: { kind: 'connection' },
    });
    expect(await setup(() => problem(400, 'MALFORMED_REQUEST')).gateway.login(CREDENTIALS)).toEqual({
      ok: false,
      failure: { kind: 'connection' },
    });
  });
});

describe('authGateway.changePassword', () => {
  const INPUT = { currentPassword: 'actual vieja 1', newPassword: 'una contraseña nueva' };

  it('envia POST /api/v1/auth/change-password con ambos campos y CSRF', async () => {
    const { gateway, calls } = setup(() => new Response(null, { status: 200 }));

    await gateway.changePassword(INPUT);

    expect(calls[0]?.url).toBe('/api/v1/auth/change-password');
    expect(calls[0]?.init.method).toBe('POST');
    expect(JSON.parse(String(calls[0]?.init.body))).toEqual(INPUT);
    expect(new Headers(calls[0]?.init.headers).get('X-XSRF-TOKEN')).toBe('tok');
  });

  it.each([[200], [204]])('%s sin cuerpo -> ok', async (status) => {
    const { gateway } = setup(() => new Response(null, { status }));

    expect(await gateway.changePassword(INPUT)).toEqual({ ok: true });
  });

  it('401 INVALID_CREDENTIALS -> current-invalid sin expirar la sesion', async () => {
    const { gateway, onUnauthorized, onSessionExpired } = setup(() => problem(401, 'INVALID_CREDENTIALS'));

    expect(await gateway.changePassword(INPUT)).toEqual({ ok: false, failure: { kind: 'current-invalid' } });
    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it('400 VALIDATION_FAILED con errores por campo -> fields', async () => {
    const { gateway } = setup(() =>
      problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'newPassword', code: 'Size' }] }),
    );

    expect(await gateway.changePassword(INPUT)).toEqual({
      ok: false,
      failure: { kind: 'fields', errors: [{ field: 'newPassword', code: 'Size' }] },
    });
  });

  it('400 VALIDATION_FAILED sin errores por campo -> connection', async () => {
    const { gateway } = setup(() => problem(400, 'VALIDATION_FAILED'));

    expect(await gateway.changePassword(INPUT)).toEqual({ ok: false, failure: { kind: 'connection' } });
  });

  it('401 UNAUTHENTICATED -> expired y onSessionExpired una sola vez', async () => {
    const { gateway, onSessionExpired, onUnauthorized } = setup(() => problem(401, 'UNAUTHENTICATED'));

    expect(await gateway.changePassword(INPUT)).toEqual({ ok: false, failure: { kind: 'expired' } });
    expect(onSessionExpired).toHaveBeenCalledTimes(1);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('403 CSRF_REQUIRED -> stale-page; red -> connection', async () => {
    expect(await setup(() => problem(403, 'CSRF_REQUIRED')).gateway.changePassword(INPUT)).toEqual({
      ok: false,
      failure: { kind: 'stale-page' },
    });
    expect(await setup(offline).gateway.changePassword(INPUT)).toEqual({ ok: false, failure: { kind: 'connection' } });
  });
});
