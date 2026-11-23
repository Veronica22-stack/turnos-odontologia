import type { HttpClient, RequestOptions } from '../../core/http/client';
import type { HttpResult } from '../../core/http/errors';
import type { HttpMethod } from '../../core/http/transport';
import type { ServiceItem, WorkingHour } from '../settings/settings-types';
import { decodeServices, decodeWorkingHours } from '../settings/settings-types';
import {
  decodeAgendaBlocks,
  decodeAppointment,
  decodeAppointments,
  decodeAvailableSlots,
  decodePatientSummaries,
  decodePatientSummary,
  type AgendaBlock,
  type AppointmentDto,
  type AvailableSlotDto,
  type CreateAppointmentPayload,
  type CreatePatientPayload,
  type PatientSummaryDto,
} from './agenda-types';
import { isRecord } from '../../core/http/decode';

/** Único lugar que conoce rutas, cuerpos y decodificadores del contrato HTTP de la agenda (D1/D2). */

export interface AgendaGateway {
  listAppointments(from?: string, to?: string): Promise<HttpResult<readonly AppointmentDto[]>>;
  createAppointment(payload: CreateAppointmentPayload): Promise<HttpResult<AppointmentDto>>;
  cancelAppointment(id: string): Promise<HttpResult<AppointmentDto>>;
  rescheduleAppointment(id: string, startsAt: string, overbooking?: boolean): Promise<HttpResult<AppointmentDto>>;
  completeAppointment(id: string): Promise<HttpResult<AppointmentDto>>;
  noShowAppointment(id: string): Promise<HttpResult<AppointmentDto>>;
  listServices(): Promise<HttpResult<readonly ServiceItem[]>>;
  searchPatients(q?: string): Promise<HttpResult<readonly PatientSummaryDto[]>>;
  createPatient(payload: CreatePatientPayload): Promise<HttpResult<PatientSummaryDto>>;
  getWorkingHours(): Promise<HttpResult<readonly WorkingHour[]>>;
  listAvailability(serviceId: string, date: string): Promise<HttpResult<readonly AvailableSlotDto[]>>;
  listBlocks(): Promise<HttpResult<readonly AgendaBlock[]>>;
}

export interface AgendaGatewayOptions {
  readonly client: HttpClient;
}

function withQuery(base: string, params: Readonly<Record<string, string>>): string {
  const query = new URLSearchParams(params);
  const text = query.toString();
  return text === '' ? base : `${base}?${text}`;
}

function decodeAvailabilitySlots(value: unknown): readonly AvailableSlotDto[] | undefined {
  if (!isRecord(value)) {
    return undefined;
  }
  return decodeAvailableSlots(value['slots']);
}

export function createAgendaGateway(options: AgendaGatewayOptions): AgendaGateway {
  const { client } = options;

  function request<T>(method: HttpMethod, path: string, options?: RequestOptions<T>): Promise<HttpResult<T>> {
    return client.request<T>(method, path, options);
  }

  return {
    listAppointments(from, to) {
      return request('GET', withQuery('/appointments', {
        ...(from !== undefined ? { from } : {}),
        ...(to !== undefined ? { to } : {}),
      }), { decode: decodeAppointments });
    },
    createAppointment(payload) {
      return request('POST', '/appointments', { body: payload, decode: decodeAppointment });
    },
    cancelAppointment(id) {
      return request('POST', `/appointments/${encodeURIComponent(id)}/cancel`, { decode: decodeAppointment });
    },
    rescheduleAppointment(id, startsAt, overbooking) {
      return request('POST', `/appointments/${encodeURIComponent(id)}/reschedule`, {
        body: { startsAt, ...(overbooking !== undefined ? { overbooking } : {}) },
        decode: decodeAppointment,
      });
    },
    completeAppointment(id) {
      return request('POST', `/appointments/${encodeURIComponent(id)}/complete`, { decode: decodeAppointment });
    },
    noShowAppointment(id) {
      return request('POST', `/appointments/${encodeURIComponent(id)}/no-show`, { decode: decodeAppointment });
    },
    listServices() {
      return request('GET', '/services', { decode: decodeServices });
    },
    searchPatients(q) {
      return request('GET', withQuery('/patients', q !== undefined ? { q } : {}), { decode: decodePatientSummaries });
    },
    createPatient(payload) {
      return request('POST', '/patients', { body: payload, decode: decodePatientSummary });
    },
    getWorkingHours() {
      return request('GET', '/working-hours', { decode: decodeWorkingHours });
    },
    listAvailability(serviceId, date) {
      return request('GET', withQuery('/availability', { serviceId, date }), { decode: decodeAvailabilitySlots });
    },
    listBlocks() {
      return request('GET', '/schedule-blocks', { decode: decodeAgendaBlocks });
    },
  };
}
