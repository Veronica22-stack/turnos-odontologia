import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PatientsFailure } from './patients-messages';
import type { PatientsGateway, PatientsResult } from './patients-gateway';
import { createPatientsView } from './patients-view';
import type { PatientDetail, PatientInput, PatientSummary } from './patients-types';

const SUMMARY: PatientSummary = {
  id: 'pac-001',
  firstName: 'Julieta',
  lastName: 'Simulada',
  dni: '30.456.789',
  phone: '11 5555 0001',
  active: true,
};
const DETAIL: PatientDetail = {
  ...SUMMARY,
  email: 'julieta@ejemplo.invalid',
  birthDate: '1992-04-18',
  address: 'Calle Falsa 123',
  source: 'MANUAL',
  createdAt: '2025-03-12T12:41:00Z',
  updatedAt: '2026-11-18T17:07:00Z',
};

interface Fakes {
  readonly search: ReturnType<typeof vi.fn>;
  readonly get: ReturnType<typeof vi.fn>;
  readonly update: ReturnType<typeof vi.fn>;
  readonly deactivate: ReturnType<typeof vi.fn>;
  readonly gateway: PatientsGateway;
}

function fakeGateway(options: {
  search?: PatientsResult<readonly PatientSummary[]>;
  detail?: PatientsResult<PatientDetail>;
  update?: PatientsResult<PatientDetail>;
  deactivate?: PatientsResult<void>;
  searchQueue?: readonly PatientsResult<readonly PatientSummary[]>[];
} = {}): Fakes {
  const queue = [...(options.searchQueue ?? [])];
  const search = vi.fn(async () => queue.shift() ?? options.search ?? { ok: true, data: [SUMMARY] });
  const get = vi.fn(async () => options.detail ?? { ok: true, data: DETAIL });
  const update = vi.fn(async () => options.update ?? { ok: true, data: DETAIL });
  const deactivate = vi.fn(async () => options.deactivate ?? { ok: true, data: undefined });
  return {
    search,
    get,
    update,
    deactivate,
    gateway: { search, get, update, deactivate } as unknown as PatientsGateway,
  };
}

function mount(gateway: PatientsGateway): HTMLElement {
  document.body.replaceChildren();
  const view = createPatientsView({ gateway });
  document.body.append(view);
  return view;
}

function button(root: ParentNode, text: string): HTMLButtonElement {
  const found = [...root.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.includes(text));
  if (found === undefined) {
    throw new Error(`Botón no encontrado: ${text}`);
  }
  return found;
}

