import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { h } from '../core/dom/h';
import { createRouter, type GuardResult, type RenderArgs, type RouteDef, type Router } from './router';

function view(title: string, extra?: string) {
  return () => h('section', {}, h('h1', {}, title), extra === undefined ? null : h('p', {}, extra));
}

function baseRoutes(): RouteDef[] {
  return [
    { name: 'reservar', path: '/reservar', layout: 'public', title: 'Reservar turno', view: view('Reservá tu turno') },
    { name: 'pacientes', path: '/pacientes', layout: 'private', title: 'Pacientes', view: view('Pacientes') },
    { name: 'agenda', path: '/agenda', layout: 'private', title: 'Agenda', view: view('Agenda') },
    { name: 'turno', path: '/turno/:token', layout: 'public', title: 'Gestionar turno', view: view('Gestionar turno') },
  ];
}

const notFound = {
  name: 'not-found',
  layout: 'public' as const,
  title: 'Página no encontrada',
  view: view('Página no encontrada', 'No encontramos la página.'),
};

let router: Router | undefined;
let render: ReturnType<typeof vi.fn<(args: RenderArgs) => void>>;

function start(routes: RouteDef[] = baseRoutes()): Router {
  render = vi.fn(({ content }: RenderArgs) => {
    document.body.replaceChildren(content);
  });
  router = createRouter({ routes, notFound, window, render, brandName: () => 'La Muela' });
  return router;
}

beforeEach(() => {
  history.replaceState(null, '', '/');
  document.body.replaceChildren();
  document.title = '';
});

afterEach(() => {
  router?.stop();
  router = undefined;
});

describe('router - resolucion y navegacion', () => {
  it('start en /reservar renderiza esa ruta con su layout', async () => {
    history.replaceState(null, '', '/reservar');

    await start().start();

    expect(render).toHaveBeenCalledTimes(1);
    expect(render.mock.calls[0]?.[0].route.name).toBe('reservar');
    expect(render.mock.calls[0]?.[0].route.layout).toBe('public');
  });

  it('navigate hace pushState y renderiza', async () => {
    const push = vi.spyOn(history, 'pushState');
    const r = start();
    await r.start();

    await r.navigate('/pacientes');

    expect(push).toHaveBeenCalledTimes(1);
    expect(location.pathname).toBe('/pacientes');
    expect(render.mock.calls.at(-1)?.[0].route.name).toBe('pacientes');
  });

  it('navigate con replace usa replaceState', async () => {
    const r = start();
    await r.start();
    const push = vi.spyOn(history, 'pushState');
    const replace = vi.spyOn(history, 'replaceState');

    await r.navigate('/agenda', { replace: true });

    expect(push).not.toHaveBeenCalled();
    expect(replace).toHaveBeenCalledTimes(1);
    expect(location.pathname).toBe('/agenda');
  });

  it('pasa los parametros a la vista y a render', async () => {
    const r = start();
    await r.start();

    await r.navigate('/turno/AbC-123_x');

    expect(render.mock.calls.at(-1)?.[0].params).toEqual({ token: 'AbC-123_x' });
  });

  it('popstate re-renderiza la ruta previa', async () => {
    const r = start();
    await r.start();
    await r.navigate('/agenda');
    await r.navigate('/pacientes');

    history.back();
    await vi.waitFor(() => expect(location.pathname).toBe('/agenda'));
    await vi.waitFor(() => expect(render.mock.calls.at(-1)?.[0].route.name).toBe('agenda'));
  });

  it('una URL desconocida muestra "no encontrada" sin cambiar la URL ni mostrarla', async () => {
    const r = start();
    await r.start();

    await r.navigate('/no-existe');

    expect(location.pathname).toBe('/no-existe');
    expect(render.mock.calls.at(-1)?.[0].route.name).toBe('not-found');
    expect(document.body.textContent).toContain('Página no encontrada');
    expect(document.body.textContent).not.toContain('no-existe');
  });
});

