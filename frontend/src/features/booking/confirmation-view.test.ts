import { afterEach, describe, expect, it } from 'vitest';
import { clearConfirmation, setConfirmation, type ConfirmationData } from './confirmation-store';
import { createConfirmationView } from './confirmation-view';

const DATA: ConfirmationData = {
  serviceName: 'Limpieza',
  startsAt: '2026-11-24T13:30:00Z',
  endsAt: '2026-11-24T14:15:00Z',
  practiceName: 'Consultorio La Muela',
  maskedEmail: 'ma•••@gmail.com',
};

function render(): HTMLElement {
  return createConfirmationView()();
}

function text(element: Element): string {
  return element.textContent?.replace(/\s+/g, ' ').trim() ?? '';
}

afterEach(() => {
  clearConfirmation();
});

describe('confirmacion con datos', () => {
  it('muestra prestacion, dia, horario de Buenos Aires, consultorio y el email enmascarado', () => {
    setConfirmation(DATA);

    const view = render();

    expect(view.querySelector('h1')?.textContent).toBe('¡Turno reservado!');
    const content = text(view);
    expect(content).toContain('Limpieza');
    expect(content).toContain('martes 24 de noviembre');
    expect(content).toContain('10:30 – 11:15');
    expect(content).toContain('Consultorio La Muela');
    expect(content).toContain('Confirmado');
    expect(content).toContain('Te enviamos la confirmación a ma•••@gmail.com.');
    expect(content).toContain('enlace para cancelar o reprogramar');
    expect(view.querySelector('a[href="/reservar"]')?.textContent).toBe('Reservar otro turno');
  });

  it('omite la linea de consultorio si el nombre viene vacio', () => {
    setConfirmation({ ...DATA, practiceName: '' });

    const view = render();

    expect(view.querySelector('dl')?.textContent).not.toContain('Consultorio');
    expect(text(view)).toContain('martes 24 de noviembre');
  });

  it('usa el dia local de Buenos Aires aunque el instante UTC sea del dia siguiente', () => {
    setConfirmation({ ...DATA, startsAt: '2026-11-25T02:30:00Z', endsAt: '2026-11-25T03:15:00Z' });

    const content = text(render());

    expect(content).toContain('martes 24 de noviembre');
    expect(content).toContain('23:30 – 00:15');
  });

  it('no enlaza a la gestion del turno (el enlace llega solo por email)', () => {
    setConfirmation(DATA);

    const view = render();

    expect(view.querySelector('a[href^="/turno"]')).toBeNull();
  });

  it('se consume una sola vez: la segunda vista es la generica', () => {
    setConfirmation(DATA);

    render();
    const second = render();

    expect(second.querySelector('h1')?.textContent).toBe('Revisá tu email');
    expect(text(second)).not.toContain('Limpieza');
  });
});

describe('confirmacion generica', () => {
  it('sin datos en memoria remite al email y ofrece reservar, sin datos de turno', () => {
    const view = render();

    expect(view.querySelector('h1')?.textContent).toBe('Revisá tu email');
    expect(text(view)).toContain('Si completaste una reserva, te enviamos la confirmación con el enlace para gestionarla.');
    expect(view.querySelector('a[href="/reservar"]')?.textContent).toBe('Reservar un turno');
    expect(view.querySelector('dl')).toBeNull();
    expect(text(view)).not.toContain('@');
  });
});
