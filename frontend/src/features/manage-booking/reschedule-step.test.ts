import { afterEach, describe, expect, it } from 'vitest';
import { button, dayButton, deferred, requireElement, slotButtons, text } from '../booking/test-harness';
import type { AvailabilityResult } from '../booking/booking-types';
import type { RescheduleResult, ViewResult } from './manage-types';
import {
  BOOKING,
  MOVED,
  PAST,
  SERVICES,
  SLOT_26_1115,
  SLOTS_BY_DATE,
  TOKEN,
  makeBookingGateway,
  makeManageGateway,
  settleAll,
  startManage,
  type ManageHarness,
} from './test-harness';

let harness: ManageHarness | undefined;

afterEach(() => {
  harness?.stop();
  harness = undefined;
  document.body.replaceChildren();
});

async function start(options: Parameters<typeof startManage>[0] = {}): Promise<ManageHarness> {
  harness = await startManage(options);
  return harness;
}

async function click(element: HTMLElement): Promise<void> {
  element.click();
  await settleAll();
}

function buttonNames(root: ParentNode): string[] {
  return [...root.querySelectorAll('button, a.btn')].map((b) => b.textContent?.trim() ?? '');
}

/** Abre la reprogramación desde el detalle y elige jueves 26 a las 11:15. */
async function chooseThursday(root: HTMLElement): Promise<void> {
  await click(button(root, 'Reprogramar'));
  await click(dayButton(root, 'jueves 26 de noviembre'));
  await click(button(root, '11:15'));
}

describe('abrir la reprogramación (spec "Reprogramación a un horario de la misma prestación")', () => {
  it('"Reprogramar" apila ?accion=reprogramar y muestra el selector con el turno actual', async () => {
    const { root, navigate, booking } = await start();

    await click(button(root, 'Reprogramar'));

    expect(navigate).toHaveBeenCalledWith(`/turno/${TOKEN}?accion=reprogramar`);
    expect(window.location.pathname + window.location.search).toBe(`/turno/${TOKEN}?accion=reprogramar`);
    const heading = requireElement(root, 'h1');
    expect(heading.textContent).toBe('Elegí el nuevo horario');
    expect(document.activeElement).toBe(heading);
    expect(text(root)).toContain('Limpieza · 45 min · horarios de Buenos Aires');
    expect(text(root)).toContain('Turno actual');
    expect(text(root)).toContain('mar 24/11 · 10:30 – 11:15');
    // La tira empieza hoy (lunes 23), con el día del turno (martes 24) elegido y su disponibilidad.
    expect(dayButton(root, 'lunes 23 de noviembre').getAttribute('aria-pressed')).toBe('false');
    expect(dayButton(root, 'martes 24 de noviembre').getAttribute('aria-pressed')).toBe('true');
    expect(booking.availability).toHaveBeenCalledWith('s-2', '2026-11-24');
  });

  it('"Confirmar cambio" empieza con aria-disabled y tocarlo no envía nada', async () => {
    const { root, gateway } = await start();
    await click(button(root, 'Reprogramar'));

    const confirm = button(root, 'Confirmar cambio');
    expect(confirm.getAttribute('aria-disabled')).toBe('true');
    expect(text(root)).toContain('Elegí un horario para continuar');
    await click(confirm);

    expect(gateway.reschedule).not.toHaveBeenCalled();
  });

  it('si el día del turno ya pasó, el selector empieza hoy', async () => {
    const past: ViewResult = {
      kind: 'ok',
      booking: { ...BOOKING, startsAt: '2026-11-20T13:30:00Z', endsAt: '2026-11-20T14:15:00Z' },
    };
    const { root, booking } = await start({ gateway: makeManageGateway({ view: async () => past }) });

    await click(button(root, 'Reprogramar'));

    expect(booking.availability).toHaveBeenCalledWith('s-2', '2026-11-23');
    expect(dayButton(root, 'lunes 23 de noviembre').getAttribute('aria-pressed')).toBe('true');
  });

  it('con serviceId en el turno usa ese id y no consulta el catálogo', async () => {
    const withId: ViewResult = { kind: 'ok', booking: { ...BOOKING, serviceId: 's-1' } };
    const { root, booking } = await start({ gateway: makeManageGateway({ view: async () => withId }) });

    await click(button(root, 'Reprogramar'));

    expect(booking.listServices).not.toHaveBeenCalled();
    expect(booking.availability).toHaveBeenCalledWith('s-1', '2026-11-24');
  });

  it('sin serviceId consulta el catálogo una sola vez por apertura y nunca al cargar el detalle', async () => {
    const { root, booking } = await start();
    expect(booking.listServices).not.toHaveBeenCalled();

    await click(button(root, 'Reprogramar'));
    await click(dayButton(root, 'jueves 26 de noviembre'));

    expect(booking.listServices).toHaveBeenCalledTimes(1);
  });
});

