import { describe, expect, it } from 'vitest';
import { safeNext } from './safe-next';

const routes = [
  { path: '/', layout: 'public' as const },
  { path: '/reservar', layout: 'public' as const },
  { path: '/turno/:token', layout: 'public' as const },
  { path: '/login', layout: 'public' as const },
  { path: '/agenda', layout: 'private' as const },
  { path: '/pacientes', layout: 'private' as const },
];

describe('safeNext', () => {
  it.each(['/pacientes', '/agenda'])('acepta la ruta privada %s', (value) => {
    expect(safeNext(value, routes)).toBe(value);
  });

  it('normaliza barra final y descarta query y hash', () => {
    expect(safeNext('/agenda/', routes)).toBe('/agenda');
    expect(safeNext('/pacientes?token=abc#x', routes)).toBe('/pacientes');
  });

  it.each([
    '//evil.example/agenda',
    '/\\evil',
    '/agenda\\x',
    'https://evil.example',
    'javascript:alert(1)',
    '/turno/abc',
    '/reservar',
    '/login',
    '/no-existe',
    '',
    '/agenda\u0000',
    '/agenda\n',
    'agenda',
  ])('rechaza %j', (value) => {
    expect(safeNext(value, routes)).toBeNull();
  });

  it('rechaza null y undefined', () => {
    expect(safeNext(null, routes)).toBeNull();
    expect(safeNext(undefined, routes)).toBeNull();
  });
});
