import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SettingsGateway } from './settings-gateway';
import type { ScheduleBlock } from './settings-types';
import { createBlocksSection, instantToLocalInput, localInputToInstant } from './blocks-section';

const BLOCK: ScheduleBlock = {
  id: 'b1',
  startsAt: '2026-03-18T12:00:00Z',
  endsAt: '2026-03-18T14:00:00Z',
  reason: 'Mantenimiento',
  createdAt: '2026-03-10T10:00:00Z',
  updatedAt: '2026-03-10T10:00:00Z',
  affectedAppointments: [],
};

interface Setup {
  readonly root: HTMLElement;
  readonly getBlocks: ReturnType<typeof vi.fn>;
  readonly createBlock: ReturnType<typeof vi.fn>;
  readonly updateBlock: ReturnType<typeof vi.fn>;
  readonly deleteBlock: ReturnType<typeof vi.fn>;
}

function mount(options: { getResult?: unknown; createResult?: unknown; updateResult?: unknown; deleteResult?: unknown } = {}): Setup {
  let getResult = options.getResult ?? { ok: true, data: [BLOCK] };
  const getBlocks = vi.fn(async () => getResult);
  const createBlock = vi.fn(async () => options.createResult ?? { ok: true, data: BLOCK });
  const updateBlock = vi.fn(async () => options.updateResult ?? { ok: true, data: BLOCK });
  const deleteBlock = vi.fn(async () => options.deleteResult ?? { ok: true, data: undefined });
  const gateway = { getBlocks, createBlock, updateBlock, deleteBlock } as unknown as Pick<
    SettingsGateway,
    'getBlocks' | 'createBlock' | 'updateBlock' | 'deleteBlock'
  >;
  const root = createBlocksSection({ gateway });
  document.body.replaceChildren(root);
  return { root, getBlocks, createBlock, updateBlock, deleteBlock };
}

function findButton(root: ParentNode, text: string): HTMLButtonElement {
  const found = [...root.querySelectorAll<HTMLButtonElement>('button')].find((button) => button.textContent === text);
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
  await vi.waitFor(() => expect(root.textContent).toContain('Mantenimiento'));
}

