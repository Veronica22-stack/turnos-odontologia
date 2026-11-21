import { describe, expect, it } from 'vitest';
import type { Session, SessionState } from '../core/auth/session';
import type { RouteContext, RouteDef } from '../router/router';
import { createRoutes } from './routes';

const session: Pick<Session, 'get' | 'ensureLoaded'> = {
  get: (): SessionState => ({ status: 'anonymous' }),
  ensureLoaded: async () => {},
};

function ctx(path: string, params: Record<string, string> = {}): RouteContext {
  return { path, params, query: new URLSearchParams() };
}

function byPath(routes: readonly RouteDef[], path: string): RouteDef {
  const route = routes.find((r) => r.path === path);
  if (route === undefined) {
    throw new Error(`falta la ruta ${path}`);
  }
  return route;
}

describe('createRoutes', () => {
  const { routes, notFound } = createRoutes(session);

  it('declara las rutas publicas y privadas de KB 03 con su layout', () => {
    const layouts = Object.fromEntries(routes.map((r) => [r.path, r.layout]));

    expect(layouts).toEqual({
      '/': 'public',
      '/reservar': 'public',
      '/reservar/confirmado': 'public',
      '/turno/:token': 'public',
      '/login': 'public',
      '/agenda': 'private',
      '/pacientes': 'private',
      '/configuracion': 'private',
    });
  });

  it('protege las privadas con guard, y / y /login tambien tienen guard', () => {
    for (const path of ['/agenda', '/pacientes', '/configuracion', '/', '/login']) {
      expect(byPath(routes, path).guard).toBeTypeOf('function');
    }
    for (const path of ['/reservar', '/reservar/confirmado', '/turno/:token']) {
      expect(byPath(routes, path).guard).toBeUndefined();
    }
  });

  it.each([
    ['/reservar', 'Reservá tu turno'],
    ['/reservar/confirmado', 'Reserva confirmada'],
    ['/turno/:token', 'Gestionar turno'],
    ['/login', 'Ingresar'],
    ['/agenda', 'Agenda'],
    ['/pacientes', 'Pacientes'],
    ['/configuracion', 'Configuración'],
  ])('la vista de %s tiene el h1 "%s"', (path, heading) => {
    const content = byPath(routes, path).view(ctx(path));

    expect(content.querySelector('h1')?.textContent).toBe(heading);
  });

  it('la vista de gestion de turno no muestra el token', () => {
    const content = byPath(routes, '/turno/:token').view(ctx('/turno/AbC-123_x', { token: 'AbC-123_x' }));

    expect(content.textContent).not.toContain('AbC-123_x');
  });

  it('la vista no encontrada tiene h1 y enlace al inicio', () => {
    const content = notFound.view(ctx('/no-existe'));

    expect(content.querySelector('h1')?.textContent).toBe('Página no encontrada');
    expect(content.querySelector('a')?.getAttribute('href')).toBe('/');
    expect(content.textContent).not.toContain('no-existe');
  });
});

describe('vista de login - avisos de sesion', () => {
  function loginView(state: SessionState): HTMLElement {
    const { routes: r } = createRoutes({ get: () => state, ensureLoaded: async () => {} });
    return byPath(r, '/login').view(ctx('/login'));
  }

  it('muestra el aviso de sesion vencida con role=status', () => {
    const content = loginView({ status: 'anonymous', notice: 'expired' });

    expect(content.querySelector('[role="status"]')?.textContent).toBe('Tu sesión venció. Ingresá de nuevo.');
  });

  it('muestra un aviso generico de conexion cuando el servidor no respondio', () => {
    const content = loginView({ status: 'anonymous', notice: 'offline' });

    expect(content.querySelector('[role="status"]')?.textContent).toBe(
      'Sin conexión con el servidor. Reintentá en unos segundos.',
    );
  });

  it('no muestra aviso si no hay motivo', () => {
    expect(loginView({ status: 'anonymous' }).querySelector('[role="status"]')).toBeNull();
    expect(loginView({ status: 'unknown' }).querySelector('[role="status"]')).toBeNull();
  });
});
