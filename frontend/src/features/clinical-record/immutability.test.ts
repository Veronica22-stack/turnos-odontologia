import { describe, expect, it, vi } from 'vitest';
import gatewaySource from './clinical-record-gateway.ts?raw';
import { createAnamnesisSection } from './anamnesis-section';
import { createEvolutionsSection } from './evolutions-section';
import type { AnamnesisEntry, EvolutionEntry } from './clinical-record-types';

const FORBIDDEN = /editar|borrar|eliminar|modificar|remove|delete|edit|update/i;

const ANAMNESIS: AnamnesisEntry = {
  id: 'a1', version: 1,
  content: { schemaVersion: 1, allergies: null, medications: null, medicalConditions: null, habits: null, notes: null },
  authorId: 'u1', createdAt: '2026-01-20T13:00:00Z',
  correctsEntryId: null, correctionReason: null, correctedByEntryId: 'a2',
};
const EVOLUTION: EvolutionEntry = {
  id: 'e1', description: 'Control', appointmentId: null, authorId: 'u1', createdAt: '2026-01-20T13:00:00Z',
  correctsEntryId: null, correctionReason: null, correctedByEntryId: 'e2',
};

describe('inmutabilidad de la historia clínica', () => {
  it('el gateway solo usa GET y POST: sin PUT, PATCH ni DELETE', () => {
    expect(gatewaySource).toContain("request('GET'");
    expect(gatewaySource).toContain("request('POST'");
    expect(gatewaySource).not.toMatch(/\b(PUT|PATCH|DELETE)\b/);
  });

  it('el render de anamnesis no expone controles de edición ni borrado', () => {
    const section = createAnamnesisSection({ load: vi.fn(async () => ({ current: ANAMNESIS, history: [ANAMNESIS] })) });
    section.render({ current: ANAMNESIS, history: [ANAMNESIS] });
    document.body.replaceChildren(section.element);

    for (const control of document.querySelectorAll('button, a, [role="button"], [contenteditable]')) {
      expect(FORBIDDEN.test(`${control.getAttribute('data-action') ?? ''} ${control.textContent ?? ''}`)).toBe(false);
      expect(control.getAttribute('contenteditable')).toBeNull();
    }
    expect(document.querySelectorAll('[data-retry]').length).toBeLessThanOrEqual(1);
  });

  it('el render de evoluciones marca la original como corregida sin controles de cambio', () => {
    const section = createEvolutionsSection({ load: vi.fn(async () => ({ entries: [EVOLUTION] })) });
    section.render({ entries: [EVOLUTION] });
    document.body.replaceChildren(section.element);

    expect(document.querySelector('.entry.corrected')?.textContent).toContain('Corregida');
    expect(document.querySelector('.entry.corrected')?.textContent).toContain('e2');
    for (const control of document.querySelectorAll('button, a, [contenteditable]')) {
      expect(FORBIDDEN.test(`${control.getAttribute('data-action') ?? ''} ${control.textContent ?? ''}`)).toBe(false);
    }
  });

  it('la corrección solo crea una entrada nueva con reason, sin campos de servidor', () => {
    const correctionBodySource = gatewaySource.match(/function correctionBody[\s\S]*?\n}/)?.[0] ?? '';
    expect(gatewaySource).toContain('function correctionBody');
    expect(correctionBodySource).toMatch(/type === 'odontogram'/);
    expect(correctionBodySource).toMatch(/type === 'evolutions'/);
    expect(correctionBodySource).toContain('reason: input.reason');
    expect(correctionBodySource).toContain('return { reason: input.reason, content }');
    expect(correctionBodySource).not.toMatch(/\b(authorId|createdAt|id|clinicalRecordId|toothCode|surface|appointmentId)\b/);
  });
});
