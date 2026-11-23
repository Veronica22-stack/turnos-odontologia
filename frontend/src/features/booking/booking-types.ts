import type { FieldError } from '../../core/http/errors';
import type { Slot } from './slots';
import type { BookingFormValues } from './validation';

/** Tipos del contrato de reserva publica (design D2). Solo tipos: sin comportamiento. */

export interface PublicService {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
  readonly durationMin: number;
}

export type ListServicesResult =
  | { readonly kind: 'ok'; readonly services: readonly PublicService[] }
  | { readonly kind: 'rate-limited'; readonly retryAfterSeconds?: number }
  | { readonly kind: 'connection' };

export type AvailabilityResult =
  | { readonly kind: 'ok'; readonly slots: readonly Slot[] }
  | { readonly kind: 'no-services' }
  | { readonly kind: 'service-gone' }
  | { readonly kind: 'rate-limited'; readonly retryAfterSeconds?: number }
  | { readonly kind: 'connection' };

export interface BookingRequest {
  readonly serviceId: string;
  /** Instante UTC del slot elegido, exactamente como lo informo el servidor. */
  readonly startsAt: string;
  readonly form: BookingFormValues;
}

export type BookResult =
  | {
      readonly kind: 'confirmed';
      readonly serviceName: string;
      readonly startsAt: string;
      readonly endsAt: string;
      readonly practiceName: string;
    }
  | { readonly kind: 'accepted' }
  | { readonly kind: 'slot-unavailable' }
  | { readonly kind: 'contact-limit' }
  | { readonly kind: 'service-gone' }
  | { readonly kind: 'fields'; readonly errors: readonly FieldError[] }
  | { readonly kind: 'rate-limited'; readonly retryAfterSeconds?: number }
  | { readonly kind: 'stale-page' }
  | { readonly kind: 'unknown-outcome' };

export interface BookingGateway {
  listServices(): Promise<ListServicesResult>;
  availability(serviceId: string, date: string): Promise<AvailabilityResult>;
  book(request: BookingRequest): Promise<BookResult>;
}
