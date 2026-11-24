import { describe, expect, it, vi } from 'vitest';
import tokensCss from '../../../../design-system/tokens.css?raw';
import type { OdontogramEntry } from './clinical-record-types';
import { createOdontogramView } from './odontogram-view';

const CARIES: OdontogramEntry = {
  id: 'o1', toothCode: '16', surface: 'OCCLUSAL', condition: 'CARIES', notes: null,
  authorId: 'u1', createdAt: '2026-11-20T15:00:00Z',
  correctsEntryId: null, correctionReason: null, correctedByEntryId: null,
};

function render() {
  const view = createOdontogramView({ data: { current: [CARIES], history: [CARIES] }, onToggle: vi.fn() });
  document.body.replaceChildren(view.element);
  return view;
}

describe('odontograma: accesibilidad', () => {
  it('un role=group por cuadrante con aria-label FDI y dentición', () => {
    render();
    const groups = [...document.querySelectorAll('[role="group"]')];
    expect(groups).toHaveLength(8);
    for (const group of groups) {
      const label = group.getAttribute('aria-label') ?? '';
      expect(label).toMatch(/^Cuadrante [1-8], .+, (permanente|temporal)$/);
    }
  });

  it('botón por pieza con aria-label FDI+estado, aria-pressed y marca textual no solo color', () => {
    render();
    const teeth = [...document.querySelectorAll<HTMLButtonElement>('button.tooth')];
    expect(teeth).toHaveLength(52);
    for (const tooth of teeth) {
      const label = tooth.getAttribute('aria-label') ?? '';
      expect(label).toMatch(/^Pieza [1-8][1-8], /);
      expect(tooth.getAttribute('aria-pressed')).toMatch(/^(true|false)$/);
      const mark = tooth.querySelector('.mark');
      expect(mark?.getAttribute('aria-hidden')).toBe('true');
      expect((mark?.textContent ?? '').trim().length).toBeGreaterThan(0);
    }
    const withState = teeth.find((t) => t.dataset['tooth'] === '16');
    expect(withState?.getAttribute('aria-label')).toContain('caries');
    expect(withState?.getAttribute('aria-label')).toContain('cara oclusal');
  });

  it('cada pieza es enfocable y el estado se anuncia por texto, no por color', () => {
    const view = render();
    const tooth = document.querySelector<HTMLButtonElement>('[data-tooth="16"]');
    tooth?.focus();
    expect(document.activeElement).toBe(tooth);
    expect(tooth?.getAttribute('tabindex')).not.toBe('-1');

    view.select('16');
    expect(tooth?.getAttribute('aria-label')).toContain('seleccionada');
    expect(tooth?.getAttribute('aria-pressed')).toBe('true');
    expect(tooth?.querySelector('.mark')?.textContent).toBe('C');
  });

  it('el design system neutraliza la animación con prefers-reduced-motion', () => {
    expect(tokensCss).toContain('@media (prefers-reduced-motion: reduce)');
    expect(tokensCss).toMatch(/--duration-fast:\s*0ms/);
  });
});
