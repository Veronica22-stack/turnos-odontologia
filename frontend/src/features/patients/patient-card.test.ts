import { describe, expect, it, vi } from 'vitest';
import { createPatientCard } from './patient-card';
import type { PatientDetail } from './patients-types';

const PATIENT: PatientDetail = {
  id: 'pac-001',
  firstName: 'Julieta',
  lastName: 'Simulada',
  dni: '00.111.111',
  phone: '11 5555 0001',
  active: true,
  email: 'julieta@ejemplo.invalid',
  birthDate: '1992-04-18',
  address: 'Calle Falsa 123',
  source: 'MANUAL',
  createdAt: '2025-03-12T12:41:00Z',
  updatedAt: '2026-11-18T17:07:00Z',
};

function render(patient: PatientDetail) {
  const callbacks = { onBack: vi.fn(), onEdit: vi.fn(), onDeactivate: vi.fn() };
  return { card: createPatientCard(patient, callbacks), callbacks };
}

describe('patient-card - ficha y RN-PA-03 (D3/D6)', () => {
  it('5.1 muestra filiación, contacto, origen y fechas en Buenos Aires', () => {
    const { card } = render(PATIENT);
    const text = card.textContent ?? '';
    expect(card.querySelector('h1')?.textContent).toBe('Julieta Simulada');
    for (const value of ['00.111.111', '11 5555 0001', 'julieta@ejemplo.invalid', '1992-04-18', 'Calle Falsa 123', 'MANUAL']) {
      expect(text).toContain(value);
    }
    expect(text).toContain('12/03/2025 09:41');
    expect(text).toContain('18/11/2026 14:07');
    expect(card.querySelectorAll('dt')).toHaveLength(6);
  });

  it('5.1/5.3 triángulo: inactiva, campos nulos y sin marcador aunque el origen sea reserva', () => {
    const inactive = render({ ...PATIENT, active: false, email: null, birthDate: null, address: null }).card;
    expect(inactive.textContent).toContain('Inactivo');
    expect(inactive.querySelectorAll('.badge-review')).toHaveLength(0);

    const booking = render({ ...PATIENT, source: 'PUBLIC_BOOKING' }).card;
    expect(booking.querySelectorAll('.badge-review')).toHaveLength(0);
    expect(render({ ...PATIENT, reviewRequired: false }).card.querySelectorAll('.badge-review')).toHaveLength(0);
  });

  it('5.3 el marcador explícito muestra insignia accesible y explicación', () => {
    const card = render({ ...PATIENT, reviewRequired: true }).card;
    const badge = card.querySelector('.badge-review');
    expect(badge?.textContent).toContain('Datos para revisar');
    expect(badge?.getAttribute('role')).toBe('status');
    expect(card.querySelector('.review-notice')?.textContent).toContain('no reemplazaron');
  });

  it('5.4 datos con etiquetas aparecen como texto, sin nodos inyectados', () => {
    const card = render({ ...PATIENT, firstName: '<img onerror>', address: '<script>alert(1)</script>' }).card;
    expect(card.querySelector('script')).toBeNull();
    expect(card.querySelector('img')).toBeNull();
    expect(card.textContent).toContain('<img onerror>');
    expect(card.textContent).toContain('<script>alert(1)</script>');
  });

  it('5.1 acciones: volver, editar y dar de baja', () => {
    const { card, callbacks } = render(PATIENT);
    const buttons = [...card.querySelectorAll<HTMLButtonElement>('button')];
    const byText = (text: string): HTMLButtonElement => {
      const found = buttons.find((button) => button.textContent?.includes(text));
      if (found === undefined) {
        throw new Error(`Botón no encontrado: ${text}`);
      }
      return found;
    };
    byText('Volver al listado').click();
    byText('Editar ficha').click();
    byText('Dar de baja').click();
    expect(callbacks.onBack).toHaveBeenCalledTimes(1);
    expect(callbacks.onEdit).toHaveBeenCalledTimes(1);
    expect(callbacks.onDeactivate).toHaveBeenCalledTimes(1);
  });
});
