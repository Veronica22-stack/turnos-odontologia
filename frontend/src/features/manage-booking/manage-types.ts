/** Tipos del contrato de gestion de turno por token (design D2). Solo tipos: sin comportamiento. */

export type BookingStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';

/** Turno tal como lo ve la vista: el `bookingId` del servidor se descarta en el gateway. */
export interface ManagedBooking {
  readonly serviceName: string;
  /** Puede ser cadena vacia: el consultorio no tiene nombre cargado. */
  readonly practiceName: string;
  /** Instantes ISO UTC exactamente como los informa el servidor. */
  readonly startsAt: string;
  readonly endsAt: string;
  readonly status: BookingStatus;
  readonly cancellable: boolean;
  readonly reschedulable: boolean;
  /** Solo si C-15 lo agrega a `BookingView` (pedido P1); si no, se resuelve por nombre (D5). */
  readonly serviceId?: string;
}

export type ViewResult =
  | { readonly kind: 'ok'; readonly booking: ManagedBooking }
  | { readonly kind: 'invalid' }
  | { readonly kind: 'rate-limited'; readonly retryAfterSeconds?: number }
  | { readonly kind: 'connection' };

export type CancelResult =
  | { readonly kind: 'done'; readonly booking: ManagedBooking }
  | { readonly kind: 'invalid' }
  | { readonly kind: 'not-modifiable' }
  | { readonly kind: 'rate-limited'; readonly retryAfterSeconds?: number }
  | { readonly kind: 'stale-page' }
  | { readonly kind: 'unknown-outcome' };

export type RescheduleResult =
  | { readonly kind: 'done'; readonly booking: ManagedBooking }
  | { readonly kind: 'slot-unavailable' }
  | { readonly kind: 'invalid' }
  | { readonly kind: 'not-modifiable' }
  | { readonly kind: 'rate-limited'; readonly retryAfterSeconds?: number }
  | { readonly kind: 'stale-page' }
  | { readonly kind: 'unknown-outcome' };

export interface ManageGateway {
  view(token: string): Promise<ViewResult>;
  cancel(token: string): Promise<CancelResult>;
  reschedule(token: string, startsAt: string): Promise<RescheduleResult>;
}
