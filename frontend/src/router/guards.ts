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

/** Rutas privadas: sin sesion redirige a /login conservando el destino en `next`. */
export function requireSession(session: SessionReader): Guard {
  return async (context) =>
    (await isAuthenticated(session))
      ? { allow: true }
      : { redirect: `/login?next=${encodeURIComponent(context.path)}`, replace: true };
}

/** /login: con sesion activa vuelve a `next` (si es una ruta privada interna) o a /agenda. */
export function redirectIfAuthenticated(session: SessionReader, getRoutes: () => readonly LayoutRoute[]): Guard {
  return async (context) => {
    if (!(await isAuthenticated(session))) {
      return { allow: true };
    }
    const next = safeNext(context.query.get('next'), getRoutes());
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
