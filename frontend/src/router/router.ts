import { findRoute, normalizePath } from './match';

export type GuardResult = { readonly allow: true } | { readonly redirect: string; readonly replace: true };

export interface RouteContext {
  readonly path: string;
  readonly params: Readonly<Record<string, string>>;
  readonly query: URLSearchParams;
}

export type Guard = (context: RouteContext) => GuardResult | Promise<GuardResult>;

export interface ViewRoute {
  readonly name: string;
  readonly layout: 'public' | 'private';
  readonly title: string;
  readonly view: (context: RouteContext) => HTMLElement;
}

export interface RouteDef extends ViewRoute {
  readonly path: string;
  readonly guard?: Guard;
}

export interface RenderArgs {
  readonly route: ViewRoute;
  readonly params: Readonly<Record<string, string>>;
  readonly content: HTMLElement;
}

export interface RouterOptions {
  readonly routes: readonly RouteDef[];
  readonly notFound: ViewRoute;
  readonly window: Window;
  readonly render: (args: RenderArgs) => void;
  readonly brandName: () => string;
}

export interface Router {
  start(): Promise<void>;
  stop(): void;
  navigate(path: string, options?: { replace?: boolean }): Promise<void>;
}

const MAX_REDIRECTS = 5;

export function createRouter(options: RouterOptions): Router {
  const { routes, notFound, window: win, render, brandName } = options;
  let counter = 0;

  function show(route: ViewRoute, context: RouteContext): void {
    const content = route.view(context);
    render({ route, params: context.params, content });
    win.document.title = `${route.title} · ${brandName()}`;
    const heading = win.document.querySelector('h1');
    if (heading !== null) {
      heading.setAttribute('tabindex', '-1');
      heading.focus();
    }
  }

  async function resolve(): Promise<void> {
    const id = ++counter;
    for (let redirects = 0; redirects <= MAX_REDIRECTS; redirects += 1) {
      const { pathname, search } = win.location;
      const path = normalizePath(pathname);
      const found = findRoute(routes, path);
      const context: RouteContext = {
        path,
        params: found?.params ?? {},
        query: new URLSearchParams(search),
      };
      if (found === null) {
        show(notFound, context);
        return;
      }
      if (found.route.guard !== undefined) {
        const result = await found.route.guard(context);
        if (id !== counter) {
          return;
        }
        if ('redirect' in result) {
          win.history.replaceState(null, '', result.redirect);
          continue;
        }
      }
      show(found.route, context);
      return;
    }
    throw new Error('Demasiadas redirecciones de guards');
  }

  async function navigate(path: string, navOptions: { replace?: boolean } = {}): Promise<void> {
    if (navOptions.replace === true) {
      win.history.replaceState(null, '', path);
    } else {
      win.history.pushState(null, '', path);
    }
    await resolve();
  }

  function isInterceptable(anchor: HTMLAnchorElement, event: MouseEvent): URL | null {
    if (event.defaultPrevented || event.button !== 0) {
      return null;
    }
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
      return null;
    }
    const target = anchor.getAttribute('target');
    if ((target !== null && target !== '' && target !== '_self') || anchor.hasAttribute('download')) {
      return null;
    }
    const href = anchor.getAttribute('href');
    if (href === null || href.startsWith('#')) {
      return null;
    }
    const url = new URL(href, win.location.href);
    if (url.origin !== win.location.origin || findRoute(routes, url.pathname) === null) {
      return null;
    }
    return url;
  }

  function onClick(event: MouseEvent): void {
    const origin = event.target;
    if (!(origin instanceof Element)) {
      return;
    }
    const anchor = origin.closest('a');
    if (anchor === null) {
      return;
    }
    const url = isInterceptable(anchor, event);
    if (url === null) {
      return;
    }
    event.preventDefault();
    void navigate(`${url.pathname}${url.search}`);
  }

  function onPopState(): void {
    void resolve();
  }

  return {
    async start() {
      win.document.addEventListener('click', onClick);
      win.addEventListener('popstate', onPopState);
      await resolve();
    },
    stop() {
      win.document.removeEventListener('click', onClick);
      win.removeEventListener('popstate', onPopState);
    },
    navigate,
  };
}
