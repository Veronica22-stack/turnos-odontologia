import type { Session } from '../core/auth/session';
import { h } from '../core/dom/h';
import { redirectIfAuthenticated, requireSession, rootRedirect } from '../router/guards';
import type { RouteDef, ViewRoute } from '../router/router';

type SessionReader = Pick<Session, 'get' | 'ensureLoaded'>;

function placeholderView(heading: string, description: string): () => HTMLElement {
  return () =>
    h(
      'section',
      {},
      h('h1', {}, heading),
      h('div', { class: 'placeholder' }, h('strong', {}, 'Contenido de la vista'), h('span', {}, description)),
    );
}

const NOTICE_TEXT = {
  expired: 'Tu sesión venció. Ingresá de nuevo.',
  offline: 'Sin conexión con el servidor. Reintentá en unos segundos.',
} as const;

function loginView(session: SessionReader): () => HTMLElement {
  const placeholder = placeholderView('Ingresar', 'El formulario de ingreso llega en C-06.');
  return () => {
    const view = placeholder();
    const state = session.get();
    if (state.status === 'anonymous' && state.notice !== undefined) {
      view.querySelector('h1')?.after(h('div', { class: 'notice', attrs: { role: 'status' } }, NOTICE_TEXT[state.notice]));
    }
    return view;
  };
}

export function createRoutes(session: SessionReader): { routes: RouteDef[]; notFound: ViewRoute } {
  const privateGuard = requireSession(session);
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
      guard: redirectIfAuthenticated(session, () => routes),
      view: loginView(session),
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
      view: placeholderView('Configuración', 'La configuracion llega en su propio change.'),
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
