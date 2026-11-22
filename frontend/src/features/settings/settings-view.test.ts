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
