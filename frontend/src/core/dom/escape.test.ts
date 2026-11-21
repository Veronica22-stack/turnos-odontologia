import { describe, expect, it } from 'vitest';
import { escapeHtml } from './escape';

describe('escapeHtml', () => {
  it('escapa etiquetas, comillas y ampersand', () => {
    expect(escapeHtml('<a href="x">O\'Hara & Co</a>')).toBe(
      '&lt;a href=&quot;x&quot;&gt;O&#39;Hara &amp; Co&lt;/a&gt;',
    );
  });

  it('devuelve cadena vacia para cadena vacia', () => {
    expect(escapeHtml('')).toBe('');
  });

  it('no escapa dos veces un texto ya limpio y deja intacto el texto sin caracteres especiales', () => {
    expect(escapeHtml('Pérez Hijos 123')).toBe('Pérez Hijos 123');
    expect(escapeHtml('&amp;')).toBe('&amp;amp;');
  });
});
