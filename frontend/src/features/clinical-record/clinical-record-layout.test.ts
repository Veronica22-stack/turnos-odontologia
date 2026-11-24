import { describe, expect, it } from 'vitest';
import clinicalRecordCss from '../../styles/clinical-record.css?raw';

describe('historia clínica: espacio inferior móvil', () => {
  it('reserva espacio para la navegación fija en viewports menores al breakpoint de escritorio', () => {
    expect(clinicalRecordCss).toMatch(
      /@media\s*\(max-width:\s*63\.99rem\)\s*\{[\s\S]*?\.clinical-record-view\s*\{[\s\S]*?padding-bottom:/,
    );
  });

  it('deriva ese espacio de tokens compartidos, sin medidas mágicas', () => {
    expect(clinicalRecordCss).toMatch(
      /padding-bottom:\s*calc\(var\(--layout-bottom-nav-height\)\s*\+\s*var\(--touch-target\)\s*\+\s*var\(--space-4\)\)/,
    );
  });
});
