import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SettingsGateway } from './settings-gateway';
import { createWorkingHoursSection } from './working-hours-section';

const HOURS = [
  { weekday: 1, startTime: '09:00:00', endTime: '13:00:00' },
  { weekday: 1, startTime: '14:00:00', endTime: '18:00:00' },
  { weekday: 2, startTime: '09:00:00', endTime: '18:00:00' },
];

interface Setup {
  readonly root: HTMLElement;
  readonly getWorkingHours: ReturnType<typeof vi.fn>;
  readonly saveWorkingHours: ReturnType<typeof vi.fn>;
  readonly hours: () => unknown;
}

function mount(options: { getResult?: unknown; saveResult?: unknown } = {}): Setup {
  let getResult = options.getResult ?? { ok: true, data: HOURS };
  const getWorkingHours = vi.fn(async () => getResult);
  const saveWorkingHours = vi.fn(async () => options.saveResult ?? { ok: true, data: HOURS });
  const gateway = { getWorkingHours, saveWorkingHours } as unknown as Pick<
    SettingsGateway,
    'getWorkingHours' | 'saveWorkingHours'
  >;
  const root = createWorkingHoursSection({ gateway });
  document.body.replaceChildren(root);
  return { root, getWorkingHours, saveWorkingHours, hours: () => getResult };
}

function times(root: HTMLElement): HTMLInputElement[] {
  return [...root.querySelectorAll<HTMLInputElement>('input[type="time"]')];
}

function button(root: HTMLElement, text: string): HTMLButtonElement {
  const found = [...root.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.includes(text));
  if (found === undefined) {
    throw new Error(`falta botón ${text}`);
  }
  return found;
}

function addButton(root: HTMLElement, day: string): HTMLButtonElement {
  const found = root.querySelector<HTMLButtonElement>(`button[aria-label="Agregar tramo el ${day}"]`);
  if (found === null) {
    throw new Error(`falta botón de agregar tramo el ${day}`);
  }
  return found;
}

async function loaded(root: HTMLElement): Promise<void> {
  await vi.waitFor(() => expect(times(root).length).toBeGreaterThan(0));
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('working-hours-section - grilla y guardado (C-19 D4)', () => {
  it('muestra siete días y los dos tramos separados del lunes', async () => {
    const { root, getWorkingHours } = mount();

    await loaded(root);

    expect(getWorkingHours).toHaveBeenCalledTimes(1);
    expect(root.querySelectorAll('.day-block')).toHaveLength(7);
    expect(root.textContent).toContain('Lunes');
    expect(root.textContent).toContain('Domingo');
    expect(times(root).map((input) => input.value)).toEqual(['09:00', '13:00', '14:00', '18:00', '09:00', '18:00']);
  });

  it('guarda la lista completa enviando HH:mm:ss', async () => {
    const { root, saveWorkingHours } = mount();
    await loaded(root);

    button(root, 'Guardar horarios').click();

    await vi.waitFor(() => expect(saveWorkingHours).toHaveBeenCalledTimes(1));
    expect(saveWorkingHours).toHaveBeenCalledWith([
      { weekday: 1, startTime: '09:00', endTime: '13:00' },
      { weekday: 1, startTime: '14:00', endTime: '18:00' },
      { weekday: 2, startTime: '09:00', endTime: '18:00' },
    ]);
    expect(root.querySelector('.section-notice [role="status"]')?.textContent).toContain('Horarios guardados.');
  });

  it('muestra estado vacío con «Agregar tramo» y puede crear un tramo', async () => {
    const { root, saveWorkingHours } = mount({ getResult: { ok: true, data: [] } });
    await vi.waitFor(() => expect(root.textContent).toContain('Todavía no hay horarios de atención'));
    expect(root.textContent).toContain('Agregá tramos por día para habilitar los turnos en la reserva online.');

    addButton(root, 'lunes').click();
    const [start, end] = times(root);
    expect(start).toBeDefined();
    expect(end).toBeDefined();
    if (start === undefined || end === undefined) {
      return;
    }
    start.value = '09:00';
    end.value = '13:00';

    button(root, 'Guardar horarios').click();

    await vi.waitFor(() => expect(saveWorkingHours).toHaveBeenCalledTimes(1));
    expect(saveWorkingHours).toHaveBeenCalledWith([
      { weekday: 1, startTime: '09:00', endTime: '13:00' },
    ]);
  });

  it('en el estado vacío muestra los siete días, cada uno con su botón de agregar', async () => {
    const { root } = mount({ getResult: { ok: true, data: [] } });
    await vi.waitFor(() => expect(root.textContent).toContain('Todavía no hay horarios de atención'));

    expect(root.querySelectorAll('.day-block')).toHaveLength(7);
    for (const day of ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo']) {
      expect(addButton(root, day)).toBeDefined();
    }
  });

  it('agrega un tramo al martes y lo guarda con weekday 2', async () => {
    const { root, saveWorkingHours } = mount({ getResult: { ok: true, data: [] } });
    await vi.waitFor(() => expect(root.textContent).toContain('Todavía no hay horarios de atención'));

    addButton(root, 'martes').click();
    expect(root.querySelector('.day-block[data-weekday="2"] input[type="time"]')).not.toBeNull();
    expect(root.querySelector('.day-block[data-weekday="1"] input[type="time"]')).toBeNull();

    button(root, 'Guardar horarios').click();

    await vi.waitFor(() => expect(saveWorkingHours).toHaveBeenCalledTimes(1));
    expect(saveWorkingHours).toHaveBeenCalledWith([{ weekday: 2, startTime: '09:00', endTime: '13:00' }]);
  });

  it('agrega un tramo al viernes junto a los existentes y guarda ordenado por día', async () => {
    const { root, saveWorkingHours } = mount();
    await loaded(root);

    addButton(root, 'viernes').click();
    expect(root.querySelector('.day-block[data-weekday="5"] input[type="time"]')).not.toBeNull();

    button(root, 'Guardar horarios').click();

    await vi.waitFor(() => expect(saveWorkingHours).toHaveBeenCalledTimes(1));
    expect(saveWorkingHours).toHaveBeenCalledWith([
      { weekday: 1, startTime: '09:00', endTime: '13:00' },
      { weekday: 1, startTime: '14:00', endTime: '18:00' },
      { weekday: 2, startTime: '09:00', endTime: '18:00' },
      { weekday: 5, startTime: '09:00', endTime: '13:00' },
    ]);
  });

  it('agregar un tramo a un día anterior mantiene el error de validación en el campo correcto', async () => {
    const { root, saveWorkingHours } = mount();
    await loaded(root);

    addButton(root, 'lunes').click(); // nuevo tramo 09:00-13:00 se superpone con el primero
    button(root, 'Guardar horarios').click();

    expect(saveWorkingHours).not.toHaveBeenCalled();
    expect(root.querySelectorAll('[aria-invalid="true"]').length).toBeGreaterThan(0);
  });

  it('muestra error de carga por sección con «Reintentar»', async () => {
    const { root, getWorkingHours } = mount({
      getResult: { ok: false, failure: { kind: 'connection' } },
    });
    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());

    expect(root.textContent).toContain('Reintentar');
    // El mock cambia a éxito antes de reintentar.
    getWorkingHours.mockImplementation(async () => ({ ok: true, data: HOURS }));
    button(root, 'Reintentar').click();

    await loaded(root);
    expect(getWorkingHours).toHaveBeenCalledTimes(2);
  });
});

