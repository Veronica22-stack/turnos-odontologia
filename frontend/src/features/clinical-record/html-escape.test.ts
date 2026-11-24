import { describe, expect, it, vi } from 'vitest';
import { createAnamnesisSection } from './anamnesis-section';
import { createCorrectionDialog } from './correction-dialog';
import { createEvolutionsSection } from './evolutions-section';
import type { AnamnesisEntry, EvolutionEntry } from './clinical-record-types';

const XSS = '<script>window.__xss=1</script> & "quotes" <img src=x onerror=alert(1)>';

const ANAMNESIS: AnamnesisEntry = {
  id: 'a1', version: 1,
  content: { schemaVersion: 1, allergies: XSS, medications: null, medicalConditions: null, habits: null, notes: XSS },
  authorId: 'u1', createdAt: '2026-01-20T13:00:00Z',
  correctsEntryId: null, correctionReason: XSS, correctedByEntryId: null,
};
const EVOLUTION: EvolutionEntry = {
  id: 'e1', description: XSS, appointmentId: null, authorId: 'u1', createdAt: '2026-01-20T13:00:00Z',
  correctsEntryId: null, correctionReason: XSS, correctedByEntryId: null,
};

function assertLiteral(root: HTMLElement, text: string): void {
  expect(root.querySelector('script')).toBeNull();
  expect(root.querySelector('img')).toBeNull();
  expect(root.textContent).toContain(text);
}

describe('escape de HTML en historia clínica', () => {
  it('notas de anamnesis se insertan como texto, nunca como markup', () => {
    const section = createAnamnesisSection({ load: vi.fn(async () => ({ current: ANAMNESIS, history: [ANAMNESIS] })) });
    section.render({ current: ANAMNESIS, history: [ANAMNESIS] });
    document.body.replaceChildren(section.element);
    assertLiteral(section.element, XSS);
  });

  it('descripción de evoluciones se inserta como texto', () => {
    const section = createEvolutionsSection({ load: vi.fn(async () => ({ entries: [EVOLUTION] })) });
    section.render({ entries: [EVOLUTION] });
    document.body.replaceChildren(section.element);
    assertLiteral(section.element, XSS);
  });

  it('el motivo del diálogo se inserta como texto y entidades no se interpretan', () => {
    const dialog = createCorrectionDialog({ entryLabel: XSS, onSubmit: vi.fn(async () => ({ ok: false as const, failure: { kind: 'validation' as const } })), onClose: vi.fn() });
    document.body.replaceChildren(dialog.element);
    assertLiteral(dialog.element, XSS);
    expect(dialog.element.querySelector('h2')?.textContent).toBe('Corregir entrada');
  });
});
