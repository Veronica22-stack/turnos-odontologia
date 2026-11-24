import { describe, expect, it, vi } from 'vitest';
import { clinicalFailureCopy } from './clinical-record-messages';
import type { ClinicalResult, CorrectionInput } from './clinical-record-gateway';
import type { ClinicalEntryType } from './clinical-record-types';
import { createCorrectionFlow } from './correction-flow';

type Correct = (
  patientId: string, type: ClinicalEntryType, entryId: string, input: CorrectionInput,
) => Promise<ClinicalResult<unknown>>;

function correct(failure: ClinicalResult<unknown> = { ok: false, failure: { kind: 'connection' } }) {
  return vi.fn(async (_patientId: string, _type: ClinicalEntryType, _entryId: string, _input: CorrectionInput) => failure);
}

function render(type: ClinicalEntryType, correctFn: Correct) {
  const flow = createCorrectionFlow({
    patientId: 'pac-001', type, entryId: 'e-1', correct: correctFn, onChanged: vi.fn(),
  });
  document.body.replaceChildren(flow.element);
  return flow;
}

async function submitReason(): Promise<void> {
  const confirm = document.querySelector<HTMLButtonElement>('[data-action="confirm"]');
  const reason = document.querySelector<HTMLTextAreaElement>('#correction-reason');
  if (reason !== null) { reason.value = 'Motivo de prueba'; }
  confirm?.click();
  await vi.waitFor(() => expect(confirm?.hasAttribute('disabled')).toBe(false));
}

describe('correction flow: endpoint uniforme por tipo', () => {
  it.each(['anamnesis', 'odontogram', 'evolutions'] as const)('envía una corrección de %s con reason y contenido permitido', async (type) => {
    const correctFn = correct();
    render(type, correctFn);
    document.querySelector<HTMLButtonElement>('[data-action="open"]')?.click();
    await submitReason();

    expect(correctFn).toHaveBeenCalledTimes(1);
    expect(correctFn.mock.calls[0]?.slice(0, 3)).toEqual(['pac-001', type, 'e-1']);
    expect(correctFn.mock.calls[0]?.[3]).toEqual({
      reason: 'Motivo de prueba',
      content: type === 'evolutions' ? { description: '' } : { notes: '' },
    });
  });

  it.each(['already-corrected', 'not-current', 'entry-not-found'] as const)('mapea el fallo %s sin reintentar ni eco clínico', async (kind) => {
    const correctFn = correct({ ok: false, failure: { kind } });
    render('anamnesis', correctFn);
    document.querySelector<HTMLButtonElement>('[data-action="open"]')?.click();
    await submitReason();

    const alert = document.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain(clinicalFailureCopy({ kind }).title);
    expect(alert?.textContent).not.toContain('Dato clinico secreto');
    expect(correctFn).toHaveBeenCalledTimes(1);
    expect(document.querySelector('[data-retry]')).toBeNull();
  });

  it('cierra y notifica cuando la corrección termina bien', async () => {
    const correctFn = correct({ ok: true, data: undefined });
    const onChanged = vi.fn();
    const flow = createCorrectionFlow({
      patientId: 'pac-001', type: 'evolutions', entryId: 'e-1', correct: correctFn, onChanged,
    });
    document.body.replaceChildren(flow.element);
    document.querySelector<HTMLButtonElement>('[data-action="open"]')?.click();
    await submitReason();
    await vi.waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1));
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });
});
