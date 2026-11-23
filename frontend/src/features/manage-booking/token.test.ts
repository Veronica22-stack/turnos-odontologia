import { describe, expect, it } from 'vitest';
import { isPlausibleToken } from './token';

describe('isPlausibleToken (design D3)', () => {
  it('acepta un token Base64URL de 43 caracteres con - y _', () => {
    const token = `${'aB3-_'.repeat(8)}xyz`;
    expect(token).toHaveLength(43);
    expect(isPlausibleToken(token)).toBe(true);
  });

  it('acepta los extremos del rango: 20 y 128 caracteres', () => {
    expect(isPlausibleToken('a'.repeat(20))).toBe(true);
    expect(isPlausibleToken('a'.repeat(128))).toBe(true);
  });

  it('rechaza 19 y 129 caracteres', () => {
    expect(isPlausibleToken('a'.repeat(19))).toBe(false);
    expect(isPlausibleToken('a'.repeat(129))).toBe(false);
  });

  it.each([
    ['vacío', ''],
    ['símbolos', 'abc$%'],
    ['barra', `${'a'.repeat(20)}/b`],
    ['punto', `${'a'.repeat(20)}.b`],
    ['espacio', `${'a'.repeat(20)} b`],
    ['porcentaje', `${'a'.repeat(20)}%2F`],
    ['salto de línea', `${'a'.repeat(20)}\n`],
  ])('rechaza %s', (_label, value) => {
    expect(isPlausibleToken(value)).toBe(false);
  });
});
