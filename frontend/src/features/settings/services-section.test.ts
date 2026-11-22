import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SettingsGateway } from './settings-gateway';
import type { ServiceItem } from './settings-types';
import { createServicesSection } from './services-section';

const SERVICES: readonly ServiceItem[] = [
  { id: 's1', name: 'Consulta y diagnóstico', description: null, durationMin: 30, active: true, publicVisible: true },
  { id: 's2', name: 'Limpieza', description: 'Higiene', durationMin: 45, active: true, publicVisible: true },
  { id: 's3', name: 'Arreglo de caries', description: null, durationMin: 60, active: false, publicVisible: true },
];

interface Setup {
  readonly root: HTMLElement;
  readonly getServices: ReturnType<typeof vi.fn>;
  readonly createService: ReturnType<typeof vi.fn>;
  readonly updateService: ReturnType<typeof vi.fn>;
  readonly deleteService: ReturnType<typeof vi.fn>;
}

function mount(options: { getResult?: unknown; createResult?: unknown; updateResult?: unknown; deleteResult?: unknown } = {}): Setup {
  let getResult = options.getResult ?? { ok: true, data: SERVICES };
  const getServices = vi.fn(async () => getResult);
  const createService = vi.fn(async () => options.createResult ?? { ok: true, data: SERVICES[0] });
  const updateService = vi.fn(async () => options.updateResult ?? { ok: true, data: SERVICES[0] });
  const deleteService = vi.fn(async () => options.deleteResult ?? { ok: true, data: undefined });
  const gateway = { getServices, createService, updateService, deleteService } as unknown as Pick<
    SettingsGateway,
    'getServices' | 'createService' | 'updateService' | 'deleteService'
  >;
  const root = createServicesSection({ gateway, granularity: 15 });
  document.body.replaceChildren(root);
  return { root, getServices, createService, updateService, deleteService };
}

function findButton(root: ParentNode, text: string): HTMLButtonElement {
  const found = [...root.querySelectorAll<HTMLButtonElement>('button')].find((button) => button.textContent?.includes(text));
  if (found === undefined) {
    throw new Error(`falta botón ${text}`);
  }
  return found;
}

function field(root: HTMLElement, id: string): HTMLInputElement | HTMLTextAreaElement {
  const found = root.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`);
  if (found === null) {
    throw new Error(`falta campo #${id}`);
  }
  return found;
}

async function loaded(root: HTMLElement): Promise<void> {
  await vi.waitFor(() => expect(root.textContent).toContain('Consulta y diagnóstico'));
}

async function confirmDialog(accept: boolean): Promise<void> {
  await vi.waitFor(() => expect(document.querySelector('dialog')).not.toBeNull());
  document.querySelector<HTMLButtonElement>(`[data-confirm="${accept ? 'accept' : 'cancel'}"]`)?.click();
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('services-section - alta, listado y edición (C-19 D6/D8/D9)', () => {
  it('lista activas e inactivas con sus badges', async () => {
    const { root, getServices } = mount();

    await loaded(root);

    expect(getServices).toHaveBeenCalledTimes(1);
    expect(root.textContent).toContain('Arreglo de caries');
    expect(root.textContent).toContain('Inactiva');
    expect(root.textContent).toContain('Duración: 30 min');
  });

  it('da de alta una prestación de 45 min visible (POST)', async () => {
    const { root, createService } = mount({
      createResult: {
        ok: true,
        data: { id: 's4', name: 'Blanqueamiento', description: 'Sesión', durationMin: 45, active: true, publicVisible: true },
      },
    });
    await loaded(root);

    findButton(root, 'Nueva prestación').click();
    field(root, 'svc-name').value = 'Blanqueamiento';
    field(root, 'svc-desc').value = 'Sesión';
    field(root, 'svc-duration').value = '45';
    const visible = root.querySelector<HTMLInputElement>('#svc-visible');
    expect(visible).not.toBeNull();
    if (visible !== null) {
      visible.checked = true;
    }
    findButton(root, 'Guardar prestación').click();

    await vi.waitFor(() => expect(createService).toHaveBeenCalledTimes(1));
    expect(createService).toHaveBeenCalledWith({
      name: 'Blanqueamiento',
      description: 'Sesión',
      durationMin: 45,
      publicVisible: true,
    });
    await vi.waitFor(() => expect(root.textContent).toContain('Blanqueamiento'));
  });

  it('edita una prestación enviando active en el cuerpo', async () => {
    const { root, updateService } = mount();
    await loaded(root);

    const editButtons = [...root.querySelectorAll<HTMLButtonElement>('button')].filter((button) => button.textContent?.includes('Editar'));
    editButtons[0]?.click();
    field(root, 'svc-name').value = 'Consulta revisada';
    findButton(root, 'Guardar prestación').click();

    await vi.waitFor(() => expect(updateService).toHaveBeenCalledTimes(1));
    expect(updateService).toHaveBeenCalledWith('s1', {
      name: 'Consulta revisada',
      description: null,
      durationMin: 30,
      publicVisible: true,
      active: true,
    });
  });
});

describe('services-section - baja lógica, reactivación y estados (C-19 D8)', () => {
  it('da de baja vía DELETE tras confirmación', async () => {
    const { root, deleteService } = mount();
    await loaded(root);

    const bajaButtons = [...root.querySelectorAll<HTMLButtonElement>('button')].filter((button) => button.textContent?.includes('Dar de baja'));
    bajaButtons[0]?.click();
    await confirmDialog(true);

    await vi.waitFor(() => expect(deleteService).toHaveBeenCalledWith('s1'));
  });

  it('reactiva vía PUT conservando publicVisible', async () => {
    const { root, updateService } = mount();
    await loaded(root);

    findButton(root, 'Reactivar').click();

    await vi.waitFor(() => expect(updateService).toHaveBeenCalledTimes(1));
    expect(updateService).toHaveBeenCalledWith('s3', {
      name: 'Arreglo de caries',
      description: null,
      durationMin: 60,
      publicVisible: true,
      active: true,
    });
  });

  it('muestra estado vacío con acción de alta', async () => {
    const { root } = mount({ getResult: { ok: true, data: [] } });
    await vi.waitFor(() => expect(root.textContent).toContain('Todavía no cargaste prestaciones'));

    expect(root.textContent).toContain('Nueva prestación');
    findButton(root, 'Nueva prestación').click();
    expect(root.querySelector('#svc-name')).not.toBeNull();
  });

  it('muestra error por sección con Reintentar sin romper otras secciones', async () => {
    const { root, getServices } = mount({ getResult: { ok: false, failure: { kind: 'connection' } } });
    const other = document.createElement('div');
    const sentinel = document.createElement('input');
    sentinel.id = 'otra-seccion';
    other.append(sentinel);
    document.body.append(other);
    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());

    expect(root.textContent).toContain('Reintentar');
    getServices.mockImplementation(async () => ({ ok: true, data: SERVICES }));
    findButton(root, 'Reintentar').click();

    await loaded(root);
    expect(getServices).toHaveBeenCalledTimes(2);
    expect(document.querySelector('#otra-seccion')).not.toBeNull();
  });
});

