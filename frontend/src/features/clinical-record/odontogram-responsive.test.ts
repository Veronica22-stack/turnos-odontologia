import { describe, expect, it, vi } from 'vitest';
import mainSource from '../../main.ts?raw';
import clinicalRecordCss from '../../styles/clinical-record.css?raw';
import { createOdontogramView } from './odontogram-view';

describe('odontograma: responsive 390×844 y 1440×900', () => {
  it('usa los contenedores flex/grid del mockup para envolver las 52 piezas', () => {
    const view = createOdontogramView({ data: { current: [], history: [] }, onToggle: vi.fn() });
    document.body.replaceChildren(view.element);

    expect(document.querySelectorAll('.odo')).toHaveLength(1);
    expect(document.querySelectorAll('.arch-row')).toHaveLength(2);
    expect(document.querySelectorAll('.odo-arch')).toHaveLength(4);
    expect(document.querySelectorAll('.odo-quad')).toHaveLength(8);
    expect(document.querySelectorAll('button.tooth')).toHaveLength(52);
  });

  it('la hoja de estilos usa tokens, media query de escritorio y evita ancho fijo de página', () => {
    const css = clinicalRecordCss;

    expect(css).toContain('@media (min-width: 900px)');
    expect(css).toMatch(/\.odo-arch\s*\{[^}]*flex-wrap:\s*wrap/s);
    expect(css).toMatch(/\.odo-quad\s*\{[^}]*max-width:\s*var\(--hc-arch-max\)/s);
    expect(css).toMatch(/\.tooth\s*\{[^}]*min-width:\s*0/s);
    expect(css).toMatch(/overflow-x:\s*(hidden|clip)/);
    // Sin colores ni medidas mágicas: todo derivado de tokens del design system.
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
    expect(css).not.toMatch(/(?<![-\w])width:\s*\d+px/);
  });

  it('la hoja se registra en el arranque de la aplicación', () => {
    expect(mainSource).toContain("import './styles/clinical-record.css'");
  });
});
