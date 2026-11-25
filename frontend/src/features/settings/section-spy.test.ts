import { describe, expect, it } from 'vitest';
import { pickActiveSection, sectionsCrossing } from './section-spy';

describe('sectionsCrossing (posición respecto de la línea bajo el header)', () => {
  it('devuelve la sección que cruza la línea', () => {
    const rects = [
      { id: 'practice', top: -500, bottom: -20 },
      { id: 'hours', top: -20, bottom: 400 },
      { id: 'services', top: 400, bottom: 900 },
    ];
    expect([...sectionsCrossing(rects, 96)]).toEqual(['hours']);
  });

  it('una sección que recién empieza en la línea cuenta; una que ya terminó, no', () => {
    const rects = [
      { id: 'hours', top: -300, bottom: 96 },
      { id: 'services', top: 96, bottom: 600 },
    ];
    expect([...sectionsCrossing(rects, 96)]).toEqual(['services']);
  });

  it('arriba de todo (todas debajo de la línea) devuelve la primera sección', () => {
    const rects = [
      { id: 'practice', top: 200, bottom: 600 },
      { id: 'hours', top: 600, bottom: 900 },
    ];
    expect([...sectionsCrossing(rects, 96)]).toEqual(['practice']);
  });

  it('si todas quedaron arriba de la línea devuelve vacío', () => {
    expect([...sectionsCrossing([{ id: 'practice', top: -400, bottom: -100 }], 96)]).toEqual([]);
  });
});

const IDS = ['practice', 'hours', 'services', 'blocks'] as const;

describe('pickActiveSection', () => {
  it('devuelve la única sección que intersecta la banda superior', () => {
    expect(pickActiveSection(IDS, new Set(['services']), 'practice', false)).toBe('services');
  });

  it('con varias intersectando elige la última en orden del documento', () => {
    expect(pickActiveSection(IDS, new Set(['hours', 'practice']), 'practice', false)).toBe('hours');
    expect(pickActiveSection(IDS, new Set(['blocks', 'hours']), 'practice', false)).toBe('blocks');
  });

  it('sin ninguna intersectando conserva la actual', () => {
    expect(pickActiveSection(IDS, new Set(), 'services', false)).toBe('services');
  });

  it('al final de la página marca la última aunque no intersecte', () => {
    expect(pickActiveSection(IDS, new Set(['services']), 'services', true)).toBe('blocks');
    expect(pickActiveSection(IDS, new Set(), 'practice', true)).toBe('blocks');
  });

  it('ignora ids desconocidos', () => {
    expect(pickActiveSection(IDS, new Set(['otro']), 'hours', false)).toBe('hours');
  });
});
