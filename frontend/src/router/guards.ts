import type { Session } from '../core/auth/session';
import type { Guard } from './router';
import { safeNext, type LayoutRoute } from './safe-next';

type SessionReader = Pick<Session, 'get' | 'ensureLoaded'>;

const DEFAULT_PRIVATE_HOME = '/agenda';
const PUBLIC_HOME = '/reservar';

async function isAuthenticated(session: SessionReader): Promise<boolean> {
  await session.ensureLoaded();
  return session.get().status === 'authenticated';
}

const CHANGE_PASSWORD_PATH = '/cambiar-contrasena';

type RoutesGetter = () => readonly LayoutRoute[];

function withNext(path: string, next: string | null): string {
  return next === null ? path : `${path}?next=${encodeURIComponent(next)}`;
}

function mustChangePassword(session: SessionReader): boolean {
  const state = session.get();
  return state.status === 'authenticated' && state.user.mustChangePassword;
}

/**
 * Rutas privadas: sin sesion redirige a /login conservando el destino en `next`; con cambio de
 * contrasena pendiente (C-04: `mustChangePassword`) redirige a /cambiar-contrasena. Es solo UX:
 * la barrera real es el 403 `PASSWORD_CHANGE_REQUIRED` del servidor.
 */
export function requireSession(session: SessionReader, getRoutes?: RoutesGetter): Guard {
  return async (context) => {
    if (!(await isAuthenticated(session))) {
      return { redirect: `/login?next=${encodeURIComponent(context.path)}`, replace: true };
    }
    if (mustChangePassword(session)) {
      const next = getRoutes === undefined ? context.path : safeNext(context.path, getRoutes());
      return { redirect: withNext(CHANGE_PASSWORD_PATH, next), replace: true };
    }
    return { allow: true };
  };
}

/** /cambiar-contrasena: solo exige sesion (redirigir por `mustChangePassword` aqui seria un bucle). */
export function requireAuthenticated(session: SessionReader, getRoutes?: RoutesGetter): Guard {
  return async (context) => {
    if (await isAuthenticated(session)) {
      return { allow: true };
    }
    const raw = context.query.get('next');
    const next = getRoutes === undefined ? null : safeNext(raw, getRoutes());
    return { redirect: withNext('/login', next), replace: true };
  };
}

/** /login: con sesion activa vuelve a `next` (ruta privada interna) o a /agenda; con cambio pendiente va al cambio. */
export function redirectIfAuthenticated(session: SessionReader, getRoutes: RoutesGetter): Guard {
  return async (context) => {
    if (!(await isAuthenticated(session))) {
      return { allow: true };
    }
    const next = safeNext(context.query.get('next'), getRoutes());
    if (mustChangePassword(session)) {
      return { redirect: withNext(CHANGE_PASSWORD_PATH, next), replace: true };
    }
    return { redirect: next ?? DEFAULT_PRIVATE_HOME, replace: true };
  };
}

/** `/`: autenticado va a la agenda; anonimo a la reserva publica. */
export function rootRedirect(session: SessionReader): Guard {
  return async () => ({
    redirect: (await isAuthenticated(session)) ? DEFAULT_PRIVATE_HOME : PUBLIC_HOME,
    replace: true,
  });
}
