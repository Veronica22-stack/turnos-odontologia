import { afterEach, describe, expect, it } from 'vitest';
import { confirmAction } from './confirm';

afterEach(() => {
  document.body.replaceChildren();
});

describe('confirmAction - diálogo nativo (C-19 D8)', () => {
  it('resuelve true al confirmar y retira el diálogo', async () => {
    const promise = confirmAction({ title: 'Dar de baja', text: 'La prestación quedará inactiva.' });
    const dialog = document.querySelector('dialog');
    expect(dialog).not.toBeNull();
    expect(dialog?.textContent).toContain('Dar de baja');

    dialog?.querySelector<HTMLButtonElement>('[data-confirm="accept"]')?.click();

    await expect(promise).resolves.toBe(true);
    expect(document.querySelector('dialog')).toBeNull();
  });

  it('resuelve false al cancelar y no deja el diálogo', async () => {
    const promise = confirmAction({ title: 'Eliminar', text: '¿Confirmás?' });
    document.querySelector<HTMLButtonElement>('[data-confirm="cancel"]')?.click();

    await expect(promise).resolves.toBe(false);
    expect(document.querySelector('dialog')).toBeNull();
  });

  it('devuelve el foco al control que abrió el diálogo', async () => {
    const opener = document.createElement('button');
    document.body.append(opener);
    opener.focus();

    const promise = confirmAction({ title: 'Baja', text: 'Texto' });
    document.querySelector<HTMLButtonElement>('[data-confirm="accept"]')?.click();
    await promise;

    expect(document.activeElement).toBe(opener);
  });

  it('inserta título y texto hostil como texto literal', async () => {
    const hostile = '<img src=x onerror=alert(1)>';
    const promise = confirmAction({ title: hostile, text: hostile });
    const dialog = document.querySelector('dialog');

    expect(dialog?.querySelector('img')).toBeNull();
    expect(dialog?.textContent).toContain(hostile);
    document.querySelector<HTMLButtonElement>('[data-confirm="cancel"]')?.click();
    await promise;
  });
});