async function confirmDialog(accept: boolean): Promise<void> {
  await vi.waitFor(() => expect(document.querySelector('dialog')).not.toBeNull());
  document.querySelector<HTMLButtonElement>(`[data-confirm="${accept ? 'accept' : 'cancel'}"]`)?.click();
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('blocks-section - listado, alta y conversión (C-19 D4/D9)', () => {
  it('las funciones puras de conversión local↔UTC hacen round-trip', () => {
    expect(instantToLocalInput('2026-03-18T12:00:00Z')).toBe('2026-03-18T09:00');
    expect(localInputToInstant('2026-03-18T09:00')).toBe('2026-03-18T12:00:00.000Z');
    expect(localInputToInstant('no-es-una-fecha')).toBeUndefined();
  });

  it('lista el bloqueo en hora del consultorio, sin depender de la zona del navegador', async () => {
    const { root, getBlocks } = mount();

    await loaded(root);

    expect(getBlocks).toHaveBeenCalledTimes(1);
    expect(root.textContent).toContain('18/03/2026');
    expect(root.textContent).toContain('09:00');
    expect(root.textContent).toContain('11:00');
    expect(root.textContent).not.toContain('13:00');
    expect(root.textContent).toContain('Motivo: Mantenimiento');
  });

  it('crea un bloqueo desde datetime-local enviando ISO con zona', async () => {
    const { root, createBlock } = mount();
    await loaded(root);

    findButton(root, 'Nuevo bloqueo').click();
    field(root, 'block-start').value = '2026-03-23T09:00';
    field(root, 'block-end').value = '2026-03-23T11:00';
    field(root, 'block-reason').value = 'Mantenimiento';
    findButton(root, 'Guardar bloqueo').click();

    await vi.waitFor(() => expect(createBlock).toHaveBeenCalledTimes(1));
    expect(createBlock).toHaveBeenCalledWith({
      startsAt: '2026-03-23T12:00:00.000Z',
      endsAt: '2026-03-23T14:00:00.000Z',
      reason: 'Mantenimiento',
    });
  });

  it('muestra el aviso con los turnos afectados devueltos, sin IDs visibles', async () => {
    const affected: ScheduleBlock = {
      ...BLOCK,
      startsAt: '2026-03-23T12:00:00Z',
      endsAt: '2026-03-23T14:00:00Z',
      affectedAppointments: [
        { appointmentId: 'turno-secreto-1', startsAt: '2026-03-23T12:30:00Z', endsAt: '2026-03-23T13:00:00Z' },
        { appointmentId: 'turno-secreto-2', startsAt: '2026-03-23T13:15:00Z', endsAt: '2026-03-23T13:45:00Z' },
      ],
    };
    const { root } = mount({ createResult: { ok: true, data: affected } });
    await loaded(root);

    findButton(root, 'Nuevo bloqueo').click();
    field(root, 'block-start').value = '2026-03-23T09:00';
    field(root, 'block-end').value = '2026-03-23T11:00';
    field(root, 'block-reason').value = 'Mantenimiento';
    findButton(root, 'Guardar bloqueo').click();

    await vi.waitFor(() => expect(root.querySelector('[role="status"]')?.textContent).toContain('2 turnos quedan dentro del rango'));
    expect(root.textContent).toContain('09:30 – 10:00');
    expect(root.textContent).toContain('10:15 – 10:45');
    expect(root.textContent).not.toContain('turno-secreto');
  });
});

describe('blocks-section - edición, baja y estados (C-19 D8)', () => {
  it('edita un bloqueo reenviando el cuerpo con zona', async () => {
    const { root, updateBlock } = mount();
    await loaded(root);

    const edit = [...root.querySelectorAll<HTMLButtonElement>('button')].find((button) => button.textContent === 'Editar');
    edit?.click();
    field(root, 'block-reason').value = 'Capacitación';
    findButton(root, 'Guardar bloqueo').click();

    await vi.waitFor(() => expect(updateBlock).toHaveBeenCalledTimes(1));
    expect(updateBlock).toHaveBeenCalledWith('b1', {
      startsAt: '2026-03-18T12:00:00.000Z',
      endsAt: '2026-03-18T14:00:00.000Z',
      reason: 'Capacitación',
    });
  });

  it('elimina tras confirmación', async () => {
    const { root, deleteBlock } = mount();
    await loaded(root);

    findButton(root, 'Eliminar').click();
    await confirmDialog(true);

    await vi.waitFor(() => expect(deleteBlock).toHaveBeenCalledWith('b1'));
  });

  it('muestra estado vacío con acción de alta', async () => {
    const { root } = mount({ getResult: { ok: true, data: [] } });
    await vi.waitFor(() => expect(root.textContent).toContain('Todavía no hay bloqueos'));

    findButton(root, 'Nuevo bloqueo').click();
    expect(root.querySelector('#block-start')).not.toBeNull();
  });

  it('muestra error por sección con Reintentar sin romper otras secciones', async () => {
    const { root, getBlocks } = mount({ getResult: { ok: false, failure: { kind: 'connection' } } });
    const other = document.createElement('div');
    const sentinel = document.createElement('input');
    sentinel.id = 'otra-seccion';
    other.append(sentinel);
    document.body.append(other);
    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());

    expect(root.textContent).toContain('Reintentar');
    getBlocks.mockImplementation(async () => ({ ok: true, data: [BLOCK] }));
    findButton(root, 'Reintentar').click();

    await loaded(root);
    expect(getBlocks).toHaveBeenCalledTimes(2);
    expect(document.querySelector('#otra-seccion')).not.toBeNull();
  });
});

