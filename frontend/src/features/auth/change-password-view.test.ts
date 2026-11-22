import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SessionState } from '../../core/auth/session';
import type { RouteContext } from '../../router/router';
import type { LayoutRoute } from '../../router/safe-next';
import type { ChangePasswordRequest, ChangePasswordResult } from './auth-gateway';
import { createChangePasswordView } from './change-password-view';

const ROUTES: LayoutRoute[] = [
  { path: '/agenda', layout: 'private' },
  { path: '/pacientes', layout: 'private' },
];

const CURRENT = 'actual-de-prueba-1';
const NEW_PASSWORD = 'una frase larga segura';

function authed(mustChangePassword: boolean): SessionState {
  return { status: 'authenticated', user: { userId: 'u1', role: 'DENTIST', mustChangePassword } };
}

interface Setup {
  readonly root: HTMLElement;
  readonly changePassword: ReturnType<typeof vi.fn<(input: ChangePasswordRequest) => Promise<ChangePasswordResult>>>;
  readonly endAfterPasswordChange: ReturnType<typeof vi.fn<() => void>>;
  readonly navigate: ReturnType<typeof vi.fn<(path: string, options?: { replace?: boolean }) => void>>;
  readonly onLogout: ReturnType<typeof vi.fn<() => void>>;
}

function mount(
  options: { mandatory?: boolean; search?: string; result?: ChangePasswordResult | Promise<ChangePasswordResult> } = {},
): Setup {
  const changePassword = vi.fn<(input: ChangePasswordRequest) => Promise<ChangePasswordResult>>(
    async () => options.result ?? { ok: true },
  );
  const endAfterPasswordChange = vi.fn<() => void>();
  const navigate = vi.fn<(path: string, options?: { replace?: boolean }) => void>();
  const onLogout = vi.fn<() => void>();
  const view = createChangePasswordView({
    gateway: { changePassword },
    session: { get: () => authed(options.mandatory ?? true), endAfterPasswordChange },
    navigate,
    routes: () => ROUTES,
    onLogout,
  });
  const context: RouteContext = {
    path: '/cambiar-contrasena',
    params: {},
    query: new URLSearchParams(options.search ?? ''),
  };
  const root = view(context);
  document.body.replaceChildren(root);
  return { root, changePassword, endAfterPasswordChange, navigate, onLogout };
}

function input(root: HTMLElement, id: string): HTMLInputElement {
  const el = root.querySelector<HTMLInputElement>(`#${id}`);
  if (el === null) {
    throw new Error(`falta #${id}`);
  }
  return el;
}

function fill(root: HTMLElement, current = CURRENT, next = NEW_PASSWORD, confirm = next): void {
  for (const [id, value] of [
    ['currentPassword', current],
    ['newPassword', next],
    ['confirmPassword', confirm],
  ] as const) {
    const el = input(root, id);
    el.value = value;
    el.dispatchEvent(new Event('input', { bubbles: true }));
  }
}

function submit(root: HTMLElement): void {
  root.querySelector('form')?.requestSubmit();
}

function errorOf(root: HTMLElement, id: string): string | undefined {
  return root.querySelector(`#${id}-error`)?.textContent ?? undefined;
}

afterEach(() => {
  document.body.replaceChildren();
  localStorage.clear();
  sessionStorage.clear();
});

describe('change-password-view - modos', () => {
  it('obligatorio: titulo, explicacion, tres campos, ayuda vinculada, "Salir" y sin "Cancelar"', () => {
    const { root } = mount({ mandatory: true });

    expect(root.querySelector('h1')?.textContent).toBe('Elegí una contraseña nueva');
    expect(root.querySelector('.lead')?.textContent).toContain('primer ingreso');
    expect(input(root, 'currentPassword').getAttribute('autocomplete')).toBe('current-password');
    expect(input(root, 'newPassword').getAttribute('autocomplete')).toBe('new-password');
    expect(input(root, 'confirmPassword').getAttribute('autocomplete')).toBe('new-password');
    expect(root.querySelector('label[for="currentPassword"]')?.textContent).toBe('Contraseña actual');
    expect(root.querySelector('label[for="newPassword"]')?.textContent).toBe('Contraseña nueva');
    expect(root.querySelector('label[for="confirmPassword"]')?.textContent).toBe('Repetí la contraseña nueva');
    const help = root.querySelector('#newPassword-help');
    expect(help?.textContent).toContain('Mínimo 12 caracteres.');
    expect(input(root, 'newPassword').getAttribute('aria-describedby')).toContain('newPassword-help');
    const buttons = [...root.querySelectorAll('button, a.btn')].map((el) => el.textContent);
    expect(buttons).toContain('Guardar contraseña');
    expect(buttons).toContain('Salir');
    expect(buttons).not.toContain('Cancelar');
  });

  it('voluntario: titulo "Cambiar contraseña" y "Cancelar" lleva a /agenda, sin "Salir"', () => {
    const { root } = mount({ mandatory: false });

    expect(root.querySelector('h1')?.textContent).toBe('Cambiar contraseña');
    const cancel = [...root.querySelectorAll('a')].find((a) => a.textContent === 'Cancelar');
    expect(cancel?.getAttribute('href')).toBe('/agenda');
    expect([...root.querySelectorAll('button')].map((b) => b.textContent)).not.toContain('Salir');
  });

  it('"Salir" en modo obligatorio invoca onLogout', () => {
    const { root, onLogout } = mount({ mandatory: true });

    [...root.querySelectorAll('button')].find((b) => b.textContent === 'Salir')?.click();

    expect(onLogout).toHaveBeenCalledTimes(1);
  });
});

