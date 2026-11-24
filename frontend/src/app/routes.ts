import type { Session } from '../core/auth/session';
import { h } from '../core/dom/h';
import type { AgendaGateway } from '../features/agenda/agenda-gateway';
import { createAgendaView } from '../features/agenda/agenda-view';
import type { AuthGateway } from '../features/auth/auth-gateway';
import { createChangePasswordView } from '../features/auth/change-password-view';
import { createLoginView } from '../features/auth/login-view';
import type { BookingGateway } from '../features/booking/booking-types';
import type { ClinicalRecordGateway } from '../features/clinical-record/clinical-record-gateway';
import { createClinicalRecordView } from '../features/clinical-record/clinical-record-view';
import type { PatientsGateway } from '../features/patients/patients-gateway';
import { createPatientsView } from '../features/patients/patients-view';
import { createBookingView } from '../features/booking/booking-view';
import { renderPrivacyNotice } from '../features/booking/privacy-notice';
import { createConfirmationView } from '../features/booking/confirmation-view';
import type { ManageGateway } from '../features/manage-booking/manage-types';
import { createManageView } from '../features/manage-booking/manage-view';
import type { Clock } from '../shared/utils/dates';
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
  readonly session: Pick<Session, 'get' | 'ensureLoaded' | 'signIn' | 'endAfterPasswordChange' | 'expire'>;
  readonly gateway: AuthGateway;
  readonly agenda: AgendaGateway;
  readonly patients: PatientsGateway;
  readonly clinicalRecord: ClinicalRecordGateway;
  readonly settings: SettingsGateway;
  readonly booking: BookingGateway;
  readonly manage: ManageGateway;
  /** Corrige la URL sin renderizar (normaliza `?paso=` de la reserva). */
  readonly replaceUrl: (path: string) => void;
  readonly goBack: () => void;
  readonly clock?: Clock;
  readonly navigate: (path: string, options?: { replace?: boolean }) => void;
  readonly onLogout: () => void;
}

export function createRoutes(deps: RoutesDeps): { routes: RouteDef[]; notFound: ViewRoute } {
  const { session, gateway, agenda, settings, navigate, onLogout } = deps;
  const bookingView = createBookingView({
    gateway: deps.booking,
    clock: deps.clock ?? (() => new Date()),
    navigate,
    replaceUrl: deps.replaceUrl,
    goBack: deps.goBack,
  });
  const manageView = createManageView({
    gateway: deps.manage,
    booking: deps.booking,
    clock: deps.clock ?? (() => new Date()),
    navigate,
    replaceUrl: deps.replaceUrl,
    goBack: deps.goBack,
  });
  const confirmationView = createConfirmationView();
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
      view: bookingView,
    },
    {
      name: 'reserva-confirmada',
      path: '/reservar/confirmado',
      layout: 'public',
      title: 'Reserva confirmada',
      view: confirmationView,
    },
    {
      name: 'gestionar-turno',
      path: '/turno/:token',
      layout: 'public',
      title: 'Gestionar turno',
      view: manageView,
    },
    {
      name: 'privacidad',
      path: '/privacidad',
      layout: 'public',
      title: 'Aviso de privacidad',
      view: () =>
        h('section', { class: 'privacy-page' }, h('h1', {}, 'Aviso de privacidad'), renderPrivacyNotice()),
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
      view: () => createAgendaView({ gateway: agenda }),
    },
    {
      name: 'pacientes',
      path: '/pacientes',
      layout: 'private',
      title: 'Pacientes',
      guard: privateGuard,
      view: () => createPatientsView({ gateway: deps.patients }),
    },
    {
      name: 'historia-clinica',
      path: '/historia-clinica',
      layout: 'private',
      title: 'Historia clínica',
      guard: privateGuard,
      view: () => createClinicalRecordView({
        patients: deps.patients,
        getAnamnesis: (patientId) => deps.clinicalRecord.getAnamnesis(patientId),
        getOdontogram: (patientId) => deps.clinicalRecord.getOdontogram(patientId),
        getEvolutions: (patientId) => deps.clinicalRecord.getEvolutions(patientId),
        registerAnamnesis: (patientId, input) => deps.clinicalRecord.registerAnamnesis(patientId, input),
        registerOdontogram: (patientId, entries) => deps.clinicalRecord.registerOdontogram(patientId, entries),
        registerEvolution: (patientId, input) => deps.clinicalRecord.registerEvolution(patientId, input),
        correctEntry: (patientId, type, entryId, input) =>
          deps.clinicalRecord.correctEntry(patientId, type, entryId, input),
        navigate: deps.navigate,
        onSessionExpired: () => session.expire(),
      }).element,
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
