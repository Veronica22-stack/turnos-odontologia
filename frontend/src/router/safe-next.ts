import { findRoute, normalizePath, type PathRoute } from './match';

export interface LayoutRoute extends PathRoute {
  readonly layout: 'public' | 'private';
}

// eslint-disable-next-line no-control-regex
const CONTROL_CHARS = /[\u0000-\u001f\u007f]/;

/**
 * Valida el destino `next` tras el login (anti open-redirect).
 * Solo acepta el pathname de una ruta privada declarada; si no, devuelve null.
 */
export function safeNext(raw: string | null | undefined, routes: readonly LayoutRoute[]): string | null {
  if (raw === null || raw === undefined || raw === '') {
    return null;
  }
  if (!raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\') || CONTROL_CHARS.test(raw)) {
    return null;
  }
  const path = normalizePath(raw);
  const match = findRoute(routes, path);
  return match !== null && match.route.layout === 'private' ? path : null;
}
