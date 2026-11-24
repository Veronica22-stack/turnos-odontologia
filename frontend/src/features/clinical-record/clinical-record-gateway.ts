import type { HttpClient, RequestOptions } from '../../core/http/client';
import type { HttpMethod } from '../../core/http/transport';
import { toClinicalFailure, type ClinicalFailure } from './clinical-record-messages';
import {
  decodeAnamnesisEntry,
  decodeAnamnesisResponse,
  decodeEvolutionEntry,
  decodeEvolutionsResponse,
  decodeOdontogramEntry,
  decodeOdontogramResponse,
  type AnamnesisEntry,
  type AnamnesisResponse,
  type ClinicalEntryType,
  type EvolutionEntry,
  type EvolutionsResponse,
  type OdontogramEntry,
  type OdontogramResponse,
  type ToothCondition,
  type ToothSurface,
} from './clinical-record-types';

/** Único lugar que conoce rutas, cuerpos y decodificadores del contrato clínico (D2). */

export type { ClinicalFailure };
export type ClinicalResult<T> = { readonly ok: true; readonly data: T } | { readonly ok: false; readonly failure: ClinicalFailure };

export interface AnamnesisInput {
  readonly allergies: string;
  readonly medications: string;
  readonly medicalConditions: string;
  readonly habits: string;
  readonly notes: string;
}

export interface OdontogramEntryInput {
  readonly toothCode: string;
  readonly condition: ToothCondition;
  readonly surface?: ToothSurface;
  readonly notes?: string;
}

export interface EvolutionInput {
  readonly description: string;
  readonly appointmentId?: string | null;
}

export type CorrectionContent =
  | { readonly allergies?: string; readonly medications?: string; readonly medicalConditions?: string; readonly habits?: string; readonly notes?: string }
  | { readonly condition?: ToothCondition; readonly notes?: string }
  | { readonly description?: string };

export interface CorrectionInput {
  readonly reason: string;
  readonly content: CorrectionContent;
}

export interface ClinicalRecordGateway {
  getAnamnesis(patientId: string): Promise<ClinicalResult<AnamnesisResponse>>;
  getOdontogram(patientId: string): Promise<ClinicalResult<OdontogramResponse>>;
  getEvolutions(patientId: string): Promise<ClinicalResult<EvolutionsResponse>>;
  registerAnamnesis(patientId: string, input: AnamnesisInput): Promise<ClinicalResult<AnamnesisEntry>>;
  registerOdontogram(patientId: string, entries: readonly OdontogramEntryInput[]): Promise<ClinicalResult<readonly OdontogramEntry[]>>;
  registerEvolution(patientId: string, input: EvolutionInput): Promise<ClinicalResult<EvolutionEntry>>;
  correctEntry(
    patientId: string, type: ClinicalEntryType, entryId: string, input: CorrectionInput,
  ): Promise<ClinicalResult<AnamnesisEntry | OdontogramEntry | EvolutionEntry>>;
}

export interface ClinicalRecordGatewayOptions {
  readonly client: HttpClient;
}

const DECODERS: Readonly<Record<ClinicalEntryType, (value: unknown) => AnamnesisEntry | OdontogramEntry | EvolutionEntry | undefined>> = {
  anamnesis: decodeAnamnesisEntry,
  odontogram: decodeOdontogramEntry,
  evolutions: decodeEvolutionEntry,
};

function anamnesisBody(input: AnamnesisInput): AnamnesisInput {
  return {
    allergies: input.allergies,
    medications: input.medications,
    medicalConditions: input.medicalConditions,
    habits: input.habits,
    notes: input.notes,
  };
}

function odontogramBody(entries: readonly OdontogramEntryInput[]): { readonly entries: readonly OdontogramEntryInput[] } {
  return {
    entries: entries.map((entry) => ({
      toothCode: entry.toothCode,
      condition: entry.condition,
      ...(entry.surface === undefined ? {} : { surface: entry.surface }),
      ...(entry.notes === undefined ? {} : { notes: entry.notes }),
    })),
  };
}

