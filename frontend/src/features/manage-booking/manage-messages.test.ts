import { describe, expect, it } from 'vitest';
import {
  cancelConfirmation,
  cancelledNotice,
  invalidLinkMessage,
  manageNotice,
  readOnlyNotice,
  rescheduledNotice,
  statusBadge,
} from './manage-messages';
import type { ManagedBooking } from './manage-types';

const BOOKING: ManagedBooking = {
  serviceName: 'Limpieza',
  practiceName: 'Consultorio La Muela',
  startsAt: '2026-11-24T13:30:00Z',
  endsAt: '2026-11-24T14:15:00Z',
  status: 'CONFIRMED',
  cancellable: true,
  reschedulable: true,
};

describe('statusBadge', () => {
  it('CONFIRMED modificable -> Confirmado', () => {
    expect(statusBadge(BOOKING)).toEqual({ tone: 'confirmed', label: 'Confirmado' });
  });

  it('CANCELLED -> Cancelado', () => {
    expect(statusBadge({ ...BOOKING, status: 'CANCELLED', cancellable: false, reschedulable: false })).toEqual({
      tone: 'cancelled',
      label: 'Cancelado',
    });
  });

  it.each<[string, Partial<ManagedBooking>]>([
    ['CONFIRMED no modificable', { cancellable: false, reschedulable: false }],
    ['COMPLETED', { status: 'COMPLETED', cancellable: false, reschedulable: false }],
    ['NO_SHOW', { status: 'NO_SHOW', cancellable: false, reschedulable: false }],
  ])('%s -> Turno pasado (no revela asistencia)', (_label, patch) => {
    const badge = statusBadge({ ...BOOKING, ...patch });
    expect(badge).toEqual({ tone: 'past', label: 'Turno pasado' });
    expect(badge.label).not.toContain('Ausente');
  });
});

describe('manageNotice', () => {
  it('not-modifiable', () => {
    expect(manageNotice({ kind: 'not-modifiable' })).toMatchObject({
      variant: 'warning',
      title: 'Este turno ya no se puede modificar.',
    });
  });

  it('slot-unavailable nombra el turno que sigue vigente', () => {
    const notice = manageNotice({ kind: 'slot-unavailable', currentStartsAt: BOOKING.startsAt });
    expect(notice.title).toBe('Ese horario se acaba de ocupar.');
    expect(notice.detail).toContain('Tu turno sigue el martes 24 de noviembre a las 10:30.');
  });

  it('slot-unavailable usa la hora de Buenos Aires de otro instante', () => {
    const notice = manageNotice({ kind: 'slot-unavailable', currentStartsAt: '2026-11-26T14:15:00Z' });
    expect(notice.detail).toContain('jueves 26 de noviembre a las 11:15');
  });

  it('stale-page y reschedule-unavailable', () => {
    expect(manageNotice({ kind: 'stale-page' }).title).toBe('La página quedó desactualizada.');
    expect(manageNotice({ kind: 'reschedule-unavailable' })).toMatchObject({
      variant: 'info',
      title: 'No se puede reprogramar este turno online.',
    });
    expect(JSON.stringify(manageNotice({ kind: 'reschedule-unavailable' }))).not.toContain('TBD');
  });

  it('resultado desconocido de cancelar y de reprogramar no afirma ni niega', () => {
    expect(manageNotice({ kind: 'unknown-cancel' }).title).toBe('No pudimos confirmar la cancelación.');
    expect(manageNotice({ kind: 'unknown-reschedule' }).title).toBe('No pudimos confirmar el cambio de horario.');
    expect(manageNotice({ kind: 'unknown-reschedule' }).detail).toContain('Puede que se haya registrado igual');
  });

  it('connection de la consulta', () => {
    expect(manageNotice({ kind: 'connection' })).toMatchObject({ variant: 'danger', title: 'No pudimos cargar tu turno.' });
  });

  it('rate-limited reutiliza bookingNotice (40 s -> en 1 minuto)', () => {
    const notice = manageNotice({ kind: 'rate-limited', retryAfterSeconds: 40 });
    expect(notice.title).toBe('Demasiados intentos seguidos.');
    expect(notice.detail).toContain('en 1 minuto');
    expect(manageNotice({ kind: 'rate-limited' }).detail).toContain('en unos minutos');
  });
});

describe('textos de pantalla', () => {
  it('enlace no válido es genérico', () => {
    const message = invalidLinkMessage();
    expect(message.title).toBe('Este enlace no es válido');
    expect(message.detail).toContain('vencido');
    expect(message.detail).toMatch(/consultorio\.$/);
    expect(JSON.stringify(message)).not.toContain('TBD');
  });

  it('solo lectura distingue pasado de cancelado', () => {
    expect(readOnlyNotice(BOOKING).title).toBe('Este turno ya pasó.');
    expect(readOnlyNotice({ ...BOOKING, status: 'CANCELLED' }).title).toBe('Este turno está cancelado.');
    expect(readOnlyNotice({ ...BOOKING, status: 'NO_SHOW' }).detail).not.toContain('Ausente');
  });

  it('confirmación de cancelar nombra prestación, día y horario', () => {
    const confirm = cancelConfirmation(BOOKING);
    expect(confirm.title).toBe('¿Cancelar este turno?');
    expect(confirm.text).toContain('Limpieza · martes 24 de noviembre, 10:30.');
    expect(confirm.text).toContain('No se puede deshacer');
    expect(confirm.confirmLabel).toBe('Sí, cancelar turno');
    expect(confirm.cancelLabel).toBe('No, mantener turno');
  });

  it('avisos de éxito', () => {
    expect(cancelledNotice().title).toBe('Te enviamos un email con la cancelación.');
    expect(cancelledNotice().detail).toContain('ya no sirve');
    expect(rescheduledNotice().title).toBe('Te enviamos un email con los nuevos datos.');
    expect(rescheduledNotice().detail).toContain('seguir usando este enlace');
  });
});
