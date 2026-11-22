import type { HttpClient, RequestOptions } from '../../core/http/client';
import type { HttpMethod } from '../../core/http/transport';
import { toSettingsFailure, type SettingsFailure } from './settings-messages';
import {
  decodeBlock,
  decodeBlocks,
  decodePracticeSettings,
  decodeService,
  decodeServices,
  decodeWorkingHour,
  decodeWorkingHours,
  type PracticeSettings,
  type ScheduleBlock,
  type ServiceItem,
  type WorkingHour,
} from './settings-types';

/** Único lugar que conoce rutas, cuerpos y decodificadores del contrato HTTP (C-19 D2). */

export type SettingsResult<T> = { readonly ok: true; readonly data: T } | { readonly ok: false; readonly failure: SettingsFailure };

export interface PracticeSettingsInput {
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

export interface ServiceInput {
  readonly name: string;
  readonly description: string | null;
  readonly durationMin: number;
  readonly publicVisible: boolean;
}

export interface ServiceUpdateInput extends ServiceInput {
  readonly active: boolean;
}

export interface BlockInput {
  readonly startsAt: string;
  readonly endsAt: string;
  readonly reason: string;
}

export interface SettingsGateway {
  getPractice(): Promise<SettingsResult<PracticeSettings>>;
  savePractice(input: PracticeSettingsInput): Promise<SettingsResult<PracticeSettings>>;
  getWorkingHours(): Promise<SettingsResult<readonly WorkingHour[]>>;
  saveWorkingHours(hours: readonly WorkingHour[]): Promise<SettingsResult<readonly WorkingHour[]>>;
  getServices(): Promise<SettingsResult<readonly ServiceItem[]>>;
  createService(input: ServiceInput): Promise<SettingsResult<ServiceItem>>;
  updateService(id: string, input: ServiceUpdateInput): Promise<SettingsResult<ServiceItem>>;
  deleteService(id: string): Promise<SettingsResult<void>>;
  getBlocks(from?: string, to?: string): Promise<SettingsResult<readonly ScheduleBlock[]>>;
  createBlock(input: BlockInput): Promise<SettingsResult<ScheduleBlock>>;
  updateBlock(id: string, input: BlockInput): Promise<SettingsResult<ScheduleBlock>>;
  deleteBlock(id: string): Promise<SettingsResult<void>>;
}

export interface SettingsGatewayOptions {
  readonly client: HttpClient;
}

function withSeconds(time: string): string {
  return time.length === 5 ? `${time}:00` : time;
}

export function createSettingsGateway(options: SettingsGatewayOptions): SettingsGateway {
  const { client } = options;

  async function request<T>(method: HttpMethod, path: string, requestOptions: RequestOptions<T>): Promise<SettingsResult<T>> {
    const result = await client.request<T>(method, path, requestOptions);
    return result.ok ? { ok: true, data: result.data } : { ok: false, failure: toSettingsFailure(result.error) };
  }

  return {
    getPractice: () => request('GET', '/practice/settings', { decode: decodePracticeSettings }),
    savePractice: (input) => request('PUT', '/practice/settings', { body: input, decode: decodePracticeSettings }),
    getWorkingHours: () => request('GET', '/working-hours', { decode: decodeWorkingHours }),
    saveWorkingHours: (hours) =>
      request('PUT', '/working-hours', {
        body: {
          workingHours: hours.map((hour) => ({
            weekday: hour.weekday,
            startTime: withSeconds(hour.startTime),
            endTime: withSeconds(hour.endTime),
          })),
        },
        decode: decodeWorkingHours,
      }),
    getServices: () => request('GET', '/services', { decode: decodeServices }),
    createService: (input) => request('POST', '/services', { body: input, decode: decodeService }),
    updateService: (id, input) => request('PUT', `/services/${encodeURIComponent(id)}`, { body: input, decode: decodeService }),
    deleteService: (id) => request('DELETE', `/services/${encodeURIComponent(id)}`, {}),
    getBlocks: (from, to) => {
      const params = new URLSearchParams();
      if (from !== undefined) {
        params.set('from', from);
      }
      if (to !== undefined) {
        params.set('to', to);
      }
      const query = params.toString();
      return request('GET', `/schedule-blocks${query === '' ? '' : `?${query}`}`, { decode: decodeBlocks });
    },
    createBlock: (input) => request('POST', '/schedule-blocks', { body: input, decode: decodeBlock }),
    updateBlock: (id, input) =>
      request('PUT', `/schedule-blocks/${encodeURIComponent(id)}`, { body: input, decode: decodeBlock }),
    deleteBlock: (id) => request('DELETE', `/schedule-blocks/${encodeURIComponent(id)}`, {}),
  };
}
