import type { Session } from '../core/auth/session';
import { h } from '../core/dom/h';
import type { AuthGateway } from '../features/auth/auth-gateway';
import { createChangePasswordView } from '../features/auth/change-password-view';
import { createLoginView } from '../features/auth/login-view';
import type { SettingsGateway } from '../features/settings/settings-gateway';
import { createSettingsView } from '../features/settings/settings-view';
import { redirectIfAuthenticated, requireAuthenticated, requireSession, rootRedirect } from '../router/guards';
import type { RouteDef, ViewRoute } from '../router/router';

function placeholderView(heading: string, description: string): () => HTMLElement {
  return () =>
    h(
      'section',
      {},
      h('h1', {}, heading),
      h('div', { class: 'placeholder' }, h('strong', {}, 'Contenido de la vista'), h('span', {}, description)),
    );
}

export interface RoutesDeps {
  readonly session: Pick<Session, 'get' | 'ensureLoaded' | 'signIn' | 'endAfterPasswordChange'>;
  readonly gateway: AuthGateway;
  readonly settings: SettingsGateway;
  readonly navigate: (path: string, options?: { replace?: boolean }) => void;
  readonly onLogout: () => void;
}

export function createRoutes(deps: RoutesDeps): { routes: RouteDef[]; notFound: ViewRoute } {
  const { session, gateway, settings, navigate, onLogout } = deps;
  const getRoutes = (): readonly RouteDef[] => routes;
  const privateGuard = requireSession(session, getRoutes);
  const loginView = createLoginView({ gateway, session, navigate, routes: getRoutes });
  const changePasswordView = createChangePasswordView({ gateway, session, navigate, routes: getRoutes, onLogout });
  const routes: RouteDef[] = [
    {
      name: 'root',
      path: '/',
      layout: 'public',
      title: 'Inicio',
      guard: rootRedirect(session),
      view: placeholderView('Inicio', 'Redirige segun la sesion.'),
    },
    {
      name: 'reservar',
      path: '/reservar',
      layout: 'public',
      title: 'Reservar turno',
      view: placeholderView('Reservá tu turno', 'La reserva llega en los changes de reserva publica.'),
    },
    {
      name: 'reserva-confirmada',
      path: '/reservar/confirmado',
      layout: 'public',
      title: 'Reserva confirmada',
      view: placeholderView('Reserva confirmada', 'La confirmacion llega en los changes de reserva publica.'),
    },
    {
      name: 'gestionar-turno',
      path: '/turno/:token',
      layout: 'public',
      title: 'Gestionar turno',
      view: placeholderView('Gestionar turno', 'La gestion por enlace llega en su propio change.'),
    },
    {
      name: 'login',
      path: '/login',
      layout: 'public',
      title: 'Ingresar',
      guard: redirectIfAuthenticated(session, getRoutes),
      view: loginView,
    },
    {
      name: 'change-password',
      path: '/cambiar-contrasena',
      layout: 'public',
      title: 'Cambiar contraseña',
      guard: requireAuthenticated(session, getRoutes),
      view: changePasswordView,
    },
    {
      name: 'agenda',
      path: '/agenda',
      layout: 'private',
      title: 'Agenda',
      guard: privateGuard,
      view: placeholderView('Agenda', 'La agenda llega en su propio change.'),
    },
    {
      name: 'pacientes',
      path: '/pacientes',
      layout: 'private',
      title: 'Pacientes',
      guard: privateGuard,
      view: placeholderView('Pacientes', 'El listado de pacientes llega en su propio change.'),
    },
    {
      name: 'configuracion',
      path: '/configuracion',
      layout: 'private',
      title: 'Configuración',
      guard: privateGuard,
      view: () => createSettingsView({ gateway: settings }),
    },
  ];

  const notFound: ViewRoute = {
    name: 'not-found',
    layout: 'public',
    title: 'Página no encontrada',
    view: () =>
      h(
        'section',
        {},
        h('h1', {}, 'Página no encontrada'),
        h('p', {}, 'La página que buscás no existe o cambió de lugar.'),
        h('p', {}, h('a', { attrs: { href: '/' } }, 'Ir al inicio')),
      ),
  };

  return { routes, notFound };
}
