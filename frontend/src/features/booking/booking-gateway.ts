import { isRecord, isString, type Decoder } from '../../core/http/decode';
import type { HttpClient } from '../../core/http/client';
import type { ApiError, FieldError } from '../../core/http/errors';
import { parseInstant } from '../../shared/utils/dates';
import type {
  AvailabilityResult,
  BookResult,
  BookingGateway,
  BookingRequest,
  ListServicesResult,
  PublicService,
} from './booking-types';
import type { Slot } from './slots';

/**
 * Contrato HTTP con C-07 / C-12 / C-14 (design D2). Unico lugar que conoce rutas, cuerpos, estados
 * y codigos del backend; las vistas solo ven los resultados clasificados de `booking-types.ts`.
 */

export interface BookingGatewayOptions {
  readonly client: HttpClient;
}

const FORM_FIELDS: ReadonlySet<string> = new Set(['firstName', 'lastName', 'dni', 'phone', 'email', 'privacyAccepted']);
const SLOT_WINDOW_CODES: ReadonlySet<string> = new Set([
  'BOOKING_TOO_SOON',
  'BOOKING_BEYOND_HORIZON',
  'APPOINTMENT_IN_PAST',
]);

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

const decodeServices: Decoder<readonly PublicService[]> = (value) => {
  if (!Array.isArray(value)) {
    return undefined;
  }
  const services: PublicService[] = [];
  for (const item of value as unknown[]) {
    if (
      !isRecord(item) ||
      !isString(item['id']) ||
      !isString(item['name']) ||
      !(item['description'] === null || isString(item['description'])) ||
      typeof item['durationMin'] !== 'number' ||
      !Number.isInteger(item['durationMin'])
    ) {
      return undefined;
    }
    services.push({
      id: item['id'],
      name: item['name'],
      description: item['description'],
      durationMin: item['durationMin'],
    });
  }
  return services;
};

interface AvailabilityBody {
  readonly status: 'OK' | 'NO_ACTIVE_SERVICES';
  readonly slots: readonly Slot[];
}

const decodeAvailability: Decoder<AvailabilityBody> = (value) => {
  if (!isRecord(value) || !Array.isArray(value['slots'])) {
    return undefined;
  }
  const status = value['status'];
  if (status !== 'OK' && status !== 'NO_ACTIVE_SERVICES') {
    return undefined;
  }
  const slots: Slot[] = [];
  for (const item of value['slots'] as unknown[]) {
    if (!isRecord(item) || !isValidInstant(item['startsAt']) || !isValidInstant(item['endsAt'])) {
      return undefined;
    }
    slots.push({ startsAt: item['startsAt'], endsAt: item['endsAt'] });
  }
  return { status, slots };
};

type BookingBody = Extract<BookResult, { kind: 'confirmed' | 'accepted' }>;

/** El `bookingId` de la respuesta se ignora a proposito: no aporta al paciente (design D2). */
const decodeBooking: Decoder<BookingBody> = (value) => {
  if (!isRecord(value)) {
    return undefined;
  }
  if (value['status'] === 'ACCEPTED') {
    return { kind: 'accepted' };
  }
  const { serviceName, startsAt, endsAt, practiceName } = value;
  if (
    value['status'] !== 'CONFIRMED' ||
    !isString(serviceName) ||
    !isValidInstant(startsAt) ||
    !isValidInstant(endsAt) ||
    !(practiceName === null || isString(practiceName))
  ) {
    return undefined;
  }
  return { kind: 'confirmed', serviceName, startsAt, endsAt, practiceName: practiceName ?? '' };
};

function rateLimited(error: Extract<ApiError, { kind: 'problem' }>): { kind: 'rate-limited'; retryAfterSeconds?: number } {
  return error.retryAfterSeconds === undefined
    ? { kind: 'rate-limited' }
    : { kind: 'rate-limited', retryAfterSeconds: error.retryAfterSeconds };
}

function isServiceGone(error: Extract<ApiError, { kind: 'problem' }>): boolean {
  return (
    (error.status === 404 && error.code === 'SERVICE_NOT_FOUND') ||
    (error.status === 422 && error.code === 'SERVICE_INACTIVE')
  );
}

function bookFailure(error: ApiError): BookResult {
  if (error.kind !== 'problem') {
    return { kind: 'unknown-outcome' };
  }
  if (error.status === 429) {
    return rateLimited(error);
  }
  if (error.status === 409 && error.code === 'SLOT_TAKEN') {
    return { kind: 'slot-unavailable' };
  }
  if (error.status === 422) {
    if (SLOT_WINDOW_CODES.has(error.code)) {
      return { kind: 'slot-unavailable' };
    }
    if (error.code === 'BOOKING_CONTACT_LIMIT') {
      return { kind: 'contact-limit' };
    }
  }
  if (isServiceGone(error)) {
    return { kind: 'service-gone' };
  }
  if (error.status === 403 && error.code === 'CSRF_REQUIRED') {
    return { kind: 'stale-page' };
  }
  if (error.status === 400 && error.code === 'VALIDATION_FAILED') {
    const known: FieldError[] = error.errors.filter((e) => FORM_FIELDS.has(e.field));
    if (known.length > 0) {
      return { kind: 'fields', errors: known };
    }
  }
  return { kind: 'unknown-outcome' };
}

export function createBookingGateway(options: BookingGatewayOptions): BookingGateway {
  const { client } = options;
  return {
    async listServices(): Promise<ListServicesResult> {
      const result = await client.request('GET', '/public/services', { decode: decodeServices });
      if (result.ok) {
        return { kind: 'ok', services: result.data };
      }
      return result.error.kind === 'problem' && result.error.status === 429
        ? rateLimited(result.error)
        : { kind: 'connection' };
    },

    async availability(serviceId, date): Promise<AvailabilityResult> {
      const query = `serviceId=${encodeURIComponent(serviceId)}&date=${encodeURIComponent(date)}`;
      const result = await client.request('GET', `/public/availability?${query}`, { decode: decodeAvailability });
      if (result.ok) {
        return result.data.status === 'NO_ACTIVE_SERVICES'
          ? { kind: 'no-services' }
          : { kind: 'ok', slots: result.data.slots };
      }
      const { error } = result;
      if (error.kind === 'problem') {
        if (error.status === 429) {
          return rateLimited(error);
        }
        if (isServiceGone(error)) {
          return { kind: 'service-gone' };
        }
      }
      return { kind: 'connection' };
    },

    async book(request: BookingRequest): Promise<BookResult> {
      const { form } = request;
      const result = await client.request('POST', '/public/bookings', {
        body: {
          serviceId: request.serviceId,
          startsAt: request.startsAt,
          firstName: form.firstName,
          lastName: form.lastName,
          dni: form.dni,
          phone: form.phone,
          email: form.email,
          website: form.website,
          privacyAccepted: true,
        },
        decode: decodeBooking,
      });
      if (result.ok) {
        return result.data;
      }
      return result.error.kind === 'invalid-response' ? { kind: 'unknown-outcome' } : bookFailure(result.error);
    },
  };
}
