import { describe, expect, it, vi } from 'vitest';
import type { ListServicesResult, PublicService } from '../booking/booking-types';
import type { ManagedBooking } from './manage-types';
import { resolveServiceId } from './service-resolver';

const BOOKING: ManagedBooking = {
  serviceName: 'Limpieza',
  practiceName: '',
  startsAt: '2026-11-24T13:30:00Z',
  endsAt: '2026-11-24T14:15:00Z',
  status: 'CONFIRMED',
  cancellable: true,
  reschedulable: true,
};

function service(id: string, name: string): PublicService {
  return { id, name, description: null, durationMin: 45 };
}

function catalog(...services: PublicService[]): () => Promise<ListServicesResult> {
  return vi.fn(async () => ({ kind: 'ok', services }) as const);
}

describe('resolveServiceId (design D5)', () => {
  it('usa el serviceId del turno sin llamar al catálogo', async () => {
    const list = catalog(service('s-9', 'Limpieza'));

    const result = await resolveServiceId({ ...BOOKING, serviceId: 's-2' }, list);

    expect(result).toEqual({ kind: 'resolved', serviceId: 's-2' });
    expect(list).not.toHaveBeenCalled();
  });

  it('sin serviceId, elige la prestación pública de nombre idéntico', async () => {
    const list = catalog(service('s-1', 'Consulta'), service('s-2', 'Limpieza'));

    expect(await resolveServiceId(BOOKING, list)).toEqual({ kind: 'resolved', serviceId: 's-2' });
    expect(list).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['ninguna coincidencia', [service('s-1', 'Consulta')]],
    ['dos coincidencias', [service('s-1', 'Limpieza'), service('s-2', 'Limpieza')]],
    ['nombre con espacio extra', [service('s-1', 'Limpieza ')]],
    ['otra capitalización', [service('s-1', 'limpieza')]],
    ['catálogo vacío', []],
  ])('%s -> unavailable', async (_label, services) => {
    expect(await resolveServiceId(BOOKING, catalog(...services))).toEqual({ kind: 'unavailable' });
  });

  it.each<[string, ListServicesResult]>([
    ['rate-limited', { kind: 'rate-limited', retryAfterSeconds: 10 }],
    ['connection', { kind: 'connection' }],
  ])('%s del catálogo -> unavailable', async (_label, reply) => {
    expect(await resolveServiceId(BOOKING, async () => reply)).toEqual({ kind: 'unavailable' });
  });
});