describe('working-hours-section - validación y borde (C-19 D3/D5)', () => {
  it('rechaza un tramo invertido sin guardar y marca inicio y fin', async () => {
    const { root, saveWorkingHours } = mount();
    await loaded(root);
    const [start, end] = times(root);
    if (start === undefined || end === undefined) {
      throw new Error('faltan campos de tiempo');
    }
    start.value = '18:00';
    end.value = '09:00';

    button(root, 'Guardar horarios').click();

    expect(saveWorkingHours).not.toHaveBeenCalled();
    expect(start.getAttribute('aria-invalid')).toBe('true');
    expect(end.getAttribute('aria-invalid')).toBe('true');
    expect(root.textContent).toContain('posterior');
  });

  it('rechaza un solape dentro del mismo día sin guardar', async () => {
    const { root, saveWorkingHours } = mount();
    await loaded(root);
    const [, , secondStart, secondEnd] = times(root);
    if (secondStart === undefined || secondEnd === undefined) {
      throw new Error('faltan campos del segundo tramo');
    }
    secondStart.value = '12:30';
    secondEnd.value = '18:00';

    button(root, 'Guardar horarios').click();

    expect(saveWorkingHours).not.toHaveBeenCalled();
    expect(secondStart.getAttribute('aria-invalid')).toBe('true');
    expect(secondEnd.getAttribute('aria-invalid')).toBe('true');
    expect(root.textContent).toContain('superpone');
  });

  it('acepta tramos adyacentes y guarda', async () => {
    const { root, saveWorkingHours } = mount();
    await loaded(root);
    const [, , secondStart] = times(root);
    if (secondStart === undefined) {
      throw new Error('falta inicio del segundo tramo');
    }
    secondStart.value = '13:00';

    button(root, 'Guardar horarios').click();

    await vi.waitFor(() => expect(saveWorkingHours).toHaveBeenCalledTimes(1));
  });

  it('muestra la advertencia sobre turnos confirmados antes de guardar', async () => {
    const { root } = mount();
    await loaded(root);

    expect(root.textContent).toContain('Los turnos confirmados no se cancelan automáticamente. Si cambiás los horarios, revisalos en la agenda.');
  });

  it('no interpreta como HTML un payload hostil en un horario', async () => {
    const hostile = '<script>alert(1)</script>';
    const { root } = mount({
      getResult: { ok: true, data: [{ weekday: 1, startTime: hostile, endTime: '13:00' }] },
    });
    await loaded(root);

    expect(root.querySelector('script')).toBeNull();
    expect(root.innerHTML).not.toContain(hostile);
  });

  it('no rompe otras secciones montadas en la misma vista', async () => {
    const { root } = mount();
    const other = document.createElement('div');
    other.replaceChildren();
    const sentinel = document.createElement('input');
    sentinel.id = 'otra-seccion';
    other.append(sentinel);
    document.body.append(other);
    await loaded(root);

    times(root)[0]?.setAttribute('aria-invalid', 'true');
    button(root, 'Guardar horarios').click();

    expect(document.querySelector('#otra-seccion')).not.toBeNull();
  });
});
