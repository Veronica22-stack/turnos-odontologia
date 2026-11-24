import { isNumber, isRecord, isString, type Decoder } from '../../core/http/decode';
import { isZonedInstant } from '../settings/settings-types';

/**
 * DTOs readonly y decodificadores estrictos del contrato clínico (C-16/C-17/C-18).
 * Los campos desconocidos se rechazan: solo se acepta exactamente el contrato de la API.
 */

export type ToothCondition = 'HEALTHY' | 'CARIES' | 'FILLED' | 'MISSING' | 'CROWN' | 'ROOT_CANAL';
export type ToothSurface = 'OCCLUSAL' | 'MESIAL' | 'DISTAL' | 'VESTIBULAR' | 'LINGUAL';
export type ClinicalEntryType = 'anamnesis' | 'odontogram' | 'evolutions';

export interface AnamnesisContent {
  readonly schemaVersion: number;
  readonly allergies: string | null;
  readonly medications: string | null;
  readonly medicalConditions: string | null;
  readonly habits: string | null;
  readonly notes: string | null;
}

export interface AnamnesisEntry {
  readonly id: string;
  readonly version: number;
  readonly content: AnamnesisContent;
  readonly authorId: string;
  readonly createdAt: string;
  readonly correctsEntryId: string | null;
  readonly correctionReason: string | null;
  readonly correctedByEntryId: string | null;
}

export interface OdontogramEntry {
  readonly id: string;
  readonly toothCode: string;
  readonly surface: ToothSurface | null;
  readonly condition: ToothCondition;
  readonly notes: string | null;
  readonly authorId: string;
  readonly createdAt: string;
  readonly correctsEntryId: string | null;
  readonly correctionReason: string | null;
  readonly correctedByEntryId: string | null;
}

export interface EvolutionEntry {
  readonly id: string;
  readonly description: string;
  readonly appointmentId: string | null;
  readonly authorId: string;
  readonly createdAt: string;
  readonly correctsEntryId: string | null;
  readonly correctionReason: string | null;
  readonly correctedByEntryId: string | null;
}

export interface AnamnesisResponse {
  readonly current: AnamnesisEntry | null;
  readonly history: readonly AnamnesisEntry[];
}

export interface OdontogramResponse {
  readonly current: readonly OdontogramEntry[];
  readonly history: readonly OdontogramEntry[];
}

export interface EvolutionsResponse {
  readonly entries: readonly EvolutionEntry[];
}

const ANAMNESIS_ENTRY_KEYS: ReadonlySet<string> = new Set([
  'id', 'version', 'content', 'authorId', 'createdAt', 'correctsEntryId', 'correctionReason', 'correctedByEntryId',
]);
const ANAMNESIS_CONTENT_KEYS: ReadonlySet<string> = new Set([
  'schemaVersion', 'allergies', 'medications', 'medicalConditions', 'habits', 'notes',
]);
const ODONTOGRAM_ENTRY_KEYS: ReadonlySet<string> = new Set([
  'id', 'toothCode', 'surface', 'condition', 'notes', 'authorId', 'createdAt',
  'correctsEntryId', 'correctionReason', 'correctedByEntryId',
]);
const EVOLUTION_ENTRY_KEYS: ReadonlySet<string> = new Set([
  'id', 'description', 'appointmentId', 'authorId', 'createdAt',
  'correctsEntryId', 'correctionReason', 'correctedByEntryId',
]);
const TOOTH_CODES: ReadonlySet<string> = new Set(
  [...Array.from({ length: 8 }, (_, quadrant) => quadrant + 1)].flatMap((quadrant) =>
    Array.from({ length: quadrant <= 4 ? 8 : 5 }, (_, index) => `${quadrant}${index + 1}`),
  ),
);
const CONDITIONS: ReadonlySet<string> = new Set<ToothCondition>([
  'HEALTHY', 'CARIES', 'FILLED', 'MISSING', 'CROWN', 'ROOT_CANAL',
]);
const SURFACES: ReadonlySet<string> = new Set<ToothSurface>([
  'OCCLUSAL', 'MESIAL', 'DISTAL', 'VESTIBULAR', 'LINGUAL',
]);
const SURFACE_ONLY_CONDITIONS: ReadonlySet<string> = new Set<ToothCondition>(['CARIES', 'FILLED']);

function hasOnlyKeys(value: Record<string, unknown>, allowed: ReadonlySet<string>): boolean {
  return Object.keys(value).every((key) => allowed.has(key));
}

function decodeText(value: unknown): string | null | undefined {
  if (value === null) {
    return null;
  }
  return isString(value) ? value : undefined;
}

function decodeId(value: unknown): string | undefined {
  return isString(value) && value !== '' ? value : undefined;
}

function decodeVersion(value: unknown): number | undefined {
  return isNumber(value) && Number.isInteger(value) && value >= 1 ? value : undefined;
}

function decodeArray<T>(value: unknown, decodeItem: Decoder<T>): readonly T[] | undefined {
  if (!Array.isArray(value)) {
    return undefined;
  }
  const result: T[] = [];
  for (const item of value as unknown[]) {
    const decoded = decodeItem(item);
    if (decoded === undefined) {
      return undefined;
    }
    result.push(decoded);
  }
  return result;
}