describe('change-password-view - validacion local', () => {
  it('nueva corta: sin llamada, aria-invalid y foco en el campo nuevo', () => {
    const { root, changePassword } = mount();
    fill(root, CURRENT, 'corta123');

    submit(root);

    expect(changePassword).not.toHaveBeenCalled();
    expect(input(root, 'newPassword').getAttribute('aria-invalid')).toBe('true');
    expect(errorOf(root, 'newPassword')).toBe('Usá al menos 12 caracteres.');
    expect(document.activeElement).toBe(input(root, 'newPassword'));
  });

  it('igual a la actual: mensaje de "distinta" y sin llamada', () => {
    const { root, changePassword } = mount();
    fill(root, 'misma contraseña 14', 'misma contraseña 14');

    submit(root);

    expect(changePassword).not.toHaveBeenCalled();
    expect(errorOf(root, 'newPassword')).toBe('La contraseña nueva tiene que ser distinta de la actual.');
  });

  it('confirmacion distinta: mensaje en la confirmacion y foco ahi', () => {
    const { root, changePassword } = mount();
    fill(root, CURRENT, NEW_PASSWORD, 'una frase larga segur');

    submit(root);

    expect(changePassword).not.toHaveBeenCalled();
    expect(errorOf(root, 'confirmPassword')).toBe('Las contraseñas no coinciden.');
    expect(document.activeElement).toBe(input(root, 'confirmPassword'));
  });

  it('todo vacio: tres errores y foco en la contraseña actual', () => {
    const { root } = mount();

    submit(root);

    expect(root.querySelectorAll('input[aria-invalid="true"]')).toHaveLength(3);
    expect(document.activeElement).toBe(input(root, 'currentPassword'));
  });

  it('12 caracteres exactos distintos de la actual: una llamada', async () => {
    const { root, changePassword } = mount();
    fill(root, CURRENT, 'abcdefghijkl');

    submit(root);

    await vi.waitFor(() => expect(changePassword).toHaveBeenCalledTimes(1));
    expect(changePassword).toHaveBeenCalledWith({ currentPassword: CURRENT, newPassword: 'abcdefghijkl' });
  });

  it('129 caracteres: maximo superado, sin llamada', () => {
    const { root, changePassword } = mount();
    const long = 'a'.repeat(129);
    fill(root, CURRENT, long);

    submit(root);

    expect(changePassword).not.toHaveBeenCalled();
    expect(errorOf(root, 'newPassword')).toBe('Usá como máximo 128 caracteres.');
  });
});

describe('change-password-view - lista de requisitos en vivo', () => {
  function rules(root: HTMLElement): string[] {
    return [...root.querySelectorAll('ul.rules li')].map((li) => li.className);
  }

  it('al inicio los tres requisitos estan pendientes y la lista tiene nombre accesible', () => {
    const { root } = mount();

    expect(rules(root)).toEqual(['pending', 'pending', 'pending']);
    expect(root.querySelector('ul.rules')?.getAttribute('aria-label')).toBe('Requisitos de la contraseña nueva');
    expect(root.querySelector('ul.rules')?.hasAttribute('aria-live')).toBe(false);
  });

  it('al escribir se marcan ok los que se cumplen y los demas siguen pendientes (sin rojo)', () => {
    const { root } = mount();

    fill(root, CURRENT, 'una frase larga segura', 'otra');

    expect(rules(root)).toEqual(['ok', 'ok', 'pending']);
  });

  it('tras un intento de envio, los requisitos incumplidos pasan a bad', () => {
    const { root } = mount();
    fill(root, CURRENT, 'corta123');

    submit(root);

    expect(rules(root)).toEqual(['bad', 'ok', 'ok']);
  });

  it('corregir despues de un intento deja los cumplidos en ok', () => {
    const { root } = mount();
    fill(root, CURRENT, 'corta123');
    submit(root);

    fill(root, CURRENT, 'una frase larga segura');

    expect(rules(root)).toEqual(['ok', 'ok', 'ok']);
  });
});

