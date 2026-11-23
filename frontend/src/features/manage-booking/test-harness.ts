import { vi } from 'vitest';
import { createRouter, type RouteContext, type Router } from '../../router/router';
import type { AvailabilityResult, ListServicesResult, PublicService } from '../booking/booking-types';
import { makeGateway, type FakeGateway } from '../booking/test-harness';
import type { Slot } from '../booking/slots';
import { createManageView } from './manage-view';
import type { CancelResult, ManageGateway, ManagedBooking, RescheduleResult, ViewResult } from './manage-types';

/** Utilidades compartidas por los tests de la gestion de turno (solo tests): router real + jsdom + gateways falsos. */

export const TOKEN = 'tok_Abc-123_xyz456789012345';

export const BOOKING: ManagedBooking = {
  serviceName: 'Limpieza',
  practiceName: 'Consultorio La Muela',
  startsAt: '2026-11-24T13:30:00Z',
  endsAt: '2026-11-24T14:15:00Z',
  status: 'CONFIRMED',
  cancellable: true,
  reschedulable: true,
};

export const MOVED: ManagedBooking = {
  ...BOOKING,
  startsAt: '2026-11-26T14:15:00Z',
  endsAt: '2026-11-26T15:00:00Z',
};

export const CANCELLED: ManagedBooking = {
  ...BOOKING,
  status: 'CANCELLED',
  cancellable: false,
  reschedulable: false,
};

export const PAST: ManagedBooking = { ...BOOKING, cancellable: false, reschedulable: false };

export const SERVICES: readonly PublicService[] = [
  { id: 's-1', name: 'Consulta y diagnóstico', description: null, durationMin: 30 },
  { id: 's-2', name: 'Limpieza', description: null, durationMin: 45 },
];

/** Disponibilidad de Limpieza (45 min): el dia del turno y el jueves 26 (UTC-3). */
export const SLOTS_BY_DATE: Readonly<Record<string, readonly Slot[]>> = {
  '2026-11-24': [
    { startsAt: '2026-11-24T12:00:00Z', endsAt: '2026-11-24T12:45:00Z' },
    { startsAt: '2026-11-24T15:00:00Z', endsAt: '2026-11-24T15:45:00Z' },
  ],
  '2026-11-26': [
    { startsAt: '2026-11-26T12:00:00Z', endsAt: '2026-11-26T12:45:00Z' },
    { startsAt: '2026-11-26T12:45:00Z', endsAt: '2026-11-26T13:30:00Z' },
    { startsAt: '2026-11-26T14:15:00Z', endsAt: '2026-11-26T15:00:00Z' },
    { startsAt: '2026-11-26T15:00:00Z', endsAt: '2026-11-26T15:45:00Z' },
    { startsAt: '2026-11-26T18:00:00Z', endsAt: '2026-11-26T18:45:00Z' },
  ],
};

export const SLOT_26_1115: Slot = { startsAt: '2026-11-26T14:15:00Z', endsAt: '2026-11-26T15:00:00Z' };

export interface FakeManageGateway extends ManageGateway {
  readonly view: ReturnType<typeof vi.fn<(token: string) => Promise<ViewResult>>>;
  readonly cancel: ReturnType<typeof vi.fn<(token: string) => Promise<CancelResult>>>;
  readonly reschedule: ReturnType<typeof vi.fn<(token: string, startsAt: string) => Promise<RescheduleResult>>>;
}

export function makeManageGateway(
  overrides: {
    view?: (token: string) => Promise<ViewResult>;
    cancel?: (token: string) => Promise<CancelResult>;
    reschedule?: (token: string, startsAt: string) => Promise<RescheduleResult>;
  } = {},
): FakeManageGateway {
  return {
    view: vi.fn(overrides.view ?? (async (): Promise<ViewResult> => ({ kind: 'ok', booking: BOOKING }))),
    cancel: vi.fn(overrides.cancel ?? (async (): Promise<CancelResult> => ({ kind: 'unknown-outcome' }))),
    reschedule: vi.fn(overrides.reschedule ?? (async (): Promise<RescheduleResult> => ({ kind: 'unknown-outcome' }))),
  };
}

export function makeBookingGateway(
  overrides: {
    services?: () => Promise<ListServicesResult>;
    availability?: (serviceId: string, date: string) => Promise<AvailabilityResult>;
  } = {},
): FakeGateway {
  return makeGateway({
    services: overrides.services ?? (async () => ({ kind: 'ok', services: SERVICES })),
    availability:
      overrides.availability ??
      (async (_serviceId, date): Promise<AvailabilityResult> => ({ kind: 'ok', slots: SLOTS_BY_DATE[date] ?? [] })),
  });
}

export interface ManageHarness {
  readonly gateway: FakeManageGateway;
  readonly booking: FakeGateway;
  readonly router: Router;
  readonly root: HTMLElement;
  readonly navigate: ReturnType<typeof vi.fn<(path: string, options?: { replace?: boolean }) => void>>;
  stop(): void;
}

export async function settleAll(): Promise<void> {
  for (let i = 0; i < 6; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
}

/** Monta `/turno/:token` y una ruta `/otra` con el router real de C-05 sobre el jsdom global. */
export async function startManage(
  options: {
    gateway?: FakeManageGateway;
    booking?: FakeGateway;
    url?: string;
    clock?: () => Date;
  } = {},
): Promise<ManageHarness> {
  const gateway = options.gateway ?? makeManageGateway();
  const booking = options.booking ?? makeBookingGateway();
  const root = document.createElement('div');
  document.body.replaceChildren(root);
  window.history.replaceState(null, '', options.url ?? `/turno/${TOKEN}`);
  const routerRef: { current?: Router } = {};
  const navigate = vi.fn((path: string, navOptions?: { replace?: boolean }) => {
    void routerRef.current?.navigate(path, navOptions);
  });
  const manageView = createManageView({
    gateway,
    booking,
    clock: options.clock ?? (() => new Date('2026-11-23T13:00:00Z')),
    navigate,
    replaceUrl: (path) => window.history.replaceState(null, '', path),
    goBack: () => window.history.back(),
  });
  const router = createRouter({
    routes: [
      {
        name: 'gestionar-turno',
        path: '/turno/:token',
        layout: 'public',
        title: 'Gestionar turno',
        view: (context: RouteContext) => manageView(context),
      },
      { name: 'otra', path: '/otra', layout: 'public', title: 'Otra', view: () => document.createElement('div') },
    ],
    notFound: { name: 'nf', layout: 'public', title: 'No encontrada', view: () => document.createElement('div') },
    window,
    render: ({ content }) => root.replaceChildren(content),
    brandName: () => 'La Muela',
  });
  routerRef.current = router;
  await router.start();
  await settleAll();
  return {
    gateway,
    booking,
    router,
    root,
    navigate,
    stop: () => {
      router.stop();
      document.body.replaceChildren();
    },
  };
}
