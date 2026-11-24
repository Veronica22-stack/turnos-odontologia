import { describe, expect, it } from 'vitest';
import {
  decodeAnamnesisResponse,
  decodeEvolutionEntry,
  decodeEvolutionsResponse,
  decodeOdontogramResponse,
  type AnamnesisEntry,
  type EvolutionEntry,
  type OdontogramEntry,
} from './clinical-record-types';

const ANAMNESIS_ENTRY = {
  id: 'a1',
  version: 2,
  content: {
    schemaVersion: 1,
    allergies: 'Penicilina',
    medications: null,
    medicalConditions: 'Asma',
    habits: null,
    notes: 'Control',
  },
  authorId: 'u1',
  createdAt: '2026-11-20T15:00:00Z',
  correctsEntryId: 'a0',
  correctionReason: 'Se completó alergia',
  correctedByEntryId: null,
};

const ODONTOGRAM_ENTRY = {
  id: 'o1',
  toothCode: '16',
  surface: 'OCCLUSAL',
  condition: 'CARIES',
  notes: 'Dolor',
  authorId: 'u1',
  createdAt: '2026-11-20T15:00:00Z',
  correctsEntryId: null,
  correctionReason: null,
  correctedByEntryId: 'o2',
};

const EVOLUTION_ENTRY = {
  id: 'e1',
  description: 'Control sin complicaciones',
  appointmentId: 'c1',
  authorId: 'u1',
  createdAt: '2026-11-20T15:00:00Z',
  correctsEntryId: null,
  correctionReason: null,
  correctedByEntryId: null,
};

describe('decodeAnamnesisResponse', () => {
  it('decodifica la vigente y el historial con todos sus campos', () => {
    const result = decodeAnamnesisResponse({ current: ANAMNESIS_ENTRY, history: [ANAMNESIS_ENTRY] });

    expect(result?.current).toEqual(ANAMNESIS_ENTRY);
    expect(result?.history).toEqual([ANAMNESIS_ENTRY]);
  });

  it('acepta una respuesta sin anamnesis vigente', () => {
    const result = decodeAnamnesisResponse({ current: null, history: [] });

    expect(result?.current).toBeNull();
    expect(result?.history).toEqual([]);
  });

  it('rechaza una versión o un contenido mal formado sin convertir unknown en any', () => {
    expect(decodeAnamnesisResponse({ current: { ...ANAMNESIS_ENTRY, version: '2' }, history: [] })).toBeUndefined();
    expect(
      decodeAnamnesisResponse({
        current: { ...ANAMNESIS_ENTRY, content: { ...ANAMNESIS_ENTRY.content, allergies: 123 } },
        history: [],
      }),
    ).toBeUndefined();
  });

  it('rechaza entradas del historial con campos desconocidos', () => {
    expect(
      decodeAnamnesisResponse({
        current: null,
        history: [{ ...ANAMNESIS_ENTRY, extra: 'dato clínico' }],
      }),
    ).toBeUndefined();
  });
});

describe('decodeOdontogramResponse', () => {
  it('decodifica el estado actual y el historial conservando correcciones', () => {
    const result = decodeOdontogramResponse({ current: [ODONTOGRAM_ENTRY], history: [ODONTOGRAM_ENTRY] });

    expect(result?.current).toEqual([ODONTOGRAM_ENTRY]);
    expect(result?.history).toEqual([ODONTOGRAM_ENTRY]);
  });

  it('rechaza un código FDI, una cara o un estado fuera del catálogo', () => {
    expect(
      decodeOdontogramResponse({ current: [{ ...ODONTOGRAM_ENTRY, toothCode: '99' }], history: [] }),
    ).toBeUndefined();
    expect(
      decodeOdontogramResponse({ current: [{ ...ODONTOGRAM_ENTRY, condition: 'UNKNOWN' }], history: [] }),
    ).toBeUndefined();
    expect(
      decodeOdontogramResponse({ current: [{ ...ODONTOGRAM_ENTRY, surface: 'TOP' }], history: [] }),
    ).toBeUndefined();
    expect(
      decodeOdontogramResponse({
        current: [{ ...ODONTOGRAM_ENTRY, condition: 'MISSING', surface: 'OCCLUSAL' }],
        history: [],
      }),
    ).toBeUndefined();
  });

  it('rechaza una lista que no es arreglo y una entrada con campos desconocidos', () => {
    expect(decodeOdontogramResponse({ current: 'no', history: [] })).toBeUndefined();
    expect(
      decodeOdontogramResponse({ current: [], history: [{ ...ODONTOGRAM_ENTRY, clinicalRecordId: 'r1' }] }),
    ).toBeUndefined();
  });
});

describe('decodeEvolutionEntry y decodeEvolutionsResponse', () => {
  it('decodifica una evolución con turno y una sin turno', () => {
    expect(decodeEvolutionEntry(EVOLUTION_ENTRY)).toEqual(EVOLUTION_ENTRY);
    expect(decodeEvolutionEntry({ ...EVOLUTION_ENTRY, appointmentId: null })).toEqual({
      ...EVOLUTION_ENTRY,
      appointmentId: null,
    });
  });

  it('decodifica el historial devuelto por el servidor', () => {
    expect(decodeEvolutionsResponse({ entries: [EVOLUTION_ENTRY, { ...EVOLUTION_ENTRY, id: 'e2' }] })?.entries).toEqual([
      EVOLUTION_ENTRY,
      { ...EVOLUTION_ENTRY, id: 'e2' },
    ]);
  });

  it('rechaza descripciones vacías, instantes inválidos y campos desconocidos', () => {
    expect(decodeEvolutionEntry({ ...EVOLUTION_ENTRY, description: '' })).toBeUndefined();
    expect(decodeEvolutionEntry({ ...EVOLUTION_ENTRY, createdAt: '2026-11-20' })).toBeUndefined();
    expect(decodeEvolutionEntry({ ...EVOLUTION_ENTRY, authorId: 'u1', extra: 1 })).toBeUndefined();
    expect(decodeEvolutionsResponse({ entries: 'no' })).toBeUndefined();
  });
});

describe('tipos readonly del contrato', () => {
  it('expone declaraciones de entrada inmutables para los tres tipos', () => {
    const anamnesis: AnamnesisEntry = ANAMNESIS_ENTRY as AnamnesisEntry;
    const odontogram: OdontogramEntry = ODONTOGRAM_ENTRY as OdontogramEntry;
    const evolution: EvolutionEntry = EVOLUTION_ENTRY as EvolutionEntry;

    expect([anamnesis.id, odontogram.id, evolution.id]).toEqual(['a1', 'o1', 'e1']);
  });
});
