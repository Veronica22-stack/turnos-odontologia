import { afterEach, describe, expect, it } from 'vitest';
import {
  SERVICES,
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

describe('paso 1: prestaciones', () => {
  it('muestra el estado de carga y luego las prestaciones con duracion y descripcion', async () => {
    const pending = deferred<{ kind: 'ok'; services: typeof SERVICES }>();
    const gateway = makeGateway({ services: () => pending.promise });
    const { root } = await start({ gateway });

    expect(requireElement(root, '[role="status"]').textContent).toContain('Cargando');

    pending.resolve({ kind: 'ok', services: SERVICES });
    await settle();

    expect(text(requireElement(root, '.stepper'))).toContain('Paso 1 de 3');
    expect(requireElement(root, 'h1').textContent).toBe('¿Qué turno necesitás?');
    expect(requireElement(root, 'fieldset legend').textContent).toBe('Prestación');
    const labels = [...root.querySelectorAll('label.service-option')];
    expect(labels).toHaveLength(2);
    for (const expected of ['Consulta y diagnóstico', '30 min', 'Primera visita o control general.']) {
      expect(text(labels[0] as Element)).toContain(expected);
    }
    expect(text(labels[1] as Element)).toContain('Limpieza');
    expect(text(labels[1] as Element)).toContain('45 min');
    expect(root.querySelectorAll('input[type="radio"]')).toHaveLength(2);
  });

  it('continuar sin elegir muestra el error en el grupo y no consulta la disponibilidad', async () => {
    const { root, gateway } = await start();

    await click(button(root, 'Continuar'));

    expect(text(root)).toContain('Elegí una prestación.');
    expect(requireElement(root, 'fieldset').getAttribute('aria-describedby')).toBe('service-error');
    expect(gateway.availability).not.toHaveBeenCalled();
    expect(window.location.search).toBe('');
  });

  it('elegir una prestacion muestra el resumen "Elegiste"', async () => {
    const { root } = await start();

    requireElement<HTMLInputElement>(root, 'input[type="radio"][value="s-2"]').click();

    expect(text(requireElement(root, '.summary-line'))).toBe('Elegiste Limpieza · 45 min');
  });

  it('un catalogo vacio muestra "Reservas online no disponibles" sin stepper', async () => {
    const { root } = await start({ gateway: makeGateway({ services: async () => ({ kind: 'ok', services: [] }) }) });

    expect(text(requireElement(root, '.empty'))).toContain('Reservas online no disponibles');
    expect(requireElement(root, 'h1').textContent).toBe('Reservar turno');
    expect(root.textContent).not.toContain('TBD');
    expect(root.querySelector('.tbd')).toBeNull();
    expect(root.querySelector('.stepper')).toBeNull();
    expect(root.querySelector('form, fieldset')).toBeNull();
  });

  it('un 429 del catalogo muestra el aviso de espera con Retry-After', async () => {
    const gateway = makeGateway({ services: async () => ({ kind: 'rate-limited', retryAfterSeconds: 40 }) });
    const { root } = await start({ gateway });

    expect(text(requireElement(root, '[role="alert"]'))).toContain('Demasiados intentos seguidos');
    expect(text(root)).toContain('Probá de nuevo en 1 minuto.');
  });

  it('un error de conexion ofrece Reintentar, que repite la carga', async () => {
    let calls = 0;
    const gateway = makeGateway({
      services: async () => (++calls === 1 ? { kind: 'connection' } : { kind: 'ok', services: SERVICES }),
    });
    const { root } = await start({ gateway });

    expect(text(requireElement(root, '[role="alert"]'))).toContain('No pudimos cargar las prestaciones');

    await click(button(root, 'Reintentar'));

    expect(gateway.listServices).toHaveBeenCalledTimes(2);
    expect(root.querySelectorAll('input[type="radio"]')).toHaveLength(2);
  });
});

describe('paso 2: dia y horario', () => {
  it('muestra el paso 2 con foco en el h1, resumen y 14 dias desde el dia local', async () => {
    const gateway = makeGateway();
    const { root } = await start({ gateway, clock: () => new Date('2026-11-24T01:30:00Z') });

    await chooseService(root, 'Limpieza');

    expect(window.location.search).toBe('?paso=2');
    expect(text(requireElement(root, '.stepper'))).toContain('Paso 2 de 3');
    const h1 = requireElement(root, 'h1');
    expect(h1.textContent).toBe('Elegí día y horario');
    expect(document.activeElement).toBe(h1);
    expect(text(requireElement(root, '.lead'))).toBe('Limpieza · 45 min · horarios de Buenos Aires');
    expect(root.querySelectorAll('button.day')).toHaveLength(14);
    const first = root.querySelector('button.day');
    expect(first?.getAttribute('aria-label')).toBe('lunes 23 de noviembre');
    expect(first?.getAttribute('aria-pressed')).toBe('true');
    expect(gateway.availability).toHaveBeenCalledWith('s-2', '2026-11-23');
  });

  it('mientras consulta muestra "Buscando horarios…" con role=status', async () => {
    const pending = deferred<{ kind: 'ok'; slots: readonly [] }>();
    const gateway = makeGateway({ availability: () => pending.promise });
    const { root } = await start({ gateway });

    await chooseService(root, 'Limpieza');

    expect(text(requireElement(root, '.slot-area [role="status"]'))).toBe('Buscando horarios…');
    expect(root.querySelectorAll('.slot-skeleton')).toHaveLength(6);
  });

  it('agrupa los horarios en Manana y Tarde con la hora de Buenos Aires', async () => {
    const { root } = await start();
    await chooseService(root, 'Limpieza');

    await click(dayButton(root, 'martes 24 de noviembre'));

    expect(slotButtons(root)).toEqual(['09:00', '09:30', '15:00']);
    expect([...root.querySelectorAll('.slot-group h2')].map((h) => h.textContent)).toEqual(['Mañana', 'Tarde']);
    expect(dayButton(root, 'martes 24 de noviembre').getAttribute('aria-pressed')).toBe('true');
    expect(dayButton(root, 'lunes 23 de noviembre').getAttribute('aria-pressed')).toBe('false');
  });

  it('elegir un horario lo marca, muestra el resumen con el fin del servidor y habilita Continuar', async () => {
    const { root } = await start();
    await chooseService(root, 'Limpieza');
    await click(dayButton(root, 'martes 24 de noviembre'));
    expect(button(root, 'Continuar').getAttribute('aria-disabled')).toBe('true');

    await click(button(root, '09:30'));

    expect(button(root, '09:30').getAttribute('aria-pressed')).toBe('true');
    expect(text(requireElement(root, '.summary-line'))).toBe('mar 24/11 09:30 – 10:00');
    expect(button(root, 'Continuar').getAttribute('aria-disabled')).toBeNull();
  });

  it('continuar sin horario no avanza', async () => {
    const { root } = await start();
    await chooseService(root, 'Limpieza');
    await click(dayButton(root, 'martes 24 de noviembre'));

    await click(button(root, 'Continuar'));

    expect(window.location.search).toBe('?paso=2');
    expect(text(requireElement(root, '.stepper'))).toContain('Paso 2 de 3');
  });

  it('un dia sin horarios ofrece "Ver el dia siguiente", que consulta y marca el dia posterior', async () => {
    const { root, gateway } = await start();
    await chooseService(root, 'Limpieza');

    expect(text(requireElement(root, '.empty'))).toContain('No hay horarios disponibles este día.');
    await click(button(root, 'Ver el día siguiente'));

    expect(gateway.availability).toHaveBeenLastCalledWith('s-2', '2026-11-24');
    expect(dayButton(root, 'martes 24 de noviembre').getAttribute('aria-pressed')).toBe('true');
    expect(slotButtons(root)).toEqual(['09:00', '09:30', '15:00']);
  });

  it('"Más días" agrega 14 dias por tanda', async () => {
    const { root } = await start();
    await chooseService(root, 'Limpieza');

    await click(button(root, 'Más días'));

    expect(root.querySelectorAll('button.day')).toHaveLength(28);
  });

  it('un error de conexion muestra el aviso y Reintentar repite la misma consulta', async () => {
    let calls = 0;
    const gateway = makeGateway({
      availability: async () => (++calls === 1 ? { kind: 'connection' } : { kind: 'ok', slots: SLOTS_24 }),
    });
    const { root } = await start({ gateway });
    await chooseService(root, 'Limpieza');

    expect(text(requireElement(root, '[role="alert"]'))).toContain('No pudimos cargar los horarios');
    await click(button(root, 'Reintentar'));

    expect(gateway.availability).toHaveBeenCalledTimes(2);
    expect(gateway.availability).toHaveBeenNthCalledWith(2, 's-2', '2026-11-23');
    expect(slotButtons(root)).toEqual(['09:00', '09:30', '15:00']);
    expect(dayButton(root, 'lunes 23 de noviembre').getAttribute('aria-pressed')).toBe('true');
  });

  it('descarta la respuesta tardia de un dia que ya no esta seleccionado', async () => {
    const monday = deferred<{ kind: 'ok'; slots: typeof SLOTS_24 }>();
    const gateway = makeGateway({
      availability: (_id, date) =>
        date === '2026-11-23' ? monday.promise : Promise.resolve({ kind: 'ok' as const, slots: SLOTS_24.slice(0, 1) }),
    });
    const { root } = await start({ gateway });
    await chooseService(root, 'Limpieza');

    await click(dayButton(root, 'martes 24 de noviembre'));
    monday.resolve({ kind: 'ok', slots: SLOTS_24.slice(1) });
    await settle();

    expect(slotButtons(root)).toEqual(['09:00']);
  });

  it('un 429 muestra el aviso de espera en lugar de los horarios', async () => {
    const gateway = makeGateway({ availability: async () => ({ kind: 'rate-limited' }) });
    const { root } = await start({ gateway });
    await chooseService(root, 'Limpieza');

    expect(text(requireElement(root, '[role="alert"]'))).toContain('Demasiados intentos seguidos');
    expect(text(root)).toContain('Probá de nuevo en unos minutos.');
    expect(root.querySelector('.slots')).toBeNull();
  });

  it('service-gone vuelve al paso 1 con el catalogo recargado y un aviso', async () => {
    const gateway = makeGateway({ availability: async () => ({ kind: 'service-gone' }) });
    const { root } = await start({ gateway });

    await chooseService(root, 'Limpieza');

    expect(window.location.search).toBe('');
    expect(text(requireElement(root, '.stepper'))).toContain('Paso 1 de 3');
    expect(text(root)).toContain('Esa prestación ya no está disponible para reservar online');
    expect(gateway.listServices).toHaveBeenCalledTimes(2);
  });

  it('NO_ACTIVE_SERVICES muestra "Reservas online no disponibles"', async () => {
    const gateway = makeGateway({ availability: async () => ({ kind: 'no-services' }) });
    const { root } = await start({ gateway });

    await chooseService(root, 'Limpieza');

    expect(text(requireElement(root, '.empty'))).toContain('Reservas online no disponibles');
    expect(root.querySelector('.stepper')).toBeNull();
  });

  it('Volver regresa al paso 1 con la prestacion marcada y el foco en el h1', async () => {
    const { root } = await start();
    await chooseService(root, 'Limpieza');

    await click(button(root, 'Volver'));
    await settle();

    expect(window.location.pathname + window.location.search).toBe('/reservar');
    expect(requireElement<HTMLInputElement>(root, 'input[value="s-2"]').checked).toBe(true);
    expect(document.activeElement).toBe(requireElement(root, 'h1'));
  });
});

describe('pasos en la URL', () => {
  it('avanzar de paso deja la URL con solo el numero de paso', async () => {
    const { root } = await start();
    await chooseService(root, 'Limpieza');

    expect(window.location.pathname + window.location.search).toBe('/reservar?paso=2');
    expect(window.location.href).not.toContain('s-2');
  });

  it('atras y adelante recorren los pasos conservando prestacion, dia y horario', async () => {
    const { root } = await start();
    await chooseService(root, 'Limpieza');
    await click(dayButton(root, 'martes 24 de noviembre'));
    await click(button(root, '09:30'));

    window.history.back();
    await settle();

    expect(window.location.search).toBe('');
    expect(text(requireElement(root, '.stepper'))).toContain('Paso 1 de 3');
    expect(requireElement<HTMLInputElement>(root, 'input[value="s-2"]').checked).toBe(true);

    window.history.forward();
    await settle();

    expect(window.location.search).toBe('?paso=2');
    expect(dayButton(root, 'martes 24 de noviembre').getAttribute('aria-pressed')).toBe('true');
    expect(button(root, '09:30').getAttribute('aria-pressed')).toBe('true');
    expect(text(requireElement(root, '.summary-line'))).toBe('mar 24/11 09:30 – 10:00');
  });

  it('volver al paso 1 con atras no vuelve a pedir el catalogo', async () => {
    const { root, gateway } = await start();
    await chooseService(root, 'Limpieza');

    window.history.back();
    await settle();

    expect(gateway.listServices).toHaveBeenCalledTimes(1);
    expect(root.querySelectorAll('input[type="radio"]')).toHaveLength(2);
  });

  it.each(['?paso=2', '?paso=3', '?paso=9', '?paso=abc', '?paso=1'])(
    'cargar %s sin estado normaliza a /reservar sin apilar entradas y muestra el paso 1',
    async (search) => {
      window.history.replaceState(null, '', '/otra');
      const before = window.history.length;

      const { root } = await start({ url: `/reservar${search}` });

      expect(window.location.pathname + window.location.search).toBe('/reservar');
      expect(window.history.length).toBe(before);
      expect(text(requireElement(root, '.stepper'))).toContain('Paso 1 de 3');
    },
  );

  it('un paso mayor al permitido por lo elegido se recorta al mas alto valido', async () => {
    const { root, router } = await start();
    await chooseService(root, 'Limpieza');

    await router.navigate('/reservar?paso=3');
    await settle();

    expect(window.location.search).toBe('?paso=2');
    expect(text(requireElement(root, '.stepper'))).toContain('Paso 2 de 3');
  });

  it('salir de /reservar y volver empieza sin lo elegido', async () => {
    const { root, router } = await start();
    await chooseService(root, 'Limpieza');

    await router.navigate('/otra');
    await settle();
    await router.navigate('/reservar');
    await settle();

    expect(text(requireElement(root, '.stepper'))).toContain('Paso 1 de 3');
    expect(requireElement<HTMLInputElement>(root, 'input[value="s-2"]').checked).toBe(false);
    expect(text(root)).not.toContain('Elegiste');
  });
});
