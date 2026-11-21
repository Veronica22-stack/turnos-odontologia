import { afterEach, describe, expect, it } from 'vitest';
import { brandName, logoAlt } from './brand';

describe('brandName', () => {
  const original: unknown = Reflect.get(window, 'BRAND');
  afterEach(() => {
    Reflect.set(window, 'BRAND', original);
  });

  it('devuelve el BRAND_NAME publicado por design-system/brand.js', () => {
    expect(brandName()).toBe('La Muela');
  });

  it('lee window.BRAND en cada llamada (no un valor fijo)', () => {
    Reflect.set(window, 'BRAND', { name: 'Otra Marca' });

    expect(brandName()).toBe('Otra Marca');
  });

  it.each([
    ['ausente', undefined],
    ['sin name', {}],
    ['name no cadena', { name: 7 }],
    ['name vacio', { name: '' }],
    ['no objeto', 'La Otra'],
  ])('usa el fallback si window.BRAND es %s', (_label, value) => {
    Reflect.set(window, 'BRAND', value);

    expect(brandName()).toBe('La Muela');
  });
});

describe('logoAlt', () => {
  it('indica que el logo es provisional', () => {
    expect(logoAlt()).toBe('La Muela (logo provisional)');
  });

  it('sigue a la marca vigente', () => {
    const original: unknown = Reflect.get(window, 'BRAND');
    Reflect.set(window, 'BRAND', { name: 'Otra Marca' });

    expect(logoAlt()).toBe('Otra Marca (logo provisional)');
    Reflect.set(window, 'BRAND', original);
  });
});
