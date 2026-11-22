import { readCookie } from './cookies';

export type HttpMethod = 'GET' | 'HEAD' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/** Forma de autenticar las llamadas; reemplazable sin tocar pantallas ni router (PA-04). */
export interface AuthTransport {
  decorate(init: RequestInit, method: HttpMethod): RequestInit;
}

const UNSAFE_METHODS: ReadonlySet<HttpMethod> = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export interface CookieCsrfOptions {
  readonly cookieName?: string;
  readonly headerName?: string;
  readonly readCookies?: () => string;
}

/** Cookie de sesion del mismo origen + cabecera CSRF (patron Spring Security `CookieCsrfTokenRepository`). */
export function cookieCsrfTransport(options: CookieCsrfOptions = {}): AuthTransport {
  const cookieName = options.cookieName ?? 'XSRF-TOKEN';
  const headerName = options.headerName ?? 'X-XSRF-TOKEN';
  const readCookies = options.readCookies ?? (() => document.cookie);

  return {
    decorate(init, method) {
      const headers = new Headers(init.headers);
      if (UNSAFE_METHODS.has(method)) {
        const token = readCookie(cookieName, readCookies());
        if (token !== undefined) {
          headers.set(headerName, token);
        }
      }
      return { ...init, headers, credentials: 'same-origin' };
    },
  };
}

/** Alternativa para un eventual JWT (PA-04). No se usa por defecto. */
export function bearerTransport(getToken: () => string | undefined): AuthTransport {
  return {
    decorate(init) {
      const headers = new Headers(init.headers);
      const token = getToken();
      if (token !== undefined) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return { ...init, headers };
    },
  };
}
