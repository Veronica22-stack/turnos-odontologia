import { describe, expect, it, vi } from 'vitest';
import type { Session, SessionState } from '../core/auth/session';
import { redirectIfAuthenticated, requireAuthenticated, requireSession, rootRedirect } from './guards';
import type { LayoutRoute } from './safe-next';
import type { RouteContext } from './router';

const routes: LayoutRoute[] = [
  { path: '/agenda', layout: 'private' },
  { path: '/pacientes', layout: 'private' },
  { path: '/reservar', layout: 'public' },
];

const ROMERO = { userId: 'u1', role: 'DENTIST', mustChangePassword: false };

function fakeSession(initial: SessionState, resolvesTo?: SessionState) {
  let state = initial;
  const ensureLoaded = vi.fn(async () => {
    await Promise.resolve();
    if (resolvesTo !== undefined) {
      state = resolvesTo;
    }
  });
  const session: Pick<Session, 'get' | 'ensureLoaded'> = { get: () => state, ensureLoaded };
  return { session, ensureLoaded };
}

function ctx(path: string, search = ''): RouteContext {
  return { path, params: {}, query: new URLSearchParams(search) };
}

describe('requireSession', () => {
  it('anonimo en /agenda redirige con replace a /login?next=%2Fagenda', async () => {
    const { session } = fakeSession({ status: 'anonymous' });

    expect(await requireSession(session)(ctx('/agenda'))).toEqual({
      redirect: '/login?next=%2Fagenda',
      replace: true,
    });
  });

  it('conserva la ruta actual en next (otro destino)', async () => {
    const { session } = fakeSession({ status: 'anonymous' });

    expect(await requireSession(session)(ctx('/pacientes'))).toEqual({
      redirect: '/login?next=%2Fpacientes',
      replace: true,
    });
  });

  it('unknown espera ensureLoaded y decide con el resultado', async () => {
    const { session, ensureLoaded } = fakeSession(
      { status: 'unknown' },
      { status: 'authenticated', user: ROMERO },
    );

    const result = await requireSession(session)(ctx('/agenda'));

    expect(ensureLoaded).toHaveBeenCalledTimes(1);
    expect(result).toEqual({ allow: true });
  });

  it('unknown que resuelve anonimo redirige', async () => {
    const { session } = fakeSession({ status: 'unknown' }, { status: 'anonymous' });

    expect(await requireSession(session)(ctx('/agenda'))).toMatchObject({ redirect: '/login?next=%2Fagenda' });
  });

  it('autenticado permite', async () => {
    const { session } = fakeSession({ status: 'authenticated', user: ROMERO });

    expect(await requireSession(session)(ctx('/agenda'))).toEqual({ allow: true });
  });
});

describe('redirectIfAuthenticated', () => {
  const authed = { status: 'authenticated', user: ROMERO } as const;

  it('autenticado con next valido va a next', async () => {
    const { session } = fakeSession(authed);

    expect(await redirectIfAuthenticated(session, () => routes)(ctx('/login', 'next=%2Fpacientes'))).toEqual({
      redirect: '/pacientes',
      replace: true,
    });
  });

  it.each([
    ['sin next', ''],
    ['next //evil', 'next=%2F%2Fevil.example%2Fagenda'],
    ['next javascript', 'next=javascript%3Aalert(1)'],
    ['next publico', 'next=%2Freservar'],
  ])('autenticado %s va a /agenda', async (_name, search) => {
    const { session } = fakeSession(authed);

    expect(await redirectIfAuthenticated(session, () => routes)(ctx('/login', search))).toEqual({
      redirect: '/agenda',
      replace: true,
    });
  });

  it('anonimo permite ver el login', async () => {
    const { session } = fakeSession({ status: 'anonymous' });

    expect(await redirectIfAuthenticated(session, () => routes)(ctx('/login'))).toEqual({ allow: true });
  });

  it('unknown espera la sesion antes de decidir', async () => {
    const { session } = fakeSession({ status: 'unknown' }, authed);

    expect(await redirectIfAuthenticated(session, () => routes)(ctx('/login'))).toMatchObject({ redirect: '/agenda' });
  });
});