describe('router - clics, titulo y foco', () => {
  function link(href: string, attrs: Record<string, string> = {}): HTMLAnchorElement {
    const a = h('a', { attrs: { href, ...attrs } }, 'ir');
    document.body.append(a);
    return a;
  }

  /** Registra en window (despues del router) si el clic llego sin prevenir, y evita la navegacion de jsdom. */
  function watchClicks(): { preventedByRouter: () => boolean } {
    let prevented = false;
    window.addEventListener(
      'click',
      (event) => {
        prevented = event.defaultPrevented;
        event.preventDefault();
      },
      { once: true },
    );
    return { preventedByRouter: () => prevented };
  }

  it('un clic primario en un enlace interno navega sin recargar', async () => {
    const r = start();
    await r.start();
    const a = link('/pacientes');
    const watch = watchClicks();

    a.click();

    expect(watch.preventedByRouter()).toBe(true);
    await vi.waitFor(() => expect(location.pathname).toBe('/pacientes'));
    await vi.waitFor(() => expect(render.mock.calls.at(-1)?.[0].route.name).toBe('pacientes'));
  });

  it.each([
    ['ctrlKey', { ctrlKey: true }],
    ['metaKey', { metaKey: true }],
    ['shiftKey', { shiftKey: true }],
    ['altKey', { altKey: true }],
  ])('no intercepta con %s', async (_name, init) => {
    const r = start();
    await r.start();
    const a = link('/pacientes');
    const watch = watchClicks();

    a.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, ...init }));

    expect(watch.preventedByRouter()).toBe(false);
    expect(location.pathname).toBe('/');
  });

  it('no intercepta botones no primarios, target, download, otro origen ni rutas no declaradas', async () => {
    const r = start();
    await r.start();
    const cases: Array<[HTMLAnchorElement, MouseEventInit]> = [
      [link('/pacientes'), { button: 1 }],
      [link('/pacientes', { target: '_blank' }), {}],
      [link('/pacientes', { download: '' }), {}],
      [link('https://evil.example/pacientes'), {}],
      [link('/no-existe'), {}],
      [link('#'), {}],
      [link('#main'), {}],
    ];

    for (const [a, init] of cases) {
      const watch = watchClicks();
      a.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, ...init }));
      expect(watch.preventedByRouter()).toBe(false);
    }
    expect(location.pathname).toBe('/');
  });

  it('no intercepta enlaces de ancla (#, #main) estando en una ruta declarada', async () => {
    history.replaceState(null, '', '/reservar');
    const r = start();
    await r.start();
    render.mockClear();

    for (const href of ['#', '#main']) {
      const a = link(href);
      const watch = watchClicks();
      a.click();
      expect(watch.preventedByRouter()).toBe(false);
    }
    expect(render).not.toHaveBeenCalled();
  });

  it('el titulo es "<vista> · <marca>" y el foco queda en el h1', async () => {
    const r = start();
    await r.start();

    await r.navigate('/pacientes');

    expect(document.title).toBe('Pacientes · La Muela');
    expect(document.activeElement).toBe(document.querySelector('h1'));
    expect(document.querySelector('h1')?.getAttribute('tabindex')).toBe('-1');
  });

  it('el titulo de /turno/:token no contiene el token y no escribe en consola', async () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((m) => vi.spyOn(console, m));
    const r = start();
    await r.start();

    await r.navigate('/turno/AbC-123_x');

    expect(document.title).toBe('Gestionar turno · La Muela');
    expect(document.title).not.toContain('AbC-123_x');
    for (const spy of spies) {
      expect(spy).not.toHaveBeenCalled();
    }
  });
});

describe('router - guards', () => {
  it('un guard con redirect reemplaza la entrada y renderiza el destino', async () => {
    const routes = baseRoutes().map((route) =>
      route.name === 'agenda'
        ? { ...route, guard: (): GuardResult => ({ redirect: '/reservar', replace: true }) }
        : route,
    );
    const r = start(routes);
    await r.start();
    const replace = vi.spyOn(history, 'replaceState');

    await r.navigate('/agenda');

    expect(replace).toHaveBeenCalledWith(null, '', '/reservar');
    expect(location.pathname).toBe('/reservar');
    expect(render.mock.calls.at(-1)?.[0].route.name).toBe('reservar');
  });

  it('descarta el resultado de un guard async obsoleto: solo se renderiza la ultima navegacion', async () => {
    let release: (value: GuardResult) => void = () => {};
    const slow = new Promise<GuardResult>((resolve) => {
      release = resolve;
    });
    const routes = baseRoutes().map((route) => (route.name === 'agenda' ? { ...route, guard: () => slow } : route));
    const r = start(routes);
    await r.start();
    render.mockClear();

    const first = r.navigate('/agenda');
    const second = r.navigate('/pacientes');
    release({ allow: true });
    await Promise.all([first, second]);

    expect(render).toHaveBeenCalledTimes(1);
    expect(render.mock.calls[0]?.[0].route.name).toBe('pacientes');
  });

  it('el guard recibe path normalizado, query y parametros', async () => {
    const guard = vi.fn((): GuardResult => ({ allow: true }));
    const routes = baseRoutes().map((route) => (route.name === 'turno' ? { ...route, guard } : route));
    const r = start(routes);
    await r.start();

    await r.navigate('/turno/abc/?x=1');

    const ctx = guard.mock.calls[0] as unknown as [{ path: string; params: Record<string, string>; query: URLSearchParams }];
    expect(ctx[0].path).toBe('/turno/abc');
    expect(ctx[0].params).toEqual({ token: 'abc' });
    expect(ctx[0].query.get('x')).toBe('1');
  });
});
