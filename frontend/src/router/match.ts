const PARAM_VALUE = /^[A-Za-z0-9_-]+$/;

export interface PathRoute {
  readonly path: string;
}

/** Quita query, hash y la barra final (salvo la raiz). */
export function normalizePath(pathname: string): string {
  const cut = pathname.search(/[?#]/);
  const path = cut === -1 ? pathname : pathname.slice(0, cut);
  return path.length > 1 && path.endsWith('/') ? path.slice(0, -1) : path;
}

function segmentsOf(path: string): string[] {
  return path === '/' ? [] : path.slice(1).split('/');
}

/** Compara por segmentos (sin RegExp construida desde datos). Devuelve los parametros o null. */
export function matchPath(pattern: string, pathname: string): Record<string, string> | null {
  const expected = segmentsOf(pattern);
  const actual = segmentsOf(normalizePath(pathname));
  if (expected.length !== actual.length) {
    return null;
  }
  const params: Record<string, string> = {};
  for (let i = 0; i < expected.length; i += 1) {
    const want = expected[i];
    const got = actual[i];
    if (want === undefined || got === undefined) {
      return null;
    }
    if (want.startsWith(':')) {
      if (!PARAM_VALUE.test(got)) {
        return null;
      }
      params[want.slice(1)] = got;
    } else if (want !== got) {
      return null;
    }
  }
  return params;
}

export function findRoute<R extends PathRoute>(
  routes: readonly R[],
  pathname: string,
): { route: R; params: Record<string, string> } | null {
  for (const route of routes) {
    const params = matchPath(route.path, pathname);
    if (params !== null) {
      return { route, params };
    }
  }
  return null;
}
