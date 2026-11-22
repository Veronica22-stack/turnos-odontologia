import type { BlockInput, PracticeSettingsInput, ServiceInput } from './settings-gateway';
import { isZonedInstant, type WorkingHour } from './settings-types';
import { findOverlaps, type HalfOpenRange } from './range-overlap';

/** Validación local pura: refleja las invariantes del backend sin relajarlas (C-19 D3). */

export type FieldErrors = Readonly<Record<string, string>>;

export type ValidationResult =
  | { readonly ok: true }
  | { readonly ok: false; readonly fields: FieldErrors; readonly message: string };

const ALLOWED_GRANULARITIES: ReadonlySet<number> = new Set([10, 15, 20, 30, 60]);
const CLOCK_PATTERN = /^([01]\d|2[0-3]):([0-5]\d)$/;

function fail(fields: FieldErrors, message: string): ValidationResult {
  return { ok: false, fields, message };
}

function finish(fields: FieldErrors, message: string): ValidationResult {
  return Object.keys(fields).length === 0 ? { ok: true } : fail(fields, message);
}

function textError(value: string, max: number, required: string, tooLong: string): string | undefined {
  const text = value.trim();
  if (text === '') {
    return required;
  }
  return text.length > max ? tooLong : undefined;
}

function indicesByWeekday(hours: readonly WorkingHour[]): readonly (readonly number[])[] {
  const groups = new Map<number, number[]>();
  for (const [index, hour] of hours.entries()) {
    const indices = groups.get(hour.weekday) ?? [];
    indices.push(index);
    groups.set(hour.weekday, indices);
  }
  return [...groups.values()];
}

export function clockToMinutes(clock: string): number {
  const parts = CLOCK_PATTERN.exec(clock);
  if (parts === null) {
    return Number.NaN;
  }
  return Number(parts[1]) * 60 + Number(parts[2]);
}

export function validatePracticeSettings(input: PracticeSettingsInput): ValidationResult {
  const fields: Record<string, string> = {};
  const nameError = textError(
    input.name,
    200,
    'El nombre del consultorio es obligatorio.',
    'El nombre no puede superar 200 caracteres.',
  );
  if (nameError !== undefined) {
    fields['name'] = nameError;
  }
  if (!ALLOWED_GRANULARITIES.has(input.slotGranularityMin)) {
    fields['slotGranularityMin'] = 'Valores permitidos: 10, 15, 20, 30 o 60 minutos.';
  }
  if (!Number.isInteger(input.minNoticeMin) || input.minNoticeMin < 0) {
    fields['minNoticeMin'] = 'La anticipación mínima debe ser un entero mayor o igual a 0.';
  }
  if (!Number.isInteger(input.maxAdvanceDays) || input.maxAdvanceDays < 1 || input.maxAdvanceDays > 365) {
    fields['maxAdvanceDays'] = 'El horizonte debe estar entre 1 y 365 días.';
  }
  if (!Number.isInteger(input.reminderHoursBefore) || input.reminderHoursBefore < 1 || input.reminderHoursBefore > 168) {
    fields['reminderHoursBefore'] = 'El recordatorio debe estar entre 1 y 168 horas.';
  }
  if (input.contactEmail !== null && input.contactEmail.length > 254) {
    fields['contactEmail'] = 'El email no puede superar 254 caracteres.';
  }
  if (input.contactPhone !== null && input.contactPhone.length > 50) {
    fields['contactPhone'] = 'El teléfono no puede superar 50 caracteres.';
  }
  if (input.address !== null && input.address.length > 300) {
    fields['address'] = 'La dirección no puede superar 300 caracteres.';
  }
  return finish(fields, 'Revisá los campos marcados.');
}

export function validateWorkingHours(hours: readonly WorkingHour[]): ValidationResult {
  const fields: Record<string, string> = {};

  for (const [index, hour] of hours.entries()) {
    const start = clockToMinutes(hour.startTime);
    const end = clockToMinutes(hour.endTime);

    if (Number.isNaN(start)) {
      fields[`start-${index}`] = 'Ingresá una hora de inicio válida.';
    }
    if (Number.isNaN(end)) {
      fields[`end-${index}`] = 'Ingresá una hora de fin válida.';
    }
    if (!Number.isNaN(start) && !Number.isNaN(end) && end <= start) {
      const message = 'La hora de fin debe ser posterior a la de inicio.';
      fields[`start-${index}`] = message;
      fields[`end-${index}`] = message;
    }
  }

  /** Los solapes solo importan dentro de un mismo día; se conservan los índices originales. */
  for (const indices of indicesByWeekday(hours)) {
    const ranges: HalfOpenRange[] = indices.map((index) => {
      const hour = hours[index];
      return { start: clockToMinutes(hour?.startTime ?? ''), end: clockToMinutes(hour?.endTime ?? '') };
    });
    for (const { a, b } of findOverlaps(ranges)) {
      const first = indices[a];
      const second = indices[b];
      if (first === undefined || second === undefined) {
        continue;
      }
      const message = 'Este tramo se superpone con otro del mismo día.';
      fields[`start-${first}`] = message;
      fields[`end-${first}`] = message;
      fields[`start-${second}`] = message;
      fields[`end-${second}`] = message;
    }
  }

  return finish(fields, 'No se guardó: revisá los tramos marcados.');
}

export function validateService(input: ServiceInput, granularity: number): ValidationResult {
  const fields: Record<string, string> = {};
  const nameError = textError(
    input.name,
    120,
    'El nombre de la prestación es obligatorio.',
    'El nombre no puede superar 120 caracteres.',
  );
  if (nameError !== undefined) {
    fields['name'] = nameError;
  }
  if (input.description !== null && input.description.length > 500) {
    fields['description'] = 'La descripción no puede superar 500 caracteres.';
  }
  if (!Number.isInteger(input.durationMin) || input.durationMin <= 0) {
    fields['durationMin'] = 'La duración debe ser un entero mayor que cero.';
  } else if (granularity > 0 && input.durationMin % granularity !== 0) {
    fields['durationMin'] = `Debe ser un múltiplo de ${granularity} minutos (${granularity}, ${granularity * 2}, ${granularity * 3}…).`;
  }
  return finish(fields, 'No se guardó la prestación.');
}

export function validateBlock(input: BlockInput): ValidationResult {
  const fields: Record<string, string> = {};
  if (!isZonedInstant(input.startsAt)) {
    fields['startsAt'] = 'Ingresá una fecha y hora de inicio válidas.';
  }
  if (!isZonedInstant(input.endsAt)) {
    fields['endsAt'] = 'Ingresá una fecha y hora de fin válidas.';
  }
  if (
    isZonedInstant(input.startsAt) &&
    isZonedInstant(input.endsAt) &&
    new Date(input.endsAt).getTime() <= new Date(input.startsAt).getTime()
  ) {
    const message = 'La hora de fin debe ser posterior a la de inicio.';
    fields['startsAt'] = message;
    fields['endsAt'] = message;
  }
  const reasonError = textError(input.reason, 500, 'El motivo es obligatorio.', 'El motivo no puede superar 500 caracteres.');
  if (reasonError !== undefined) {
    fields['reason'] = reasonError;
  }
  return finish(fields, 'No se guardó el bloqueo.');
}
