import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SettingsGateway } from './settings-gateway';
import type { PracticeSettings, ScheduleBlock, ServiceItem, WorkingHour } from './settings-types';
import { createSettingsView } from './settings-view';

const PRACTICE: PracticeSettings = {
  id: 'p1',
  name: 'Consultorio Villa Crespo',
  timezone: 'America/Argentina/Buenos_Aires',
  contactEmail: 'recepcion@villacrespo.example',
  contactPhone: '+54 11 4471 0916',
  address: 'Av. Corrientes 3954',
  slotGranularityMin: 15,
  minNoticeMin: 120,
  maxAdvanceDays: 30,
  reminderHoursBefore: 24,
};
const HOUR: WorkingHour = { weekday: 1, startTime: '09:00', endTime: '13:00' };
const SERVICE: ServiceItem = {
  id: 's1',
  name: 'Limpieza',
  description: 'Higiene',
  durationMin: 45,
  active: true,
  publicVisible: true,
};
const BLOCK: ScheduleBlock = {
  id: 'b1',
  startsAt: '2026-03-18T12:00:00Z',
  endsAt: '2026-03-18T14:00:00Z',
  reason: 'Mantenimiento',
  createdAt: '2026-03-10T10:00:00Z',
  updatedAt: '2026-03-10T10:00:00Z',
  affectedAppointments: [],
};

interface Deferred<T> {
  readonly promise: Promise<T>;
  readonly resolve: (value: T) => void;
}