async function loaded(root: HTMLElement): Promise<void> {
  await vi.waitFor(() => expect(root.textContent).not.toContain('Cargando pacientes'));
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('patients-view - listado y búsqueda (D6)', () => {
  it('4.1 monta en carga, luego tabla desktop y lista móvil con nombre/DNI/teléfono', async () => {
    const fakes = fakeGateway();
    const root = mount(fakes.gateway);
    expect(root.querySelector('[role="status"]')?.textContent).toContain('Cargando pacientes');
    await loaded(root);
    expect(root.querySelector('table.data caption')?.classList.contains('sr-only')).toBe(true);
    expect(root.querySelector('ul.patient-list')?.getAttribute('aria-label')).toBe('Pacientes activos');
    expect(root.textContent).toContain('30.456.789');
    expect(root.textContent).toContain('11 5555 0001');
    expect(fakes.search).toHaveBeenCalledWith('');
  });

  it('4.2 busca por término con espacios y por DNI sin escribir en la URL', async () => {
    const fakes = fakeGateway();
    const root = mount(fakes.gateway);
    await loaded(root);
    const form = root.querySelector<HTMLFormElement>('form[role="search"]');
    const input = root.querySelector<HTMLInputElement>('input[name="q"]');
    expect(input?.getAttribute('placeholder')).toBe('Buscar por nombre, DNI o teléfono');
    expect(root.querySelector('label[for="q"]')?.textContent).toContain('Buscar pacientes');
    if (form === null || input === null) {
      throw new Error('Buscador no encontrado');
    }
    input.value = 'Julieta Simulada';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(fakes.search).toHaveBeenLastCalledWith('Julieta Simulada'));
    expect(window.location.search).toBe('');

    input.value = '30.456';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(fakes.search).toHaveBeenLastCalledWith('30.456'));
  });

  it('4.3 distingue sin pacientes de búsqueda sin coincidencias y recupera resultados', async () => {
    const fakes = fakeGateway({
      searchQueue: [{ ok: true, data: [] }],
    });
    const root = mount(fakes.gateway);
    await loaded(root);
    expect(root.textContent).toContain('Todavía no hay pacientes');
    expect(root.querySelector('table.data')).toBeNull();
    expect(root.textContent).not.toContain('Crear paciente');

    fakes.search.mockResolvedValueOnce({ ok: true, data: [] });
    const form = root.querySelector<HTMLFormElement>('form[role="search"]');
    const input = root.querySelector<HTMLInputElement>('input[name="q"]');
    if (form === null || input === null) {
      throw new Error('Buscador no encontrado');
    }
    input.value = 'no-existe';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    expect(await textEventually(root, 'No encontramos pacientes con ese criterio')).toBe(true);
    expect(root.textContent).toContain('o revisá la ortografía del nombre');

    fakes.search.mockResolvedValueOnce({ ok: true, data: [SUMMARY] });
    input.value = 'Julieta';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(root.textContent).toContain('30.456.789'));
    expect(root.textContent).not.toContain('No encontramos pacientes');
  });

  it('4.3/5.4 renderiza texto hostil como literal sin crear etiquetas', async () => {
    const fakes = fakeGateway({ search: { ok: true, data: [{ ...SUMMARY, firstName: '<script>', lastName: 'alert(1)' }] } });
    const root = mount(fakes.gateway);
    await loaded(root);
    expect(root.textContent).toContain('<script> alert(1)');
    expect(root.querySelector('script')).toBeNull();
  });

  it.each(['timeout', 'invalid-response'] as const)('4.4 TRIANGULATE: %s comparte el copy de conexión', async () => {
    const failure = { kind: 'connection' } as const;
    const fakes = fakeGateway({
      searchQueue: [{ ok: false, failure }, { ok: false, failure }, { ok: true, data: [SUMMARY] }],
    });
    const root = mount(fakes.gateway);
    await vi.waitFor(() => expect(root.textContent).toContain('No pudimos cargar los pacientes'));
    button(root, 'Reintentar').click();
    await vi.waitFor(() => expect(fakes.search).toHaveBeenCalledTimes(2));
    expect(root.textContent).toContain('No pudimos cargar los pacientes');
    button(root, 'Reintentar').click();
    await vi.waitFor(() => expect(root.textContent).toContain('30.456.789'));
    expect(root.textContent).not.toContain('title');
    expect(root.textContent).not.toContain('detail');
  });

  it('4.4 TRIANGULATE: una respuesta sin cuerpo no rompe el listado y muestra error de conexión', async () => {
    const fakes = fakeGateway({ search: { ok: true, data: undefined as never } });
    const root = mount(fakes.gateway);
    await vi.waitFor(() => expect(root.textContent).toContain('No pudimos cargar los pacientes'));
    expect(root.querySelector('table.data')).toBeNull();
  });

  it('4.4 error de carga: copy propio, sin title/detail, y reintento con resultados', async () => {
    const fakes = fakeGateway({
      searchQueue: [{ ok: false, failure: { kind: 'connection' } }, { ok: true, data: [SUMMARY] }],
    });
    const root = mount(fakes.gateway);
    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
    expect(root.textContent).toContain('No pudimos cargar los pacientes');
    expect(root.textContent).not.toContain('title');
    button(root, 'Reintentar').click();
    await vi.waitFor(() => expect(root.textContent).toContain('30.456.789'));
    expect(fakes.search).toHaveBeenCalledTimes(2);
  });
});