describe('elegir y confirmar (spec "Envío de la reprogramación")', () => {
  it('elegir jueves 26 11:15 muestra el resumen del nuevo horario', async () => {
    const { root } = await start();

    await chooseThursday(root);

    expect(slotButtons(root, '.slot-area').length).toBeGreaterThan(0);
    const summary = requireElement(root, '.summary-line').textContent ?? '';
    expect(summary).toContain('jue 26/11');
    expect(summary).toContain('11:15 – 12:00');
    expect(button(root, 'Confirmar cambio').getAttribute('aria-disabled')).toBeNull();
  });

  it('confirmar envía un único POST con el startsAt del slot; pendiente: "Reprogramando…", selector inerte y segundo clic ignorado', async () => {
    const pending = deferred<RescheduleResult>();
    const { root, gateway } = await start({ gateway: makeManageGateway({ reschedule: () => pending.promise }) });
    await chooseThursday(root);

    await click(button(root, 'Confirmar cambio'));

    expect(gateway.reschedule).toHaveBeenCalledTimes(1);
    expect(gateway.reschedule).toHaveBeenCalledWith(TOKEN, SLOT_26_1115.startsAt);
    const busy = button(root, 'Reprogramando…');
    expect(busy.getAttribute('aria-busy')).toBe('true');
    expect(requireElement(root, '.picker-busy').hasAttribute('inert')).toBe(true);
    await click(busy);
    expect(gateway.reschedule).toHaveBeenCalledTimes(1);
    pending.resolve({ kind: 'unknown-outcome' });
    await settleAll();
  });

  it('200 -> replaceUrl a /turno/<t>, "¡Turno reprogramado!" con los datos de la respuesta y el horario anterior', async () => {
    const { root } = await start({
      gateway: makeManageGateway({ reschedule: async () => ({ kind: 'done', booking: MOVED }) }),
    });
    await chooseThursday(root);

    await click(button(root, 'Confirmar cambio'));

    expect(window.location.pathname + window.location.search).toBe(`/turno/${TOKEN}`);
    const heading = requireElement(root, 'h1');
    expect(heading.textContent).toBe('¡Turno reprogramado!');
    expect(document.activeElement).toBe(heading);
    const page = text(root);
    expect(page).toContain('jueves 26 de noviembre');
    expect(page).toContain('11:15 – 12:00');
    expect(page).toContain('Antes: mar 24/11 · 10:30');
    expect(page).toContain('Te enviamos un email con los nuevos datos.');
    expect(page).toContain('seguir usando este enlace');
  });

  it('"Ver mi turno" lleva al detalle actualizado', async () => {
    let views = 0;
    const { root } = await start({
      gateway: makeManageGateway({
        view: async () => ({ kind: 'ok', booking: ++views === 1 ? BOOKING : MOVED }),
        reschedule: async () => ({ kind: 'done', booking: MOVED }),
      }),
    });
    await chooseThursday(root);
    await click(button(root, 'Confirmar cambio'));

    await click(root.querySelector<HTMLElement>('.actions button') ?? button(root, 'Ver mi turno'));

    expect(requireElement(root, 'h1').textContent).toBe('Tu turno');
    expect(text(root)).toContain('jueves 26 de noviembre');
    expect(buttonNames(root)).toContain('Cancelar turno');
  });
});

