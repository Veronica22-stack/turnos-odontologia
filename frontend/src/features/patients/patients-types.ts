import { isBoolean, isRecord, isString, type Decoder } from '../../core/http/decode';
import { isZonedInstant } from '../settings/settings-types';

/** DTOs readonly del contrato de pacientes (C-10 D2). Decodificadores estrictos, sin `any`. */

export type PatientSource = 'MANUAL' | 'PUBLIC_BOOKING';

export interface PatientSummary {
  readonly id: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly dni: string;
  readonly phone: string;
  readonly active: boolean;
}

export interface PatientDetail extends PatientSummary {
  readonly email: string | null;
  readonly birthDate: string | null;
  readonly address: string | null;
  readonly source: PatientSource;
  readonly createdAt: string;
  readonly updatedAt: string;
  /** RN-PA-03: solo si el servidor lo informa; ausente nunca se infiere del origen. */
  readonly reviewRequired?: boolean;
}

/** Campos editables; `null` representa un valor opcional vacío. */
export interface PatientInput {
  readonly firstName: string;
  readonly lastName: string;
  readonly phone: string;
  readonly email: string | null;
  readonly birthDate: string | null;
  readonly address: string | null;
}

const PATIENT_SOURCES: ReadonlySet<string> = new Set<PatientSource>(['MANUAL', 'PUBLIC_BOOKING']);
const LOCAL_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

/** Fecha calendario `yyyy-MM-dd` válida, sin zonas ni conversiones. */
export function isLocalDate(value: unknown): value is string {
  if (!isString(value)) {
    return false;
  }
  const match = LOCAL_DATE_PATTERN.exec(value);
  if (match === null) {
    return false;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const probe = new Date(Date.UTC(year, month - 1, day));
  return probe.getUTCFullYear() === year && probe.getUTCMonth() === month - 1 && probe.getUTCDate() === day;
}

function decodeText(value: unknown): string | undefined {
  return isString(value) ? value : undefined;
}

function decodeNullableText(value: unknown): string | null | undefined {
  if (value === null) {
    return null;
  }
  return isString(value) ? value : undefined;
}

function decodeSource(value: unknown): PatientSource | undefined {
  return isString(value) && PATIENT_SOURCES.has(value) ? (value as PatientSource) : undefined;
}

function decodeArray<T>(value: unknown, decodeItem: Decoder<T>): readonly T[] | undefined {
  if (!Array.isArray(value)) {
    return undefined;
  }
  const items: T[] = [];
  for (const item of value as unknown[]) {
    const decoded = decodeItem(item);
    if (decoded === undefined) {
      return undefined;
    }
    items.push(decoded);
  }
  return items;
}

export const decodePatientSummary: Decoder<PatientSummary> = (value) => {
  if (!isRecord(value)) {
    return undefined;
  }
  const id = decodeText(value['id']);
  const firstName = decodeText(value['firstName']);
  const lastName = decodeText(value['lastName']);
  const dni = decodeText(value['dni']);
  const phone = decodeText(value['phone']);
  const active = value['active'];
  if (
    id === undefined ||
    firstName === undefined ||
    lastName === undefined ||
    dni === undefined ||
    phone === undefined ||
    !isBoolean(active)
  ) {
    return undefined;
  }
  return { id, firstName, lastName, dni, phone, active };
};

export const decodePatientSummaries = (value: unknown): readonly PatientSummary[] | undefined =>
  decodeArray(value, decodePatientSummary);

export const decodePatientDetail: Decoder<PatientDetail> = (value) => {
  if (!isRecord(value)) {
    return undefined;
  }
  const id = decodeText(value['id']);
  const firstName = decodeText(value['firstName']);
  const lastName = decodeText(value['lastName']);
  const dni = decodeText(value['dni']);
  const phone = decodeText(value['phone']);
  const active = value['active'];
  const email = decodeNullableText(value['email']);
  const birthDate = decodeNullableText(value['birthDate']);
  const address = decodeNullableText(value['address']);
  const source = decodeSource(value['source']);
  const createdAt = decodeText(value['createdAt']);
  const updatedAt = decodeText(value['updatedAt']);
  if (
    id === undefined ||
    firstName === undefined ||
    lastName === undefined ||
    dni === undefined ||
    phone === undefined ||
    !isBoolean(active) ||
    email === undefined ||
    birthDate === undefined ||
    address === undefined ||
    source === undefined ||
    createdAt === undefined ||
    updatedAt === undefined ||
    !isZonedInstant(createdAt) ||
    !isZonedInstant(updatedAt)
  ) {
    return undefined;
  }
  const reviewRequired = isBoolean(value['reviewRequired']) ? value['reviewRequired'] : undefined;
  return {
    id,
    firstName,
    lastName,
    dni,
    phone,
    active,
    email,
    birthDate,
    address,
    source,
    createdAt,
    updatedAt,
    ...(reviewRequired !== undefined ? { reviewRequired } : {}),
  };
};
