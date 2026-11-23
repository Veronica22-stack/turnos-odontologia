import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { button, click, dayButton, requireElement, settle, text } from './features/booking/test-harness';
import { BOOKING, CANCELLED, MOVED, SERVICES, SLOTS_BY_DATE, TOKEN } from './features/manage-booking/test-harness';
import { bootstrap, type App } from './main';

interface Call {
  readonly method: string;
  readonly url: string;
  readonly body: string | undefined;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function problem(status: number, code: string): Response {
  return new Response(JSON.stringify({ code, title: 'T', detail: 'D' }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

function view(booking: typeof BOOKING): Record<string, unknown> {
  return { bookingId: '9f1c0000-0000-4000-8000-000000000001', ...booking };
}

describe('gestión de turno integrada (C-25)', () => {
  let app: App | undefined;
  let calls: Call[];
  let rescheduleReplies: Array<() => Response>;
  let titles: string[];
  let consoleSpies: Array<ReturnType<typeof vi.spyOn>>;

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
        calls.push({ method: init.method ?? 'GET', url, body: typeof init.body === 'string' ? init.body : undefined });
        if (url.endsWith('/auth/me')) {
          return new Response(null, { status: 401 });
        }
        if (url.endsWith('/public/services')) {
          return json(SERVICES);
        }
        if (url.includes('/public/availability')) {
          const date = /date=([\d-]+)/.exec(url)?.[1] ?? '';
          return json({ status: 'OK', slots: SLOTS_BY_DATE[date] ?? [] });
        }
        if (url.endsWith(`/public/bookings/${TOKEN}/reschedule`)) {
          const next = rescheduleReplies.shift();
          return next === undefined ? problem(500, 'INTERNAL_ERROR') : next();
        }
        if (url.endsWith(`/public/bookings/${TOKEN}`)) {
          if (init.method === 'DELETE') {
            return json(view(CANCELLED));
          }
          return url.includes('missing') ? problem(404, 'INVALID_OR_EXPIRED_TOKEN') : json(view(BOOKING));
        }
        if (url.includes('/public/bookings/')) {
          return problem(404, 'INVALID_OR_EXPIRED_TOKEN');
        }
        return new Response(null, { status: 404 });
      },
    });
    return app;
  }

  function recordTitle(): void {
    titles.push(document.title);
  }

  beforeEach(() => {
    calls = [];
    rescheduleReplies = [];
    titles = [];
    document.cookie = 'XSRF-TOKEN=tok-123';
    localStorage.clear();
    sessionStorage.clear();
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-11-23T13:00:00Z') });
    consoleSpies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((level) =>
      vi.spyOn(console, level).mockImplementation(() => {}),
    );
  });

  afterEach(() => {
    app?.router.stop();
    app = undefined;
    vi.useRealTimers();
    vi.restoreAllMocks();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  /** Recorre: detalle -> reprogramar -> conflicto -> reprogramar OK -> detalle -> cancelar. */
  async function fullJourney(): Promise<HTMLElement> {
    rescheduleReplies.push(() => problem(409, 'SLOT_TAKEN'), () => json(view(MOVED)));
    const { root } = start(`/turno/${TOKEN}`);
    await settle();
    recordTitle();
    expect(requireElement(root, 'h1').textContent).toBe('Tu turno');

    await click(button(root, 'Reprogramar'));
    recordTitle();
    await click(dayButton(root, 'jueves 26 de noviembre'));
    await click(button(root, '11:15'));
    await click(button(root, 'Confirmar cambio'));
    recordTitle();
    expect(text(root)).toContain('Ese horario se acaba de ocupar.');

    await click(requireElement<HTMLButtonElement>(root, '.slot-group button.slot[data-starts-at="2026-11-26T15:00:00Z"]'));
    await click(button(root, 'Confirmar cambio'));
    recordTitle();
    expect(requireElement(root, 'h1').textContent).toBe('¡Turno reprogramado!');

    await click(button(root, 'Ver mi turno'));
    recordTitle();
    button(root, 'Cancelar turno').click();
    button(document, 'Sí, cancelar turno').click();
    await settle();
    recordTitle();
    expect(requireElement(root, 'h1').textContent).toBe('Turno cancelado');
    return root;
  }

  it('recorre consulta, reprogramación con conflicto, reprogramación correcta y cancelación', async () => {
    await fullJourney();

    const manage = calls.filter((c) => c.url.includes('/public/bookings/'));
    expect(manage.map((c) => `${c.method} ${c.url.replace(TOKEN, '<t>')}`)).toEqual([
      'GET /api/v1/public/bookings/<t>',
      'POST /api/v1/public/bookings/<t>/reschedule',
      'POST /api/v1/public/bookings/<t>/reschedule',
      'DELETE /api/v1/public/bookings/<t>',
    ]);
    expect(manage[2]?.body).toBe('{"startsAt":"2026-11-26T15:00:00Z"}');
  });

  it('atrás desde ?accion=reprogramar vuelve al detalle', async () => {
    const { root } = start(`/turno/${TOKEN}`);
    await settle();
    await click(button(root, 'Reprogramar'));
    expect(location.search).toBe('?accion=reprogramar');

    history.back();
    await settle();

    expect(location.search).toBe('');
    expect(requireElement(root, 'h1').textContent).toBe('Tu turno');
  });

  it('el título es siempre "Gestionar turno · <marca>" y nunca lleva el token ni la prestación', async () => {
    await fullJourney();

    expect(titles.length).toBeGreaterThanOrEqual(6);
    for (const title of titles) {
      expect(title).toMatch(/^Gestionar turno · /);
      expect(title).not.toContain(TOKEN);
      expect(title).not.toContain('Limpieza');
    }
  });

  it('sin rastros del token: consola, título, texto, storages, enlaces y peticiones ajenas', async () => {
    const root = await fullJourney();

    for (const spy of consoleSpies) {
      expect(JSON.stringify(spy.mock.calls)).not.toContain(TOKEN);
    }
    expect(document.title).not.toContain(TOKEN);
    expect(document.body.textContent).not.toContain(TOKEN);
    expect(JSON.stringify({ ...localStorage })).not.toContain(TOKEN);
    expect(JSON.stringify({ ...sessionStorage })).not.toContain(TOKEN);
    for (const link of root.querySelectorAll('[href]')) {
      expect(link.getAttribute('href')).not.toContain(TOKEN);
    }
    const others = calls.filter((c) => !c.url.includes('/public/bookings/'));
    expect(others.length).toBeGreaterThan(0);
    for (const call of others) {
      expect(call.url).not.toContain(TOKEN);
      expect(call.body ?? '').not.toContain(TOKEN);
    }
    // Solo las peticiones de gestión llevan el token, y en la ruta.
    for (const call of calls.filter((c) => c.url.includes('/public/bookings/'))) {
      expect(call.url.startsWith(`/api/v1/public/bookings/${TOKEN}`)).toBe(true);
      expect(call.body ?? '').not.toContain(TOKEN);
    }
  });

  it('un 404 tampoco deja rastros del token', async () => {
    const missing = `missing${'x'.repeat(20)}`;
    const { root } = start(`/turno/${missing}`);
    await settle();

    expect(requireElement(root, 'h1').textContent).toBe('Este enlace no es válido');
    expect(document.title).toMatch(/^Gestionar turno · /);
    expect(document.title).not.toContain(missing);
    expect(document.body.textContent).not.toContain(missing);
    expect(JSON.stringify(consoleSpies.map((spy) => spy.mock.calls))).not.toContain(missing);
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain(missing);
  });
});
