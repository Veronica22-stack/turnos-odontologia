import { describe, expect, it, vi } from 'vitest';
import { createPatientForm } from './patient-form';
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
  source: 'PUBLIC_BOOKING',
  createdAt: '2025-03-12T12:41:00Z',
  updatedAt: '2026-11-18T17:07:00Z',
};

function render(patient = PATIENT) {
  const onSave = vi.fn();
  const onCancel = vi.fn();
  const form = createPatientForm({ patient, draft: {
    firstName: patient.firstName,
    lastName: patient.lastName,
    phone: patient.phone,
    email: patient.email,
    birthDate: patient.birthDate,
    address: patient.address,
  }, errors: {}, hasUnknownField: false }, { onSave, onCancel });
  return { form, onSave, onCancel };
}

function input(form: HTMLElement, name: string): HTMLInputElement {
  const found = form.querySelector<HTMLInputElement>(`[name="${name}"]`);
  if (found === null) {
    throw new Error(`Campo no encontrado: ${name}`);
  }
  return found;
}

describe('patient-form - edición (D5/D6)', () => {
  it('6.1 precarga los campos y muestra DNI/origen solo como texto', () => {
    const { form } = render();
    expect(input(form, 'firstName').value).toBe('Julieta');
    expect(input(form, 'phone').value).toBe('11 5555 0001');
    expect(input(form, 'birthDate').value).toBe('1992-04-18');
    expect(form.querySelector('input[name="dni"]')).toBeNull();
    expect(form.querySelector('input[name="source"]')).toBeNull();
    expect(form.textContent).toContain('00.111.111');
    expect(form.textContent).toContain('PUBLIC_BOOKING');
  });

  it('6.1/6.2 guarda un único envío válido y bloquea los inválidos con error junto al campo', () => {
    const { form, onSave } = render();
    input(form, 'phone').value = '11 5555 0099';
    form.querySelector<HTMLButtonElement>('[data-action="save"]')?.click();
    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onSave.mock.calls[0]?.[0]).toMatchObject({ phone: '11 5555 0099' });

    const { form: invalid, onSave: noSave } = render();
    input(invalid, 'lastName').value = '   ';
    input(invalid, 'birthDate').value = '2026-02-30';
    invalid.querySelector<HTMLButtonElement>('[data-action="save"]')?.click();
    expect(noSave).not.toHaveBeenCalled();
    expect(invalid.querySelectorAll('[aria-invalid="true"]')).toHaveLength(2);
    expect(invalid.querySelector('#lastName-error')?.textContent).toContain('no puede estar vacío');
    expect(invalid.querySelector('#birthDate-error')?.textContent).toContain('fecha válida');
  });

  it('6.2 triángulo: email inválido y dirección demasiado larga sin petición', () => {
    const { form, onSave } = render();
    input(form, 'email').value = 'sin-arroba';
    input(form, 'address').value = 'x'.repeat(256);
    form.querySelector<HTMLButtonElement>('[data-action="save"]')?.click();
    expect(onSave).not.toHaveBeenCalled();
    expect(form.querySelector('#email-error')?.textContent).toContain('email válido');
    expect(form.querySelector('#address-error')?.textContent).toContain('demasiado largo');
  });

  it('6.1 cancelar no envía y los errores del servidor se muestran por campo', () => {
    const { form, onCancel } = render();
    form.querySelector<HTMLButtonElement>('[data-action="cancel"]')?.click();
    expect(onCancel).toHaveBeenCalledTimes(1);

    const withErrors = createPatientForm({
      patient: PATIENT,
      draft: { firstName: '', lastName: 'Simulada', phone: '11 5555 0001', email: null, birthDate: null, address: null },
      errors: { firstName: 'El nombre no puede estar vacío.' },
      hasUnknownField: true,
    }, { onSave: vi.fn(), onCancel: vi.fn() });
    expect(withErrors.querySelector('#firstName-error')?.textContent).toContain('no puede estar vacío');
    expect(withErrors.textContent).toContain('El servidor rechazó un dato');
  });
});
