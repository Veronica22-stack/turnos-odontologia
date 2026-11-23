import { describe, expect, it, vi } from 'vitest';
import type { SessionState } from '../core/auth/session';
import type { SettingsGateway } from '../features/settings/settings-gateway';
import { safeNext } from '../router/safe-next';
import type { RouteContext, RouteDef } from '../router/router';
import type { BookingGateway } from '../features/booking/booking-types';
import { createRoutes, type RoutesDeps } from './routes';

const BOOKING_GATEWAY: BookingGateway = {
  listServices: async () => ({ kind: 'ok', services: [{ id: 's-1', name: 'Limpieza', description: null, durationMin: 30 }] }),
  availability: async () => ({ kind: 'ok', slots: [] }),
  book: async () => ({ kind: 'unknown-outcome' }),
};

const SETTINGS_GATEWAY = {
  getPractice: async () => ({
    ok: true,
    data: {
      id: 'p1',
      name: 'Consultorio',
      timezone: 'America/Argentina/Buenos_Aires',
      contactEmail: null,
      contactPhone: null,
      address: null,
      slotGranularityMin: 15,
      minNoticeMin: 0,
      maxAdvanceDays: 30,
      reminderHoursBefore: 24,
    },
  }),
  getWorkingHours: async () => ({ ok: true, data: [] }),
  getServices: async () => ({ ok: true, data: [] }),
  getBlocks: async () => ({ ok: true, data: [] }),
} as unknown as SettingsGateway;

function deps(state: SessionState = { status: 'anonymous' }, settings: SettingsGateway = SETTINGS_GATEWAY): RoutesDeps {
  return {
    session: { get: () => state, ensureLoaded: async () => {}, signIn: () => {}, endAfterPasswordChange: () => {} },
    gateway: { login: async () => ({ ok: false, failure: { kind: 'connection' } }), changePassword: async () => ({ ok: true }) },
    settings,
    booking: BOOKING_GATEWAY,
    replaceUrl: () => {},
    goBack: () => {},
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
    ['/turno/:token', 'Gestionar turno'],
    ['/login', 'Ingresar'],
    ['/agenda', 'Agenda'],
    ['/pacientes', 'Pacientes'],
    ['/configuracion', 'Configuración'],
  ])('la vista de %s tiene el h1 "%s"', (path, heading) => {
    const content = byPath(routes, path).view(ctx(path));

    expect(content.querySelector('h1')?.textContent).toBe(heading);
  });

  it('/reservar usa la vista real de reserva (no el placeholder) con titulo "Reservar turno"', async () => {
    const getServices = vi.spyOn(BOOKING_GATEWAY, 'listServices');
    const { routes: r } = createRoutes(deps());
    const route = byPath(r, '/reservar');

    const content = route.view(ctx('/reservar'));
    await Promise.resolve();
    await Promise.resolve();

    expect(route.title).toBe('Reservar turno');
    expect(route.layout).toBe('public');
    expect(content.querySelector('.placeholder')).toBeNull();
    expect(getServices).toHaveBeenCalledTimes(1);
    expect(content.textContent).not.toContain('La reserva llega en los changes');
  });

  it('/reservar/confirmado usa la vista real: sin datos en memoria muestra la confirmacion generica', () => {
    const route = byPath(routes, '/reservar/confirmado');

    const content = route.view(ctx('/reservar/confirmado'));

    expect(route.title).toBe('Reserva confirmada');
    expect(content.querySelector('h1')?.textContent).toBe('Revisá tu email');
    expect(content.querySelector('.placeholder')).toBeNull();
    expect(content.querySelector('a[href="/reservar"]')).not.toBeNull();
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

describe('/configuracion operativa (C-19)', () => {
  const authenticated = (mustChangePassword: boolean): SessionState => ({
    status: 'authenticated',
    user: { userId: 'u1', role: 'DENTIST', mustChangePassword },
  });

  it('con sesión autenticada muestra la vista de cuatro secciones, no el placeholder', async () => {
    const { routes } = createRoutes(deps(authenticated(false)));

    const content = byPath(routes, '/configuracion').view(ctx('/configuracion'));
    document.body.replaceChildren(content);

    expect(content.querySelector('h1')?.textContent).toBe('Configuración');
    await vi.waitFor(() => expect(content.querySelectorAll('.settings-card')).toHaveLength(4));
    expect(content.querySelector('.placeholder')).toBeNull();
  });

  it('sin sesión redirige a /login?next=%2Fconfiguracion', async () => {
    const { routes } = createRoutes(deps());

    const result = await byPath(routes, '/configuracion').guard?.(ctx('/configuracion'));

    expect(result).toEqual({ redirect: '/login?next=%2Fconfiguracion', replace: true });
  });

  it('con cambio de contraseña pendiente redirige a /cambiar-contrasena?next=%2Fconfiguracion', async () => {
    const { routes } = createRoutes(deps(authenticated(true)));

    const result = await byPath(routes, '/configuracion').guard?.(ctx('/configuracion'));

    expect(result).toEqual({ redirect: '/cambiar-contrasena?next=%2Fconfiguracion', replace: true });
  });

  it('con fallos de carga muestra errores por sección sin impedir las demás', async () => {
    const settings = {
      getPractice: async () => ({ ok: false, failure: { kind: 'connection' } }),
      getWorkingHours: async () => ({ ok: false, failure: { kind: 'connection' } }),
      getServices: async () => ({ ok: false, failure: { kind: 'connection' } }),
      getBlocks: async () => ({ ok: false, failure: { kind: 'connection' } }),
    } as unknown as SettingsGateway;
    const { routes } = createRoutes(deps(authenticated(false), settings));

    const content = byPath(routes, '/configuracion').view(ctx('/configuracion'));
    document.body.replaceChildren(content);

    await vi.waitFor(() => expect(content.querySelectorAll('.settings-card [role="alert"]')).toHaveLength(4));
    expect(content.querySelectorAll('.settings-card')).toHaveLength(4);
    expect(content.textContent).toContain('Reintentar');
  });
});
