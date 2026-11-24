import type { HttpClient, RequestOptions } from '../../core/http/client';
import type { HttpMethod } from '../../core/http/transport';
import { toPatientsFailure, type PatientsFailure } from './patients-messages';
import {
  decodePatientDetail,
  decodePatientSummaries,
  type PatientDetail,
  type PatientInput,
  type PatientSummary,
} from './patients-types';

/** Único lugar que conoce rutas, cuerpos y decodificadores del contrato C-10 (D2). */

export type PatientsResult<T> = { readonly ok: true; readonly data: T } | { readonly ok: false; readonly failure: PatientsFailure };

export interface PatientsGateway {
  search(query?: string): Promise<PatientsResult<readonly PatientSummary[]>>;
  get(id: string): Promise<PatientsResult<PatientDetail>>;
  update(id: string, input: PatientInput): Promise<PatientsResult<PatientDetail>>;
  deactivate(id: string): Promise<PatientsResult<void>>;
}

export interface PatientsGatewayOptions {
  readonly client: HttpClient;
}

export function createPatientsGateway(options: PatientsGatewayOptions): PatientsGateway {
  const { client } = options;

  async function request<T>(
    method: HttpMethod,
    path: string,
    requestOptions: RequestOptions<T>,
  ): Promise<PatientsResult<T>> {
    const result = await client.request<T>(method, path, requestOptions);
    return result.ok ? { ok: true, data: result.data } : { ok: false, failure: toPatientsFailure(result.error) };
  }

  const getPath = (id: string): string => `/patients/${encodeURIComponent(id)}`;

  return {
    search(query) {
      const term = query?.trim() ?? '';
      const params = new URLSearchParams();
      if (term !== '') {
        params.set('q', term);
      }
      const qs = params.toString();
      return request('GET', `/patients${qs === '' ? '' : `?${qs}`}`, { decode: decodePatientSummaries });
    },
    get: (id) => request('GET', getPath(id), { decode: decodePatientDetail }),
    update: (id, input) => request('PUT', getPath(id), { body: input, decode: decodePatientDetail }),
    deactivate: (id) => request('DELETE', getPath(id), {}),
  };
}