describe('blocks-section - validación y errores de negocio (C-19 D3/D5/D8/D9)', () => {
  it('rechaza fin anterior al inicio sin enviar la solicitud', async () => {
    const { root, createBlock } = mount();
    await loaded(root);

    findButton(root, 'Nuevo bloqueo').click();
    field(root, 'block-start').value = '2026-03-23T11:00';
    field(root, 'block-end').value = '2026-03-23T09:00';
    field(root, 'block-reason').value = 'Mantenimiento';
    findButton(root, 'Guardar bloqueo').click();

    expect(createBlock).not.toHaveBeenCalled();
    expect(field(root, 'block-start').getAttribute('aria-invalid')).toBe('true');
    expect(field(root, 'block-end').getAttribute('aria-invalid')).toBe('true');
    expect(root.textContent).toContain('posterior a la de inicio');
  });

  it('rechaza motivo vacío sin enviar la solicitud', async () => {
    const { root, createBlock } = mount();
    await loaded(root);

    findButton(root, 'Nuevo bloqueo').click();
    field(root, 'block-start').value = '2026-03-23T09:00';
    field(root, 'block-end').value = '2026-03-23T11:00';
    field(root, 'block-reason').value = '   ';
    findButton(root, 'Guardar bloqueo').click();

    expect(createBlock).not.toHaveBeenCalled();
    expect(field(root, 'block-reason').getAttribute('aria-invalid')).toBe('true');
    expect(root.textContent).toContain('obligatorio');
  });

  it('muestra copy de conflicto ante 409 SCHEDULE_BLOCK_OVERLAP y conserva el formulario', async () => {
    const { root, createBlock } = mount({ createResult: { ok: false, failure: { kind: 'conflict' } } });
    await loaded(root);

    findButton(root, 'Nuevo bloqueo').click();
    field(root, 'block-start').value = '2026-03-23T09:00';
    field(root, 'block-end').value = '2026-03-23T11:00';
    field(root, 'block-reason').value = 'Reparación';
    findButton(root, 'Guardar bloqueo').click();

    await vi.waitFor(() => expect(createBlock).toHaveBeenCalledTimes(1));
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Conflicto: el rango se superpone');
    expect(root.querySelector('#block-start')).not.toBeNull();
    expect(field(root, 'block-reason').value).toBe('Reparación');
  });

  it('muestra copy propio ante 400 INVALID_RANGE', async () => {
    const { root, createBlock } = mount({ createResult: { ok: false, failure: { kind: 'range' } } });
    await loaded(root);

    findButton(root, 'Nuevo bloqueo').click();
    field(root, 'block-start').value = '2026-03-23T09:00';
    field(root, 'block-end').value = '2026-03-23T11:00';
    field(root, 'block-reason').value = 'Mantenimiento';
    findButton(root, 'Guardar bloqueo').click();

    await vi.waitFor(() => expect(createBlock).toHaveBeenCalledTimes(1));
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('El rango no es válido.');
  });

  it('recarga el listado ante 404 SCHEDULE_BLOCK_NOT_FOUND al eliminar', async () => {
    const { root, getBlocks, deleteBlock } = mount({ deleteResult: { ok: false, failure: { kind: 'not-found' } } });
    await loaded(root);

    findButton(root, 'Eliminar').click();
    await confirmDialog(true);

    await vi.waitFor(() => expect(deleteBlock).toHaveBeenCalledWith('b1'));
    await vi.waitFor(() => expect(getBlocks).toHaveBeenCalledTimes(2));
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('ya no existe');
  });

  it('no envía la eliminación si se cancela la confirmación', async () => {
    const { root, deleteBlock } = mount();
    await loaded(root);

    findButton(root, 'Eliminar').click();
    await confirmDialog(false);

    expect(deleteBlock).not.toHaveBeenCalled();
  });

  it('usa texto honesto cuando no hay turnos afectados', async () => {
    const { root } = mount();
    await loaded(root);

    findButton(root, 'Nuevo bloqueo').click();
    field(root, 'block-start').value = '2026-03-23T09:00';
    field(root, 'block-end').value = '2026-03-23T11:00';
    field(root, 'block-reason').value = 'Mantenimiento';
    findButton(root, 'Guardar bloqueo').click();

    await vi.waitFor(() => expect(root.querySelector('[role="status"]')).not.toBeNull());
    expect(root.querySelector('[role="status"]')?.textContent).toContain(
      'No hay turnos confirmados dentro del rango según la información disponible',
    );
  });

  it('muestra 09:00 de Buenos Aires para un instante 12:00 UTC con independencia del navegador', async () => {
    const { root } = mount();
    await loaded(root);

    expect(root.textContent).toContain('09:00 – 11:00');
    expect(root.textContent).not.toContain('12:00');
    expect(root.textContent).not.toContain('13:00');
  });
});
