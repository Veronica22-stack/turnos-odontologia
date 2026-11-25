import { describe, expect, it, vi } from 'vitest';
import type { AppointmentDto } from '../agenda/agenda-types';
import { releasePatientAppointments, type ReleaseGateway } from './release-appointments';

const NOW = new Date('2026-11-24T12:00:00Z');

function appt(id: string, patientId: string, startsAt: string, status: AppointmentDto['status'] = 'CONFIRMED'): AppointmentDto {
  return {
    id,
    patientId,
    serviceId: 'srv-1',
    startsAt,
    endsAt: new Date(new Date(startsAt).getTime() + 30 * 60_000).toISOString(),
    durationMin: 30,
    status,
    origin: 'INTERNAL',
    overbooking: false,
    cancelledBy: null,
    cancelledAt: null,
    notes: null,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
  };
}

function gatewayWith(all: readonly AppointmentDto[], failCancel: readonly string[] = []): {
  gateway: ReleaseGateway;
  list: ReturnType<typeof vi.fn>;
  cancel: ReturnType<typeof vi.fn>;
} {
  const list = vi.fn(async (from?: string, to?: string) => {
    const lo = new Date(from ?? '').getTime();
    const hi = new Date(to ?? '').getTime();
    return { ok: true as const, data: all.filter((a) => new Date(a.endsAt).getTime() > lo && new Date(a.startsAt).getTime() < hi) };
  });
  const cancel = vi.fn(async (id: string) =>
    failCancel.includes(id)
      ? { ok: false as const, error: { kind: 'network' as const } }
      : { ok: true as const, data: appt(id, 'x', '2026-12-01T10:00:00Z', 'CANCELLED') },
  );
  return { gateway: { listAppointments: list, cancelAppointment: cancel } as unknown as ReleaseGateway, list, cancel };
}

describe('releasePatientAppointments', () => {
  it('cancela solo los turnos CONFIRMED y futuros del paciente indicado', async () => {
    const { gateway, cancel } = gatewayWith([
      appt('a1', 'pac-1', '2026-12-01T13:00:00Z'),
      appt('a2', 'pac-2', '2026-12-02T13:00:00Z'),
      appt('a3', 'pac-1', '2026-12-03T13:00:00Z', 'COMPLETED'),
      appt('a4', 'pac-1', '2026-12-04T13:00:00Z', 'CANCELLED'),
      appt('a5', 'pac-1', '2026-06-01T13:00:00Z'),
      appt('a6', 'pac-1', '2027-03-10T13:00:00Z'),
    ]);
    const result = await releasePatientAppointments({ gateway, patientId: 'pac-1', now: NOW });
    expect(result).toEqual({ ok: true, released: 2, failed: [] });
    expect(cancel.mock.calls.map((c) => c[0]).sort()).toEqual(['a1', 'a6']);
  });

  it('sin turnos no cancela nada y informa cero', async () => {
    const { gateway, cancel } = gatewayWith([appt('a2', 'pac-2', '2026-12-02T13:00:00Z')]);
    const result = await releasePatientAppointments({ gateway, patientId: 'pac-1', now: NOW });
    expect(result).toEqual({ ok: true, released: 0, failed: [] });
    expect(cancel).not.toHaveBeenCalled();
  });

  it('con falla parcial informa cuáles no se pudieron liberar', async () => {
    const { gateway } = gatewayWith(
      [appt('a1', 'pac-1', '2026-12-01T13:00:00Z'), appt('a2', 'pac-1', '2026-12-02T13:00:00Z')],
      ['a2'],
    );
    const result = await releasePatientAppointments({ gateway, patientId: 'pac-1', now: NOW });
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.released).toBe(1);
      expect(result.failed.map((a) => a.id)).toEqual(['a2']);
    }
  });

  it('pagina en tramos de a lo sumo 93 días dentro del horizonte y no duplica turnos', async () => {
    const { gateway, list } = gatewayWith([appt('a1', 'pac-1', '2027-05-01T13:00:00Z')]);
    await releasePatientAppointments({ gateway, patientId: 'pac-1', now: NOW });
    expect(list.mock.calls.length).toBeGreaterThanOrEqual(4);
    for (const [from, to] of list.mock.calls as [string, string][]) {
      expect((new Date(to).getTime() - new Date(from).getTime()) / 86_400_000).toBeLessThanOrEqual(93);
    }
  });

  it('un turno que cruza el borde de dos tramos se cancela una sola vez', async () => {
    const { gateway, cancel } = gatewayWith([appt('edge', 'pac-1', '2027-02-20T12:00:00Z')]);
    await releasePatientAppointments({ gateway, patientId: 'pac-1', now: NOW });
    expect(cancel).toHaveBeenCalledTimes(1);
  });

  it('si el listado falla no cancela nada y devuelve ok:false', async () => {
    const cancel = vi.fn();
    const gateway = {
      listAppointments: vi.fn(async () => ({ ok: false as const, error: { kind: 'network' as const } })),
      cancelAppointment: cancel,
    } as unknown as ReleaseGateway;
    const result = await releasePatientAppointments({ gateway, patientId: 'pac-1', now: NOW });
    expect(result.ok).toBe(false);
    expect(cancel).not.toHaveBeenCalled();
  });
});