describe('services-section - validación y errores de negocio (C-19 D3/D6)', () => {
  it('rechaza duración 20 con granularidad 15 sin enviar la solicitud', async () => {
    const { root, createService } = mount();
    await loaded(root);

    findButton(root, 'Nueva prestación').click();
    field(root, 'svc-name').value = 'Blanqueamiento';
    field(root, 'svc-duration').value = '20';
    findButton(root, 'Guardar prestación').click();

    expect(createService).not.toHaveBeenCalled();
    expect(field(root, 'svc-duration').getAttribute('aria-invalid')).toBe('true');
    expect(root.textContent).toContain('múltiplo de 15');
  });

  it('mantiene el formulario con sus valores ante 422 DURATION_NOT_MULTIPLE_OF_GRANULARITY', async () => {
    const { root, createService } = mount({ createResult: { ok: false, failure: { kind: 'duration' } } });
    await loaded(root);

    findButton(root, 'Nueva prestación').click();
    field(root, 'svc-name').value = 'Blanqueamiento';
    field(root, 'svc-duration').value = '45';
    findButton(root, 'Guardar prestación').click();

    await vi.waitFor(() => expect(createService).toHaveBeenCalledTimes(1));
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('múltiplo de la granularidad vigente');
    expect(root.querySelector('#svc-name')).not.toBeNull();
    expect(field(root, 'svc-name').value).toBe('Blanqueamiento');
    expect(field(root, 'svc-duration').value).toBe('45');
  });

  it('recarga el listado ante 404 SERVICE_NOT_FOUND', async () => {
    const { root, getServices, updateService } = mount({ updateResult: { ok: false, failure: { kind: 'not-found' } } });
    await loaded(root);

    const edit = [...root.querySelectorAll<HTMLButtonElement>('button')].find((button) => button.textContent === 'Editar');
    edit?.click();
    findButton(root, 'Guardar prestación').click();

    await vi.waitFor(() => expect(updateService).toHaveBeenCalledTimes(1));
    await vi.waitFor(() => expect(getServices).toHaveBeenCalledTimes(2));
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('ya no existe');
  });

  it('no envía la baja si se cancela la confirmación', async () => {
    const { root, deleteService } = mount();
    await loaded(root);

    const baja = [...root.querySelectorAll<HTMLButtonElement>('button')].find((button) => button.textContent === 'Dar de baja');
    baja?.click();
    await confirmDialog(false);

    expect(deleteService).not.toHaveBeenCalled();
  });

  it('renderiza un nombre con <script> como texto literal', async () => {
    const hostile = '<script>alert(1)</script>';
    const { root } = mount({
      getResult: { ok: true, data: [{ ...SERVICES[0], name: hostile, description: hostile }] },
    });

    await vi.waitFor(() => expect(root.textContent).toContain(hostile));

    expect(root.querySelector('script')).toBeNull();
  });
});
