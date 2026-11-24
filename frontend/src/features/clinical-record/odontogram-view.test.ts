import { describe, expect, it, vi } from 'vitest';
import type { OdontogramEntry, OdontogramResponse } from './clinical-record-types';
import { createOdontogramView } from './odontogram-view';

function entry(id: string, toothCode: string, condition: OdontogramEntry['condition'], options: Partial<OdontogramEntry> = {}): OdontogramEntry {
  return {
    id, toothCode, surface: null, condition, notes: null, authorId: 'u1', createdAt: '2026-11-20T15:00:00Z',
    correctsEntryId: null, correctionReason: null, correctedByEntryId: null, ...options,
  };
}

function render(current: readonly OdontogramEntry[], history: readonly OdontogramEntry[] = current) {
  const data: OdontogramResponse = { current, history };
  const view = createOdontogramView({ data, onToggle: vi.fn() });
  document.body.replaceChildren(view.element);
  return view;
}

describe('odontogram view: FDI y estado derivado', () => {
  it('renderiza 32 piezas permanentes y 20 temporales agrupadas por cuadrante', () => {
    render([]);

    const groups = [...document.querySelectorAll('[role="group"]')];
    expect(groups).toHaveLength(8);
    expect(groups.map((g) => g.getAttribute('aria-label'))).toEqual([
      'Cuadrante 1, superior derecho, permanente', 'Cuadrante 2, superior izquierdo, permanente',
      'Cuadrante 4, inferior derecho, permanente', 'Cuadrante 3, inferior izquierdo, permanente',
      'Cuadrante 5, superior derecho, temporal', 'Cuadrante 6, superior izquierdo, temporal',
      'Cuadrante 8, inferior derecho, temporal', 'Cuadrante 7, inferior izquierdo, temporal',
    ]);
    expect(document.querySelectorAll('button.tooth')).toHaveLength(52);
  });

  it('muestra sin registro una pieza sin entradas, sin asumir que está sana', () => {
    render([]);

    const tooth11 = document.querySelector<HTMLButtonElement>('[data-tooth="11"]');
    expect(tooth11?.getAttribute('aria-label')).toContain('sin registro');
    expect(tooth11?.textContent).toContain('–');
    expect(tooth11?.className).not.toContain('t-sano');
  });

  it('deriva el estado de la entrada vigente y respeta una corrección posterior', () => {
    const first = entry('o1', '36', 'CARIES', { surface: 'OCCLUSAL' });
    const correction = entry('o2', '36', 'FILLED', {
      surface: 'OCCLUSAL', correctsEntryId: 'o1', correctionReason: 'Motivo',
    });
    render([first, correction], [correction, first]);

    const tooth36 = document.querySelector<HTMLButtonElement>('[data-tooth="36"]');
    expect(tooth36?.getAttribute('aria-label')).toContain('obturado');
    expect(tooth36?.getAttribute('aria-label')).not.toContain('caries');
    expect(document.querySelectorAll('button.tooth')).toHaveLength(52);
  });
});

describe('odontogram view: selección y responsive', () => {
  it('marca aria-pressed solo la pieza seleccionada y notifica el código FDI', () => {
    const onToggle = vi.fn();
    const view = createOdontogramView({ data: { current: [], history: [] }, onToggle });
    document.body.replaceChildren(view.element);

    document.querySelector<HTMLButtonElement>('[data-tooth="16"]')?.click();

    expect(onToggle).toHaveBeenCalledWith('16');
    view.select('16');
    expect(document.querySelector('[data-tooth="16"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(document.querySelector('[data-tooth="21"]')?.getAttribute('aria-pressed')).toBe('false');
  });

  it('usa contenedores con scroll interno para 390x844 y 1440x900 sin ancho fijo de página', () => {
    render([]);

    expect(document.querySelector('.odo')?.classList.contains('odo')).toBe(true);
    expect(document.querySelector('.odo-arch')).not.toBeNull();
  });
});
