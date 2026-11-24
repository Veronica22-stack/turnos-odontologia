import { isBoolean, isNumber, isRecord, isString, type Decoder } from '../../core/http/decode';

/** DTOs readonly del contrato de configuración (C-19 D2). Sin `any` ni aserciones inseguras. */

export interface PracticeSettings {
  readonly id: string;
  readonly name: string;
  readonly timezone: string;
  readonly contactEmail: string | null;
  readonly contactPhone: string | null;
  readonly address: string | null;
  readonly slotGranularityMin: number;
  readonly minNoticeMin: number;
  readonly maxAdvanceDays: number;
  readonly reminderHoursBefore: number;
}

export interface WorkingHour {
  readonly weekday: number;
  /** `HH:mm` en hora del consultorio. */
  readonly startTime: string;
  /** `HH:mm` en hora del consultorio. */
  readonly endTime: string;
}

export interface ServiceItem {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
  readonly durationMin: number;
  readonly active: boolean;
  readonly publicVisible: boolean;
}

export interface AffectedAppointment {
  readonly appointmentId: string;
  /** ISO-8601 con zona. El id y los datos del paciente no se muestran en la UI. */
  readonly startsAt: string;
  readonly endsAt: string;
}

export interface ScheduleBlock {
  readonly id: string;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly reason: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly affectedAppointments: readonly AffectedAppointment[];
}

const CLOCK_PATTERN = /^([01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/;
/** Mismo criterio que `dates.ts`: ISO-8601 con `Z` u offset obligatorio. */
const INSTANT_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?(?:Z|[+-]\d{2}:\d{2})$/;

export function isZonedInstant(value: unknown): value is string {
  return isString(value) && INSTANT_PATTERN.test(value) && !Number.isNaN(new Date(value).getTime());
}

/** Texto obligatorio: ausente o de otro tipo se considera respuesta inválida. */
function decodeRequiredText(value: unknown): string | undefined {
  return isString(value) ? value : undefined;
}

/** Texto opcional del backend: `null` es un valor válido y se conserva. */
function decodeText(value: unknown): string | null | undefined {
  if (value === null) {
    return null;
  }
  return isString(value) ? value : undefined;
}

const decodeInstant: Decoder<string> = (value) => (isZonedInstant(value) ? value : undefined);

function decodeClock(value: unknown): string | undefined {
  if (!isString(value) || !CLOCK_PATTERN.test(value)) {
    return undefined;
  }
  return value.slice(0, 5);
}

function decodeWeekday(value: unknown): number | undefined {
  return isNumber(value) && Number.isInteger(value) && value >= 1 && value <= 7 ? value : undefined;
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

export function decodePracticeSettings(value: unknown): PracticeSettings | undefined {
  if (!isRecord(value)) {
    return undefined;
  }
  const id = decodeRequiredText(value['id']);
  const name = decodeRequiredText(value['name']);
  const timezone = decodeRequiredText(value['timezone']);
  const contactEmail = decodeText(value['contactEmail']);
  const contactPhone = decodeText(value['contactPhone']);
  const address = decodeText(value['address']);
  const slotGranularityMin = value['slotGranularityMin'];
  const minNoticeMin = value['minNoticeMin'];
  const maxAdvanceDays = value['maxAdvanceDays'];
  const reminderHoursBefore = value['reminderHoursBefore'];
  if (
    id === undefined ||
    name === undefined ||
    timezone === undefined ||
    contactEmail === undefined ||
    contactPhone === undefined ||
    address === undefined ||
    !isNumber(slotGranularityMin) ||
    !isNumber(minNoticeMin) ||
    !isNumber(maxAdvanceDays) ||
    !isNumber(reminderHoursBefore)
  ) {
    return undefined;
  }
  return {
    id,
    name,
    timezone,
    contactEmail,
    contactPhone,
    address,
    slotGranularityMin,
    minNoticeMin,
    maxAdvanceDays,
    reminderHoursBefore,
  };
}

export const decodeWorkingHour: Decoder<WorkingHour> = (value) => {
  if (!isRecord(value)) {
    return undefined;
  }
  const weekday = decodeWeekday(value['weekday']);
  const startTime = decodeClock(value['startTime']);
  const endTime = decodeClock(value['endTime']);
  return weekday === undefined || startTime === undefined || endTime === undefined
    ? undefined
    : { weekday, startTime, endTime };
};

export const decodeWorkingHours = (value: unknown): readonly WorkingHour[] | undefined =>
  decodeArray(value, decodeWorkingHour);

export const decodeService: Decoder<ServiceItem> = (value) => {
  if (!isRecord(value)) {
    return undefined;
  }
  const id = decodeRequiredText(value['id']);
  const name = decodeRequiredText(value['name']);
  const description = decodeText(value['description']);
  const durationMin = value['durationMin'];
  const active = value['active'];
  const publicVisible = value['publicVisible'];
  if (
    id === undefined ||
    name === undefined ||
    description === undefined ||
    !isNumber(durationMin) ||
    !isBoolean(active) ||
    !isBoolean(publicVisible)
  ) {
    return undefined;
  }
  return { id, name, description, durationMin, active, publicVisible };
};

export const decodeServices = (value: unknown): readonly ServiceItem[] | undefined => decodeArray(value, decodeService);

const decodeAffectedAppointment: Decoder<AffectedAppointment> = (value) => {
  if (!isRecord(value)) {
    return undefined;
  }
  const appointmentId = decodeRequiredText(value['appointmentId']);
  const startsAt = decodeInstant(value['startsAt']);
  const endsAt = decodeInstant(value['endsAt']);
  if (appointmentId === undefined || startsAt === undefined || endsAt === undefined) {
    return undefined;
  }
  return { appointmentId, startsAt, endsAt };
};

export const decodeBlock: Decoder<ScheduleBlock> = (value) => {
  if (!isRecord(value)) {
    return undefined;
  }
  const id = decodeRequiredText(value['id']);
  const startsAt = decodeInstant(value['startsAt']);
  const endsAt = decodeInstant(value['endsAt']);
  const reason = decodeRequiredText(value['reason']);
  const createdAt = decodeInstant(value['createdAt']);
  const updatedAt = decodeInstant(value['updatedAt']);
  const affected = decodeArray(value['affectedAppointments'], decodeAffectedAppointment);
  if (
    id === undefined ||
    startsAt === undefined ||
    endsAt === undefined ||
    reason === undefined ||
    createdAt === undefined ||
    updatedAt === undefined ||
    affected === undefined
  ) {
    return undefined;
  }
  return { id, startsAt, endsAt, reason, createdAt, updatedAt, affectedAppointments: affected };
};

export const decodeBlocks = (value: unknown): readonly ScheduleBlock[] | undefined => decodeArray(value, decodeBlock);