function evolutionBody(input: EvolutionInput): { readonly description: string; readonly appointmentId?: string | null } {
  return {
    description: input.description,
    ...(input.appointmentId === undefined || input.appointmentId === null ? {} : { appointmentId: input.appointmentId }),
  };
}

const ANAMNESIS_CORRECTION_FIELDS = ['allergies', 'medications', 'medicalConditions', 'habits', 'notes'] as const;

function contentField(content: CorrectionContent, key: string): unknown {
  return (content as unknown as Record<string, unknown>)[key];
}

function correctionBody(type: ClinicalEntryType, input: CorrectionInput): Record<string, unknown> {
  if (type === 'odontogram') {
    const condition = contentField(input.content, 'condition');
    const notes = contentField(input.content, 'notes');
    return {
      reason: input.reason,
      ...(typeof condition === 'string' ? { condition } : {}),
      ...(typeof notes === 'string' ? { notes } : {}),
    };
  }
  if (type === 'evolutions') {
    const description = contentField(input.content, 'description');
    return { reason: input.reason, ...(typeof description === 'string' ? { description } : {}) };
  }
  const content: Record<string, string> = {};
  for (const key of ANAMNESIS_CORRECTION_FIELDS) {
    const value = contentField(input.content, key);
    if (typeof value === 'string') {
      content[key] = value;
    }
  }
  return { reason: input.reason, content };
}

export function createClinicalRecordGateway(options: ClinicalRecordGatewayOptions): ClinicalRecordGateway {
  const { client } = options;
  const base = (patientId: string): string => `/patients/${encodeURIComponent(patientId)}/clinical-record`;

  async function request<T>(
    method: HttpMethod,
    path: string,
    requestOptions: RequestOptions<T>,
  ): Promise<ClinicalResult<T>> {
    const result = await client.request<T>(method, path, requestOptions);
    return result.ok
      ? { ok: true, data: result.data }
      : { ok: false, failure: toClinicalFailure(result.error) };
  }

  return {
    getAnamnesis: (patientId) =>
      request('GET', `${base(patientId)}/anamnesis`, {
        decode: decodeAnamnesisResponse,
      }),
    getOdontogram: (patientId) =>
      request('GET', `${base(patientId)}/odontogram`, {
        decode: decodeOdontogramResponse,
      }),
    getEvolutions: (patientId) =>
      request('GET', `${base(patientId)}/evolutions`, {
        decode: decodeEvolutionsResponse,
      }),
    registerAnamnesis: (patientId, input) =>
      request('POST', `${base(patientId)}/anamnesis`, {
        body: anamnesisBody(input),
        decode: decodeAnamnesisEntry,
      }),
    registerOdontogram: (patientId, entries) =>
      request('POST', `${base(patientId)}/odontogram`, {
        body: odontogramBody(entries),
        decode: (value: unknown): readonly OdontogramEntry[] | undefined => {
          if (!value || typeof value !== 'object' || !('entries' in value)) {
            return undefined;
          }
          const raw = (value as { readonly entries: unknown }).entries;
          if (!Array.isArray(raw)) {
            return undefined;
          }
          const result: OdontogramEntry[] = [];
          for (const item of raw as unknown[]) {
            const decoded = decodeOdontogramEntry(item);
            if (decoded === undefined) {
              return undefined;
            }
            result.push(decoded);
          }
          return result;
        },
      }),
    registerEvolution: (patientId, input) =>
      request('POST', `${base(patientId)}/evolutions`, {
        body: evolutionBody(input),
        decode: decodeEvolutionEntry,
      }),
    correctEntry: (patientId, type, entryId, input) =>
      request('POST', `${base(patientId)}/${type}/${encodeURIComponent(entryId)}/corrections`, {
        body: correctionBody(type, input),
        decode: DECODERS[type],
      }),
  };
}