const decodeAnamnesisContent: Decoder<AnamnesisContent> = (value) => {
  if (!isRecord(value) || !hasOnlyKeys(value, ANAMNESIS_CONTENT_KEYS)) {
    return undefined;
  }
  const schemaVersion = value['schemaVersion'];
  const allergies = decodeText(value['allergies']);
  const medications = decodeText(value['medications']);
  const medicalConditions = decodeText(value['medicalConditions']);
  const habits = decodeText(value['habits']);
  const notes = decodeText(value['notes']);
  if (
    !isNumber(schemaVersion) ||
    !Number.isInteger(schemaVersion) ||
    allergies === undefined ||
    medications === undefined ||
    medicalConditions === undefined ||
    habits === undefined ||
    notes === undefined
  ) {
    return undefined;
  }
  return { schemaVersion, allergies, medications, medicalConditions, habits, notes };
};

export const decodeAnamnesisEntry: Decoder<AnamnesisEntry> = (value) => {
  if (!isRecord(value) || !hasOnlyKeys(value, ANAMNESIS_ENTRY_KEYS)) {
    return undefined;
  }
  const id = decodeId(value['id']);
  const version = decodeVersion(value['version']);
  const content = decodeAnamnesisContent(value['content']);
  const authorId = decodeId(value['authorId']);
  const createdAt = isZonedInstant(value['createdAt']) ? value['createdAt'] : undefined;
  const correctsEntryId = decodeText(value['correctsEntryId']);
  const correctionReason = decodeText(value['correctionReason']);
  const correctedByEntryId = decodeText(value['correctedByEntryId']);
  if (
    id === undefined ||
    version === undefined ||
    content === undefined ||
    authorId === undefined ||
    createdAt === undefined ||
    correctsEntryId === undefined ||
    correctionReason === undefined ||
    correctedByEntryId === undefined
  ) {
    return undefined;
  }
  return { id, version, content, authorId, createdAt, correctsEntryId, correctionReason, correctedByEntryId };
};

export const decodeAnamnesisResponse: Decoder<AnamnesisResponse> = (value) => {
  if (!isRecord(value) || Object.keys(value).some((key) => key !== 'current' && key !== 'history')) {
    return undefined;
  }
  const currentValue = value['current'];
  const current = currentValue === null ? null : decodeAnamnesisEntry(currentValue);
  const history = decodeArray(value['history'], decodeAnamnesisEntry);
  if (current === undefined || history === undefined) {
    return undefined;
  }
  return { current, history };
};

export const decodeOdontogramEntry: Decoder<OdontogramEntry> = (value) => {
  if (!isRecord(value) || !hasOnlyKeys(value, ODONTOGRAM_ENTRY_KEYS)) {
    return undefined;
  }
  const id = decodeId(value['id']);
  const toothCode = value['toothCode'];
  const surfaceValue = value['surface'];
  const conditionValue = value['condition'];
  const surface = surfaceValue === null ? null : decodeText(surfaceValue);
  const notes = decodeText(value['notes']);
  const authorId = decodeId(value['authorId']);
  const createdAt = isZonedInstant(value['createdAt']) ? value['createdAt'] : undefined;
  const correctsEntryId = decodeText(value['correctsEntryId']);
  const correctionReason = decodeText(value['correctionReason']);
  const correctedByEntryId = decodeText(value['correctedByEntryId']);
  if (
    id === undefined ||
    !isString(toothCode) ||
    !TOOTH_CODES.has(toothCode) ||
    !isString(conditionValue) ||
    !CONDITIONS.has(conditionValue) ||
    surface === undefined ||
    (surface !== null && (!SURFACES.has(surface) || !SURFACE_ONLY_CONDITIONS.has(conditionValue))) ||
    notes === undefined ||
    authorId === undefined ||
    createdAt === undefined ||
    correctsEntryId === undefined ||
    correctionReason === undefined ||
    correctedByEntryId === undefined
  ) {
    return undefined;
  }
  return {
    id,
    toothCode,
    surface: surface as ToothSurface | null,
    condition: conditionValue as ToothCondition,
    notes,
    authorId,
    createdAt,
    correctsEntryId,
    correctionReason,
    correctedByEntryId,
  };
};

export const decodeOdontogramResponse: Decoder<OdontogramResponse> = (value) => {
  if (!isRecord(value) || Object.keys(value).some((key) => key !== 'current' && key !== 'history')) {
    return undefined;
  }
  const current = decodeArray(value['current'], decodeOdontogramEntry);
  const history = decodeArray(value['history'], decodeOdontogramEntry);
  return current === undefined || history === undefined ? undefined : { current, history };
};

export const decodeEvolutionEntry: Decoder<EvolutionEntry> = (value) => {
  if (!isRecord(value) || !hasOnlyKeys(value, EVOLUTION_ENTRY_KEYS)) {
    return undefined;
  }
  const id = decodeId(value['id']);
  const description = value['description'];
  const appointmentId = decodeText(value['appointmentId']);
  const authorId = decodeId(value['authorId']);
  const createdAt = isZonedInstant(value['createdAt']) ? value['createdAt'] : undefined;
  const correctsEntryId = decodeText(value['correctsEntryId']);
  const correctionReason = decodeText(value['correctionReason']);
  const correctedByEntryId = decodeText(value['correctedByEntryId']);
  if (
    id === undefined ||
    !isString(description) ||
    description.trim() === '' ||
    appointmentId === undefined ||
    authorId === undefined ||
    createdAt === undefined ||
    correctsEntryId === undefined ||
    correctionReason === undefined ||
    correctedByEntryId === undefined
  ) {
    return undefined;
  }
  return { id, description, appointmentId, authorId, createdAt, correctsEntryId, correctionReason, correctedByEntryId };
};

export const decodeEvolutionsResponse: Decoder<EvolutionsResponse> = (value) => {
  if (!isRecord(value) || Object.keys(value).some((key) => key !== 'entries')) {
    return undefined;
  }
  const entries = decodeArray(value['entries'], decodeEvolutionEntry);
  return entries === undefined ? undefined : { entries };
};
