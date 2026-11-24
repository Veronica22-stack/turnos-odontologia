import { describe, expect, it, vi } from 'vitest';
import { formatClinicalInstant } from './clinical-record-dates';
import type { AnamnesisEntry, AnamnesisResponse } from './clinical-record-types';
import { createAnamnesisSection } from './anamnesis-section';

const ENTRY_1: AnamnesisEntry = {
  id: 'a1',
  version: 1,
  content: {
    schemaVersion: 1,
    allergies: 'Penicilina',
    medications: 'Ibuprofeno',
    medicalConditions: 'Asma',
    habits: 'No fuma',
    notes: 'Control inicial',
  },
  authorId: 'u1',
  createdAt: '2026-01-20T13:00:00Z',
  correctsEntryId: null,
  correctionReason: null,
  correctedByEntryId: 'a2',
};
const ENTRY_2: AnamnesisEntry = {
  ...ENTRY_1,
  id: 'a2',
  version: 2,
  content: { ...ENTRY_1.content, allergies: 'Penicilina y amoxicilina' },
  correctsEntryId: 'a1',
  correctionReason: 'Se completó la alergia',
  correctedByEntryId: null,
};

function response(current: AnamnesisEntry | null = ENTRY_2, history: readonly AnamnesisEntry[] = [ENTRY_2, ENTRY_1]): AnamnesisResponse {
  return { current, history };
}

function render(data: AnamnesisResponse = response(), load = vi.fn(async () => data)) {
  const section = createAnamnesisSection({ load });
  document.body.replaceChildren(section.element);
  section.render(data);
  return section;
}

describe('anamnesis section: render', () => {
  it('muestra la versión vigente con los cinco campos, autor e instante local', () => {
    render();

    expect(document.querySelector('.anamnesis-current h3')?.textContent).toContain('Versión 2');
    const text = document.querySelector('.anamnesis-current')?.textContent ?? '';
    for (const value of ['Penicilina y amoxicilina', 'Ibuprofeno', 'Asma', 'No fuma', 'Control inicial']) {
      expect(text).toContain(value);
    }
    expect(text).toContain(formatClinicalInstant(ENTRY_2.createdAt));
    expect(text).toContain('Se completó la alergia');
  });

  it('muestra el historial descendente, marca la corregida y referencia su reemplazo', () => {
    render();

    const items = [...document.querySelectorAll('.anamnesis-history li')];
    expect(items).toHaveLength(2);
    expect(items[0]?.textContent).toContain('Versión 2');
    expect(items[1]?.textContent).toContain('Versión 1');
    expect(items[1]?.textContent).toContain('Corregida');
    expect(items[1]?.textContent).toContain('a2');
  });

  it('no muestra ninguna versión como vigente si la respuesta no tiene actual', () => {
    render(response(null, []));

    expect(document.querySelector('.anamnesis-current')?.textContent).toContain('Sin anamnesis registrada');
    expect(document.querySelectorAll('.anamnesis-history li')).toHaveLength(0);
  });
});

describe('anamnesis section: estados de carga', () => {
  it('muestra un indicador anunciable mientras carga', () => {
    const load = vi.fn(() => new Promise<AnamnesisResponse>(() => {}));
    const section = createAnamnesisSection({ load });
    document.body.replaceChildren(section.element);

    void section.reload();

    expect(section.element.querySelector('[role="status"]')?.textContent).toContain('Cargando');
    expect(section.element.querySelector('[role="alert"]')).toBeNull();
  });

  it('muestra error con Reintentar solo en lectura y sin datos parciales', async () => {
    const load = vi.fn(async () => { throw new Error('red'); });
    const section = createAnamnesisSection({ load });
    document.body.replaceChildren(section.element);

    await section.reload();

    expect(section.element.querySelector('[role="alert"]')?.textContent).toContain('No pudimos cargar');
    expect(section.element.textContent).toContain('Reintentar');
    expect(section.element.textContent).not.toContain('Penicilina');

    section.element.querySelector<HTMLButtonElement>('[data-retry]')?.click();
    expect(load).toHaveBeenCalledTimes(2);
  });
});
