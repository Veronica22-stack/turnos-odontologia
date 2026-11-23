import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearConfirmation } from './features/booking/confirmation-store';
import { SLOTS_24, button, chooseService, click, dayButton, requireElement, settle, text } from './features/booking/test-harness';
import { bootstrap, type App } from './main';

const BOOKING_ID = '9f1c0000-0000-4000-8000-000000000001';
const FORBIDDEN = ['Ana', 'Pérez', '30456789', 'ana@ejemplo.com', '2345 6789', BOOKING_ID];

interface Call {
  readonly url: string;
  readonly init: RequestInit;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function problem(status: number, code: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ code, title: 'T', detail: 'D', ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

const CREATED = {
  bookingId: BOOKING_ID,
  serviceName: 'Limpieza',
  startsAt: '2026-11-24T12:30:00Z',
  endsAt: '2026-11-24T13:15:00Z',
  status: 'CONFIRMED',
  practiceName: 'Consultorio La Muela',
};

describe('reserva publica integrada (C-24)', () => {
  let app: App | undefined;
  let calls: Call[];
  let bookResponses: Array<() => Response>;

  function start(path: string): App {
    history.replaceState(null, '', path);
    document.body.replaceChildren();
    const container = document.createElement('div');
    container.id = 'app';
    document.body.append(container);
    app = bootstrap({
      doc: document,
      win: window,
      fetch: async (url, init) => {
        calls.push({ url, init });
        if (url.endsWith('/auth/me')) {
          return new Response(null, { status: 401 });
        }
        if (url.endsWith('/public/services')) {
          return json([{ id: 's-2', name: 'Limpieza', description: null, durationMin: 45 }]);
        }
        if (url.includes('/public/availability')) {
          return json({ status: 'OK', slots: url.includes('date=2026-11-24') ? SLOTS_24 : [] });
        }
        if (url.endsWith('/public/bookings')) {
          const next = bookResponses.shift();
          return next === undefined ? problem(500, 'INTERNAL_ERROR') : next();
        }
        return new Response(null, { status: 404 });
      },
    });
    return app;
  }

  beforeEach(() => {
    calls = [];
    bookResponses = [];
    document.cookie = 'XSRF-TOKEN=tok-123';
    localStorage.clear();
    sessionStorage.clear();
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-11-23T13:00:00Z') });
  });

  afterEach(() => {
    app?.router.stop();
    app = undefined;
    clearConfirmation();
    vi.useRealTimers();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  async function fillAndSubmit(root: ParentNode): Promise<void> {
    const values: Record<string, string> = {
      firstName: 'Ana',
      lastName: 'Pérez',
      dni: '30456789',
      phone: '11 2345 6789',
      email: 'ana@ejemplo.com',
    };
    for (const [id, value] of Object.entries(values)) {
      const input = requireElement<HTMLInputElement>(root, `#${id}`);
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    const privacy = requireElement<HTMLInputElement>(root, '#privacyAccepted');
    privacy.checked = true;
    privacy.dispatchEvent(new Event('change', { bubbles: true }));
    requireElement<HTMLFormElement>(root, 'form').requestSubmit();
    await settle();
  }

  async function toDetails(root: ParentNode): Promise<void> {
    await chooseService(root, 'Limpieza');
    await click(dayButton(root, 'martes 24 de noviembre'));
    await click(button(root, '09:30'));
    await click(button(root, 'Continuar'));
  }

  it('recorre /reservar paso 1 -> 2 -> 3 -> /reservar/confirmado con una sola reserva', async () => {
    bookResponses.push(() => json(CREATED, 201));
    const { ready, root } = start('/reservar');
    await ready;
    await settle();

    expect(text(requireElement(root, '.stepper'))).toContain('Paso 1 de 3');
    await toDetails(root);
    expect(location.search).toBe('?paso=3');
    await fillAndSubmit(root);

    const posts = calls.filter((c) => c.init.method === 'POST');
    expect(posts).toHaveLength(1);
    expect(posts[0]?.url).toBe('/api/v1/public/bookings');
    expect(new Headers(posts[0]?.init.headers).get('X-XSRF-TOKEN')).toBe('tok-123');
    expect(location.pathname + location.search).toBe('/reservar/confirmado');
    expect(requireElement(root, 'h1').textContent).toBe('¡Turno reservado!');
    expect(text(root)).toContain('an•••@ejemplo.com');
    expect(document.title).toBe('Reserva confirmada · La Muela');

    history.back();
    await settle();

    expect(calls.filter((c) => c.init.method === 'POST')).toHaveLength(1);
    expect(location.pathname + location.search).toBe('/reservar');
  });

  it('atras y adelante recorren los pasos sin volver a reservar', async () => {
    const { ready, root } = start('/reservar');
    await ready;
    await settle();
    await toDetails(root);

    history.back();
    await settle();
    expect(location.search).toBe('?paso=2');
    history.back();
    await settle();
    expect(location.search).toBe('');
    history.forward();
    await settle();
    history.forward();
    await settle();

    expect(location.search).toBe('?paso=3');
    expect(text(requireElement(root, '.stepper'))).toContain('Paso 3 de 3');
    expect(calls.filter((c) => c.init.method === 'POST')).toHaveLength(0);
  });

  it('sin rastros: consola, URL, titulo y storages no contienen datos personales ni el id de la reserva', async () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((method) => vi.spyOn(console, method));
    bookResponses.push(
      () => problem(409, 'SLOT_TAKEN'),
      () => problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'email', code: 'Email' }] }),
      () => json(CREATED, 201),
    );
    const { ready, root } = start('/reservar');
    await ready;
    await settle();
    const snapshots: string[] = [];
    const snapshot = (): void => {
      snapshots.push(
        location.href,
        document.title,
        JSON.stringify({ ...localStorage }),
        JSON.stringify({ ...sessionStorage }),
      );
    };

    await toDetails(root);
    await fillAndSubmit(root); // 409: vuelve al paso 2
    snapshot();
    await click(button(root, '09:30'));
    await click(button(root, 'Continuar'));
    await fillAndSubmit(root); // 400
    snapshot();
    await fillAndSubmit(root); // 201
    snapshot();

    expect(location.pathname).toBe('/reservar/confirmado');
    for (const forbidden of FORBIDDEN) {
      expect(snapshots.join('\n')).not.toContain(forbidden);
      for (const spy of spies) {
        expect(JSON.stringify(spy.mock.calls)).not.toContain(forbidden);
      }
    }
  });

  it('abrir /reservar/confirmado directo muestra la generica y salir de /reservar descarta el flujo', async () => {
    const { ready, root, router } = start('/reservar/confirmado');
    await ready;

    expect(requireElement(root, 'h1').textContent).toBe('Revisá tu email');

    await router.navigate('/reservar');
    await settle();
    await chooseService(root, 'Limpieza');
    await router.navigate('/login');
    await settle();
    await router.navigate('/reservar');
    await settle();

    expect(text(requireElement(root, '.stepper'))).toContain('Paso 1 de 3');
    expect(requireElement<HTMLInputElement>(root, 'input[value="s-2"]').checked).toBe(false);
  });
});