function deferred<T>(): Deferred<T> {
  let resolve: (value: T) => void = () => {};
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

function makeGateway(overrides: Readonly<Record<string, unknown>> = {}): SettingsGateway {
  return {
    getPractice: async () => ({ ok: true, data: PRACTICE }),
    savePractice: async () => ({ ok: true, data: PRACTICE }),
    getWorkingHours: async () => ({ ok: true, data: [HOUR] }),
    saveWorkingHours: async (hours: readonly WorkingHour[]) => ({ ok: true, data: hours }),
    getServices: async () => ({ ok: true, data: [SERVICE] }),
    createService: async () => ({ ok: true, data: SERVICE }),
    updateService: async () => ({ ok: true, data: SERVICE }),
    deleteService: async () => ({ ok: true, data: undefined }),
    getBlocks: async () => ({ ok: true, data: [BLOCK] }),
    createBlock: async () => ({ ok: true, data: BLOCK }),
    updateBlock: async () => ({ ok: true, data: BLOCK }),
    deleteBlock: async () => ({ ok: true, data: undefined }),
    ...overrides,
  } as unknown as SettingsGateway;
}

function mount(root: HTMLElement): void {
  document.body.replaceChildren(root);
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('settings-view - composición por secciones (C-19 D1/D3/D8)', () => {
  it('compone cuatro secciones independientes con aria-busy y lo retira al cargar', async () => {
    const hours = deferred<unknown>();
    const services = deferred<unknown>();
    const blocks = deferred<unknown>();
    const root = createSettingsView({
      gateway: makeGateway({
        getWorkingHours: () => hours.promise,
        getServices: () => services.promise,
        getBlocks: () => blocks.promise,
      }),
    });
    mount(root);

    await vi.waitFor(() => expect(root.querySelectorAll('.settings-card')).toHaveLength(4));
    expect(root.querySelector('h1')?.textContent).toBe('Configuración');
    expect(root.querySelector('.placeholder')).toBeNull();
    for (const id of ['practice', 'hours', 'services', 'blocks']) {
      expect(root.querySelector(`#${id}`)).not.toBeNull();
    }
    for (const id of ['hours', 'services', 'blocks']) {
      expect(root.querySelector(`#${id}`)?.getAttribute('aria-busy')).toBe('true');
    }

    hours.resolve({ ok: true, data: [HOUR] });
    services.resolve({ ok: true, data: [SERVICE] });
    blocks.resolve({ ok: true, data: [BLOCK] });

    await vi.waitFor(() => expect(root.querySelector('#blocks')?.hasAttribute('aria-busy')).toBe(false));
    expect(root.querySelector('#hours')?.hasAttribute('aria-busy')).toBe(false);
    expect(root.querySelector('#services')?.hasAttribute('aria-busy')).toBe(false);
    expect((root.querySelector('#practice-name') as HTMLInputElement | null)?.value).toBe(PRACTICE.name);
    expect(root.textContent).toContain(SERVICE.name);
    expect(root.textContent).toContain(BLOCK.reason);
  });

  it('muestra Reintentar solo en la sección fallida sin romper las demás', async () => {
    const root = createSettingsView({
      gateway: makeGateway({ getServices: async () => ({ ok: false, failure: { kind: 'connection' } }) }),
    });
    mount(root);

    await vi.waitFor(() => expect(root.querySelector('#services [role="alert"]')).not.toBeNull());
    expect(root.querySelector('#services')?.textContent).toContain('Reintentar');
    expect(root.querySelector('#practice [role="alert"]')).toBeNull();
    expect(root.querySelector('#hours [role="alert"]')).toBeNull();
    expect(root.querySelector('#blocks [role="alert"]')).toBeNull();
    expect(root.querySelectorAll('.settings-card')).toHaveLength(4);
  });
});

describe('settings-view - render seguro (C-19 D5/D9)', () => {
  it('muestra nombres de prestación y motivos de bloqueo hostiles como texto literal', async () => {
    const hostile = '<img src=x onerror=alert(1)>';
    const root = createSettingsView({
      gateway: makeGateway({
        getServices: async () => ({ ok: true, data: [{ ...SERVICE, name: hostile }] }),
        getBlocks: async () => ({ ok: true, data: [{ ...BLOCK, reason: hostile }] }),
      }),
    });
    mount(root);

    await vi.waitFor(() => expect(root.textContent).toContain(hostile));

    expect(root.querySelectorAll('img')).toHaveLength(0);
    expect(root.querySelectorAll('script')).toHaveLength(0);
  });
});

describe('settings-view - índice lateral resalta la sección actual', () => {
  function currentLinks(root: HTMLElement): string[] {
    return Array.from(root.querySelectorAll('.section-index a[aria-current="true"]')).map((a) =>
      a.getAttribute('href') ?? '',
    );
  }

  it('al iniciar solo "Consultorio y reserva" está marcado', () => {
    const root = createSettingsView({ gateway: makeGateway() });
    mount(root);
    expect(currentLinks(root)).toEqual(['#practice']);
  });

  it('click en "Prestaciones" marca solo ese link, hace scroll a la sección y no deja que el router lo vea', () => {
    const scrollIntoView = vi.fn();
    Element.prototype.scrollIntoView = scrollIntoView;
    const root = createSettingsView({ gateway: makeGateway() });
    mount(root);
    const link = root.querySelector<HTMLAnchorElement>('.section-index a[href="#services"]');
    const event = new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 });
    link?.dispatchEvent(event);

    expect(currentLinks(root)).toEqual(['#services']);
    expect(event.defaultPrevented).toBe(true);
    expect(scrollIntoView).toHaveBeenCalledTimes(1);
    expect(document.activeElement?.id).toBe('services');
  });

  it('click en otro link mueve la marca (triangulación)', () => {
    Element.prototype.scrollIntoView = vi.fn();
    const root = createSettingsView({ gateway: makeGateway() });
    mount(root);
    root.querySelector<HTMLAnchorElement>('a[href="#blocks"]')?.click();
    root.querySelector<HTMLAnchorElement>('a[href="#hours"]')?.click();
    expect(currentLinks(root)).toEqual(['#hours']);
  });

  it('con IntersectionObserver simulado, al intersectar "hours" se marca ese', () => {
    type Cb = (entries: Partial<IntersectionObserverEntry>[]) => void;
    let callback: Cb = () => {};
    const observed: Element[] = [];
    const disconnect = vi.fn();
    class FakeObserver {
      constructor(cb: Cb) {
        callback = cb;
      }
      observe(el: Element): void {
        observed.push(el);
      }
      disconnect = disconnect;
    }
    vi.stubGlobal('IntersectionObserver', FakeObserver);
    try {
      const root = createSettingsView({ gateway: makeGateway() });
      mount(root);
      expect(observed.map((el) => el.id)).toEqual(['practice', 'hours', 'services', 'blocks']);

      const hours = root.querySelector('#hours') as Element;
      callback([{ target: hours, isIntersecting: true }]);
      expect(currentLinks(root)).toEqual(['#hours']);

      const services = root.querySelector('#services') as Element;
      callback([
        { target: hours, isIntersecting: false },
        { target: services, isIntersecting: true },
      ]);
      expect(currentLinks(root)).toEqual(['#services']);

      document.body.replaceChildren();
      callback([{ target: hours, isIntersecting: true }]);
      expect(disconnect).toHaveBeenCalled();
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
