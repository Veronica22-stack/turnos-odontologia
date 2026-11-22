import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SessionState, SessionUser } from '../../core/auth/session';
import type { RouteContext } from '../../router/router';
import type { LayoutRoute } from '../../router/safe-next';
import type { LoginResult } from './auth-gateway';
import { createLoginView } from './login-view';

const ROUTES: LayoutRoute[] = [
  { path: '/agenda', layout: 'private' },
  { path: '/pacientes', layout: 'private' },
  { path: '/reservar', layout: 'public' },
];

const USER: SessionUser = { userId: 'u1', role: 'DENTIST', mustChangePassword: false };
const EMAIL = 'romero@ejemplo.com';
const PASSWORD = 'clave-secreta-de-prueba';

interface Setup {
  readonly root: HTMLElement;
  readonly login: ReturnType<typeof vi.fn<(input: { email: string; password: string }) => Promise<LoginResult>>>;
  readonly signIn: ReturnType<typeof vi.fn<(user: SessionUser) => void>>;
  readonly navigate: ReturnType<typeof vi.fn<(path: string, options?: { replace?: boolean }) => void>>;
}

function mount(
  options: { state?: SessionState; search?: string; result?: LoginResult | Promise<LoginResult> } = {},
): Setup {
  const login = vi.fn<(input: { email: string; password: string }) => Promise<LoginResult>>(async () =>
    options.result ?? { ok: true, user: USER },
  );
  const signIn = vi.fn<(user: SessionUser) => void>();
  const navigate = vi.fn<(path: string, options?: { replace?: boolean }) => void>();
  const view = createLoginView({
    gateway: { login },
    session: { get: () => options.state ?? { status: 'anonymous' }, signIn },
    navigate,
    routes: () => ROUTES,
  });
  const context: RouteContext = { path: '/login', params: {}, query: new URLSearchParams(options.search ?? '') };
  const root = view(context);
  document.body.replaceChildren(root);
  return { root, login, signIn, navigate };
}

function input(root: HTMLElement, id: string): HTMLInputElement {
  const el = root.querySelector<HTMLInputElement>(`#${id}`);
  if (el === null) {
    throw new Error(`falta #${id}`);
  }
  return el;
}

function form(root: HTMLElement): HTMLFormElement {
  const el = root.querySelector('form');
  if (el === null) {
    throw new Error('falta el formulario');
  }
  return el;
}

function submitButton(root: HTMLElement): HTMLButtonElement {
  const el = root.querySelector<HTMLButtonElement>('button[type="submit"]');
  if (el === null) {
    throw new Error('falta el boton');
  }
  return el;
}

function fill(root: HTMLElement, email = EMAIL, password = PASSWORD): void {
  input(root, 'email').value = email;
  input(root, 'password').value = password;
}

function submit(root: HTMLElement): void {
  form(root).requestSubmit();
}

afterEach(() => {
  document.body.replaceChildren();
  localStorage.clear();
  sessionStorage.clear();
});

describe('login-view - vista inicial', () => {
  it('muestra titulo, campos con autocomplete correcto, boton habilitado y ningun error', () => {
    const { root } = mount();

    expect(root.querySelector('h1')?.textContent).toBe('Ingresar');
    expect(input(root, 'email').getAttribute('autocomplete')).toBe('username');
    expect(input(root, 'email').type).toBe('email');
    expect(input(root, 'password').type).toBe('password');
    expect(input(root, 'password').getAttribute('autocomplete')).toBe('current-password');
    expect(root.querySelector('label[for="email"]')?.textContent).toBe('Email');
    expect(root.querySelector('label[for="password"]')?.textContent).toBe('Contraseña');
    expect(submitButton(root).textContent).toBe('Ingresar');
    expect(submitButton(root).hasAttribute('aria-busy')).toBe(false);
    expect(root.querySelector('[role="alert"]')).toBeNull();
    expect(root.querySelector('[aria-invalid]')).toBeNull();
    expect(form(root).hasAttribute('novalidate')).toBe(true);
  });

  it('"Mostrar contraseña" alterna el tipo del campo', () => {
    const { root } = mount();
    const toggle = root.querySelector<HTMLButtonElement>('.reveal-btn');

    toggle?.click();
    expect(input(root, 'password').type).toBe('text');
    expect(toggle?.getAttribute('aria-pressed')).toBe('true');

    toggle?.click();
    expect(input(root, 'password').type).toBe('password');
  });
});

