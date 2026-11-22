import { describe, expect, it } from 'vitest';
import { passwordRules, validateLogin, validatePasswordChange } from './validation';

describe('validateLogin', () => {
  it('campos vacios: errores en email y password, en ese orden', () => {
    const result = validateLogin({ email: '', password: '' });

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.errors).toEqual([
        { field: 'email', message: 'Ingresá tu email.' },
        { field: 'password', message: 'Ingresá tu contraseña.' },
      ]);
    }
  });

  it('email sin dominio es invalido', () => {
    const result = validateLogin({ email: 'romero@', password: 'secreta' });

    expect(result).toEqual({ ok: false, errors: [{ field: 'email', message: 'Ingresá un email válido.' }] });
  });

  it('email sin arroba es invalido y email solo con espacios cuenta como vacio', () => {
    expect(validateLogin({ email: 'romero.ejemplo.com', password: 'x' })).toEqual({
      ok: false,
      errors: [{ field: 'email', message: 'Ingresá un email válido.' }],
    });
    expect(validateLogin({ email: '   ', password: 'x' })).toEqual({
      ok: false,
      errors: [{ field: 'email', message: 'Ingresá tu email.' }],
    });
  });

  it('recorta el email y conserva la contraseña tal cual', () => {
    expect(validateLogin({ email: ' romero@ejemplo.com ', password: ' con espacios ' })).toEqual({
      ok: true,
      value: { email: 'romero@ejemplo.com', password: ' con espacios ' },
    });
  });

  it('solo la contraseña vacia marca un unico error', () => {
    expect(validateLogin({ email: 'romero@ejemplo.com', password: '' })).toEqual({
      ok: false,
      errors: [{ field: 'password', message: 'Ingresá tu contraseña.' }],
    });
  });
});

describe('validatePasswordChange', () => {
  const base = { currentPassword: 'actual-segura-1', newPassword: 'una frase larga segura', confirmPassword: 'una frase larga segura' };

  function errorsOf(input: Parameters<typeof validatePasswordChange>[0]) {
    const result = validatePasswordChange(input);
    return result.ok ? [] : result.errors;
  }

  it('todo vacio: tres errores en orden actual, nueva, confirmacion', () => {
    expect(errorsOf({ currentPassword: '', newPassword: '', confirmPassword: '' })).toEqual([
      { field: 'currentPassword', message: 'Ingresá tu contraseña actual.' },
      { field: 'newPassword', message: 'Ingresá una contraseña nueva.' },
      { field: 'confirmPassword', message: 'Repetí la contraseña nueva.' },
    ]);
  });

  it('nueva corta: pide al menos 12 caracteres', () => {
    expect(errorsOf({ ...base, newPassword: 'corta123', confirmPassword: 'corta123' })).toEqual([
      { field: 'newPassword', message: 'Usá al menos 12 caracteres.' },
    ]);
  });

  it('12 caracteres exactos es valida; 11 no', () => {
    const twelve = 'abcdefghijkl';
    expect(validatePasswordChange({ ...base, newPassword: twelve, confirmPassword: twelve })).toEqual({
      ok: true,
      value: { currentPassword: base.currentPassword, newPassword: twelve },
    });
    expect(errorsOf({ ...base, newPassword: twelve.slice(1), confirmPassword: twelve.slice(1) })[0]?.message).toBe(
      'Usá al menos 12 caracteres.',
    );
  });

  it('128 es valida y 129 supera el maximo', () => {
    const ok = 'a'.repeat(128);
    const long = 'a'.repeat(129);
    expect(validatePasswordChange({ ...base, newPassword: ok, confirmPassword: ok }).ok).toBe(true);
    expect(errorsOf({ ...base, newPassword: long, confirmPassword: long })).toEqual([
      { field: 'newPassword', message: 'Usá como máximo 128 caracteres.' },
    ]);
  });

  it('igual a la actual: tiene que ser distinta', () => {
    const same = 'misma contraseña 14';
    expect(errorsOf({ currentPassword: same, newPassword: same, confirmPassword: same })).toEqual([
      { field: 'newPassword', message: 'La contraseña nueva tiene que ser distinta de la actual.' },
    ]);
  });

  it('confirmacion distinta', () => {
    expect(errorsOf({ ...base, confirmPassword: 'una frase larga segur' })).toEqual([
      { field: 'confirmPassword', message: 'Las contraseñas no coinciden.' },
    ]);
  });

  it('nueva de solo espacios: pide una contraseña nueva', () => {
    const blanks = ' '.repeat(14);
    expect(errorsOf({ ...base, newPassword: blanks, confirmPassword: blanks })).toEqual([
      { field: 'newPassword', message: 'Ingresá una contraseña nueva.' },
    ]);
  });

  it('cuenta unidades UTF-16 como el servidor', () => {
    const ene = 'ñ'.repeat(12);
    expect(validatePasswordChange({ ...base, newPassword: ene, confirmPassword: ene }).ok).toBe(true);
    const sixEmojis = '😀'.repeat(6); // 12 unidades
    expect(validatePasswordChange({ ...base, newPassword: sixEmojis, confirmPassword: sixEmojis }).ok).toBe(true);
    const manyEmojis = '😀'.repeat(65); // 130 unidades
    expect(errorsOf({ ...base, newPassword: manyEmojis, confirmPassword: manyEmojis })).toEqual([
      { field: 'newPassword', message: 'Usá como máximo 128 caracteres.' },
    ]);
  });
});

describe('passwordRules', () => {
  it('evalua longitud, distinta y coincidencia', () => {
    expect(passwordRules({ currentPassword: 'actual', newPassword: 'abcdefghijkl', confirmPassword: 'abcdefghijkl' })).toEqual({
      minLength: true,
      differs: true,
      matches: true,
    });
    expect(passwordRules({ currentPassword: 'abcdefghijkl', newPassword: 'abcdefghijkl', confirmPassword: 'otra' })).toEqual({
      minLength: true,
      differs: false,
      matches: false,
    });
  });

  it('con campos vacios ninguna regla se cumple', () => {
    expect(passwordRules({ currentPassword: '', newPassword: '', confirmPassword: '' })).toEqual({
      minLength: false,
      differs: false,
      matches: false,
    });
  });

  it('una nueva corta no cumple minLength', () => {
    expect(passwordRules({ currentPassword: 'x', newPassword: 'corta', confirmPassword: 'corta' }).minLength).toBe(false);
  });
});