describe('patients-view - ficha y marcador RN-PA-03', () => {
  it('5.1 abre la ficha con GET por id y muestra datos y fechas de Buenos Aires', async () => {
    const fakes = fakeGateway();
    const root = mount(fakes.gateway);
    await loaded(root);
    root.querySelector<HTMLAnchorElement>('.pname')?.click();
    await vi.waitFor(() => expect(fakes.get).toHaveBeenCalledWith('pac-001'));
    expect(root.querySelector('h1')?.textContent).toBe('Julieta Simulada');
    expect(root.textContent).toContain('12/03/2025 09:41');
    expect(root.textContent).toContain('18/11/2026 14:07');
  });

  it('5.2 404 muestra copy propio y vuelve sin repetir la consulta', async () => {
    const fakes = fakeGateway({ detail: { ok: false, failure: { kind: 'not-found' } } });
    const root = mount(fakes.gateway);
    await loaded(root);
    root.querySelector<HTMLAnchorElement>('.pname')?.click();
    await vi.waitFor(() => expect(root.textContent).toContain('No encontramos este paciente'));
    expect(root.textContent).not.toContain('pac-001');
    expect(root.textContent).not.toContain('30.456.789');
    button(root, 'Volver al listado').click();
    await vi.waitFor(() => expect(root.querySelector('table.data')).not.toBeNull());
    expect(fakes.get).toHaveBeenCalledTimes(1);
  });

  it('5.2 error de red en la ficha ofrece reintentar y no muestra datos parciales', async () => {
    const fakes = fakeGateway({ detail: { ok: false, failure: { kind: 'connection' } } });
    const root = mount(fakes.gateway);
    await loaded(root);
    root.querySelector<HTMLAnchorElement>('.pname')?.click();
    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
    expect(root.textContent).not.toContain('julieta@ejemplo.invalid');
    fakes.get.mockResolvedValueOnce({ ok: true, data: DETAIL });
    button(root, 'Reintentar').click();
    await vi.waitFor(() => expect(root.querySelector('h1')?.textContent).toBe('Julieta Simulada'));
    expect(fakes.get).toHaveBeenCalledTimes(2);
  });

  it('5.3 solo el marcador explícito habilita la insignia de revisión', async () => {
    const flagged: PatientDetail = { ...DETAIL, reviewRequired: true };
    const fakes = fakeGateway({ detail: { ok: true, data: flagged } });
    const root = mount(fakes.gateway);
    await loaded(root);
    root.querySelector<HTMLAnchorElement>('.pname')?.click();
    await vi.waitFor(() => expect(root.querySelector('.badge-review')).not.toBeNull());
    expect(root.textContent).toContain('Datos para revisar');
    expect(root.textContent).toContain('no reemplazaron');

    const other = fakeGateway({ detail: { ok: true, data: { ...DETAIL, source: 'PUBLIC_BOOKING' } } });
    const root2 = mount(other.gateway);
    await loaded(root2);
    root2.querySelector<HTMLAnchorElement>('.pname')?.click();
    await vi.waitFor(() => expect(root2.querySelector('h1')?.textContent).toBe('Julieta Simulada'));
    expect(root2.querySelector('.badge-review')).toBeNull();
  });
});

