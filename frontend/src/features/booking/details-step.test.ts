import { afterEach, describe, expect, it, vi } from 'vitest';
import { bookingNotice } from './booking-messages';
import type { PublicService } from './booking-types';
import { renderDetailsStep, type DetailsStep } from './details-step';
import { EMPTY_BOOKING_FORM, type BookingFormValues } from './validation';

const SERVICE: PublicService = { id: 's-2', name: 'Limpieza', description: null, durationMin: 45 };
const SLOT = { startsAt: '2026-11-24T13:30:00Z', endsAt: '2026-11-24T14:15:00Z' };
const VALID: BookingFormValues = {
  firstName: 'Ana',
  lastName: 'Pérez',
  dni: '30456789',
  phone: '11 2345 6789',
  email: 'ana@ejemplo.com',
  website: '',
  privacyAccepted: true,
};

function mount(values: BookingFormValues = EMPTY_BOOKING_FORM): {
  step: DetailsStep;
  onSubmit: ReturnType<typeof vi.fn<(values: BookingFormValues) => void>>;
  onChange: ReturnType<typeof vi.fn<(values: BookingFormValues) => void>>;
} {
  const onSubmit = vi.fn<(values: BookingFormValues) => void>();
  const onChange = vi.fn<(values: BookingFormValues) => void>();
  const step = renderDetailsStep({ service: SERVICE, slot: SLOT, values, onSubmit, onChange, onBack: () => {} });
  document.body.replaceChildren(step.element);
  return { step, onSubmit, onChange };
}

function input(id: string): HTMLInputElement {
  const found = document.getElementById(id);
  if (!(found instanceof HTMLInputElement)) {
    throw new Error(`falta #${id}`);
  }
  return found;
}

function form(): HTMLFormElement {
  const found = document.querySelector('form');
  if (found === null) {
    throw new Error('falta el formulario');
  }
  return found;
}

function submitButton(): HTMLButtonElement {
  const found = document.querySelector<HTMLButtonElement>('button[type="submit"]');
  if (found === null) {
    throw new Error('falta el boton');
  }
  return found;
}

