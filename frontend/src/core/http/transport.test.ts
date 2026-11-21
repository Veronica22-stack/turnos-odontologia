import { describe, expect, it } from 'vitest';
import { readCookie } from './cookies';
import { bearerTransport, cookieCsrfTransport } from './transport';

function headersOf(init: RequestInit): Headers {
  return new Headers(init.headers);
}

describe('readCookie', () => {
  it('devuelve el valor de la cookie pedida', () => {
    expect(readCookie('XSRF-TOKEN', 'a=1; XSRF-TOKEN=abc123')).toBe('abc123');
    expect(readCookie('a', 'a=1; XSRF-TOKEN=abc123')).toBe('1');
  });

  it('devuelve undefined si falta o la cadena esta vacia', () => {
    expect(readCookie('XSRF-TOKEN', 'a=1')).toBeUndefined();
    expect(readCookie('XSRF-TOKEN', '')).toBeUndefined();
  });

  it('decodifica el valor URL-encoded y no confunde prefijos', () => {
    expect(readCookie('XSRF-TOKEN', 'XSRF-TOKEN=a%2Fb%3D')).toBe('a/b=');
    expect(readCookie('TOKEN', 'XSRF-TOKEN=abc')).toBeUndefined();
  });
});

describe('cookieCsrfTransport', () => {
  const transport = cookieCsrfTransport({ readCookies: () => 'XSRF-TOKEN=abc123' });

  it.each(['POST', 'PUT', 'PATCH', 'DELETE'] as const)('agrega X-XSRF-TOKEN en %s', (method) => {
    const init = transport.decorate({ method }, method);

    expect(headersOf(init).get('X-XSRF-TOKEN')).toBe('abc123');
  });

  it.each(['GET', 'HEAD'] as const)('no agrega X-XSRF-TOKEN en %s', (method) => {
    const init = transport.decorate({ method }, method);

    expect(headersOf(init).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('no agrega la cabecera si no hay cookie', () => {
    const sinCookie = cookieCsrfTransport({ readCookies: () => 'otra=1' });

    expect(headersOf(sinCookie.decorate({ method: 'POST' }, 'POST')).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('siempre fija credentials same-origin y conserva las cabeceras previas', () => {
    const init = transport.decorate({ method: 'POST', headers: { Accept: 'application/json' } }, 'POST');

    expect(init.credentials).toBe('same-origin');
    expect(headersOf(init).get('Accept')).toBe('application/json');
    expect(transport.decorate({}, 'GET').credentials).toBe('same-origin');
  });

  it('permite nombres de cookie y cabecera distintos', () => {
    const custom = cookieCsrfTransport({ cookieName: 'CSRF', headerName: 'X-CSRF', readCookies: () => 'CSRF=z' });

    expect(headersOf(custom.decorate({}, 'DELETE')).get('X-CSRF')).toBe('z');
  });
});

describe('bearerTransport', () => {
  it('agrega Authorization Bearer y no X-XSRF-TOKEN', () => {
    const transport = bearerTransport(() => 't');

    const init = transport.decorate({ method: 'POST' }, 'POST');

    expect(headersOf(init).get('Authorization')).toBe('Bearer t');
    expect(headersOf(init).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('no agrega Authorization si no hay token', () => {
    const transport = bearerTransport(() => undefined);

    expect(headersOf(transport.decorate({}, 'GET')).has('Authorization')).toBe(false);
  });
});
