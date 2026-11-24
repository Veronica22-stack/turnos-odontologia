import { describe, expect, it, vi } from 'vitest';
import { createAnamnesisSection } from './anamnesis-section';
import { createClinicalRecordView, type ClinicalRecordViewDeps } from './clinical-record-view';
import { createEvolutionsSection } from './evolutions-section';
import type { AnamnesisEntry, EvolutionEntry } from './clinical-record-types';
import type { PatientSummary } from '../patients/patients-types';

const SECRET = 'DiagnosticoReservado12345';
const PATIENT: PatientSummary = {
  id: 'pac-001', firstName: 'Julieta', lastName: 'Simulada', dni: '30.456.789', phone: '11 5555 0001', active: true,
};
const ANAMNESIS: AnamnesisEntry = {
  id: 'a1', version: 1,
  content: { schemaVersion: 1, allergies: SECRET, medications: null, medicalConditions: null, habits: null, notes: SECRET },
  authorId: 'u1', createdAt: '2026-01-20T13:00:00Z',
  correctsEntryId: null, correctionReason: SECRET, correctedByEntryId: null,
};
const EVOLUTION: EvolutionEntry = {
  id: 'e1', description: SECRET, appointmentId: 'turno-9', authorId: 'u1', createdAt: '2026-01-20T13:00:00Z',
  correctsEntryId: null, correctionReason: SECRET, correctedByEntryId: null,
};

function deps(): ClinicalRecordViewDeps {
  return {
    patients: { search: vi.fn(async () => ({ ok: true as const, data: [PATIENT] })) } as unknown as ClinicalRecordViewDeps['patients'],
    getAnamnesis: vi.fn(async () => ({ ok: true as const, data: { current: ANAMNESIS, history: [ANAMNESIS] } })),
    getOdontogram: vi.fn(async () => ({ ok: true as const, data: { current: [], history: [] } })),
    getEvolutions: vi.fn(async () => ({ ok: true as const, data: { entries: [EVOLUTION] } })),
    registerAnamnesis: vi.fn(async () => ({ ok: false as const, failure: { kind: 'validation' as const } })),
    registerOdontogram: vi.fn(async () => ({ ok: false as const, failure: { kind: 'validation' as const } })),
    registerEvolution: vi.fn(async () => ({ ok: false as const, failure: { kind: 'validation' as const } })),
    correctEntry: vi.fn(async () => ({ ok: false as const, failure: { kind: 'validation' as const } })),
    navigate: vi.fn(),
    onSessionExpired: vi.fn(),
  };
}

function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

function capture(): { stop(): void; dump(): string } {
  const sinks: string[] = [];
  const log = vi.spyOn(console, 'log').mockImplementation(() => {});
  const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
  const error = vi.spyOn(console, 'error').mockImplementation(() => {});
  const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {});
  const replace = vi.spyOn(window.history, 'replaceState').mockImplementation(() => {});
  const push = vi.spyOn(window.history, 'pushState').mockImplementation(() => {});
  const title = document.title;
  return {
    stop() { log.mockRestore(); warn.mockRestore(); error.mockRestore(); setItem.mockRestore(); replace.mockRestore(); push.mockRestore(); document.title = title; },
    dump() {
      return [
        window.location.href, window.location.search, window.location.hash, document.title,
        JSON.stringify(window.history.state ?? null),
        ...(setItem.mock.calls.map((call) => String(call[1]))),
        ...(replace.mock.calls.map((call) => String(call[2]))),
        ...(push.mock.calls.map((call) => String(call[2]))),
      ].join(' ');
    },
  };
}

describe('privacidad del contenido clínico', () => {
  it('la selección en memoria no filtra paciente ni contenido a URL/título/consola/almacenamiento', async () => {
    const spy = capture();
    const beforeTitle = document.title;
    const view = createClinicalRecordView(deps());
    document.body.replaceChildren(view.element);
    await settle();
    [...view.element.querySelectorAll('button')][0]?.click();
    await settle();

    expect(spy.dump()).not.toContain(SECRET);
    expect(spy.dump()).not.toContain(PATIENT.dni);
    expect(spy.dump()).not.toContain(PATIENT.id);
    expect(document.title).toBe(beforeTitle);
    spy.stop();
  });

  it('los render de anamnesis y evoluciones no escriben datos clínicos fuera del DOM', () => {
    const spy = capture();
    const a = createAnamnesisSection({ load: vi.fn(async () => ({ current: ANAMNESIS, history: [ANAMNESIS] })) });
    a.render({ current: ANAMNESIS, history: [ANAMNESIS] });
    document.body.replaceChildren(a.element);
    const e = createEvolutionsSection({ load: vi.fn(async () => ({ entries: [EVOLUTION] })) });
    e.render({ entries: [EVOLUTION] });
    expect(spy.dump()).not.toContain(SECRET);
    spy.stop();
  });

  it('los mensajes de error no repiten el contenido rechazado', async () => {
    const spy = capture();
    const form = createClinicalRecordView(deps());
    document.body.replaceChildren(form.element);
    await settle();
    expect(spy.dump()).not.toContain(SECRET);
    spy.stop();
  });
});
