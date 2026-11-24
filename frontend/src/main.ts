import './styles/base.css';
import './styles/layout.css';
import './styles/forms.css';
import './styles/settings.css';
import './styles/agenda.css';
import './styles/booking.css';
import './styles/patients.css';
import './styles/clinical-record.css';
import { createRoutes } from './app/routes';
import { createSession, type Session } from './core/auth/session';
import { httpSessionGateway } from './core/auth/session-gateway';
import { h } from './core/dom/h';
import { createHttpClient, type FetchFn, type HttpClient } from './core/http/client';
import { cookieCsrfTransport } from './core/http/transport';
import { renderPrivateLayout, type PrivateSection } from './layouts/private-layout';
import { createAgendaGateway } from './features/agenda/agenda-gateway';
import { createAuthGateway } from './features/auth/auth-gateway';
import { createBookingGateway } from './features/booking/booking-gateway';
import { createClinicalRecordGateway } from './features/clinical-record/clinical-record-gateway';
import { createPatientsGateway } from './features/patients/patients-gateway';
import { createSettingsGateway } from './features/settings/settings-gateway';
import { markProLink, renderPublicLayout } from './layouts/public-layout';
import { brandName } from './layouts/brand';
import { findRoute, normalizePath } from './router/match';
import { createRouter, type RenderArgs, type Router } from './router/router';
import { safeNext } from './router/safe-next';

const APP_CONTAINER_SELECTOR = '#app';

/**
 * Monta el nodo raiz de la aplicacion dentro de `#app`.
 * Es idempotente: si ya existe un `[data-app-root]` lo devuelve sin crear otro.
 * Falla sin tocar el documento si el contenedor no existe.
 */
export function mountApp(doc: Document): HTMLElement {
  const container = doc.querySelector<HTMLElement>(APP_CONTAINER_SELECTOR);
  if (container === null) {
    throw new Error(`No se encontro el contenedor ${APP_CONTAINER_SELECTOR} en el documento`);
  }

  const existing = container.querySelector<HTMLElement>('[data-app-root]');
  if (existing !== null) {
    return existing;
  }

  const root = doc.createElement('div');
  root.setAttribute('data-app-root', '');
  container.appendChild(root);
  return root;
}

export interface BootstrapOptions {
  readonly doc?: Document;
  readonly win?: Window;
  readonly fetch?: FetchFn;
}

export interface App {
  readonly root: HTMLElement;
  readonly session: Session;
  readonly client: HttpClient;
  readonly router: Router;
  /** Se resuelve cuando la ruta inicial quedo renderizada (tras los guards). */
  readonly ready: Promise<void>;
}

/** C-04 todavia no expone el nombre (pedido P1): etiqueta de respaldo en el encabezado privado. */
const FALLBACK_USER_LABEL = 'Profesional';

const PRIVATE_SECTIONS: ReadonlySet<string> = new Set<PrivateSection>(['agenda', 'pacientes', 'historia-clinica', 'configuracion']);

function sectionOf(routeName: string): PrivateSection | null {
  return PRIVATE_SECTIONS.has(routeName) ? (routeName as PrivateSection) : null;
}

type MountedLayout =
  | { kind: 'public'; main: HTMLElement }
  | { kind: 'private'; main: HTMLElement; current: PrivateSection | null; fullName: string };

/** Arranca la SPA: sesion, cliente HTTP, router y layouts. Todo inyectable para tests. */
export function bootstrap(options: BootstrapOptions = {}): App {
  const doc = options.doc ?? document;
  const win = options.win ?? window;
  const fetchFn: FetchFn = options.fetch ?? ((url, init) => win.fetch(url, init));
  const root = mountApp(doc);

  let router: Router | undefined;

  const client = createHttpClient({
    fetch: fetchFn,
    transport: cookieCsrfTransport(),
    onUnauthorized: () => handleUnauthorized(),
  });
  const sessionRef = createSession(httpSessionGateway(client));

  const authGateway = createAuthGateway({ client, onSessionExpired: () => sessionRef.expire() });
  const agendaGateway = createAgendaGateway({ client });
  const settingsGateway = createSettingsGateway({ client });
  const bookingGateway = createBookingGateway({ client });
  const patientsGateway = createPatientsGateway({ client });
  const clinicalRecordGateway = createClinicalRecordGateway({ client });

  const { routes, notFound } = createRoutes({
    session: sessionRef,
    gateway: authGateway,
    agenda: agendaGateway,
    patients: patientsGateway,
    clinicalRecord: clinicalRecordGateway,
    settings: settingsGateway,
    booking: bookingGateway,
    replaceUrl: (path) => win.history.replaceState(null, '', path),
    goBack: () => win.history.back(),
    navigate: (path, options) => {
      void router?.navigate(path, options);
    },
    onLogout: () => onLogout(),
  });

  function handleUnauthorized(): void {
    if (sessionRef.get().status !== 'authenticated') {
      return;
    }
    const current = findRoute(routes, win.location.pathname);
    sessionRef.expire();
    if (current?.route.layout !== 'private') {
      return;
    }
    const next = safeNext(normalizePath(win.location.pathname), routes);
    const target = next === null ? '/login' : `/login?next=${encodeURIComponent(next)}`;
    void router?.navigate(target, { replace: true });
  }

  function onLogout(): void {
    void (async () => {
      await sessionRef.logout();
      await router?.navigate('/login', { replace: true });
    })();
  }

  let mounted: MountedLayout | undefined;

  function render({ route, content }: RenderArgs): void {
    const state = sessionRef.get();
    if (route.layout === 'private' && state.status === 'authenticated') {
      const current = sectionOf(route.name);
      const fullName = state.user.fullName ?? FALLBACK_USER_LABEL;
      if (mounted?.kind === 'private' && mounted.current === current && mounted.fullName === fullName) {
        mounted.main.replaceChildren(content);
        return;
      }
      const layout = renderPrivateLayout({ user: { fullName }, current, content, onLogout });
      mounted = { kind: 'private', main: mainOf(layout), current, fullName };
      root.replaceChildren(layout);
      return;
    }
    const currentPath = normalizePath(win.location.pathname);
    if (mounted?.kind === 'public') {
      mounted.main.replaceChildren(content);
      markProLink(root, currentPath);
      return;
    }
    const layout = renderPublicLayout({ content, currentPath });
    mounted = { kind: 'public', main: mainOf(layout) };
    root.replaceChildren(layout);
  }

  function mainOf(layout: HTMLElement): HTMLElement {
    const main = layout.querySelector<HTMLElement>('main#main');
    if (main === null) {
      throw new Error('El layout no tiene main#main');
    }
    return main;
  }

  const routerRef = createRouter({ routes, notFound, window: win, render, brandName });
  router = routerRef;

  // Indicador accesible mientras la sesion es `unknown`; el primer render lo reemplaza.
  root.replaceChildren(h('div', { class: 'loading', attrs: { role: 'status' } }, 'Cargando…'));
  void sessionRef.load();
  const ready = routerRef.start();

  return { root, session: sessionRef, client, router: routerRef, ready };
}

if (import.meta.env.MODE !== 'test') {
  void bootstrap();
}
