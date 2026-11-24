import { describe, expect, it, vi } from 'vitest';
import type { ClinicalResult, CorrectionInput } from './clinical-record-gateway';
import type { AnamnesisEntry } from './clinical-record-types';
import { createCorrectionDialog } from './correction-dialog';

function onSubmit(result: Promise<ClinicalResult<AnamnesisEntry>> = Promise.resolve({ ok: false, failure: { kind: 'connection' } })) {
  return vi.fn((_input: CorrectionInput) => result);
}

describe('correction dialog: motivo inmutable', () => {
  it('enfoca el motivo al abrir y cierra con Escape sin enviar', async () => {
    const submit = onSubmit();
    const close = vi.fn();
    const dialog = createCorrectionDialog({ entryLabel: 'Versión 2', onSubmit: submit, onClose: close });
    document.body.replaceChildren(dialog.element);
    await Promise.resolve();

    const reason = dialog.element.querySelector<HTMLTextAreaElement>('#correction-reason');
    expect(document.activeElement).toBe(reason);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(close).toHaveBeenCalledTimes(1);
    expect(submit).not.toHaveBeenCalled();
  });

  it('cierra con Cancelar', () => {
    const close = vi.fn();
    const dialog = createCorrectionDialog({ entryLabel: 'Versión 2', onSubmit: onSubmit(), onClose: close });
    document.body.replaceChildren(dialog.element);

    dialog.element.querySelector<HTMLButtonElement>('[data-action="cancel"]')?.click();
    expect(close).toHaveBeenCalledTimes(1);
  });

  it('exige motivo no vacío y de hasta 500 caracteres sin repetir el valor', () => {
    const submit = onSubmit();
    const dialog = createCorrectionDialog({ entryLabel: 'Versión 2', onSubmit: submit, onClose: vi.fn() });
    document.body.replaceChildren(dialog.element);
    const reason = dialog.element.querySelector<HTMLTextAreaElement>('#correction-reason');
    const confirm = dialog.element.querySelector<HTMLButtonElement>('[data-action="confirm"]');

    confirm?.click();
    expect(submit).not.toHaveBeenCalled();
    expect(dialog.element.querySelector('[role="alert"]')?.textContent).toContain('obligatorio');

    if (reason !== null) { reason.value = 'm'.repeat(501); }
    confirm?.click();
    expect(submit).not.toHaveBeenCalled();
    expect(dialog.element.querySelector('[role="alert"]')?.textContent).toContain('500');
    expect(dialog.element.textContent).not.toContain('m'.repeat(501));
  });

  it('envía el motivo recortado una sola vez y bloquea el doble envío', async () => {
    let release: (() => void) | undefined;
    const pending = new Promise<ClinicalResult<AnamnesisEntry>>((resolve) => { release = () => resolve({ ok: false, failure: { kind: 'connection' } }); });
    const submit = onSubmit(pending);
    const dialog = createCorrectionDialog({ entryLabel: 'Versión 2', onSubmit: submit, onClose: vi.fn() });
    document.body.replaceChildren(dialog.element);
    const reason = dialog.element.querySelector<HTMLTextAreaElement>('#correction-reason');
    const confirm = dialog.element.querySelector<HTMLButtonElement>('[data-action="confirm"]');
    if (reason !== null) { reason.value = '  Motivo  '; }

    confirm?.click();
    confirm?.click();
    expect(submit).toHaveBeenCalledTimes(1);
    expect(submit.mock.calls[0]?.[0]?.reason).toBe('Motivo');
    expect(confirm?.hasAttribute('disabled')).toBe(true);

    release?.();
    await vi.waitFor(() => expect(confirm?.hasAttribute('disabled')).toBe(false));
  });

  it('precarga el contenido corregido del tipo y lo envía con el motivo', async () => {
    const submit = onSubmit();
    const dialog = createCorrectionDialog({
      entryLabel: 'Evolución vigente',
      type: 'evolutions',
      content: { description: 'Control original' },
      onSubmit: submit,
      onClose: vi.fn(),
    });
    document.body.replaceChildren(dialog.element);
    const field = dialog.element.querySelector<HTMLTextAreaElement>('#correction-content');
    const reason = dialog.element.querySelector<HTMLTextAreaElement>('#correction-reason');
    expect(field).not.toBeNull();
    expect(field?.value).toBe('Control original');
    if (field !== null) { field.value = 'Control corregido'; }
    if (reason !== null) { reason.value = 'Motivo'; }

    dialog.element.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();
    await vi.waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
    expect(submit.mock.calls[0]?.[0]).toEqual({ reason: 'Motivo', content: { description: 'Control corregido' } });
  });

  it('sin contenido ni motivo no envía y el error va junto al motivo', () => {
    const submit = onSubmit();
    const dialog = createCorrectionDialog({
      entryLabel: 'Evolución vigente', type: 'evolutions', onSubmit: submit, onClose: vi.fn(),
    });
    document.body.replaceChildren(dialog.element);
    const reason = dialog.element.querySelector<HTMLTextAreaElement>('#correction-reason');
    const confirm = dialog.element.querySelector<HTMLButtonElement>('[data-action="confirm"]');

    if (reason !== null) { reason.value = '   '; }
    confirm?.click();

    expect(submit).not.toHaveBeenCalled();
    expect(dialog.element.querySelector('[role="alert"]')?.textContent).toContain('obligatorio');
  });

  it('Escape y Cancelar conservan el borrador sin enviar', () => {
    const submit = onSubmit();
    const close = vi.fn();
    const dialog = createCorrectionDialog({
      entryLabel: 'Anamnesis vigente', type: 'anamnesis', content: { notes: 'Nota original' },
      onSubmit: submit, onClose: close,
    });
    document.body.replaceChildren(dialog.element);
    const field = dialog.element.querySelector<HTMLTextAreaElement>('#correction-content');
    if (field !== null) { field.value = 'Borrador sin enviar'; }

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(close).toHaveBeenCalledTimes(1);
    expect(submit).not.toHaveBeenCalled();
    expect(field?.value).toBe('Borrador sin enviar');
  });
});
