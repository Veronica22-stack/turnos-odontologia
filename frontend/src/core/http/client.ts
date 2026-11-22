import { isRecord, isString, type Decoder } from './decode';
import type { ApiError, FieldError, HttpResult } from './errors';
import type { AuthTransport, HttpMethod } from './transport';

export type FetchFn = (input: string, init: RequestInit) => Promise<Response>;

export interface HttpClientOptions {
  readonly fetch: FetchFn;
  readonly transport: AuthTransport;
  readonly baseUrl?: string;
  readonly timeoutMs?: number;
  readonly onUnauthorized?: () => void;
}

export interface RequestOptions<T> {
  readonly body?: unknown;
  readonly decode?: Decoder<T>;
  /** Consulta inicial de sesion: un 401 es un resultado esperado, no un vencimiento. */
  readonly sessionProbe?: boolean;
  /**
   * Intento de credenciales (login, cambio de contrasena): un 401 es una respuesta de negocio
   * (`INVALID_CREDENTIALS`, `UNAUTHENTICATED`), se devuelve como `problem` y no dispara `onUnauthorized`.
   */
  readonly credentialAttempt?: boolean;
  readonly timeoutMs?: number;
}

export interface HttpClient {
  request<T = void>(method: HttpMethod, path: string, options?: RequestOptions<T>): Promise<HttpResult<T>>;
}

const DEFAULT_BASE_URL = '/api/v1';
const DEFAULT_TIMEOUT_MS = 15_000;

function assertRelativeApiPath(path: string): void {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('://') || path.includes('\\')) {
    throw new Error('Ruta de API invalida: debe ser relativa, empezar con "/" y no apuntar a otro origen');
  }
}

function fail<T>(error: ApiError): HttpResult<T> {
  return { ok: false, error };
}

function parseFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const result: FieldError[] = [];
  for (const item of value as unknown[]) {
    if (isRecord(item) && isString(item['field']) && isString(item['code'])) {
      result.push({ field: item['field'], code: item['code'] });
    }
  }
  return result;
}

/** `Retry-After` solo en su forma de segundos enteros; la fecha HTTP se ignora. */
function parseRetryAfter(response: Response): { retryAfterSeconds: number } | Record<string, never> {
  const raw = response.headers.get('Retry-After');
  return raw !== null && /^\d{1,9}$/.test(raw.trim()) ? { retryAfterSeconds: Number(raw.trim()) } : {};
}

async function toProblem(response: Response): Promise<ApiError> {
  const retryAfter = parseRetryAfter(response);
  const unexpected: ApiError = {
    kind: 'problem',
    status: response.status,
    code: 'UNEXPECTED_RESPONSE',
    errors: [],
    ...retryAfter,
  };
  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.toLowerCase().includes('application/problem+json')) {
    return unexpected;
  }
  let body: unknown;
  try {
    body = JSON.parse(await response.text());
  } catch {
    return unexpected;
  }
  if (!isRecord(body) || !isString(body['code'])) {
    return unexpected;
  }
  const errorId = body['errorId'];
  return {
    kind: 'problem',
    status: response.status,
    code: body['code'],
    errors: parseFieldErrors(body['errors']),
    ...(isString(errorId) ? { errorId } : {}),
    ...retryAfter,
  };
}

async function toSuccess<T>(response: Response, decode: Decoder<T> | undefined): Promise<HttpResult<T>> {
  if (response.status === 204 || decode === undefined) {
    return { ok: true, data: undefined as T };
  }
  let body: unknown;
  try {
    body = JSON.parse(await response.text());
  } catch {
    return fail({ kind: 'invalid-response' });
  }
  const data = decode(body);
  return data === undefined ? fail({ kind: 'invalid-response' }) : { ok: true, data };
}

export function createHttpClient(options: HttpClientOptions): HttpClient {
  const baseUrl = options.baseUrl ?? DEFAULT_BASE_URL;
  const defaultTimeout = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;

  return {
    async request<T = void>(method: HttpMethod, path: string, request: RequestOptions<T> = {}): Promise<HttpResult<T>> {
      assertRelativeApiPath(path);

      const headers = new Headers({ Accept: 'application/json' });
      let body: string | undefined;
      if (request.body !== undefined) {
        headers.set('Content-Type', 'application/json');
        body = JSON.stringify(request.body);
      }
      const base: RequestInit = { method, headers, ...(body !== undefined ? { body } : {}) };

      const controller = new AbortController();
      let timedOut = false;
      const timer = setTimeout(() => {
        timedOut = true;
        controller.abort();
      }, request.timeoutMs ?? defaultTimeout);

      let response: Response;
      try {
        const init = options.transport.decorate({ ...base, signal: controller.signal }, method);
        response = await options.fetch(`${baseUrl}${path}`, init);
        if (response.status === 401 && request.credentialAttempt !== true) {
          if (request.sessionProbe !== true) {
            options.onUnauthorized?.();
          }
          return fail({ kind: 'unauthorized' });
        }
        if (!response.ok) {
          return fail(await toProblem(response));
        }
        return await toSuccess(response, request.decode);
      } catch {
        return fail(timedOut ? { kind: 'timeout' } : { kind: 'network' });
      } finally {
        clearTimeout(timer);
      }
    },
  };
}
