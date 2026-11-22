import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createHttpClient } from '../http/client';
import { cookieCsrfTransport } from '../http/transport';
import { createSession, type SessionGateway, type SessionResult, type SessionUser } from './session';
import { httpSessionGateway } from './session-gateway';

const ROMERO: SessionUser = { userId: 'u1', role: 'DENTIST', mustChangePassword: false, expiresAt: '2026-11-21T22:00:00Z' };
const ME_BODY = '{"userId":"u1","role":"DENTIST","mustChangePassword":false,"expiresAt":"2026-11-21T22:00:00Z"}';

function gateway(result: SessionResult | (() => Promise<SessionResult>), logout: () => Promise<void> = async () => {}) {
  const me = vi.fn(typeof result === 'function' ? result : async () => result);
  const logoutFn = vi.fn(logout);
  const value: SessionGateway = { me, logout: logoutFn };
  return { gateway: value, me, logout: logoutFn };
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

describe('session - carga inicial', () => {
  it('arranca en unknown y no escribe en localStorage/sessionStorage', async () => {
    const { gateway: g } = gateway({ status: 'anonymous' });
    const session = createSession(g);

    expect(session.get()).toEqual({ status: 'unknown' });
    await session.load();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('me 200 pasa a authenticated', async () => {
    const { gateway: g } = gateway({ status: 'authenticated', user: ROMERO });
    const session = createSession(g);

    await session.load();

    expect(session.get()).toEqual({ status: 'authenticated', user: ROMERO });
  });

  it('401 pasa a anonymous sin aviso', async () => {
    const session = createSession(gateway({ status: 'anonymous' }).gateway);

    await session.load();

    expect(session.get()).toEqual({ status: 'anonymous' });
  });

  it('falla de red pasa a anonymous con aviso offline', async () => {
    const session = createSession(gateway({ status: 'error' }).gateway);

    await session.load();

    expect(session.get()).toEqual({ status: 'anonymous', notice: 'offline' });
  });

  it('dos ensureLoaded concurrentes hacen una sola llamada a me', async () => {
    const { gateway: g, me } = gateway({ status: 'anonymous' });
    const session = createSession(g);

    await Promise.all([session.ensureLoaded(), session.ensureLoaded()]);

    expect(me).toHaveBeenCalledTimes(1);
  });

  it('ensureLoaded no vuelve a consultar cuando ya se conoce el estado', async () => {
    const { gateway: g, me } = gateway({ status: 'anonymous' });
    const session = createSession(g);

    await session.ensureLoaded();
    await session.ensureLoaded();

    expect(me).toHaveBeenCalledTimes(1);
  });
});

describe('session - logout, expiracion y observacion', () => {
  async function authenticated() {
    const g = gateway({ status: 'authenticated', user: ROMERO });
    const session = createSession(g.gateway);
    await session.load();
    return { session, ...g };
  }

  it('logout con exito pasa a anonymous', async () => {
    const { session, logout } = await authenticated();

    await session.logout();

    expect(logout).toHaveBeenCalledTimes(1);
    expect(session.get()).toEqual({ status: 'anonymous' });
  });

  it('logout con error de red tambien pasa a anonymous', async () => {
    const g = gateway({ status: 'authenticated', user: ROMERO }, async () => {
      throw new Error('red');
    });
    const session = createSession(g.gateway);
    await session.load();

    await session.logout();

    expect(session.get()).toEqual({ status: 'anonymous' });
  });

  it('expire pasa a anonymous con aviso expired y una segunda llamada no notifica de nuevo', async () => {
    const { session } = await authenticated();
    const listener = vi.fn();
    session.subscribe(listener);

    session.expire();
    session.expire();

    expect(session.get()).toEqual({ status: 'anonymous', notice: 'expired' });
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('expire no hace nada si no hay sesion autenticada', async () => {
    const session = createSession(gateway({ status: 'anonymous' }).gateway);
    await session.load();

    session.expire();

    expect(session.get()).toEqual({ status: 'anonymous' });
  });

  it('el suscriptor recibe cada cambio y deja de recibirlos al desuscribirse', async () => {
    const session = createSession(gateway({ status: 'authenticated', user: ROMERO }).gateway);
    const listener = vi.fn();
    const unsubscribe = session.subscribe(listener);

    await session.load();
    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenLastCalledWith({ status: 'authenticated', user: ROMERO });

    unsubscribe();
    session.expire();
    expect(listener).toHaveBeenCalledTimes(1);
  });
});

describe('session - signIn y endAfterPasswordChange (C-06)', () => {
  it('signIn desde anonymous con aviso expired pasa a authenticated sin aviso y notifica una vez', async () => {
    const g = gateway({ status: 'authenticated', user: ROMERO });
    const session = createSession(g.gateway);
    await session.load();
    session.expire();
    expect(session.get()).toEqual({ status: 'anonymous', notice: 'expired' });
    const listener = vi.fn();
    session.subscribe(listener);

    session.signIn(ROMERO);

    expect(session.get()).toEqual({ status: 'authenticated', user: ROMERO });
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('signIn no hace llamadas HTTP (me no se vuelve a consultar)', async () => {
    const g = gateway({ status: 'anonymous' });
    const session = createSession(g.gateway);
    await session.load();

    session.signIn({ ...ROMERO, mustChangePassword: true });

    expect(g.me).toHaveBeenCalledTimes(1);
    expect(session.get()).toMatchObject({ status: 'authenticated', user: { mustChangePassword: true } });
  });

  it('endAfterPasswordChange desde authenticated pasa a anonymous con aviso password-changed y notifica', async () => {
    const g = gateway({ status: 'authenticated', user: ROMERO });
    const session = createSession(g.gateway);
    await session.load();
    const listener = vi.fn();
    session.subscribe(listener);

    session.endAfterPasswordChange();

    expect(session.get()).toEqual({ status: 'anonymous', notice: 'password-changed' });
    expect(listener).toHaveBeenCalledTimes(1);
    expect(g.logout).not.toHaveBeenCalled();
  });

  it('endAfterPasswordChange estando anonymous tambien deja el aviso password-changed', async () => {
    const session = createSession(gateway({ status: 'anonymous' }).gateway);
    await session.load();

    session.endAfterPasswordChange();

    expect(session.get()).toEqual({ status: 'anonymous', notice: 'password-changed' });
  });
});

describe('httpSessionGateway', () => {
  function gatewayWith(respond: (url: string, init: RequestInit) => Response) {
    const calls: Array<{ url: string; init: RequestInit }> = [];
    const onUnauthorized = vi.fn();
    const client = createHttpClient({
      fetch: async (url, init) => {
        calls.push({ url, init });
        return respond(url, init);
      },
      transport: cookieCsrfTransport({ readCookies: () => 'XSRF-TOKEN=tok' }),
      onUnauthorized,
    });
    return { gateway: httpSessionGateway(client), calls, onUnauthorized };
  }

  const json = (body: string, status = 200) =>
    new Response(body, { status, headers: { 'Content-Type': 'application/json' } });

  it('me: 200 con la forma real de C-04 -> authenticated (sin id ni fullName)', async () => {
    const { gateway: g, calls } = gatewayWith(() => json(ME_BODY.replace('}', ',"extra":1}')));

    expect(await g.me()).toEqual({ status: 'authenticated', user: ROMERO });
    expect(calls[0]?.url).toBe('/api/v1/auth/me');
  });

  it('me: mustChangePassword true se conserva y su ausencia vale false', async () => {
    const withFlag = gatewayWith(() => json('{"userId":"u1","role":"DENTIST","mustChangePassword":true}'));
    const without = gatewayWith(() => json('{"userId":"u1","role":"DENTIST"}'));

    expect(await withFlag.gateway.me()).toEqual({
      status: 'authenticated',
      user: { userId: 'u1', role: 'DENTIST', mustChangePassword: true },
    });
    expect(await without.gateway.me()).toEqual({
      status: 'authenticated',
      user: { userId: 'u1', role: 'DENTIST', mustChangePassword: false },
    });
  });

  it('me: fullName presente se conserva', async () => {
    const { gateway: g } = gatewayWith(() => json('{"userId":"u1","role":"DENTIST","fullName":"Dra. Romero"}'));

    expect(await g.me()).toEqual({
      status: 'authenticated',
      user: { userId: 'u1', role: 'DENTIST', mustChangePassword: false, fullName: 'Dra. Romero' },
    });
  });

  it('me: 401 -> anonymous sin avisar onUnauthorized (sessionProbe)', async () => {
    const { gateway: g, onUnauthorized } = gatewayWith(() => new Response(null, { status: 401 }));

    expect(await g.me()).toEqual({ status: 'anonymous' });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('me: forma invalida, 5xx y red -> error', async () => {
    expect(await gatewayWith(() => json('{"id":42}')).gateway.me()).toEqual({ status: 'error' });
    expect(await gatewayWith(() => json('{"role":"DENTIST"}')).gateway.me()).toEqual({ status: 'error' });
    expect(await gatewayWith(() => json('{"userId":"u1","role":"DENTIST","mustChangePassword":"si"}')).gateway.me()).toEqual({
      status: 'error',
    });
    expect(await gatewayWith(() => new Response('x', { status: 503 })).gateway.me()).toEqual({ status: 'error' });
    const failing = gatewayWith(() => {
      throw new TypeError('offline');
    });
    expect(await failing.gateway.me()).toEqual({ status: 'error' });
  });

  it('logout hace POST /auth/logout con la cabecera CSRF', async () => {
    const { gateway: g, calls } = gatewayWith(() => new Response(null, { status: 204 }));

    await g.logout();

    expect(calls[0]?.url).toBe('/api/v1/auth/logout');
    expect(calls[0]?.init.method).toBe('POST');
    expect(new Headers(calls[0]?.init.headers).get('X-XSRF-TOKEN')).toBe('tok');
  });
});
