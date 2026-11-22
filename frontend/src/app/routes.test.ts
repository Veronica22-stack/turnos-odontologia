import { describe, expect, it } from 'vitest';
import type { SessionState } from '../core/auth/session';
import { safeNext } from '../router/safe-next';
import type { RouteContext, RouteDef } from '../router/router';
import { createRoutes, type RoutesDeps } from './routes';

function deps(state: SessionState = { status: 'anonymous' }): RoutesDeps {
  return {
    session: { get: () => state, ensureLoaded: async () => {}, signIn: () => {}, endAfterPasswordChange: () => {} },
    gateway: { login: async () => ({ ok: false, failure: { kind: 'connection' } }), changePassword: async () => ({ ok: true }) },
    navigate: () => {},
    onLogout: () => {},
  };
}

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
  const { routes, notFound } = createRoutes(deps());

  it('declara las rutas publicas y privadas de KB 03 con su layout', () => {
    const layouts = Object.fromEntries(routes.map((r) => [r.path, r.layout]));

    expect(layouts).toEqual({
      '/': 'public',
      '/reservar': 'public',
      '/reservar/confirmado': 'public',
      '/turno/:token': 'public',
      '/login': 'public',
      '/cambiar-contrasena': 'public',
      '/agenda': 'private',
      '/pacientes': 'private',
      '/configuracion': 'private',
    });
  });

  it('protege las privadas con guard, y / y /login tambien tienen guard', () => {
    for (const path of ['/agenda', '/pacientes', '/configuracion', '/', '/login', '/cambiar-contrasena']) {
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

describe('vistas de acceso (C-06)', () => {
  const { routes } = createRoutes(deps());

  it('/login usa la vista real: formulario con email y contraseña, no el placeholder', () => {
    const { routes: r } = createRoutes(deps());

    const content = byPath(r, '/login').view(ctx('/login'));

    expect(content.querySelector('form input#email')).not.toBeNull();
    expect(content.querySelector('form input#password')).not.toBeNull();
    expect(content.querySelector('.placeholder')).toBeNull();
  });

  it('declara la ruta change-password con titulo y layout public', () => {
    const route = byPath(routes, '/cambiar-contrasena');

    expect(route.name).toBe('change-password');
    expect(route.layout).toBe('public');
    expect(route.title).toBe('Cambiar contraseña');
  });

  it('/cambiar-contrasena usa la vista real: tres campos de contraseña', () => {
    const { routes: r } = createRoutes(
      deps({ status: 'authenticated', user: { userId: 'u1', role: 'DENTIST', mustChangePassword: true } }),
    );

    const content = byPath(r, '/cambiar-contrasena').view(ctx('/cambiar-contrasena'));

    expect(content.querySelectorAll('input[type="password"]')).toHaveLength(3);
    expect(content.querySelector('h1')?.textContent).toBe('Elegí una contraseña nueva');
  });

  it('safeNext no acepta /cambiar-contrasena como destino (es publica)', () => {
    expect(safeNext('/cambiar-contrasena', routes)).toBeNull();
    expect(safeNext('/agenda', routes)).toBe('/agenda');
  });
});
