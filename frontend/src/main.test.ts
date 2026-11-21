import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import indexHtml from '../index.html?raw';
import { bootstrap, mountApp, type App } from './main';

function createDocument(markup: string): Document {
  return new DOMParser().parseFromString(markup, 'text/html');
}

describe('mountApp', () => {
  it('monta un unico [data-app-root] dentro de #app y lo devuelve', () => {
    const doc = createDocument('<body><div id="app"></div></body>');

    const root = mountApp(doc);

    const roots = doc.querySelectorAll('#app [data-app-root]');
    expect(roots).toHaveLength(1);
    expect(roots[0]).toBe(root);
  });

  it('es idempotente: un arranque repetido no duplica el contenedor raiz', () => {
    const doc = createDocument('<body><div id="app"></div></body>');

    const first = mountApp(doc);
    const second = mountApp(doc);

    expect(second).toBe(first);
    expect(doc.querySelectorAll('[data-app-root]')).toHaveLength(1);
  });

  it('falla mencionando #app y no modifica el body cuando falta el contenedor', () => {
    const doc = createDocument('<body><p>otro contenido</p></body>');
    const before = doc.body.innerHTML;

    expect(() => mountApp(doc)).toThrowError(/#app/);
    expect(doc.body.innerHTML).toBe(before);
  });
});

describe('index.html', () => {
  it('declara el idioma es-AR', () => {
    const doc = createDocument(indexHtml);

    expect(doc.documentElement.getAttribute('lang')).toBe('es-AR');
  });

  it('contiene el contenedor #app y carga el modulo /src/main.ts', () => {
    const doc = createDocument(indexHtml);

    expect(doc.querySelector('#app')).not.toBeNull();
    expect(doc.querySelector('script[type="module"][src="/src/main.ts"]')).not.toBeNull();
  });
});

describe('index.html - referrer y marca', () => {
  it('declara la politica de referrer no-referrer', () => {
    const doc = createDocument(indexHtml);

    expect(doc.querySelector('meta[name="referrer"][content="no-referrer"]')).not.toBeNull();
  });

  it('usa el marcador de marca en el titulo, sin escribir el nombre a mano', () => {
    const doc = createDocument(indexHtml);

    expect(doc.title).toContain('{{brand}}');
  });
});

describe('bootstrap', () => {
  const ROMERO = '{"id":"u1","fullName":"Dra. Romero","role":"DENTIST"}';
  let app: App | undefined;
  let calls: Array<{ url: string; init: RequestInit }>;

  function json(body: string, status = 200): Response {
    return new Response(body, { status, headers: { 'Content-Type': 'application/json' } });
  }

  function start(path: string, respond: (url: string, init: RequestInit) => Promise<Response> | Response): App {
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
        return respond(url, init);
      },
    });
    return app;
  }

  beforeEach(() => {
    calls = [];
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  afterEach(() => {
    app?.router.stop();
    app = undefined;
  });

  it('en /reservar con me 401 renderiza el layout publico con la vista de reserva', async () => {
    const { ready } = start('/reservar', () => new Response(null, { status: 401 }));

    await ready;

    expect(document.querySelector('.public-shell')).not.toBeNull();
    expect(document.querySelector('h1')?.textContent).toBe('Reservá tu turno');
    expect(document.querySelector('nav[aria-label="Principal"]')).toBeNull();
  });

  it('en /agenda con me pendiente muestra role=status sin contenido privado y luego el layout privado', async () => {
    let resolveMe: (response: Response) => void = () => {};
    const pending = new Promise<Response>((resolve) => {
      resolveMe = resolve;
    });
    const { ready } = start('/agenda', () => pending);

    expect(document.querySelector('[data-app-root] [role="status"]')).not.toBeNull();
    expect(document.querySelector('nav[aria-label="Principal"]')).toBeNull();
    expect(document.querySelector('h1')).toBeNull();

    resolveMe(json(ROMERO));
    await ready;

    expect(document.querySelector('nav[aria-label="Principal"]')).not.toBeNull();
    expect(document.querySelector('nav a[aria-current="page"]')?.textContent).toBe('Agenda');
    expect(document.querySelector('h1')?.textContent).toBe('Agenda');
    expect(document.querySelector('header')?.textContent).toContain('Dra. Romero');
    expect(calls.filter((c) => c.url === '/api/v1/auth/me')).toHaveLength(1);
  });

  it('en /agenda con me 401 termina en /login?next=%2Fagenda', async () => {
    const { ready } = start('/agenda', () => new Response(null, { status: 401 }));

    await ready;

    expect(location.pathname + location.search).toBe('/login?next=%2Fagenda');
    expect(document.querySelector('h1')?.textContent).toBe('Ingresar');
    expect(document.querySelector('.public-shell')).not.toBeNull();
  });

  it('un 401 posterior en una llamada privada lleva a /login con next y el aviso de sesion vencida', async () => {
    const { ready, client } = start('/pacientes', (url) =>
      url === '/api/v1/auth/me' ? json(ROMERO) : new Response(null, { status: 401 }),
    );
    await ready;
    expect(document.querySelector('nav a[aria-current="page"]')?.textContent).toBe('Pacientes');

    await client.request('GET', '/patients');

    await vi.waitFor(() => expect(location.pathname + location.search).toBe('/login?next=%2Fpacientes'));
    await vi.waitFor(() =>
      expect(document.querySelector('[role="status"]')?.textContent).toBe('Tu sesión venció. Ingresá de nuevo.'),
    );
    expect(document.querySelector('nav[aria-label="Principal"]')).toBeNull();
  });

  it('un 401 al iniciar (sin sesion previa) no muestra el aviso de sesion vencida', async () => {
    const { ready } = start('/login', () => new Response(null, { status: 401 }));

    await ready;

    expect(document.querySelector('[role="status"]')).toBeNull();
  });

  it('Salir llama POST /api/v1/auth/logout con X-XSRF-TOKEN y termina en /login', async () => {
    document.cookie = 'XSRF-TOKEN=tok123';
    const { ready } = start('/agenda', (url) => (url === '/api/v1/auth/me' ? json(ROMERO) : new Response(null, { status: 204 })));
    await ready;

    document.querySelector<HTMLButtonElement>('header button')?.click();

    await vi.waitFor(() => expect(location.pathname).toBe('/login'));
    const logout = calls.find((c) => c.url === '/api/v1/auth/logout');
    expect(logout?.init.method).toBe('POST');
    expect(new Headers(logout?.init.headers).get('X-XSRF-TOKEN')).toBe('tok123');
    await vi.waitFor(() => expect(document.querySelector('nav[aria-label="Principal"]')).toBeNull());
  });

  it('Salir con error de red igual termina en /login', async () => {
    const { ready } = start('/agenda', (url) => {
      if (url === '/api/v1/auth/me') {
        return json(ROMERO);
      }
      throw new TypeError('offline');
    });
    await ready;

    document.querySelector<HTMLButtonElement>('header button')?.click();

    await vi.waitFor(() => expect(location.pathname).toBe('/login'));
  });

  it('navegar entre rutas privadas actualiza la seccion actual', async () => {
    const { ready, router } = start('/agenda', () => json(ROMERO));
    await ready;

    await router.navigate('/configuracion');

    expect(document.querySelector('nav a[aria-current="page"]')?.textContent).toBe('Configuración');
    expect(document.querySelectorAll('nav [aria-current]')).toHaveLength(1);
    expect(document.title).toBe('Configuración · La Muela');
  });
});
