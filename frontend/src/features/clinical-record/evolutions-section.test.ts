import { describe, expect, it, vi } from 'vitest';
import { formatClinicalInstant } from './clinical-record-dates';
import type { EvolutionEntry, EvolutionsResponse } from './clinical-record-types';
import { createEvolutionsSection } from './evolutions-section';

function entry(id: string, createdAt: string, overrides: Partial<EvolutionEntry> = {}): EvolutionEntry {
  return {
    id, description: `Evolución ${id}`, appointmentId: null, authorId: 'u1', createdAt,
    correctsEntryId: null, correctionReason: null, correctedByEntryId: null, ...overrides,
  };
}

const OLD = entry('e1', '2026-01-20T13:00:00Z', { correctedByEntryId: 'e2' });
const NEW = entry('e2', '2026-06-20T15:00:00Z', { correctsEntryId: 'e1', correctionReason: 'Se completó', appointmentId: 'turno-9' });

function render(data: EvolutionsResponse = { entries: [OLD, NEW] }, load = vi.fn(async () => data)) {
  const section = createEvolutionsSection({ load });
  document.body.replaceChildren(section.element);
  section.render(data);
  return section;
}

describe('evolutions section: render', () => {
  it('muestra las entradas en orden cronológico descendente con autor, fecha local y turno', () => {
    render();
    const items = [...document.querySelectorAll('.entry')];
    expect(items).toHaveLength(2);
    expect(items[0]?.textContent).toContain('Evolución e2');
    expect(items[1]?.textContent).toContain('Evolución e1');
    expect(items[0]?.textContent).toContain(formatClinicalInstant(NEW.createdAt));
    expect(items[0]?.textContent).toContain('u1');
    expect(items[0]?.textContent).toContain('turno-9');
  });

  it('marca la entrada corregida y no la presenta como vigente', () => {
    render();
    const corrected = document.querySelectorAll('.entry.corrected');
    expect(corrected).toHaveLength(1);
    expect(corrected[0]?.textContent).toContain('Evolución e1');
    expect(corrected[0]?.textContent).toContain('Corregida');
    expect(corrected[0]?.textContent).toContain('e2');
  });

  it('muestra estado vacío propio', () => {
    render({ entries: [] });
    expect(document.querySelector('.entry')).toBeNull();
    expect(document.querySelector('.clinical-section__body')?.textContent).toContain('Sin evoluciones');
  });
});

describe('evolutions section: estados', () => {
  it('muestra carga anunciable', () => {
    const load = vi.fn(() => new Promise<EvolutionsResponse>(() => {}));
    const section = createEvolutionsSection({ load });
    document.body.replaceChildren(section.element);
    void section.reload();
    expect(section.element.querySelector('[role="status"]')?.textContent).toContain('Cargando');
  });

  it('muestra error con reintento solo en lectura', async () => {
    const load = vi.fn(async () => { throw new Error('red'); });
    const section = createEvolutionsSection({ load });
    document.body.replaceChildren(section.element);
    await section.reload();
    expect(section.element.querySelector('[role="alert"]')?.textContent).toContain('No pudimos cargar');
    section.element.querySelector<HTMLButtonElement>('[data-retry]')?.click();
    expect(load).toHaveBeenCalledTimes(2);
  });
});
