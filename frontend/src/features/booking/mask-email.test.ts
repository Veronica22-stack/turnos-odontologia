import { describe, expect, it } from 'vitest';
import { maskEmail } from './mask-email';

describe('maskEmail', () => {
  it.each([
    ['maria@gmail.com', 'ma•••@gmail.com'],
    ['ana@ejemplo.com', 'an•••@ejemplo.com'],
    ['jo@ejemplo.com', 'j•••@ejemplo.com'],
    ['a@x.com', 'a•••@x.com'],
  ])('%s -> %s', (email, expected) => {
    expect(maskEmail(email)).toBe(expected);
  });

  it('conserva el dominio completo y no deja la parte local de 3 o mas caracteres', () => {
    const masked = maskEmail('lucia.fernandez@mail.example.com.ar');

    expect(masked.endsWith('@mail.example.com.ar')).toBe(true);
    expect(masked).not.toContain('lucia');
  });

  it('con una parte local de 3 caracteres muestra solo 2', () => {
    expect(maskEmail('ana@x.com')).toBe('an•••@x.com');
  });

  it('sin arroba no filtra nada del texto', () => {
    expect(maskEmail('sinarroba')).toBe('•••');
  });
});