describe('change-password-view - errores del servidor', () => {
  it('contraseña actual incorrecta: campo vaciado y enfocado, mensaje propio, sin navegar ni cerrar sesion', async () => {
    const { root, navigate, endAfterPasswordChange } = mount({ result: { ok: false, failure: { kind: 'current-invalid' } } });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(errorOf(root, 'currentPassword')).toBe('La contraseña actual no es correcta.'));
    expect(input(root, 'currentPassword').value).toBe('');
    expect(input(root, 'currentPassword').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(input(root, 'currentPassword'));
    expect(navigate).not.toHaveBeenCalled();
    expect(endAfterPasswordChange).not.toHaveBeenCalled();
  });

  it('400 Size en newPassword: "Usá entre 12 y 128 caracteres." en ese campo', async () => {
    const { root } = mount({
      result: { ok: false, failure: { kind: 'fields', errors: [{ field: 'newPassword', code: 'Size' }] } },
    });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(errorOf(root, 'newPassword')).toBe('Usá entre 12 y 128 caracteres.'));
    expect(document.activeElement).toBe(input(root, 'newPassword'));
  });

  it('400 NotBlank en currentPassword: mensaje en ese campo', async () => {
    const { root } = mount({
      result: { ok: false, failure: { kind: 'fields', errors: [{ field: 'currentPassword', code: 'NotBlank' }] } },
    });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(errorOf(root, 'currentPassword')).toBe('Ingresá tu contraseña actual.'));
  });

  it('401 UNAUTHENTICATED (expired): navega a /login con replace', async () => {
    const { root, navigate } = mount({ result: { ok: false, failure: { kind: 'expired' } } });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/login', { replace: true }));
  });

  it('403 CSRF_REQUIRED: alerta de pagina desactualizada y los campos conservan su estado', async () => {
    const { root } = mount({ result: { ok: false, failure: { kind: 'stale-page' } } });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
    expect(root.querySelector('[role="alert"]')?.textContent).toBe(
      'La página quedó desactualizada. Recargala e intentá de nuevo.',
    );
    expect(input(root, 'currentPassword').value).toBe(CURRENT);
    expect(input(root, 'newPassword').value).toBe(NEW_PASSWORD);
  });

  it('falla de conexion: alerta generica y boton habilitado', async () => {
    const { root } = mount({ result: { ok: false, failure: { kind: 'connection' } } });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(root.querySelector('[role="alert"]')).not.toBeNull());
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('No pudimos conectar con el servidor.');
    expect(root.querySelector('button[type="submit"]')?.hasAttribute('aria-busy')).toBe(false);
  });

  it('en curso: aria-busy, campos readonly y segundo envio ignorado', async () => {
    let resolve: (r: ChangePasswordResult) => void = () => {};
    const pending = new Promise<ChangePasswordResult>((r) => {
      resolve = r;
    });
    const { root, changePassword } = mount({ result: pending });
    fill(root);

    submit(root);
    submit(root);

    expect(changePassword).toHaveBeenCalledTimes(1);
    const button = root.querySelector('button[type="submit"]');
    expect(button?.getAttribute('aria-busy')).toBe('true');
    expect(input(root, 'newPassword').readOnly).toBe(true);

    resolve({ ok: false, failure: { kind: 'connection' } });
    await vi.waitFor(() => expect(button?.hasAttribute('aria-busy')).toBe(false));
    expect(input(root, 'newPassword').readOnly).toBe(false);
  });
});

describe('change-password-view - exito', () => {
  it('obligatorio con next=/pacientes: cierra la sesion local, vacia campos y va a /login?next=', async () => {
    const { root, endAfterPasswordChange, navigate } = mount({ search: 'next=%2Fpacientes' });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/login?next=%2Fpacientes', { replace: true }));
    expect(endAfterPasswordChange).toHaveBeenCalledTimes(1);
    for (const id of ['currentPassword', 'newPassword', 'confirmPassword']) {
      expect(input(root, id).value).toBe('');
    }
  });

  it('voluntario sin next: va a /login', async () => {
    const { root, navigate } = mount({ mandatory: false });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/login', { replace: true }));
  });

  it('next //evil.example se descarta: va a /login', async () => {
    const { root, navigate } = mount({ search: 'next=%2F%2Fevil.example' });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/login', { replace: true }));
  });

  it('el orden es: sesion anonima con aviso primero, navegacion despues', async () => {
    const order: string[] = [];
    const endAfterPasswordChange = vi.fn(() => order.push('end'));
    const navigate = vi.fn(() => order.push('navigate'));
    const view = createChangePasswordView({
      gateway: { changePassword: async () => ({ ok: true }) },
      session: { get: () => authed(true), endAfterPasswordChange },
      navigate,
      routes: () => ROUTES,
      onLogout: () => {},
    });
    const root = view({ path: '/cambiar-contrasena', params: {}, query: new URLSearchParams() });
    document.body.replaceChildren(root);
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(order).toEqual(['end', 'navigate']));
  });
});

describe('change-password-view - sin rastros de contraseñas', () => {
  it('tras un rechazo del servidor ni consola, ni URL, ni storage contienen las contraseñas', async () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((method) => vi.spyOn(console, method));
    const { root } = mount({
      result: { ok: false, failure: { kind: 'fields', errors: [{ field: 'newPassword', code: 'Size' }] } },
    });
    fill(root);

    submit(root);

    await vi.waitFor(() => expect(errorOf(root, 'newPassword')).toBeDefined());
    for (const spy of spies) {
      expect(spy).not.toHaveBeenCalled();
    }
    const exposed = location.href + JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage });
    expect(exposed).not.toContain(CURRENT);
    expect(exposed).not.toContain(NEW_PASSWORD);
  });
});