describe('patients-view - edición (D5/D6)', () => {
  async function openEdit(root: HTMLElement, fakes: Fakes): Promise<void> {
    await loaded(root);
    root.querySelector<HTMLAnchorElement>('.pname')?.click();
    await vi.waitFor(() => expect(root.querySelector('h1')?.textContent).toBe('Julieta Simulada'));
    button(root, 'Editar ficha').click();
    await vi.waitFor(() => expect(root.querySelector('input[name="firstName"]')).not.toBeNull());
    expect(fakes.get).toHaveBeenCalledTimes(1);
  }

  it('6.1 guarda una vez con el cuerpo editable y muestra la respuesta del servidor', async () => {
    const fakes = fakeGateway();
    const root = mount(fakes.gateway);
    await openEdit(root, fakes);
    const input = root.querySelector<HTMLInputElement>('input[name="phone"]');
    if (input === null) {
      throw new Error('Teléfono no encontrado');
    }
    input.value = '11 5555 0099';
    const updated = { ...DETAIL, phone: '11 5555 0099' };
    fakes.update.mockResolvedValueOnce({ ok: true, data: updated });
    button(root, 'Guardar cambios').click();
    await vi.waitFor(() => expect(fakes.update).toHaveBeenCalledTimes(1));
    const [id, body] = fakes.update.mock.calls[0] as [string, PatientInput];
    expect(id).toBe('pac-001');
    expect(body).toEqual({ firstName: 'Julieta', lastName: 'Simulada', phone: '11 5555 0099', email: DETAIL.email, birthDate: DETAIL.birthDate, address: DETAIL.address });
    await vi.waitFor(() => expect(root.querySelector('h1')?.textContent).toBe('Julieta Simulada'));
    expect(root.textContent).toContain('11 5555 0099');
  });

  it('6.1 TRIANGULATE: doble envío (también desde el botón ya reemplazado) ignora el segundo PUT', async () => {
    const fakes = fakeGateway();
    const root = mount(fakes.gateway);
    await openEdit(root, fakes);
    const save = button(root, 'Guardar cambios');
    let resolveUpdate: ((result: PatientsResult<PatientDetail>) => void) | undefined;
    fakes.update.mockImplementationOnce(() => new Promise<PatientsResult<PatientDetail>>((resolve) => {
      resolveUpdate = resolve;
    }));
    save.click();
    save.click();
    expect(fakes.update).toHaveBeenCalledTimes(1);
    resolveUpdate?.({ ok: true, data: DETAIL });
    await vi.waitFor(() => expect(root.querySelector('input[name="firstName"]')).toBeNull());
  });

  it('6.1 cancelar no envía y vuelve a la ficha', async () => {
    const fakes = fakeGateway();
    const root = mount(fakes.gateway);
    await openEdit(root, fakes);
    button(root, 'Cancelar').click();
    await vi.waitFor(() => expect(root.querySelector('input[name="firstName"]')).toBeNull());
    expect(fakes.update).not.toHaveBeenCalled();
    expect(root.querySelector('h1')?.textContent).toBe('Julieta Simulada');
  });

  it('6.2/6.3 validación local y 400 por campo sin title/detail ni petición inválida', async () => {
    const fakes = fakeGateway({ update: { ok: false, failure: { kind: 'invalid', errors: [{ field: 'phone', code: 'NotBlank' }] } } });
    const root = mount(fakes.gateway);
    await openEdit(root, fakes);
    const lastName = root.querySelector<HTMLInputElement>('input[name="lastName"]');
    if (lastName === null) {
      throw new Error('Apellido no encontrado');
    }
    lastName.value = '';
    button(root, 'Guardar cambios').click();
    expect(fakes.update).not.toHaveBeenCalled();
    expect(root.querySelector('#lastName-error')).not.toBeNull();

    lastName.value = 'Simulada';
    button(root, 'Guardar cambios').click();
    await vi.waitFor(() => expect(root.querySelector('#phone-error')).not.toBeNull());
    expect(root.querySelector<HTMLInputElement>('input[name="phone"]')?.value).toBe('11 5555 0001');
    expect(root.textContent).not.toContain('title');
    expect(root.textContent).not.toContain('detail');
  });

  it('6.3 el 400 del servidor conserva el borrador editado junto al error de campo', async () => {
    const fakes = fakeGateway({ update: { ok: false, failure: { kind: 'invalid', errors: [{ field: 'phone', code: 'NotBlank' }] } } });
    const root = mount(fakes.gateway);
    await openEdit(root, fakes);
    const phone = root.querySelector<HTMLInputElement>('input[name="phone"]');
    const address = root.querySelector<HTMLInputElement>('input[name="address"]');
    if (phone === null || address === null) {
      throw new Error('Formulario no encontrado');
    }
    phone.value = '11 5555 0099';
    address.value = 'Calle Falsa 999';
    button(root, 'Guardar cambios').click();
    await vi.waitFor(() => expect(root.querySelector('#phone-error')).not.toBeNull());
    expect(root.querySelector<HTMLInputElement>('input[name="phone"]')?.value).toBe('11 5555 0099');
    expect(root.querySelector<HTMLInputElement>('input[name="address"]')?.value).toBe('Calle Falsa 999');
  });

  it('6.3 TRIANGULATE: campo desconocido avisa y 404 muestra el copy propio sin title/detail', async () => {
    const unknown = fakeGateway({ update: { ok: false, failure: { kind: 'invalid', errors: [{ field: 'otro', code: 'Unknown' }] } } });
    const root = mount(unknown.gateway);
    await openEdit(root, unknown);
    button(root, 'Guardar cambios').click();
    await vi.waitFor(() => expect(root.textContent).toContain('El servidor rechazó un dato'));
    expect(root.querySelector('input[name="firstName"]')).not.toBeNull();
    expect(root.textContent).not.toContain('Unknown');

    const missing = fakeGateway({ update: { ok: false, failure: { kind: 'not-found' } } });
    const root2 = mount(missing.gateway);
    await openEdit(root2, missing);
    button(root2, 'Guardar cambios').click();
    await vi.waitFor(() => expect(root2.textContent).toContain('No encontramos este paciente'));
    expect(root2.textContent).not.toContain('Ficha actualizada');
    expect(root2.textContent).not.toContain('title');
    expect(root2.textContent).not.toContain('detail');
  });

  it('6.4 CSRF → aviso de página desactualizada; timeout → Ver ficha actual recalcula el marcador', async () => {
    const stale = fakeGateway({ update: { ok: false, failure: { kind: 'stale-page' } } });
    const root = mount(stale.gateway);
    await openEdit(root, stale);
    button(root, 'Guardar cambios').click();
    await vi.waitFor(() => expect(root.textContent).toContain('La página quedó desactualizada'));
    expect(root.textContent).not.toContain('Cambios guardados');

    const timeout = fakeGateway({ update: { ok: false, failure: { kind: 'connection' } } });
    const root2 = mount(timeout.gateway);
    await openEdit(root2, timeout);
    timeout.get.mockResolvedValueOnce({ ok: true, data: { ...DETAIL, reviewRequired: true } });
    button(root2, 'Guardar cambios').click();
    await vi.waitFor(() => expect(button(root2, 'Ver ficha actual')).toBeTruthy());
    button(root2, 'Ver ficha actual').click();
    await vi.waitFor(() => expect(root2.querySelector('.badge-review')).not.toBeNull());
    expect(timeout.get).toHaveBeenCalledTimes(2);
  });
});

