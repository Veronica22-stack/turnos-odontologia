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
  const ROMERO = '{"userId":"u1","role":"DENTIST","mustChangePassword":false,"expiresAt":"2026-11-21T22:00:00Z"}';
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
    expect(document.querySelector('header')?.textContent).toContain('Profesional');
    expect(calls.filter((c) => c.url === '/api/v1/auth/me')).toHaveLength(1);
  });

  it('si /me trae fullName el encabezado privado lo muestra', async () => {
    const { ready } = start('/agenda', () =>
      json('{"userId":"u1","role":"DENTIST","mustChangePassword":false,"fullName":"Dra. Romero"}'),
    );

    await ready;

    expect(document.querySelector('header .user-name')?.textContent).toBe('Dra. Romero');
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

  describe('flujos de acceso (C-06)', () => {
    const PROBLEM = 'application/problem+json';
    const me = (mustChangePassword: boolean) =>
      json(`{"userId":"u1","role":"DENTIST","mustChangePassword":${String(mustChangePassword)},"expiresAt":"2026-11-21T22:00:00Z"}`);
    const invalid = () =>
      new Response('{"status":401,"code":"INVALID_CREDENTIALS"}', { status: 401, headers: { 'Content-Type': PROBLEM } });
    const url = () => location.pathname + location.search;

    function type(selector: string, value: string): void {
      const el = document.querySelector<HTMLInputElement>(selector);
      if (el === null) {
        throw new Error(`falta ${selector}`);
      }
      el.value = value;
      el.dispatchEvent(new Event('input', { bubbles: true }));
    }

    function submitForm(): void {
      document.querySelector('form')?.requestSubmit();
    }

    function login(): void {
      type('#email', 'romero@ejemplo.com');
      type('#password', 'clave-de-prueba');
      submitForm();
    }

    it('/agenda sin sesion -> /login?next -> login OK -> /agenda con layout privado y una sola llamada a /auth/me', async () => {
      const { ready } = start('/agenda', (path) =>
        path === '/api/v1/auth/me' ? new Response(null, { status: 401 }) : me(false),
      );
      await ready;
      expect(url()).toBe('/login?next=%2Fagenda');

      login();

      await vi.waitFor(() => expect(url()).toBe('/agenda'));
      await vi.waitFor(() => expect(document.querySelector('nav[aria-label="Principal"]')).not.toBeNull());
      expect(document.querySelector('h1')?.textContent).toBe('Agenda');
      expect(calls.filter((c) => c.url === '/api/v1/auth/me')).toHaveLength(1);
      const loginCall = calls.find((c) => c.url === '/api/v1/auth/login');
      expect(JSON.parse(String(loginCall?.init.body))).toEqual({ email: 'romero@ejemplo.com', password: 'clave-de-prueba' });
    });

    it('primer ingreso: login -> /cambiar-contrasena?next -> 200 -> /login?next con aviso -> reingreso -> /agenda; luego /pacientes sin redireccion', async () => {
      let logins = 0;
      const { ready, router } = start('/agenda', (path) => {
        if (path === '/api/v1/auth/me') {
          return new Response(null, { status: 401 });
        }
        if (path === '/api/v1/auth/login') {
          logins += 1;
          return me(logins === 1);
        }
        return new Response(null, { status: 200 });
      });
      await ready;

      login();
      await vi.waitFor(() => expect(url()).toBe('/cambiar-contrasena?next=%2Fagenda'));
      expect(document.querySelector('h1')?.textContent).toBe('Elegí una contraseña nueva');
      expect(document.querySelector('nav[aria-label="Principal"]')).toBeNull();

      type('#currentPassword', 'clave-de-prueba');
      type('#newPassword', 'una frase larga segura');
      type('#confirmPassword', 'una frase larga segura');
      submitForm();

      await vi.waitFor(() => expect(url()).toBe('/login?next=%2Fagenda'));
      expect(document.querySelector('[role="status"]')?.textContent).toBe(
        'Contraseña actualizada. Ingresá de nuevo con la contraseña nueva.',
      );
      const change = calls.find((c) => c.url === '/api/v1/auth/change-password');
      expect(JSON.parse(String(change?.init.body))).toEqual({
        currentPassword: 'clave-de-prueba',
        newPassword: 'una frase larga segura',
      });

      login();
      await vi.waitFor(() => expect(url()).toBe('/agenda'));

      await router.navigate('/pacientes');
      expect(url()).toBe('/pacientes');
      expect(document.querySelector('h1')?.textContent).toBe('Pacientes');
    });

    it('next invalido en /cambiar-contrasena: se ignora y tras cambiar la contraseña la URL es /login con el aviso, sin next', async () => {
      const { ready } = start('/cambiar-contrasena?next=%2F%2Fevil.example', (path) => {
        if (path === '/api/v1/auth/me') {
          return me(true);
        }
        return new Response(null, { status: 200 });
      });
      await ready;
      expect(document.querySelector('h1')?.textContent).toBe('Elegí una contraseña nueva');

      type('#currentPassword', 'clave-de-prueba');
      type('#newPassword', 'una frase larga segura');
      type('#confirmPassword', 'una frase larga segura');
      submitForm();

      await vi.waitFor(() => expect(url()).toBe('/login'));
      expect(document.querySelector('[role="status"]')?.textContent).toBe(
        'Contraseña actualizada. Ingresá de nuevo con la contraseña nueva.',
      );
      expect(location.href).not.toContain('evil.example');
    });

    it('401 en una llamada privada -> /login?next con aviso de vencida -> reingreso -> /pacientes', async () => {
      const { ready, client } = start('/pacientes', (path) => {
        if (path === '/api/v1/auth/me' || path === '/api/v1/auth/login') {
          return me(false);
        }
        return new Response(null, { status: 401 });
      });
      await ready;

      await client.request('GET', '/patients');
      await vi.waitFor(() => expect(url()).toBe('/login?next=%2Fpacientes'));
      await vi.waitFor(() =>
        expect(document.querySelector('[role="status"]')?.textContent).toBe('Tu sesión venció. Ingresá de nuevo.'),
      );

      login();

      await vi.waitFor(() => expect(url()).toBe('/pacientes'));
      await vi.waitFor(() => expect(document.querySelector('nav a[aria-current="page"]')?.textContent).toBe('Pacientes'));
    });

    it('Salir y luego atras: /login?next=%2Fagenda sin contenido privado', async () => {
      const { ready, router } = start('/agenda', (path) => (path === '/api/v1/auth/me' ? me(false) : new Response(null, { status: 204 })));
      await ready;
      await router.navigate('/pacientes');

      document.querySelector<HTMLButtonElement>('header button')?.click();
      await vi.waitFor(() => expect(url()).toBe('/login'));

      history.back();

      await vi.waitFor(() => expect(url()).toBe('/login?next=%2Fagenda'));
      await vi.waitFor(() => expect(document.querySelector('h1')?.textContent).toBe('Ingresar'));
      expect(document.querySelector('nav[aria-label="Principal"]')).toBeNull();
      expect(document.querySelector('.private-shell')).toBeNull();
    });

    it('un 401 en el login no dispara el vencimiento ni cambia la URL', async () => {
      const { ready, session } = start('/login', (path) =>
        path === '/api/v1/auth/me' ? new Response(null, { status: 401 }) : invalid(),
      );
      await ready;

      login();

      await vi.waitFor(() => expect(document.querySelector('[role="alert"]')).not.toBeNull());
      expect(document.querySelector('[role="alert"]')?.textContent).toContain('Email o contraseña incorrectos.');
      expect(url()).toBe('/login');
      expect(session.get()).toEqual({ status: 'anonymous' });
      expect(document.querySelector('[role="status"]')).toBeNull();
    });

    it('"Acceso profesionales" se marca aria-current en /login y deja de estarlo al ir a /reservar', async () => {
      const { ready, router } = start('/login', () => new Response(null, { status: 401 }));
      await ready;
      expect(document.querySelector('a.pro-link')?.getAttribute('aria-current')).toBe('page');

      await router.navigate('/reservar');

      expect(document.querySelector('a.pro-link')?.hasAttribute('aria-current')).toBe(false);
    });
  });
});
