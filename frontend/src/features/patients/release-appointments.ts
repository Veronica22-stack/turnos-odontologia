import type { AgendaGateway } from '../agenda/agenda-gateway';
import type { AppointmentDto } from '../agenda/agenda-types';

/**
 * Libera los turnos futuros confirmados de un paciente antes de darlo de baja.
 * Solo usa los endpoints existentes de la agenda; el listado admite como máximo 93 días por consulta.
 */

export type ReleaseGateway = Pick<AgendaGateway, 'listAppointments' | 'cancelAppointment'>;

export type ReleaseResult =
  | { readonly ok: true; readonly released: number; readonly failed: readonly AppointmentDto[] }
  | { readonly ok: false };

export interface ReleaseOptions {
  readonly gateway: ReleaseGateway;
  readonly patientId: string;
  readonly now: Date;
  readonly horizonDays?: number;
}

const DAY_MS = 86_400_000;
const CHUNK_DAYS = 90;
const DEFAULT_HORIZON_DAYS = 365;

export async function releasePatientAppointments(options: ReleaseOptions): Promise<ReleaseResult> {
  const { gateway, patientId, now } = options;
  const start = now.getTime();
  const end = start + (options.horizonDays ?? DEFAULT_HORIZON_DAYS) * DAY_MS;
  const targets = new Map<string, AppointmentDto>();

  for (let from = start; from < end; from += CHUNK_DAYS * DAY_MS) {
    const to = Math.min(from + CHUNK_DAYS * DAY_MS, end);
    const result = await gateway.listAppointments(new Date(from).toISOString(), new Date(to).toISOString());
    if (!result.ok) {
      return { ok: false };
    }
    for (const appointment of result.data) {
      if (
        appointment.patientId === patientId &&
        appointment.status === 'CONFIRMED' &&
        new Date(appointment.startsAt).getTime() >= start
      ) {
        targets.set(appointment.id, appointment);
      }
    }
  }

  const failed: AppointmentDto[] = [];
  let released = 0;
  for (const appointment of targets.values()) {
    const cancelled = await gateway.cancelAppointment(appointment.id);
    if (cancelled.ok) {
      released += 1;
    } else {
      failed.push(appointment);
    }
  }
  return { ok: true, released, failed };
}