describe('patients-view - baja lógica (D6)', () => {
  async function openDeactivate(root: HTMLElement, fakes: Fakes): Promise<void> {
    await loaded(root);
    root.querySelector<HTMLAnchorElement>('.pname')?.click();
    await vi.waitFor(() => expect(root.querySelector('h1')?.textContent).toBe('Julieta Simulada'));
    button(root, 'Dar de baja').focus();
    button(root, 'Dar de baja').click();
    await vi.waitFor(() => expect(document.querySelector('dialog')).not.toBeNull());
  }

  it('7.1 diálogo con foco seguro, texto de conservación y cancelación sin DELETE', async () => {
    const fakes = fakeGateway();
    const root = mount(fakes.gateway);
    await openDeactivate(root, fakes);
    const dialog = document.querySelector('dialog');
    expect(dialog?.textContent).toContain('Sus turnos y su historia clínica se conservan');
    expect(document.activeElement?.getAttribute('data-confirm')).toBe('cancel');
    dialog?.dispatchEvent(new Event('cancel', { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(document.querySelector('dialog')).toBeNull());
    expect(fakes.deactivate).not.toHaveBeenCalled();
    expect(document.activeElement?.getAttribute('data-action')).toBe('deactivate');
  });

  it('7.2 confirma una sola DELETE y vuelve al listado sin el paciente', async () => {
    const fakes = fakeGateway();
    const root = mount(fakes.gateway);
    await openDeactivate(root, fakes);
    fakes.search.mockResolvedValueOnce({ ok: true, data: [] });
    document.querySelector<HTMLButtonElement>('[data-confirm="accept"]')?.click();
    await vi.waitFor(() => expect(root.textContent).toContain('Paciente dado de baja'));
    expect(fakes.deactivate).toHaveBeenCalledTimes(1);
    expect(fakes.deactivate).toHaveBeenCalledWith('pac-001');
    expect(root.textContent).not.toContain('Eliminar definitivamente');
    button(root, 'Volver al listado').click();
    await vi.waitFor(() => expect(root.textContent).toContain('Todavía no hay pacientes'));
  });

  it('7.2 TRIANGULATE: ficha inactiva no ofrece otra baja ni controles de borrado físico', async () => {
    const fakes = fakeGateway({ detail: { ok: true, data: { ...DETAIL, active: false } } });
    const root = mount(fakes.gateway);
    await loaded(root);
    root.querySelector<HTMLAnchorElement>('.pname')?.click();
    await vi.waitFor(() => expect(root.textContent).toContain('Inactivo'));
    expect([...root.querySelectorAll('button')].some((b) => b.textContent?.includes('Dar de baja'))).toBe(false);
    expect([...root.querySelectorAll('button')].some((b) => b.textContent?.includes('Eliminar definitivamente'))).toBe(false);
  });

  it('7.3 404/403/red no afirman la baja y permiten reintentar', async () => {
    for (const failure of [{ kind: 'not-found' }, { kind: 'stale-page' }, { kind: 'connection' }, { kind: 'connection' }] as readonly PatientsFailure[]) {
      const fakes = fakeGateway({ deactivate: { ok: false, failure } });
      const root = mount(fakes.gateway);
      await openDeactivate(root, fakes);
      document.querySelector<HTMLButtonElement>('[data-confirm="accept"]')?.click();
      await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
      expect(root.textContent).toMatch(/No pudimos confirmar la baja|No encontramos este paciente|desactualizada/);
      expect(root.textContent).not.toMatch(/Paciente dado de baja/);
      expect(root.querySelector('input[name="firstName"]')).toBeNull();
      expect(button(root, 'Reintentar')).toBeTruthy();
      document.body.replaceChildren();
    }
  });
});

async function textEventually(root: HTMLElement, text: string): Promise<boolean> {
  try {
    await vi.waitFor(() => expect(root.textContent).toContain(text), { timeout: 300 });
    return true;
  } catch {
    return root.textContent?.includes(text) === true;
  }
}