describe('rootRedirect', () => {
  it('anonimo va a /reservar', async () => {
    const { session } = fakeSession({ status: 'anonymous' });

    expect(await rootRedirect(session)(ctx('/'))).toEqual({ redirect: '/reservar', replace: true });
  });

  it('autenticado va a /agenda', async () => {
    const { session } = fakeSession({ status: 'authenticated', user: ROMERO });

    expect(await rootRedirect(session)(ctx('/'))).toEqual({ redirect: '/agenda', replace: true });
  });

  it('unknown espera la sesion', async () => {
    const { session } = fakeSession({ status: 'unknown' }, { status: 'authenticated', user: ROMERO });

    expect(await rootRedirect(session)(ctx('/'))).toEqual({ redirect: '/agenda', replace: true });
  });
});

describe('cambio de contrasena obligatorio (C-06)', () => {
  const mustChange = { status: 'authenticated', user: { ...ROMERO, mustChangePassword: true } } as const;

  it('requireSession con cambio pendiente redirige (replace) a /cambiar-contrasena conservando next', async () => {
    const { session } = fakeSession(mustChange);

    expect(await requireSession(session, () => routes)(ctx('/pacientes'))).toEqual({
      redirect: '/cambiar-contrasena?next=%2Fpacientes',
      replace: true,
    });
  });

  it('requireSession con cambio resuelto permite', async () => {
    const { session } = fakeSession({ status: 'authenticated', user: ROMERO });

    expect(await requireSession(session, () => routes)(ctx('/pacientes'))).toEqual({ allow: true });
  });

  it('requireSession anonimo sigue yendo a /login?next=', async () => {
    const { session } = fakeSession({ status: 'anonymous' });

    expect(await requireSession(session, () => routes)(ctx('/agenda'))).toEqual({
      redirect: '/login?next=%2Fagenda',
      replace: true,
    });
  });

  it('redirectIfAuthenticated con cambio pendiente va a /cambiar-contrasena con next valido', async () => {
    const { session } = fakeSession(mustChange);

    expect(await redirectIfAuthenticated(session, () => routes)(ctx('/login', 'next=%2Fpacientes'))).toEqual({
      redirect: '/cambiar-contrasena?next=%2Fpacientes',
      replace: true,
    });
  });

  it('redirectIfAuthenticated con cambio pendiente y next invalido omite next', async () => {
    const { session } = fakeSession(mustChange);

    expect(await redirectIfAuthenticated(session, () => routes)(ctx('/login', 'next=%2F%2Fevil.example'))).toEqual({
      redirect: '/cambiar-contrasena',
      replace: true,
    });
  });
});

describe('requireAuthenticated', () => {
  it('anonimo va a /login', async () => {
    const { session } = fakeSession({ status: 'anonymous' });

    expect(await requireAuthenticated(session)(ctx('/cambiar-contrasena'))).toEqual({
      redirect: '/login',
      replace: true,
    });
  });

  it('conserva next valido al ir a /login', async () => {
    const { session } = fakeSession({ status: 'anonymous' });

    expect(await requireAuthenticated(session, () => routes)(ctx('/cambiar-contrasena', 'next=%2Fagenda'))).toEqual({
      redirect: '/login?next=%2Fagenda',
      replace: true,
    });
  });

  it('autenticado con cambio pendiente permite (sin bucle)', async () => {
    const { session } = fakeSession({ status: 'authenticated', user: { ...ROMERO, mustChangePassword: true } });

    expect(await requireAuthenticated(session)(ctx('/cambiar-contrasena'))).toEqual({ allow: true });
  });

  it('autenticado voluntario tambien permite', async () => {
    const { session } = fakeSession({ status: 'authenticated', user: ROMERO });

    expect(await requireAuthenticated(session)(ctx('/cambiar-contrasena'))).toEqual({ allow: true });
  });
});
