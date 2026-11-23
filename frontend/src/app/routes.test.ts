import { describe, expect, it, vi } from 'vitest';
import type { SessionState } from '../core/auth/session';
import type { AgendaGateway } from '../features/agenda/agenda-gateway';
import type { SettingsGateway } from '../features/settings/settings-gateway';
import { safeNext } from '../router/safe-next';
import type { RouteContext, RouteDef } from '../router/router';
import { createRoutes, type RoutesDeps } from './routes';

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

const AGENDA_GATEWAY = {
  listAppointments: async () => ({ ok: true, data: [] }),
  createAppointment: async () => ({ ok: true, data: [] }),
  cancelAppointment: async () => ({ ok: true, data: [] }),
  rescheduleAppointment: async () => ({ ok: true, data: [] }),
  completeAppointment: async () => ({ ok: true, data: [] }),
  noShowAppointment: async () => ({ ok: true, data: [] }),
  listServices: async () => ({ ok: true, data: [] }),
  searchPatients: async () => ({ ok: true, data: [] }),
  createPatient: async () => ({ ok: true, data: [] }),
  getWorkingHours: async () => ({ ok: true, data: [] }),
  listAvailability: async () => ({ ok: true, data: [] }),
  listBlocks: async () => ({ ok: true, data: [] }),
} as unknown as AgendaGateway;

function deps(state: SessionState = { status: 'anonymous' }, settings: SettingsGateway = SETTINGS_GATEWAY): RoutesDeps {
  return {
    session: { get: () => state, ensureLoaded: async () => {}, signIn: () => {}, endAfterPasswordChange: () => {} },
    gateway: { login: async () => ({ ok: false, failure: { kind: 'connection' } }), changePassword: async () => ({ ok: true }) },
    agenda: AGENDA_GATEWAY,
    settings,
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
