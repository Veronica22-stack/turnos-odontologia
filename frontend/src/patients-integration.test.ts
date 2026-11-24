import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { settle } from './features/booking/test-harness';
import type { PatientDetail } from './features/patients/patients-types';
import { bootstrap, type App } from './main';

const ID = 'pac-001';
const DNI = '30.456.789';
const PHONE = '11 5555 0001';
const EMAIL = 'julieta@ejemplo.invalid';
const NAME = 'Julieta Simulada';
const PII = [NAME, DNI, PHONE, EMAIL, ID];

const SUMMARY = {
  id: ID,
  firstName: 'Julieta',
  lastName: 'Simulada',
  dni: DNI,
  phone: PHONE,
  active: true,
};
const DETAIL: PatientDetail = {
  ...SUMMARY,
  email: EMAIL,
  birthDate: '1992-04-18',
  address: 'Calle Falsa 123',
  source: 'MANUAL',
  createdAt: '2025-03-12T12:41:00Z',
  updatedAt: '2026-11-18T17:07:00Z',
};

interface Call {
  readonly url: string;
  readonly init: RequestInit;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function requireElement<T extends Element>(root: ParentNode, selector: string): T {
  const found = root.querySelector<T>(selector);
  if (found === null) {
    throw new Error(`falta ${selector}`);
  }
  return found;
}

async function eventually(check: () => void): Promise<void> {
  await vi.waitFor(check, { timeout: 2000 });
}

describe('pacientes integrados (C-21)', () => {
  let app: App | undefined;
  let calls: Call[];
  let list: typeof SUMMARY[];
  let detail: PatientDetail;
  let failFirstList: boolean;

  function start(path = '/agenda'): App {
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
          return json({ userId: 'u1', role: 'DENTIST', mustChangePassword: false, fullName: 'Dra. Prueba' });
        }
        if (url.includes('/patients?') || url.endsWith('/patients')) {
          if (failFirstList) {
            failFirstList = false;
            throw new Error('red caída');
          }
          const query = new URL(url, window.location.href).searchParams.get('q')?.toLowerCase() ?? '';
          return json(list.filter((p) => `${p.firstName} ${p.lastName} ${p.dni} ${p.phone}`.toLowerCase().includes(query)));
        }
        if (url.includes(`/patients/${ID}`)) {
          if ((init.method ?? 'GET') === 'DELETE') {
            list = [];
            return new Response(null, { status: 204 });
          }
          if ((init.method ?? 'GET') === 'PUT') {
            const body = JSON.parse(String(init.body ?? '{}')) as Record<string, unknown>;
            detail = { ...detail, ...body } as PatientDetail;
            return json(detail);
          }
          return json(detail);
        }
        return new Response(null, { status: 404 });
      },
    });
    return app;
  }

  async function openPatients(): Promise<HTMLElement> {
    const { ready, root, router } = app ?? start();
    await ready;
    await settle();
    await router.navigate('/pacientes');
    await settle();
    return root;
  }

  async function search(root: HTMLElement, term: string): Promise<void> {
    const form = requireElement<HTMLFormElement>(root, 'form[role="search"]');
    const input = requireElement<HTMLInputElement>(root, 'input[name="q"]');
    input.value = term;
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await settle();
  }

  function button(root: ParentNode, text: string): HTMLButtonElement {
    const found = [...root.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.includes(text));
    if (found === undefined) {
      throw new Error(`falta botón ${text}`);
    }
    return found;
  }

  beforeEach(() => {
    calls = [];
    list = [SUMMARY];
    detail = DETAIL;
    failFirstList = false;
    document.cookie = 'XSRF-TOKEN=tok-123';
    localStorage.clear();
    sessionStorage.clear();
  });

  afterEach(() => {
    app?.router.stop();
    app = undefined;
    vi.restoreAllMocks();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('8.2 busca → abre ficha → edita → da de baja → vuelve al listado sin el paciente', async () => {
    const root = await openPatients();
    await eventually(() => expect(root.textContent).toContain(DNI));

    await search(root, DNI);
    const searches = calls.filter((c) => c.init.method === 'GET' && c.url.includes('/patients?'));
    expect(searches.at(-1)?.url).toBe(`/api/v1/patients?q=${encodeURIComponent(DNI)}`);
    expect(root.textContent).toContain(NAME);

    requireElement<HTMLAnchorElement>(root, '.pname').click();
    await eventually(() => expect(root.textContent).toContain(EMAIL));
    expect(calls.some((c) => c.url === `/api/v1/patients/${ID}`)).toBe(true);

    button(root, 'Editar ficha').click();
    requireElement<HTMLInputElement>(root, 'input[name="phone"]').value = '11 5555 0099';
    button(root, 'Guardar cambios').click();
    await eventually(() => expect(root.textContent).toContain('11 5555 0099'));
    const puts = calls.filter((c) => c.init.method === 'PUT');
    expect(puts).toHaveLength(1);
    expect(puts[0]?.url).toBe(`/api/v1/patients/${ID}`);
    expect(new Headers(puts[0]?.init.headers).get('X-XSRF-TOKEN')).toBe('tok-123');
    expect(JSON.parse(String(puts[0]?.init.body ?? '{}'))).not.toHaveProperty('dni');

    button(root, 'Dar de baja').click();
    await eventually(() => expect(document.querySelector('dialog')).not.toBeNull());
    requireElement<HTMLButtonElement>(document, '[data-confirm="accept"]').click();
    await eventually(() => expect(root.textContent).toContain('Paciente dado de baja'));
    expect(calls.filter((c) => c.init.method === 'DELETE')).toHaveLength(1);
    button(root, 'Volver al listado').click();
    await eventually(() => expect(root.textContent).toContain('No encontramos pacientes con ese criterio'));
    expect(root.textContent).not.toContain(NAME);
  });

  it('8.2 TRIANGULATE: fallo de red con reintento y salida a /agenda descarta el estado', async () => {
    const started = start();
    await started.ready;
    await settle();
    failFirstList = true;
    await started.router.navigate('/pacientes');
    await settle();
    const root = started.root;
    await eventually(() => expect(root.textContent).toContain('No pudimos cargar los pacientes'));
    button(root, 'Reintentar').click();
    await eventually(() => expect(root.textContent).toContain(DNI));

    await search(root, 'Julieta');
    await eventually(() => expect(root.textContent).toContain(NAME));
    const beforeLeave = calls.filter((c) => c.url.startsWith('/api/v1/patients')).length;

    await app?.router.navigate('/agenda');
    await settle();
    await app?.router.navigate('/pacientes');
    await settle();
    await eventually(() => expect(requireElement<HTMLInputElement>(root, 'input[name="q"]').value).toBe(''));
    expect(root.textContent).toContain(DNI);
    expect(calls.filter((c) => c.url.startsWith('/api/v1/patients')).length).toBeGreaterThan(beforeLeave);
  });

  it('8.3 sin rastros de PII en consola, URL, título, storages ni href; el id solo vive en /patients', async () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((method) => vi.spyOn(console, method));
    const root = await openPatients();
    await settle();
    const snapshots: string[] = [];
    const snapshot = (): void => {
      snapshots.push(
        location.href,
        document.title,
        JSON.stringify({ ...localStorage }),
        JSON.stringify({ ...sessionStorage }),
        [...root.querySelectorAll('a[href]')].map((a) => a.getAttribute('href') ?? '').join(' '),
        [...document.querySelectorAll('nav a[href], header a[href]')].map((a) => a.getAttribute('href') ?? '').join(' '),
      );
    };

    await search(root, DNI);
    snapshot();
    requireElement<HTMLAnchorElement>(root, '.pname').click();
    await eventually(() => expect(root.textContent).toContain(EMAIL));
    snapshot();
    button(root, 'Editar ficha').click();
    requireElement<HTMLInputElement>(root, 'input[name="phone"]').value = PHONE;
    button(root, 'Guardar cambios').click();
    await eventually(() => expect(root.textContent).toContain(EMAIL));
    snapshot();

    expect(location.pathname).toBe('/pacientes');
    for (const value of PII) {
      expect(snapshots.join('\n')).not.toContain(value);
      for (const spy of spies) {
        expect(JSON.stringify(spy.mock.calls)).not.toContain(value);
      }
    }
    for (const call of calls) {
      if (call.url.includes(ID)) {
        expect(call.url.startsWith('/api/v1/patients/')).toBe(true);
      }
    }
  });
});