function fillAll(values: BookingFormValues): void {
  input('firstName').value = values.firstName;
  input('lastName').value = values.lastName;
  input('dni').value = values.dni;
  input('phone').value = values.phone;
  input('email').value = values.email;
  input('privacyAccepted').checked = values.privacyAccepted;
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('estructura del paso 3', () => {
  it('muestra paso, titulo y resumen del turno con hora de Buenos Aires', () => {
    mount();

    expect(document.querySelector('.stepper')?.textContent).toContain('Paso 3 de 3');
    expect(document.querySelector('h1')?.textContent).toBe('Tus datos');
    const summary = document.querySelector('.choice-summary');
    expect(summary?.textContent).toContain('Limpieza');
    expect(summary?.textContent).toContain('45 min');
    expect(summary?.textContent).toContain('martes 24 de noviembre');
    expect(summary?.textContent).toContain('10:30 – 11:15');
    expect(summary?.getAttribute('aria-label')).toBe('Turno elegido');
  });

  it('cada campo tiene etiqueta, autocomplete y teclado adecuados', () => {
    mount();

    for (const [id, label] of [
      ['firstName', 'Nombre'],
      ['lastName', 'Apellido'],
      ['dni', 'DNI'],
      ['phone', 'Teléfono'],
      ['email', 'Email'],
    ] as const) {
      expect(document.querySelector(`label[for="${id}"]`)?.textContent).toBe(label);
    }
    expect(input('firstName').autocomplete).toBe('given-name');
    expect(input('lastName').autocomplete).toBe('family-name');
    expect(input('dni').autocomplete).toBe('off');
    expect(input('dni').getAttribute('inputmode')).toBe('numeric');
    expect(input('phone').type).toBe('tel');
    expect(input('phone').autocomplete).toBe('tel');
    expect(input('email').type).toBe('email');
    expect(input('email').autocomplete).toBe('email');
  });

  it('el paso de datos no muestra marcas TBD ni enlaces muertos', () => {
    mount();

    expect(document.body.textContent).not.toContain('TBD');
    expect(document.querySelector('.tbd')).toBeNull();
    expect(document.querySelector('a[href="#"]')).toBeNull();
  });

  it('el aviso de privacidad se despliega sin salir del formulario', () => {
    mount();

    const details = document.querySelector<HTMLDetailsElement>('form details.privacy-details');
    expect(details).not.toBeNull();
    expect(details?.open).toBe(false);
    expect(details?.querySelector('summary')?.textContent).toBe('Ver aviso de privacidad');
    expect(details?.textContent).toContain('Ley 25.326');
    expect(details?.querySelector('summary')?.closest('label')).toBeNull();
    details!.open = true;
    expect(document.querySelector('form')?.contains(details)).toBe(true);
  });

  it('la casilla del aviso de privacidad sigue siendo obligatoria', () => {
    mount();

    expect(input('privacyAccepted').type).toBe('checkbox');
    expect(input('privacyAccepted').checked).toBe(false);
  });

  it('precarga los valores conservados', () => {
    mount(VALID);

    expect(input('firstName').value).toBe('Ana');
    expect(input('dni').value).toBe('30456789');
    expect(input('privacyAccepted').checked).toBe(true);
  });
});

describe('campo trampa', () => {
  it('website esta dentro de .hp oculto a lectores, fuera del tabulado y sin autocompletar', () => {
    mount();

    const trap = input('website');
    expect(trap.closest('.hp')?.getAttribute('aria-hidden')).toBe('true');
    expect(trap.getAttribute('tabindex')).toBe('-1');
    expect(trap.autocomplete).toBe('off');
    expect(trap.value).toBe('');
  });

  it('ningun elemento alcanzable con tab es el campo trampa', () => {
    mount();

    const tabbable = [...form().querySelectorAll<HTMLElement>('input, button, a[href], select, textarea')].filter(
      (el) => el.getAttribute('tabindex') !== '-1',
    );

    expect(tabbable.length).toBeGreaterThan(5);
    expect(tabbable.map((el) => el.id)).not.toContain('website');
    expect(tabbable.every((el) => el.closest('.hp') === null)).toBe(true);
  });
});

describe('validacion y envio', () => {
  it('con el formulario vacio no envia, marca todos los campos y enfoca Nombre', () => {
    const { onSubmit } = mount();

    form().requestSubmit();

    expect(onSubmit).not.toHaveBeenCalled();
    expect(document.querySelectorAll('.error-msg')).toHaveLength(6);
    expect(document.activeElement).toBe(input('firstName'));
    expect(input('firstName').getAttribute('aria-invalid')).toBe('true');
  });

  it('un DNI corto marca solo ese campo', () => {
    const { onSubmit } = mount();
    fillAll({ ...VALID, dni: '12345' });

    form().requestSubmit();

    expect(onSubmit).not.toHaveBeenCalled();
    expect(document.querySelectorAll('.error-msg')).toHaveLength(1);
    expect(document.getElementById('dni-error')?.textContent).toBe('Ingresá un DNI de 6 a 9 números.');
    expect(document.activeElement).toBe(input('dni'));
  });

  it('corregir y reenviar limpia los errores y entrega los valores recortados con website vacio', () => {
    const { onSubmit } = mount();
    fillAll({ ...VALID, dni: '12345' });
    form().requestSubmit();
    fillAll({ ...VALID, firstName: '  Ana ', dni: '30.456.789' });

    form().requestSubmit();

    expect(document.querySelectorAll('.error-msg')).toHaveLength(0);
    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith({ ...VALID, dni: '30.456.789' });
  });

  it('informa cada cambio para que el flujo conserve los datos', () => {
    const { onChange } = mount();

    input('email').value = 'ana@ejemplo.com';
    input('email').dispatchEvent(new Event('input', { bubbles: true }));
    input('privacyAccepted').checked = true;
    input('privacyAccepted').dispatchEvent(new Event('change', { bubbles: true }));

    expect(onChange).toHaveBeenLastCalledWith({ ...EMPTY_BOOKING_FORM, email: 'ana@ejemplo.com', privacyAccepted: true });
  });

  it('enviando: boton "Reservando…" con aria-busy, campos de solo lectura y segundo envio ignorado', () => {
    const { step, onSubmit } = mount(VALID);

    form().requestSubmit();
    step.setSubmitting(true);
    form().requestSubmit();

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(submitButton().textContent).toBe('Reservando…');
    expect(submitButton().getAttribute('aria-busy')).toBe('true');
    expect(submitButton().getAttribute('aria-disabled')).toBe('true');
    expect(input('firstName').readOnly).toBe(true);
    expect(input('privacyAccepted').getAttribute('aria-disabled')).toBe('true');

    step.setSubmitting(false);

    expect(submitButton().textContent).toBe('Confirmar reserva');
    expect(submitButton().hasAttribute('aria-busy')).toBe(false);
    expect(input('firstName').readOnly).toBe(false);
  });
});

describe('respuestas del servidor', () => {
  it('showNotice muestra el aviso con role=alert arriba del formulario', () => {
    const { step } = mount(VALID);

    step.showNotice(bookingNotice({ kind: 'rate-limited', retryAfterSeconds: 40 }));

    const alert = document.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Demasiados intentos seguidos');
    expect(alert?.textContent).toContain('en 1 minuto');
    expect(alert?.compareDocumentPosition(form())).toBe(Node.DOCUMENT_POSITION_FOLLOWING);

    step.showNotice(null);

    expect(document.querySelector('[role="alert"]')).toBeNull();
  });

  it('showServerErrors marca los campos con mensajes propios y enfoca el primero', () => {
    const { step } = mount(VALID);

    step.showServerErrors([
      { field: 'email', code: 'Email' },
      { field: 'dni', code: 'dni length must be between 6 and 9 digits' },
    ]);

    expect(document.getElementById('email-error')?.textContent).toBe('Ingresá un email válido.');
    expect(document.getElementById('dni-error')?.textContent).toBe('Ingresá un DNI de 6 a 9 números.');
    expect(document.activeElement).toBe(input('email'));
  });
});