describe('conflicto y errores al reprogramar', () => {
  it('409 -> mismo día, aviso con el turno vigente, disponibilidad del jueves de nuevo, "Horarios cercanos" y sin preselección', async () => {
    const { root, booking } = await start({
      gateway: makeManageGateway({ reschedule: async () => ({ kind: 'slot-unavailable' }) }),
    });
    await chooseThursday(root);
    const before = booking.availability.mock.calls.filter(([, date]) => date === '2026-11-26').length;

    await click(button(root, 'Confirmar cambio'));

    const page = text(root);
    expect(page).toContain('Ese horario se acaba de ocupar.');
    expect(page).toContain('Tu turno sigue el martes 24 de noviembre a las 10:30.');
    expect(booking.availability.mock.calls.filter(([, date]) => date === '2026-11-26').length).toBe(before + 1);
    expect(dayButton(root, 'jueves 26 de noviembre').getAttribute('aria-pressed')).toBe('true');
    expect(root.querySelector('.suggested h2')?.textContent).toBe('Horarios cercanos');
    expect(root.querySelectorAll('button.slot[aria-pressed="true"]')).toHaveLength(0);
    expect(button(root, 'Confirmar cambio').getAttribute('aria-disabled')).toBe('true');
  });

  it('tras el 409 otro horario + confirmar termina bien', async () => {
    let calls = 0;
    const { root, gateway } = await start({
      gateway: makeManageGateway({
        reschedule: async () => (++calls === 1 ? { kind: 'slot-unavailable' } : { kind: 'done', booking: MOVED }),
      }),
    });
    await chooseThursday(root);
    await click(button(root, 'Confirmar cambio'));

    await click(requireElement<HTMLButtonElement>(root, '.slot-group button.slot[data-starts-at="2026-11-26T15:00:00Z"]'));
    await click(button(root, 'Confirmar cambio'));

    expect(gateway.reschedule).toHaveBeenLastCalledWith(TOKEN, '2026-11-26T15:00:00Z');
    expect(requireElement(root, 'h1').textContent).toBe('¡Turno reprogramado!');
  });

  it('timeout -> "No pudimos confirmar el cambio de horario." y "Ver estado actual" consulta y muestra el detalle', async () => {
    let views = 0;
    const { root, gateway } = await start({
      gateway: makeManageGateway({
        view: async () => ({ kind: 'ok', booking: ++views === 1 ? BOOKING : MOVED }),
        reschedule: async () => ({ kind: 'unknown-outcome' }),
      }),
    });
    await chooseThursday(root);
    await click(button(root, 'Confirmar cambio'));
    expect(text(root)).toContain('No pudimos confirmar el cambio de horario.');

    await click(button(root, 'Ver estado actual'));

    expect(gateway.view).toHaveBeenCalledTimes(2);
    expect(requireElement(root, 'h1').textContent).toBe('Tu turno');
    expect(text(root)).toContain('jueves 26 de noviembre');
  });

  it('422 BOOKING_MODIFICATION_NOT_ALLOWED -> aviso y detalle en solo lectura', async () => {
    let views = 0;
    const { root } = await start({
      gateway: makeManageGateway({
        view: async () => ({ kind: 'ok', booking: ++views === 1 ? BOOKING : PAST }),
        reschedule: async () => ({ kind: 'not-modifiable' }),
      }),
    });
    await chooseThursday(root);

    await click(button(root, 'Confirmar cambio'));

    expect(text(root)).toContain('Este turno ya no se puede modificar.');
    expect(text(root)).toContain('Turno pasado');
    expect(buttonNames(root)).not.toContain('Reprogramar');
  });

  it.each([
    ['403', { kind: 'stale-page' } as const, 'La página quedó desactualizada.'],
    ['429', { kind: 'rate-limited', retryAfterSeconds: 40 } as const, 'Probá de nuevo en 1 minuto.'],
  ])('%s -> aviso y la elección se conserva', async (_label, reply, expected) => {
    const { root } = await start({ gateway: makeManageGateway({ reschedule: async () => reply }) });
    await chooseThursday(root);

    await click(button(root, 'Confirmar cambio'));

    expect(text(root)).toContain(expected);
    expect(button(root, 'Confirmar cambio').getAttribute('aria-disabled')).toBeNull();
  });

  it('404 -> "Este enlace no es válido"', async () => {
    const { root } = await start({ gateway: makeManageGateway({ reschedule: async () => ({ kind: 'invalid' }) }) });
    await chooseThursday(root);

    await click(button(root, 'Confirmar cambio'));

    expect(requireElement(root, 'h1').textContent).toBe('Este enlace no es válido');
  });
});