describe('login-view - validacion local', () => {
  it('envio vacio: sin llamada, aria-invalid en ambos campos con mensaje vinculado y foco en email', () => {
    const { root, login } = mount();

    submit(root);

    expect(login).not.toHaveBeenCalled();
    for (const id of ['email', 'password']) {
      const el = input(root, id);
      expect(el.getAttribute('aria-invalid')).toBe('true');
      const described = document.getElementById(el.getAttribute('aria-describedby') ?? '');
      expect(described?.textContent).toBe(id === 'email' ? 'Ingresá tu email.' : 'Ingresá tu contraseña.');
    }
    expect(document.activeElement).toBe(input(root, 'email'));
  });

  it('email "romero@": mensaje de email, sin llamada', () => {
    const { root, login } = mount();
    fill(root, 'romero@', PASSWORD);

    submit(root);

    expect(login).not.toHaveBeenCalled();
    expect(root.querySelector('#email-error')?.textContent).toBe('Ingresá un email válido.');
    expect(input(root, 'password').hasAttribute('aria-invalid')).toBe(false);
    expect(document.activeElement).toBe(input(root, 'email'));
  });

  it('solo la contraseña vacia: foco en contraseña', () => {
    const { root } = mount();
    fill(root, EMAIL, '');

    submit(root);

    expect(document.activeElement).toBe(input(root, 'password'));
  });

  it('corregir los datos y reenviar limpia los errores previos', async () => {
    const { root, login } = mount();
    submit(root);
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(login).toHaveBeenCalledTimes(1));
    expect(root.querySelector('.error-msg')).toBeNull();
    expect(root.querySelector('[aria-invalid]')).toBeNull();
  });
});

describe('login-view - envio y errores del servidor', () => {
  it('envia email recortado y contraseña; en curso: aria-busy, "Ingresando…", readonly y segundo envio ignorado', async () => {
    let resolve: (result: LoginResult) => void = () => {};
    const pending = new Promise<LoginResult>((r) => {
      resolve = r;
    });
    const { root, login } = mount({ result: pending });
    fill(root, ` ${EMAIL} `);

    submit(root);
    submit(root);

    expect(login).toHaveBeenCalledTimes(1);
    expect(login).toHaveBeenCalledWith({ email: EMAIL, password: PASSWORD });
    const button = submitButton(root);
    expect(button.getAttribute('aria-busy')).toBe('true');
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.textContent).toBe('Ingresando…');
    expect(input(root, 'email').readOnly).toBe(true);
    expect(input(root, 'password').readOnly).toBe(true);

    resolve({ ok: false, failure: { kind: 'invalid-credentials' } });
    await vi.waitFor(() => expect(button.hasAttribute('aria-busy')).toBe(false));
    expect(button.textContent).toBe('Ingresar');
    expect(input(root, 'email').readOnly).toBe(false);
  });

  it('credenciales invalidas: alerta generica, contraseña vacia y enfocada, email conservado, sesion intacta', async () => {
    const { root, signIn, navigate } = mount({ result: { ok: false, failure: { kind: 'invalid-credentials' } } });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Email o contraseña incorrectos.');
    expect(input(root, 'password').value).toBe('');
    expect(document.activeElement).toBe(input(root, 'password'));
    expect(input(root, 'email').value).toBe(EMAIL);
    expect(signIn).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('el texto es identico exista o no el email y aun con cuenta bloqueada (mismo resultado del gateway)', async () => {
    const texts: string[] = [];
    for (const email of [EMAIL, 'otra@ejemplo.com']) {
      const { root } = mount({ result: { ok: false, failure: { kind: 'invalid-credentials' } } });
      fill(root, email);
      submit(root);
      await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
      texts.push(root.querySelector('[role="alert"]')?.textContent ?? '');
    }

    expect(texts[0]).toBe(texts[1]);
    expect(texts[0]).not.toContain('bloquead');
  });

  it('un segundo intento fallido reemplaza la alerta (no la duplica)', async () => {
    const { root } = mount({ result: { ok: false, failure: { kind: 'invalid-credentials' } } });
    fill(root);
    submit(root);
    await vi.waitFor(() => expect(root.querySelectorAll('[role="alert"]')).toHaveLength(1));
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(submitButton(root).hasAttribute('aria-busy')).toBe(false));
    expect(root.querySelectorAll('[role="alert"]')).toHaveLength(1);
  });

  it('limite de tasa con Retry-After 45: bloqueo temporal "en 1 minuto"', async () => {
    const { root } = mount({ result: { ok: false, failure: { kind: 'locked', retryAfterSeconds: 45 } } });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
    const text = root.querySelector('[role="alert"]')?.textContent ?? '';
    expect(text).toContain('Ingreso bloqueado temporalmente');
    expect(text).toContain('Probá de nuevo en 1 minuto.');
  });

  it('limite de tasa sin Retry-After: "en unos minutos"', async () => {
    const { root } = mount({ result: { ok: false, failure: { kind: 'locked' } } });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Probá de nuevo en unos minutos.');
  });

  it('red caida: mensaje de conexion, email conservado y boton habilitado', async () => {
    const { root } = mount({ result: { ok: false, failure: { kind: 'connection' } } });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
    expect(root.querySelector('[role="alert"]')?.textContent).toBe(
      'No pudimos conectar con el servidor. Revisá tu conexión e intentá de nuevo.',
    );
    expect(submitButton(root).hasAttribute('aria-busy')).toBe(false);
    expect(input(root, 'email').value).toBe(EMAIL);
  });

  it('pagina desactualizada: mensaje de recarga', async () => {
    const { root } = mount({ result: { ok: false, failure: { kind: 'stale-page' } } });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Recargala');
  });

  it('una contraseña visible vuelve a ocultarse tras enviar', async () => {
    const { root } = mount({ result: { ok: false, failure: { kind: 'invalid-credentials' } } });
    fill(root);
    root.querySelector<HTMLButtonElement>('.reveal-btn')?.click();
    expect(input(root, 'password').type).toBe('text');

    submit(root);

    expect(input(root, 'password').type).toBe('password');
    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
  });
});

