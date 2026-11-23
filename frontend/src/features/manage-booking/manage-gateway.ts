import { isBoolean, isRecord, isString, type Decoder } from '../../core/http/decode';
import type { HttpClient } from '../../core/http/client';
import type { ApiError } from '../../core/http/errors';
import { parseInstant } from '../../shared/utils/dates';
import type {
  BookingStatus,
  CancelResult,
  ManageGateway,
  ManagedBooking,
  RescheduleResult,
  ViewResult,
} from './manage-types';

/**
 * Contrato HTTP con C-15 (design D2). Unico lugar que conoce rutas, cuerpos, estados y codigos del
 * backend; las vistas solo ven los resultados clasificados de `manage-types.ts`. El token viaja solo
 * en la ruta, codificado como segmento.
 */

export interface ManageGatewayOptions {
  readonly client: HttpClient;
}

const STATUSES: ReadonlySet<string> = new Set<BookingStatus>(['CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW']);

function isValidInstant(value: unknown): value is string {
  if (!isString(value)) {
    return false;
  }
  try {
    parseInstant(value);
    return true;
  } catch {
    return false;
  }
}

function isStatus(value: unknown): value is BookingStatus {
  return isString(value) && STATUSES.has(value);
}

/** El `bookingId` se valida como texto y se descarta: nunca llega a las vistas. */
const decodeBooking: Decoder<ManagedBooking> = (value) => {
  if (!isRecord(value)) {
    return undefined;
  }
  const { bookingId, serviceName, practiceName, startsAt, endsAt, status, cancellable, reschedulable, serviceId } = value;
  if (
    !isString(bookingId) ||
    !isString(serviceName) ||
    !isString(practiceName) ||
    !isValidInstant(startsAt) ||
    !isValidInstant(endsAt) ||
    !isStatus(status) ||
    !isBoolean(cancellable) ||
    !isBoolean(reschedulable)
  ) {
    return undefined;
  }
  return {
    serviceName,
    practiceName,
    startsAt,
    endsAt,
    status,
    cancellable,
    reschedulable,
    ...(isString(serviceId) && serviceId !== '' ? { serviceId } : {}),
  };
};

type ProblemError = Extract<ApiError, { kind: 'problem' }>;

function rateLimited(error: ProblemError): { kind: 'rate-limited'; retryAfterSeconds?: number } {
  return error.retryAfterSeconds === undefined
    ? { kind: 'rate-limited' }
    : { kind: 'rate-limited', retryAfterSeconds: error.retryAfterSeconds };
}

/** Errores de las acciones con efecto (DELETE y POST) comunes a ambas. */
function actionFailure(error: ProblemError): ReturnType<typeof rateLimited> | { kind: 'invalid' | 'not-modifiable' | 'stale-page' } | null {
  if (error.status === 404) {
    return { kind: 'invalid' };
  }
  if (error.status === 429) {
    return rateLimited(error);
  }
  if (error.status === 403 && error.code === 'CSRF_REQUIRED') {
    return { kind: 'stale-page' };
  }
  if (error.status === 422 && error.code === 'BOOKING_MODIFICATION_NOT_ALLOWED') {
    return { kind: 'not-modifiable' };
  }
  return null;
}

function bookingPath(token: string): string {
  return `/public/bookings/${encodeURIComponent(token)}`;
}

export function createManageGateway(options: ManageGatewayOptions): ManageGateway {
  const { client } = options;
  return {
    async view(token): Promise<ViewResult> {
      const result = await client.request('GET', bookingPath(token), { decode: decodeBooking });
      if (result.ok) {
        return { kind: 'ok', booking: result.data };
      }
      const { error } = result;
      if (error.kind === 'problem') {
        if (error.status === 404) {
          return { kind: 'invalid' };
        }
        if (error.status === 429) {
          return rateLimited(error);
        }
      }
      return { kind: 'connection' };
    },

    async cancel(token): Promise<CancelResult> {
      const result = await client.request('DELETE', bookingPath(token), { decode: decodeBooking });
      if (result.ok) {
        return { kind: 'done', booking: result.data };
      }
      const failure = result.error.kind === 'problem' ? actionFailure(result.error) : null;
      return failure ?? { kind: 'unknown-outcome' };
    },

    async reschedule(token, startsAt): Promise<RescheduleResult> {
      const result = await client.request('POST', `${bookingPath(token)}/reschedule`, {
        body: { startsAt },
        decode: decodeBooking,
      });
      if (result.ok) {
        return { kind: 'done', booking: result.data };
      }
      const { error } = result;
      if (error.kind === 'problem') {
        if (
          (error.status === 409 && error.code === 'SLOT_TAKEN') ||
          (error.status === 422 && error.code === 'APPOINTMENT_IN_PAST')
        ) {
          return { kind: 'slot-unavailable' };
        }
        const failure = actionFailure(error);
        if (failure !== null) {
          return failure;
        }
      }
      return { kind: 'unknown-outcome' };
    },
  };
}
