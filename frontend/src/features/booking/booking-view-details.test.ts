import { afterEach, describe, expect, it } from 'vitest';
import type { BookResult } from './booking-types';
import {
  SLOTS_24,
  button,
  chooseService,
  click,
  dayButton,
  deferred,
  makeGateway,
  requireElement,
  settle,
  slotButtons,
  startHarness,
  text,
  type Harness,
} from './test-harness';

let harness: Harness | undefined;

afterEach(() => {
  harness?.stop();
  harness = undefined;
  window.history.replaceState(null, '', '/');
});

async function start(options: Parameters<typeof startHarness>[0] = {}): Promise<Harness> {
  harness = await startHarness(options);
  return harness;
}

function field(root: ParentNode, id: string): HTMLInputElement {
  return requireElement<HTMLInputElement>(root, `#${id}`);
}

function fillForm(root: ParentNode, overrides: Partial<Record<string, string>> = {}): void {
  const values: Record<string, string> = {
    firstName: 'Ana',
    lastName: 'Pérez',
    dni: '30456789',
    phone: '11 2345 6789',
    email: 'ana@ejemplo.com',
    ...overrides,
  };
  for (const [id, value] of Object.entries(values)) {
    const input = field(root, id);
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
  const privacy = field(root, 'privacyAccepted');
  privacy.checked = true;
  privacy.dispatchEvent(new Event('change', { bubbles: true }));
}

async function toDetails(h: Harness): Promise<void> {
  await chooseService(h.root, 'Limpieza');
  await click(dayButton(h.root, 'martes 24 de noviembre'));
  await click(button(h.root, '09:30'));
  await click(button(h.root, 'Continuar'));
}

function submit(root: ParentNode): Promise<void> {
  requireElement<HTMLFormElement>(root, 'form').requestSubmit();
  return settle();
}

const CONFIRMED: BookResult = {
  kind: 'confirmed',
  serviceName: 'Limpieza',
  startsAt: '2026-11-24T12:30:00Z',
  endsAt: '2026-11-24T13:15:00Z',
  practiceName: 'Consultorio La Muela',
};

describe('paso 3: envio de la reserva', () => {
  it('continuar desde el paso 2 lleva al paso 3 con el resumen', async () => {
    const h = await start();

    await toDetails(h);

    expect(window.location.search).toBe('?paso=3');
    expect(text(requireElement(h.root, '.stepper'))).toContain('Paso 3 de 3');
    expect(text(requireElement(h.root, '.choice-summary'))).toContain('martes 24 de noviembre · 09:30 – 10:00');
    expect(document.activeElement).toBe(requireElement(h.root, 'h1'));
  });

  it('un envio vacio no llama al servidor y enfoca Nombre', async () => {
    const h = await start();
    await toDetails(h);

    await submit(h.root);

    expect(h.gateway.book).not.toHaveBeenCalled();
    expect(h.root.querySelectorAll('.error-msg')).toHaveLength(6);
    expect(document.activeElement).toBe(field(h.root, 'firstName'));
  });

  it('un envio valido hace una unica llamada con el cuerpo exacto y website vacio', async () => {
    const pending = deferred<BookResult>();
    const gateway = makeGateway({ book: () => pending.promise });
    const h = await start({ gateway });
    await toDetails(h);
    fillForm(h.root);

    await submit(h.root);
    await submit(h.root);

    expect(gateway.book).toHaveBeenCalledTimes(1);
    expect(gateway.book).toHaveBeenCalledWith({
      serviceId: 's-2',
      startsAt: '2026-11-24T12:30:00Z',
      form: {
        firstName: 'Ana',
        lastName: 'Pérez',
        dni: '30456789',
        phone: '11 2345 6789',
        email: 'ana@ejemplo.com',
        website: '',
        privacyAccepted: true,
      },
    });
    expect(button(h.root, 'Reservando…').getAttribute('aria-busy')).toBe('true');
    expect(field(h.root, 'firstName').readOnly).toBe(true);

    pending.resolve(CONFIRMED);
    await settle();
  });

  it('atras y adelante entre los pasos 2 y 3 conservan horario y datos cargados', async () => {
    const h = await start();
    await toDetails(h);
    fillForm(h.root);

    window.history.back();
    await settle();

    expect(window.location.search).toBe('?paso=2');
    expect(button(h.root, '09:30').getAttribute('aria-pressed')).toBe('true');

    window.history.forward();
    await settle();

    expect(window.location.search).toBe('?paso=3');
    expect(field(h.root, 'firstName').value).toBe('Ana');
    expect(field(h.root, 'email').value).toBe('ana@ejemplo.com');
    expect(field(h.root, 'privacyAccepted').checked).toBe(true);
  });

  it('Volver conserva el horario y los datos', async () => {
    const h = await start();
    await toDetails(h);
    fillForm(h.root);

    await click(button(h.root, 'Volver'));
    await click(button(h.root, 'Continuar'));

    expect(field(h.root, 'lastName').value).toBe('Pérez');
  });
});

describe('paso 3: rechazos del servidor', () => {
  async function rejected(result: BookResult): Promise<Harness> {
    const gateway = makeGateway({ book: async () => result });
    const h = await start({ gateway });
    await toDetails(h);
    fillForm(h.root);
    await submit(h.root);
    return h;
  }

  it('fields marca el campo con mensaje propio, enfoca y conserva los datos', async () => {
    const h = await rejected({ kind: 'fields', errors: [{ field: 'email', code: 'Email' }] });

    expect(text(requireElement(h.root, '#email-error'))).toBe('Ingresá un email válido.');
    expect(document.activeElement).toBe(field(h.root, 'email'));
    expect(field(h.root, 'firstName').value).toBe('Ana');
  });

  it('rate-limited muestra la espera, conserva datos y rehabilita el boton', async () => {
    const h = await rejected({ kind: 'rate-limited', retryAfterSeconds: 40 });

    expect(text(requireElement(h.root, '[role="alert"]'))).toContain('Demasiados intentos seguidos');
    expect(text(h.root)).toContain('Probá de nuevo en 1 minuto.');
    expect(field(h.root, 'dni').value).toBe('30456789');
    expect(button(h.root, 'Confirmar reserva').hasAttribute('aria-busy')).toBe(false);
    expect(window.location.search).toBe('?paso=3');
  });

  it.each<[BookResult, string]>([
    [{ kind: 'contact-limit' }, 'Ya tenés turnos reservados'],
    [{ kind: 'stale-page' }, 'La página quedó desactualizada'],
    [{ kind: 'unknown-outcome' }, 'No pudimos confirmar tu reserva'],
  ])('%j muestra su aviso y conserva los datos', async (result, title) => {
    const h = await rejected(result);

    expect(text(requireElement(h.root, '[role="alert"]'))).toContain(title);
    expect(field(h.root, 'email').value).toBe('ana@ejemplo.com');
    expect(h.gateway.book).toHaveBeenCalledTimes(1);
  });

  it('service-gone vuelve al paso 1 con aviso y catalogo recargado', async () => {
    const h = await rejected({ kind: 'service-gone' });

    expect(window.location.search).toBe('');
    expect(text(requireElement(h.root, '.stepper'))).toContain('Paso 1 de 3');
    expect(text(h.root)).toContain('Esa prestación ya no está disponible para reservar online');
    expect(h.gateway.listServices).toHaveBeenCalledTimes(2);
  });
});

describe('409: horario tomado mientras se reservaba', () => {
  const FIRST_LOAD = [
    { startsAt: '2026-11-24T13:30:00Z', endsAt: '2026-11-24T14:15:00Z' },
    { startsAt: '2026-11-24T18:00:00Z', endsAt: '2026-11-24T18:45:00Z' },
  ];
  const AFTER_CONFLICT = [
    { startsAt: '2026-11-24T13:00:00Z', endsAt: '2026-11-24T13:45:00Z' },
    { startsAt: '2026-11-24T14:00:00Z', endsAt: '2026-11-24T14:45:00Z' },
    { startsAt: '2026-11-24T14:30:00Z', endsAt: '2026-11-24T15:15:00Z' },
    { startsAt: '2026-11-24T18:00:00Z', endsAt: '2026-11-24T18:45:00Z' },
  ];

  async function conflict(reloaded: typeof SLOTS_24): Promise<Harness> {
    let calls = 0;
    const gateway = makeGateway({
      availability: async (_id, date) => {
        if (date !== '2026-11-24') {
          return { kind: 'ok', slots: [] };
        }
        calls += 1;
        return { kind: 'ok', slots: calls === 1 ? FIRST_LOAD : reloaded };
      },
      book: async () => ({ kind: 'slot-unavailable' }),
    });
    const h = await start({ gateway });
    await chooseService(h.root, 'Limpieza');
    await click(dayButton(h.root, 'martes 24 de noviembre'));
    await click(button(h.root, '10:30'));
    await click(button(h.root, 'Continuar'));
    fillForm(h.root);
    await submit(h.root);
    return h;
  }

  it('vuelve al paso 2 del mismo dia, recarga y sugiere los 3 horarios mas cercanos', async () => {
    const h = await conflict(AFTER_CONFLICT);

    expect(window.location.search).toBe('?paso=2');
    expect(text(requireElement(h.root, '[role="alert"]'))).toContain('Ese horario se acaba de ocupar');
    expect(h.gateway.availability).toHaveBeenLastCalledWith('s-2', '2026-11-24');
    expect(slotButtons(h.root, '.suggested')).toEqual(['10:00', '11:00', '11:30']);
    expect(h.root.querySelector('button.slot[aria-pressed="true"]')).toBeNull();
    expect(dayButton(h.root, 'martes 24 de noviembre').getAttribute('aria-pressed')).toBe('true');
  });

  it('elegir una sugerencia y continuar conserva los datos del formulario', async () => {
    const h = await conflict(AFTER_CONFLICT);

    const suggestion = [...h.root.querySelectorAll<HTMLButtonElement>('.suggested button.slot')].find(
      (b) => b.textContent === '11:00',
    );
    await click(suggestion as HTMLButtonElement);
    await click(button(h.root, 'Continuar'));

    expect(window.location.search).toBe('?paso=3');
    expect(field(h.root, 'firstName').value).toBe('Ana');
    expect(field(h.root, 'email').value).toBe('ana@ejemplo.com');
    expect(text(requireElement(h.root, '.choice-summary'))).toContain('11:00 – 11:45');
  });

  it('con el dia vacio muestra el aviso junto al estado vacio y "Ver el dia siguiente"', async () => {
    const h = await conflict([]);

    expect(text(requireElement(h.root, '[role="alert"]'))).toContain('Ese horario se acaba de ocupar');
    expect(text(requireElement(h.root, '.empty'))).toContain('No hay horarios disponibles este día.');
    expect(button(h.root, 'Ver el día siguiente')).toBeTruthy();
  });
});

describe('confirmacion', () => {
  it('201 navega a /reservar/confirmado reemplazando la entrada, con el email enmascarado', async () => {
    const gateway = makeGateway({ book: async () => CONFIRMED });
    const h = await start({ gateway });
    await toDetails(h);
    fillForm(h.root, { email: 'maria@gmail.com' });

    await submit(h.root);

    expect(window.location.pathname + window.location.search).toBe('/reservar/confirmado');
    expect(requireElement(h.root, 'h1').textContent).toBe('¡Turno reservado!');
    expect(text(h.root)).toContain('ma•••@gmail.com');
    expect(text(h.root)).not.toContain('maria@gmail.com');
    expect(text(h.root)).toContain('martes 24 de noviembre');
  });

  it('atras desde la confirmacion no reenvia y el flujo empieza sin datos cargados', async () => {
    const gateway = makeGateway({ book: async () => CONFIRMED });
    const h = await start({ gateway });
    await toDetails(h);
    fillForm(h.root);
    await submit(h.root);

    window.history.back();
    await settle();

    expect(gateway.book).toHaveBeenCalledTimes(1);
    expect(window.location.pathname + window.location.search).toBe('/reservar');
    expect(text(requireElement(h.root, '.stepper'))).toContain('Paso 1 de 3');
    expect(requireElement<HTMLInputElement>(h.root, 'input[value="s-2"]').checked).toBe(false);
  });

  it('200 ACCEPTED del honeypot navega a la confirmacion generica', async () => {
    const gateway = makeGateway({ book: async () => ({ kind: 'accepted' }) });
    const h = await start({ gateway });
    await toDetails(h);
    fillForm(h.root);

    await submit(h.root);

    expect(window.location.pathname).toBe('/reservar/confirmado');
    expect(requireElement(h.root, 'h1').textContent).toBe('Revisá tu email');
    expect(h.root.querySelector('dl')).toBeNull();
  });

  it('abrir la confirmacion directo muestra la generica', async () => {
    const h = await start({ url: '/reservar/confirmado' });

    expect(requireElement(h.root, 'h1').textContent).toBe('Revisá tu email');
  });
});
