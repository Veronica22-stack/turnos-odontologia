import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SettingsGateway } from './settings-gateway';
import { createPracticeSection } from './practice-section';

const PRACTICE = {
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
const { id: _id, ...INPUT } = PRACTICE;
const ORIGIN = 'https://consultorio.ejemplo';

interface Setup {
  readonly root: HTMLElement;
  readonly getPractice: ReturnType<typeof vi.fn>;
  readonly savePractice: ReturnType<typeof vi.fn>;
  readonly writeText: ReturnType<typeof vi.fn>;
}

function mount(
  options: { saveResult?: unknown; getResult?: unknown; clipboard?: 'ok' | 'reject' | 'absent' } = {},
): Setup {
  const getPractice = vi.fn(async () => options.getResult ?? { ok: true, data: PRACTICE });
  const savePractice = vi.fn(async () => options.saveResult ?? { ok: true, data: PRACTICE });
  const writeText = vi.fn(async (_text: string) => {
    if (options.clipboard === 'reject') {
      throw new Error('denegado');
    }
  });
  const clipboard = options.clipboard === 'absent' ? undefined : { writeText };
  Object.defineProperty(navigator, 'clipboard', { value: clipboard, configurable: true });
  const gateway = { getPractice, savePractice } as unknown as Pick<SettingsGateway, 'getPractice' | 'savePractice'>;
  const root = createPracticeSection({ gateway, origin: ORIGIN });
  document.body.replaceChildren(root);
  return { root, getPractice, savePractice, writeText };
}

function input(root: HTMLElement, id: string): HTMLInputElement {
  const el = root.querySelector<HTMLInputElement>(`#${id}`);
  if (el === null) {
    throw new Error(`falta #${id}`);
  }
  return el;
}

function button(root: HTMLElement, text: string): HTMLButtonElement {
  const found = [...root.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.includes(text));
  if (found === undefined) {
    throw new Error(`falta botón ${text}`);
  }
  return found;
}

function select(root: HTMLElement, id: string): HTMLSelectElement {
  const el = root.querySelector<HTMLSelectElement>(`#${id}`);
  if (el === null) {
    throw new Error(`falta #${id}`);
  }
  return el;
}

async function loaded(root: HTMLElement, expectedName = PRACTICE.name): Promise<void> {
  await vi.waitFor(() => expect(input(root, 'practice-name').value).toBe(expectedName));
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('practice-section - carga y guardado (C-19 D7)', () => {
  it('carga los valores de GET /practice/settings en el formulario', async () => {
    const { root, getPractice } = mount();

    await loaded(root);

    expect(getPractice).toHaveBeenCalledTimes(1);
    expect(input(root, 'practice-name').value).toBe(PRACTICE.name);
    expect(input(root, 'practice-tz').value).toBe(PRACTICE.timezone);
    expect(input(root, 'practice-email').value).toBe(PRACTICE.contactEmail ?? '');
    expect(input(root, 'practice-phone').value).toBe(PRACTICE.contactPhone ?? '');
    expect(input(root, 'practice-granularity').value).toBe('15');
    expect(input(root, 'practice-notice').value).toBe('120');
    expect(input(root, 'practice-horizon').value).toBe('30');
    expect(input(root, 'practice-reminder').value).toBe('24');
  });

  it('guarda los values vigentes con timezone inalterado y anuncia el éxito', async () => {
    const { root, savePractice } = mount();
    await loaded(root);

    input(root, 'practice-name').value = 'Consultorio Nuevo';
    button(root, 'Guardar datos del consultorio').click();

    await vi.waitFor(() => expect(savePractice).toHaveBeenCalledTimes(1));
    expect(savePractice).toHaveBeenCalledWith({
      ...INPUT,
      name: 'Consultorio Nuevo',
      timezone: PRACTICE.timezone,
    });
    await vi.waitFor(() => expect(root.querySelector('[role="status"]')?.textContent).toContain('Cambios guardados.'));
  });

  it('compone el link con new URL y lo copia con navigator.clipboard', async () => {
    const { root, writeText } = mount();
    await loaded(root);

    button(root, 'Copiar link público de reserva').click();

    await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith(`${ORIGIN}/reservar`));
    expect(root.querySelector('[role="status"]')?.textContent).toContain('Link copiado.');
  });
});

describe('practice-section - errores y borde (C-19 D3/D6/D7)', () => {
  it('vincula 400 VALIDATION_FAILED al campo correspondiente', async () => {
    const { root, savePractice } = mount({
      saveResult: {
        ok: false,
        failure: { kind: 'fields', errors: [{ field: 'name', code: 'NotBlank' }] },
      },
    });
    await loaded(root);

    button(root, 'Guardar datos del consultorio').click();

    await vi.waitFor(() => expect(savePractice).toHaveBeenCalledTimes(1));
    const name = input(root, 'practice-name');
    expect(name.getAttribute('aria-invalid')).toBe('true');
    expect(name.getAttribute('aria-describedby')).toContain('practice-name-error');
    expect(root.querySelector('#practice-name-error')?.textContent).toContain('obligatorio');
  });

  it('rechaza granularidad 25 localmente sin llamar a savePractice', async () => {
    const { root, savePractice } = mount();
    await loaded(root);
    const granularity = select(root, 'practice-granularity');
    const option = document.createElement('option');
    option.value = '25';
    option.textContent = '25';
    granularity.append(option);
    granularity.value = '25';

    button(root, 'Guardar datos del consultorio').click();

    expect(savePractice).not.toHaveBeenCalled();
    expect(granularity.getAttribute('aria-invalid')).toBe('true');
    expect(root.textContent).toContain('10, 15, 20, 30 o 60');
  });

  it('muestra copy de recarga ante 403 CSRF_REQUIRED', async () => {
    const { root } = mount({ saveResult: { ok: false, failure: { kind: 'stale-page' } } });
    await loaded(root);

    button(root, 'Guardar datos del consultorio').click();

    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')?.textContent).toContain('desactualizada'));
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Recargá');
  });

  it('revela un input readonly cuando el portapapeles rechaza o no existe', async () => {
    for (const clipboard of ['reject', 'absent'] as const) {
      const { root } = mount({ clipboard });
      await loaded(root);

      button(root, 'Copiar link público de reserva').click();

      await vi.waitFor(() => expect(root.querySelector('#public-link')).not.toBeNull());
      const fallback = root.querySelector<HTMLInputElement>('#public-link');
      expect(fallback?.readOnly).toBe(true);
      expect(fallback?.value).toBe(`${ORIGIN}/reservar`);
      expect(root.querySelector('[role="status"]')?.textContent).toContain('No pudimos copiar automáticamente.');
      document.body.replaceChildren();
    }
  });

  it('inserta un nombre hostil como texto, sin crear elementos img', async () => {
    const hostile = '<img src=x onerror=alert(1)>';
    const { root } = mount({ getResult: { ok: true, data: { ...PRACTICE, name: hostile } } });

    await loaded(root, hostile);

    expect(input(root, 'practice-name').value).toBe(hostile);
    expect(root.querySelector('img')).toBeNull();
  });
});
