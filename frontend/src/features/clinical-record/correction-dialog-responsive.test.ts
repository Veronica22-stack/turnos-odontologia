import { describe, expect, it } from 'vitest';
import clinicalRecordCss from '../../styles/clinical-record.css?raw';

function block(selector: string): string {
  const match = clinicalRecordCss.match(new RegExp(`${selector}\\s*\\{([^}]*)\\}`, 's'));
  if (match === null) {
    throw new Error(`Falta el bloque ${selector} en clinical-record.css`);
  }
  return match[1] ?? '';
}

describe('diálogo de corrección: responsive 390×844 y 1440×900', () => {
  it('usa una capa fija por encima de la navegación y permite scrollear el contenido', () => {
    const backdrop = block('.dialog-backdrop');
    const dialog = block('.dialog-static');

    expect(backdrop).toMatch(/position:\s*fixed/);
    expect(backdrop).toMatch(/inset:\s*0/);
    expect(backdrop).toMatch(/z-index:\s*var\(--z-modal\)/);
    expect(backdrop).toMatch(/overflow-y:\s*auto/);
    expect(dialog).toMatch(/max-height:\s*(?:100%|calc\([^)]+\))/);
    expect(dialog).toMatch(/overflow-y:\s*auto/);
  });

  it('no introduce colores ni medidas mágicas fuera de los tokens', () => {
    expect(clinicalRecordCss).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
    expect(clinicalRecordCss).not.toMatch(/(?<![-\w])width:\s*\d+px/);
  });
});
