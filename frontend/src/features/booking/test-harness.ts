import { vi } from 'vitest';
import type { RouteContext, Router } from '../../router/router';
import { createRouter } from '../../router/router';
import type {
  AvailabilityResult,
  BookResult,
  BookingGateway,
  BookingRequest,
  ListServicesResult,
  PublicService,
} from './booking-types';
import { createBookingView } from './booking-view';
import { createConfirmationView } from './confirmation-view';
import type { Slot } from './slots';

/** Utilidades compartidas por los tests de la reserva (solo tests): router real + jsdom + gateway falso. */

export const SERVICES: readonly PublicService[] = [
  { id: 's-1', name: 'Consulta y diagnóstico', description: 'Primera visita o control general.', durationMin: 30 },
  { id: 's-2', name: 'Limpieza', description: null, durationMin: 45 },
];

export const SLOTS_24: readonly Slot[] = [
  { startsAt: '2026-11-24T12:00:00Z', endsAt: '2026-11-24T12:30:00Z' },
  { startsAt: '2026-11-24T12:30:00Z', endsAt: '2026-11-24T13:00:00Z' },
  { startsAt: '2026-11-24T18:00:00Z', endsAt: '2026-11-24T18:30:00Z' },
];

/** Lunes 23/11/2026 10:00 en Buenos Aires. */
export const NOW = new Date('2026-11-23T13:00:00Z');

export interface FakeGateway extends BookingGateway {
  readonly listServices: ReturnType<typeof vi.fn<() => Promise<ListServicesResult>>>;
  readonly availability: ReturnType<typeof vi.fn<(serviceId: string, date: string) => Promise<AvailabilityResult>>>;
  readonly book: ReturnType<typeof vi.fn<(request: BookingRequest) => Promise<BookResult>>>;
}

export function makeGateway(
  overrides: {
    services?: () => Promise<ListServicesResult>;
    availability?: (serviceId: string, date: string) => Promise<AvailabilityResult>;
    book?: (request: BookingRequest) => Promise<BookResult>;
  } = {},
): FakeGateway {
  return {
    listServices: vi.fn(overrides.services ?? (async () => ({ kind: 'ok', services: SERVICES }))),
    availability: vi.fn(
      overrides.availability ??
        (async (_id: string, date: string): Promise<AvailabilityResult> => ({
          kind: 'ok',
          slots: date === '2026-11-24' ? SLOTS_24 : [],
        })),
    ),
    book: vi.fn(overrides.book ?? (async () => ({ kind: 'unknown-outcome' }))),
  };
}

export interface Deferred<T> {
  readonly promise: Promise<T>;
  resolve(value: T): void;
}

export function deferred<T>(): Deferred<T> {
  let resolve: (value: T) => void = () => {};
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

export async function settle(): Promise<void> {
  for (let i = 0; i < 4; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
}

export interface Harness {
  readonly gateway: FakeGateway;
  readonly router: Router;
  readonly root: HTMLElement;
  stop(): void;
}

/** Monta `/reservar` y `/reservar/confirmado` con el router real de C-05 sobre el jsdom global. */
export async function startHarness(
  options: { gateway?: FakeGateway; url?: string; clock?: () => Date } = {},
): Promise<Harness> {
  const gateway = options.gateway ?? makeGateway();
  const root = document.createElement('div');
  document.body.replaceChildren(root);
  window.history.replaceState(null, '', options.url ?? '/reservar');
  const routerRef: { current?: Router } = {};
  const bookingView = createBookingView({
    gateway,
    clock: options.clock ?? (() => NOW),
    navigate: (path, navOptions) => {
      void routerRef.current?.navigate(path, navOptions);
    },
    replaceUrl: (path) => window.history.replaceState(null, '', path),
    goBack: () => window.history.back(),
  });
  const confirmation = createConfirmationView();
  const router = createRouter({
    routes: [
      {
        name: 'reservar',
        path: '/reservar',
        layout: 'public',
        title: 'Reservar turno',
        view: (context: RouteContext) => bookingView(context),
      },
      {
        name: 'reserva-confirmada',
        path: '/reservar/confirmado',
        layout: 'public',
        title: 'Reserva confirmada',
        view: () => confirmation(),
      },
      {
        name: 'otra',
        path: '/otra',
        layout: 'public',
        title: 'Otra',
        view: () => document.createElement('div'),
      },
    ],
    notFound: { name: 'nf', layout: 'public', title: 'No encontrada', view: () => document.createElement('div') },
    window,
    render: ({ content }) => root.replaceChildren(content),
    brandName: () => 'La Muela',
  });
  routerRef.current = router;
  await router.start();
  await settle();
  return {
    gateway,
    router,
    root,
    stop: () => {
      router.stop();
      document.body.replaceChildren();
    },
  };
}

export function button(root: ParentNode, name: string): HTMLButtonElement {
  const found = [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === name);
  if (found === undefined) {
    throw new Error(`falta el boton "${name}"`);
  }
  return found;
}

export function dayButton(root: ParentNode, label: string): HTMLButtonElement {
  const found = root.querySelector<HTMLButtonElement>(`button.day[aria-label="${label}"]`);
  if (found === null) {
    throw new Error(`falta el dia "${label}"`);
  }
  return found;
}

export function slotButtons(root: ParentNode, selector = '.slot-group'): string[] {
  return [...root.querySelectorAll(`${selector} button.slot`)].map((b) => b.textContent?.trim() ?? '');
}

export async function click(element: HTMLElement): Promise<void> {
  element.click();
  await settle();
}

export function text(root: ParentNode): string {
  return root.textContent?.replace(/\s+/g, ' ').trim() ?? '';
}

export function requireElement<T extends Element>(root: ParentNode, selector: string): T {
  const found = root.querySelector<T>(selector);
  if (found === null) {
    throw new Error(`falta ${selector}`);
  }
  return found;
}

/** Elige una prestacion en el paso 1 y continua. */
export async function chooseService(root: ParentNode, name: string): Promise<void> {
  const radio = [...root.querySelectorAll<HTMLInputElement>('input[type="radio"]')].find((r) =>
    r.closest('label')?.textContent?.includes(name),
  );
  if (radio === undefined) {
    throw new Error(`falta la prestacion "${name}"`);
  }
  radio.click();
  await click(button(root, 'Continuar'));
}
