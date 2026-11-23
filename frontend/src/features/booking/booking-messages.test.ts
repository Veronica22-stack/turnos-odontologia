import { describe, expect, it } from 'vitest';
import { bookingNotice, noServicesMessage, serverFieldMessage } from './booking-messages';

describe('bookingNotice', () => {
  it('slot-unavailable', () => {
    expect(bookingNotice({ kind: 'slot-unavailable' })).toEqual({
      variant: 'warning',
      title: 'Ese horario se acaba de ocupar.',
      detail: 'Elegí otro. Te mostramos los más cercanos; tus datos quedan guardados.',
    });
  });

  it('rate-limited con y sin Retry-After', () => {
    expect(bookingNotice({ kind: 'rate-limited', retryAfterSeconds: 40 })).toEqual({
      variant: 'warning',
      title: 'Demasiados intentos seguidos.',
      detail: 'Por seguridad, esperá un momento. Probá de nuevo en 1 minuto.',
    });
    expect(bookingNotice({ kind: 'rate-limited' }).detail).toBe(
      'Por seguridad, esperá un momento. Probá de nuevo en unos minutos.',
    );
    expect(bookingNotice({ kind: 'rate-limited', retryAfterSeconds: 400 }).detail).toContain('en unos 7 minutos');
  });

  it('contact-limit, service-gone, stale-page y unknown-outcome', () => {
    expect(bookingNotice({ kind: 'contact-limit' })).toMatchObject({
      variant: 'warning',
      title: 'Ya tenés turnos reservados.',
      tbd: '(TBD · PA-05)',
    });
    expect(bookingNotice({ kind: 'service-gone' })).toEqual({
      variant: 'info',
      title: 'Esa prestación ya no está disponible para reservar online.',
      detail: 'Elegí otra.',
    });
    expect(bookingNotice({ kind: 'stale-page' })).toEqual({
      variant: 'danger',
      title: 'La página quedó desactualizada.',
      detail: 'Recargala e intentá de nuevo. Tus datos no se enviaron.',
    });
    const unknown = bookingNotice({ kind: 'unknown-outcome' });
    expect(unknown.title).toBe('No pudimos confirmar tu reserva.');
    expect(unknown.detail).toContain('Revisá tu email antes de intentar de nuevo');
  });

  it('connection distingue horarios de prestaciones', () => {
    expect(bookingNotice({ kind: 'connection', what: 'slots' })).toMatchObject({
      variant: 'danger',
      title: 'No pudimos cargar los horarios.',
      detail: 'Revisá tu conexión.',
    });
    expect(bookingNotice({ kind: 'connection', what: 'services' }).title).toBe('No pudimos cargar las prestaciones.');
  });
});

describe('noServicesMessage', () => {
  it('usa el copy de "Reservas online no disponibles"', () => {
    expect(noServicesMessage()).toEqual({
      title: 'Reservas online no disponibles',
      detail: 'Por ahora no se pueden reservar turnos desde acá. Comunicate con el consultorio',
      tbd: '(TBD · datos)',
    });
  });
});

describe('serverFieldMessage', () => {
  it.each([
    ['email', 'Email', 'Ingresá un email válido.'],
    ['email', 'NotBlank', 'Ingresá tu email.'],
    ['firstName', 'NotBlank', 'Ingresá tu nombre.'],
    ['lastName', 'Size', 'Usá como máximo 100 caracteres.'],
    ['phone', 'Size', 'Usá como máximo 50 caracteres.'],
    ['privacyAccepted', 'AssertTrue', 'Tenés que aceptar el aviso de privacidad para reservar.'],
    ['dni', 'dni length must be between 6 and 9 digits', 'Ingresá un DNI de 6 a 9 números.'],
    ['dni', 'NotBlank', 'Ingresá un DNI de 6 a 9 números.'],
  ])('%s + %s', (field, code, expected) => {
    expect(serverFieldMessage(field, code)).toBe(expected);
  });

  it('un campo desconocido no tiene mensaje y un codigo desconocido usa el generico del campo', () => {
    expect(serverFieldMessage('website', 'Size')).toBeUndefined();
    expect(serverFieldMessage('email', 'Raro')).toBe('Revisá este dato.');
  });
});