describe('reprogramación online no disponible (spec "Reprogramación online no disponible")', () => {
  const unavailable = (
    over: Parameters<typeof makeBookingGateway>[0],
  ): ReturnType<typeof makeBookingGateway> => makeBookingGateway(over);

  it.each([
    ['catálogo sin coincidencia', { services: async () => ({ kind: 'ok', services: [SERVICES[0]!] }) as const }],
    [
      'dos coincidencias',
      {
        services: async () =>
          ({ kind: 'ok', services: [SERVICES[1]!, { ...SERVICES[1]!, id: 's-3' }] }) as const,
      },
    ],
    ['catálogo con error', { services: async () => ({ kind: 'connection' }) as const }],
  ])('%s -> "No se puede reprogramar este turno online." con salida al detalle', async (_label, over) => {
    const { root } = await start({ booking: unavailable(over) });

    await click(button(root, 'Reprogramar'));

    expect(text(root)).toContain('No se puede reprogramar este turno online.');
    expect(root.querySelector('.slot-area')).toBeNull();
    await click(button(root, 'Volver'));
    expect(requireElement(root, 'h1').textContent).toBe('Tu turno');
    expect(buttonNames(root)).toContain('Cancelar turno');
  });

  it.each<[string, AvailabilityResult]>([
    ['service-gone', { kind: 'service-gone' }],
    ['no-services', { kind: 'no-services' }],
  ])('la disponibilidad responde %s -> el mismo aviso', async (_label, reply) => {
    const { root } = await start({ booking: makeBookingGateway({ availability: async () => reply }) });

    await click(button(root, 'Reprogramar'));

    expect(text(root)).toContain('No se puede reprogramar este turno online.');
    expect(root.querySelector('.slot-area')).toBeNull();
  });
});

describe('URL e historial (spec spa-routing)', () => {
  it('"Volver" retrocede y muestra el detalle sin enviar nada', async () => {
    const { root, gateway } = await start();
    await chooseThursday(root);

    await click(button(root, 'Volver'));

    expect(window.location.search).toBe('');
    expect(requireElement(root, 'h1').textContent).toBe('Tu turno');
    expect(text(root)).toContain('martes 24 de noviembre');
    expect(gateway.reschedule).not.toHaveBeenCalled();
  });

  it('atrás del navegador vuelve al detalle sin nueva consulta', async () => {
    const { root, gateway } = await start();
    await click(button(root, 'Reprogramar'));
    expect(gateway.view).toHaveBeenCalledTimes(1);

    window.history.back();
    await settleAll();

    expect(window.location.search).toBe('');
    expect(requireElement(root, 'h1').textContent).toBe('Tu turno');
    expect(gateway.view).toHaveBeenCalledTimes(1);
  });

  it('carga directa de ?accion=reprogramar con reschedulable abre la reprogramación', async () => {
    const { root } = await start({ url: `/turno/${TOKEN}?accion=reprogramar` });

    expect(requireElement(root, 'h1').textContent).toBe('Elegí el nuevo horario');
  });

  it('carga directa de ?accion=reprogramar sin reschedulable normaliza la URL y muestra solo lectura', async () => {
    const before = window.history.length;
    const { root } = await start({
      url: `/turno/${TOKEN}?accion=reprogramar`,
      gateway: makeManageGateway({ view: async () => ({ kind: 'ok', booking: PAST }) }),
    });

    expect(window.location.pathname + window.location.search).toBe(`/turno/${TOKEN}`);
    expect(requireElement(root, 'h1').textContent).toBe('Tu turno');
    expect(text(root)).toContain('Turno pasado');
    expect(window.history.length).toBe(before);
  });

  it('accion desconocida -> detalle y ninguna cancelación', async () => {
    const { root, gateway } = await start({ url: `/turno/${TOKEN}?accion=borrar` });

    expect(requireElement(root, 'h1').textContent).toBe('Tu turno');
    expect(gateway.cancel).not.toHaveBeenCalled();
  });

  it('salir a otra ruta y volver hace una nueva consulta', async () => {
    const { root, gateway, router } = await start();
    expect(gateway.view).toHaveBeenCalledTimes(1);

    await router.navigate('/otra');
    await settleAll();
    await router.navigate(`/turno/${TOKEN}`);
    await settleAll();

    expect(gateway.view).toHaveBeenCalledTimes(2);
    expect(requireElement(root, 'h1').textContent).toBe('Tu turno');
  });

  it('la disponibilidad del día del turno sale de SLOTS_BY_DATE (control de la fixture)', () => {
    expect(SLOTS_BY_DATE['2026-11-26']).toContainEqual(SLOT_26_1115);
  });
});
