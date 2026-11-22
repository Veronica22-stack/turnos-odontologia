import { describe, expect, it } from 'vitest';
import { clearFieldErrors, field, setFieldError } from './form';

describe('field', () => {
  it('vincula label[for] con input[id] y aplica los atributos de autocompletado', () => {
    const f = field({ id: 'email', label: 'Email', type: 'email', autocomplete: 'username', inputmode: 'email' });

    const label = f.element.querySelector('label');
    expect(label?.getAttribute('for')).toBe('email');
    expect(label?.textContent).toBe('Email');
    expect(f.input.id).toBe('email');
    expect(f.input.type).toBe('email');
    expect(f.input.getAttribute('autocomplete')).toBe('username');
    expect(f.input.getAttribute('inputmode')).toBe('email');
    expect(f.input.classList.contains('input')).toBe(true);
    expect(f.element.classList.contains('field')).toBe(true);
  });

  it('el texto de ayuda se vincula por aria-describedby', () => {
    const f = field({ id: 'newPassword', label: 'Contraseña nueva', type: 'password', autocomplete: 'new-password', help: 'Mínimo 12 caracteres.' });

    const help = f.element.querySelector('.help');
    expect(help?.textContent).toBe('Mínimo 12 caracteres.');
    expect(help?.id).toBe('newPassword-help');
    expect(f.input.getAttribute('aria-describedby')).toBe('newPassword-help');
  });

  it('sin ayuda no declara aria-describedby', () => {
    const f = field({ id: 'email', label: 'Email', autocomplete: 'username' });

    expect(f.input.hasAttribute('aria-describedby')).toBe(false);
  });

  it('el valor inicial se asigna como propiedad, no como HTML', () => {
    const f = field({ id: 'email', label: 'Email', autocomplete: 'username', value: '"><b>x</b>' });

    expect(f.input.value).toBe('"><b>x</b>');
    expect(f.element.querySelector('b')).toBeNull();
  });
});

describe('boton "Mostrar contraseña"', () => {
  function revealField() {
    const f = field({ id: 'password', label: 'Contraseña', type: 'password', autocomplete: 'current-password', reveal: true });
    document.body.replaceChildren(f.element);
    const button = f.element.querySelector<HTMLButtonElement>('button.reveal-btn');
    if (button === null) {
      throw new Error('falta el boton');
    }
    return { f, button };
  }

  it('es un boton type=button con nombre accesible, aria-pressed=false y aria-controls al input', () => {
    const { button } = revealField();

    expect(button.type).toBe('button');
    expect(button.getAttribute('aria-label')).toBe('Mostrar contraseña');
    expect(button.getAttribute('aria-pressed')).toBe('false');
    expect(button.getAttribute('aria-controls')).toBe('password');
  });

  it('alterna type y aria-pressed en cada activacion', () => {
    const { f, button } = revealField();

    button.click();
    expect(f.input.type).toBe('text');
    expect(button.getAttribute('aria-pressed')).toBe('true');

    button.click();
    expect(f.input.type).toBe('password');
    expect(button.getAttribute('aria-pressed')).toBe('false');
  });

  it('conceal() vuelve a ocultar la contraseña', () => {
    const { f, button } = revealField();
    button.click();

    f.conceal();

    expect(f.input.type).toBe('password');
    expect(button.getAttribute('aria-pressed')).toBe('false');
  });

  it('un campo sin reveal no tiene boton', () => {
    const f = field({ id: 'email', label: 'Email', autocomplete: 'username' });

    expect(f.element.querySelector('button')).toBeNull();
  });
});

describe('setFieldError / clearFieldErrors', () => {
  it('marca aria-invalid, crea .error-msg con id y lo agrega a aria-describedby sin borrar la ayuda', () => {
    const f = field({ id: 'newPassword', label: 'Nueva', type: 'password', autocomplete: 'new-password', help: 'Ayuda' });

    setFieldError(f.input, 'Usá al menos 12 caracteres.');

    expect(f.input.getAttribute('aria-invalid')).toBe('true');
    const error = f.element.querySelector('.error-msg');
    expect(error?.id).toBe('newPassword-error');
    expect(error?.textContent).toBe('Usá al menos 12 caracteres.');
    expect(f.input.getAttribute('aria-describedby')).toBe('newPassword-help newPassword-error');
  });

  it('repetir el error reemplaza el texto sin duplicar el mensaje ni el id', () => {
    const f = field({ id: 'email', label: 'Email', autocomplete: 'username' });

    setFieldError(f.input, 'Ingresá tu email.');
    setFieldError(f.input, 'Ingresá un email válido.');

    expect(f.element.querySelectorAll('.error-msg')).toHaveLength(1);
    expect(f.element.querySelector('.error-msg')?.textContent).toBe('Ingresá un email válido.');
    expect(f.input.getAttribute('aria-describedby')).toBe('email-error');
  });

  it('el texto se inserta como texto, no como HTML', () => {
    const f = field({ id: 'email', label: 'Email', autocomplete: 'username' });

    setFieldError(f.input, '<img src=x onerror=alert(1)>');

    expect(f.element.querySelector('img')).toBeNull();
    expect(f.element.querySelector('.error-msg')?.textContent).toBe('<img src=x onerror=alert(1)>');
  });

  it('clearFieldErrors quita aria-invalid y el mensaje y conserva la ayuda', () => {
    const f = field({ id: 'newPassword', label: 'Nueva', type: 'password', autocomplete: 'new-password', help: 'Ayuda' });
    const form = document.createElement('form');
    form.append(f.element);
    setFieldError(f.input, 'Error');

    clearFieldErrors(form);

    expect(f.input.hasAttribute('aria-invalid')).toBe(false);
    expect(form.querySelector('.error-msg')).toBeNull();
    expect(f.input.getAttribute('aria-describedby')).toBe('newPassword-help');
  });

  it('clearFieldErrors en un campo sin ayuda deja sin aria-describedby', () => {
    const f = field({ id: 'email', label: 'Email', autocomplete: 'username' });
    const form = document.createElement('form');
    form.append(f.element);
    setFieldError(f.input, 'Error');

    clearFieldErrors(form);

    expect(f.input.hasAttribute('aria-describedby')).toBe(false);
  });
});
