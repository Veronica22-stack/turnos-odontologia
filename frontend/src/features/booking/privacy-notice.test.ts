import { describe, expect, it } from 'vitest';
import { renderPrivacyNotice } from './privacy-notice';

describe('renderPrivacyNotice', () => {
  it('menciona la ley, la finalidad, los datos, los derechos y la AAIP', () => {
    const text = renderPrivacyNotice().textContent ?? '';

    expect(text).toContain('Ley 25.326');
    expect(text).toContain('recordatorios por email');
    for (const dato of ['nombre', 'DNI', 'email', 'teléfono']) {
      expect(text).toContain(dato);
    }
    for (const derecho of ['acceso', 'rectificación', 'supresión']) {
      expect(text).toContain(derecho);
    }
    expect(text).toContain('Agencia de Acceso a la Información Pública');
    expect(text).toContain('consultorio');
  });

  it('no contiene marcas pendientes y cada llamada devuelve un nodo nuevo', () => {
    const first = renderPrivacyNotice();
    const second = renderPrivacyNotice();

    expect(first.textContent).not.toMatch(/TBD|PA-\d+/);
    expect(first).not.toBe(second);
  });
});