describe('login-view - destino tras el ingreso', () => {
  it('exito con next=/pacientes: signIn y navega con replace a /pacientes', async () => {
    const { root, signIn, navigate } = mount({ search: 'next=%2Fpacientes' });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));
    expect(signIn).toHaveBeenCalledWith(USER);
    expect(navigate).toHaveBeenCalledWith('/pacientes', { replace: true });
  });

  it.each([
    ['next externo', 'next=https%3A%2F%2Fevil.example'],
    ['next //evil', 'next=%2F%2Fevil.example'],
    ['next publico', 'next=%2Freservar'],
    ['sin next', ''],
  ])('%s -> /agenda', async (_label, search) => {
    const { root, navigate } = mount({ search });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/agenda', { replace: true }));
  });

  it('mustChangePassword true -> /cambiar-contrasena conservando next', async () => {
    const { root, navigate, signIn } = mount({
      search: 'next=%2Fpacientes',
      result: { ok: true, user: { ...USER, mustChangePassword: true } },
    });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/cambiar-contrasena?next=%2Fpacientes', { replace: true }));
    expect(signIn).toHaveBeenCalledWith({ ...USER, mustChangePassword: true });
  });

  it('mustChangePassword true con next invalido -> /cambiar-contrasena sin next', async () => {
    const { root, navigate } = mount({
      search: 'next=%2F%2Fevil.example',
      result: { ok: true, user: { ...USER, mustChangePassword: true } },
    });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/cambiar-contrasena', { replace: true }));
  });
});

describe('login-view - avisos de sesion', () => {
  it.each([
    ['expired', 'Tu sesión venció. Ingresá de nuevo.'],
    ['offline', 'Sin conexión con el servidor. Reintentá en unos segundos.'],
    ['password-changed', 'Contraseña actualizada. Ingresá de nuevo con la contraseña nueva.'],
  ] as const)('%s: aviso role=status sobre el formulario habilitado', (notice, text) => {
    const { root } = mount({ state: { status: 'anonymous', notice } });

    const status = root.querySelector('[role="status"]');
    expect(status?.textContent).toBe(text);
    expect(root.querySelector('form')).not.toBeNull();
    expect(submitButton(root).hasAttribute('aria-disabled')).toBe(false);
    expect(status?.compareDocumentPosition(form(root)) ?? 0).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('sin aviso o con sesion unknown no hay role=status', () => {
    expect(mount({ state: { status: 'anonymous' } }).root.querySelector('[role="status"]')).toBeNull();
    expect(mount({ state: { status: 'unknown' } }).root.querySelector('[role="status"]')).toBeNull();
  });
});

describe('login-view - sin rastros de credenciales', () => {
  it('tras un intento fallido ni la consola, ni la URL, ni el storage contienen email ni contraseña', async () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((method) => vi.spyOn(console, method));
    const { root } = mount({ result: { ok: false, failure: { kind: 'invalid-credentials' } } });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
    for (const spy of spies) {
      expect(spy).not.toHaveBeenCalled();
    }
    expect(location.href).not.toContain(EMAIL);
    expect(location.href).not.toContain(PASSWORD);
    const stored = JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage });
    expect(stored).not.toContain(EMAIL);
    expect(stored).not.toContain(PASSWORD);
    expect(localStorage.length + sessionStorage.length).toBe(0);
  });

  it('el envio no cambia la URL con los datos del formulario (sin GET nativo)', () => {
    const before = location.href;
    const { root } = mount();
    fill(root);
    const event = new Event('submit', { cancelable: true });

    form(root).dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
    expect(location.href).toBe(before);
  });
});
