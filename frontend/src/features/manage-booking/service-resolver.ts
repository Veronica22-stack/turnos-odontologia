import type { ListServicesResult } from '../booking/booking-types';
import type { ManagedBooking } from './manage-types';

/**
 * Prestacion publica del turno para consultar la disponibilidad al reprogramar (design D5).
 * `BookingView` no trae `serviceId` (pedido P1): si lo trae se usa; si no, se busca en el catalogo
 * publico por nombre EXACTO y UNICO. Ante cualquier duda es `unavailable`: nunca una prestacion equivocada.
 */

export type ServiceResolution =
  | { readonly kind: 'resolved'; readonly serviceId: string }
  | { readonly kind: 'unavailable' };

export async function resolveServiceId(
  booking: ManagedBooking,
  listServices: () => Promise<ListServicesResult>,
): Promise<ServiceResolution> {
  if (booking.serviceId !== undefined) {
    return { kind: 'resolved', serviceId: booking.serviceId };
  }
  const result = await listServices();
  if (result.kind !== 'ok') {
    return { kind: 'unavailable' };
  }
  const matches = result.services.filter((service) => service.name === booking.serviceName);
  const [only] = matches;
  return matches.length === 1 && only !== undefined ? { kind: 'resolved', serviceId: only.id } : { kind: 'unavailable' };
}
