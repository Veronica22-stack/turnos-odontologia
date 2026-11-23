import { isBoolean, isNumber, isRecord, isString, type Decoder } from '../../core/http/decode';
import { isZonedInstant } from '../settings/settings-types';

/** DTOs y tipos del contrato de la agenda (C-20 D1): decodificadores estrictos; sin `any`. */

export type AppointmentStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';
export type AppointmentOrigin = 'PUBLIC' | 'INTERNAL';
export type CancelledBy = 'PATIENT' | 'DENTIST';

export interface AppointmentDto {
  readonly id: string;
  readonly patientId: string;
  readonly serviceId: string;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly durationMin: number;
  readonly status: AppointmentStatus;
  readonly origin: AppointmentOrigin;
  readonly overbooking: boolean;
  readonly cancelledBy: CancelledBy | null;
  readonly cancelledAt: string | null;
  readonly notes: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface AvailableSlotDto {
  readonly startsAt: string;
  readonly endsAt: string;
}

export interface AgendaBlock {
  readonly startsAt: string;
  readonly endsAt: string;
}

export interface CreateAppointmentPayload {
  readonly patientId: string;
  readonly serviceId: string;
  readonly startsAt: string;
  readonly overbooking?: boolean;
  readonly notes?: string;
}

export interface PatientSummaryDto {
  readonly id: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly dni: string;
  readonly phone: string;
  readonly active: boolean;
}

export interface CreatePatientPayload {
  readonly firstName: string;
  readonly lastName: string;
  readonly dni: string;
  readonly phone: string;
  readonly email?: string;
  readonly birthDate?: string;
  readonly address?: string;
}

function decodeText(value: unknown): string | undefined {
  return isString(value) ? value : undefined;
}

function decodeNullableString(value: unknown): string | null | undefined {
  if (value === null) {
    return null;
  }
  return isString(value) ? value : undefined;
}

function decodeEnum<T extends string>(value: unknown, allowed: ReadonlySet<T>): T | undefined {
  if (isString(value)) {
    for (const candidate of allowed) {
      if (candidate === value) {
        return candidate;
      }
    }
  }
  return undefined;
}

function decodeNullableEnum<T extends string>(value: unknown, allowed: ReadonlySet<T>): T | null | undefined {
  if (value === null) {
    return null;
  }
  return decodeEnum(value, allowed);
}

const APPOINTMENT_STATUSES: ReadonlySet<AppointmentStatus> = new Set<AppointmentStatus>([
  'CONFIRMED',
  'CANCELLED',
  'COMPLETED',
  'NO_SHOW',
]);
const APPOINTMENT_ORIGINS: ReadonlySet<AppointmentOrigin> = new Set<AppointmentOrigin>(['PUBLIC', 'INTERNAL']);
const CANCELLED_BYS: ReadonlySet<CancelledBy> = new Set<CancelledBy>(['PATIENT', 'DENTIST']);

function decodeArray<T>(value: unknown, decodeItem: Decoder<T>): readonly T[] | undefined {
  if (!Array.isArray(value)) {
    return undefined;
  }
  const items: T[] = [];
  for (const item of value) {
    const decoded = decodeItem(item);
    if (decoded === undefined) {
      return undefined;
    }
    items.push(decoded);
  }
  return items;
}

export const decodeAppointment: Decoder<AppointmentDto> = (value) => {
  if (!isRecord(value)) {
    return undefined;
  }
  const id = decodeText(value['id']);
  const patientId = decodeText(value['patientId']);
  const serviceId = decodeText(value['serviceId']);
  const startsAt = decodeText(value['startsAt']);
  const endsAt = decodeText(value['endsAt']);
  const durationMin = value['durationMin'];
  const status = decodeEnum(value['status'], APPOINTMENT_STATUSES);
  const origin = decodeEnum(value['origin'], APPOINTMENT_ORIGINS);
  const overbooking = value['overbooking'];
  const cancelledBy = decodeNullableEnum(value['cancelledBy'], CANCELLED_BYS);
  const cancelledAt = decodeNullableString(value['cancelledAt']);
  const notes = decodeNullableString(value['notes']);
  const createdAt = decodeText(value['createdAt']);
  const updatedAt = decodeText(value['updatedAt']);
  if (
    id === undefined ||
    patientId === undefined ||
    serviceId === undefined ||
    startsAt === undefined ||
    endsAt === undefined ||
    !isZonedInstant(startsAt) ||
    !isZonedInstant(endsAt) ||
    !isNumber(durationMin) ||
    status === undefined ||
    origin === undefined ||
    !isBoolean(overbooking) ||
    !isZonedInstant(createdAt) ||
    !isZonedInstant(updatedAt) ||
    hasInvalidNullable(value['cancelledBy'], cancelledBy) ||
    hasInvalidNullable(value['cancelledAt'], cancelledAt) ||
    hasInvalidNullable(value['notes'], notes)
  ) {
    return undefined;
  }
  return {
    id,
    patientId,
    serviceId,
    startsAt,
    endsAt,
    durationMin,
    status,
    origin,
    overbooking,
    cancelledBy: cancelledBy ?? null,
    cancelledAt: cancelledAt ?? null,
    notes: notes ?? null,
    createdAt,
    updatedAt,
  };
};

/** Valor nulo o ausente según el contrato; valor que no decodifica es respuesta inválida. */
function hasInvalidNullable(raw: unknown, decoded: string | null | undefined): boolean {
  return decoded === undefined && raw !== undefined && raw !== null;
}

export const decodeAppointments = (value: unknown): readonly AppointmentDto[] | undefined =>
  decodeArray(value, decodeAppointment);

export const decodeAvailableSlot: Decoder<AvailableSlotDto> = (value) => {
  if (!isRecord(value)) {
    return undefined;
  }
  const startsAt = decodeText(value['startsAt']);
  const endsAt = decodeText(value['endsAt']);
  if (startsAt === undefined || endsAt === undefined || !isZonedInstant(startsAt) || !isZonedInstant(endsAt)) {
    return undefined;
  }
  return { startsAt, endsAt };
};

export const decodeAvailableSlots = (value: unknown): readonly AvailableSlotDto[] | undefined =>
  decodeArray(value, decodeAvailableSlot);

export const decodeAgendaBlocks = (value: unknown): readonly AgendaBlock[] | undefined =>
  decodeArray(value, decodeAvailableSlot);

export const decodePatientSummary: Decoder<PatientSummaryDto> = (value) => {
  if (!isRecord(value)) {
    return undefined;
  }
  const id = decodeText(value['id']);
  const firstName = decodeText(value['firstName']);
  const lastName = decodeText(value['lastName']);
  const dni = decodeText(value['dni']);
  const phone = decodeText(value['phone']);
  const active = value['active'];
  if (id === undefined || firstName === undefined || lastName === undefined || dni === undefined || phone === undefined || !isBoolean(active)) {
    return undefined;
  }
  return { id, firstName, lastName, dni, phone, active };
};

export const decodePatientSummaries = (value: unknown): readonly PatientSummaryDto[] | undefined =>
  decodeArray(value, decodePatientSummary);
