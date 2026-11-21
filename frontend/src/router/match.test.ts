import { describe, expect, it } from 'vitest';
import { findRoute, matchPath, normalizePath } from './match';

describe('matchPath', () => {
  it('extrae el parametro :token', () => {
    expect(matchPath('/turno/:token', '/turno/AbC-123_x')).toEqual({ token: 'AbC-123_x' });
  });

  it('no coincide con segmentos de mas o de menos', () => {
    expect(matchPath('/turno/:token', '/turno/a/b')).toBeNull();
    expect(matchPath('/turno/:token', '/turno/')).toBeNull();
    expect(matchPath('/turno/:token', '/turno')).toBeNull();
  });

  it('normaliza la barra final', () => {
    expect(matchPath('/agenda', '/agenda/')).toEqual({});
    expect(matchPath('/agenda', '/agenda')).toEqual({});
  });

  it('coincide por pathname ignorando query y hash', () => {
    expect(matchPath('/agenda', '/agenda?x=1')).toEqual({});
    expect(matchPath('/agenda', '/agenda#top')).toEqual({});
  });

  it('rechaza parametros con caracteres fuera de [A-Za-z0-9_-]', () => {
    expect(matchPath('/turno/:token', '/turno/a.b')).toBeNull();
    expect(matchPath('/turno/:token', '/turno/a%20b')).toBeNull();
    expect(matchPath('/turno/:token', '/turno/<x>')).toBeNull();
  });

  it('distingue rutas estaticas distintas y la raiz', () => {
    expect(matchPath('/agenda', '/pacientes')).toBeNull();
    expect(matchPath('/', '/')).toEqual({});
    expect(matchPath('/reservar', '/reservar/confirmado')).toBeNull();
  });
});

describe('normalizePath', () => {
  it('quita barra final, query y hash pero conserva la raiz', () => {
    expect(normalizePath('/agenda/')).toBe('/agenda');
    expect(normalizePath('/agenda/?a=1#x')).toBe('/agenda');
    expect(normalizePath('/')).toBe('/');
  });
});

describe('findRoute', () => {
  const routes = [{ path: '/reservar' }, { path: '/turno/:token' }];

  it('devuelve la ruta y sus parametros', () => {
    expect(findRoute(routes, '/turno/abc')).toEqual({ route: routes[1], params: { token: 'abc' } });
  });

  it('devuelve null si ninguna coincide', () => {
    expect(findRoute(routes, '/no-existe')).toBeNull();
  });
});
