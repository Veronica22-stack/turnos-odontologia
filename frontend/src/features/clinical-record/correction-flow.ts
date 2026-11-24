import { h } from '../../core/dom/h';
import type { ClinicalResult, CorrectionContent, CorrectionInput } from './clinical-record-gateway';
import { createCorrectionDialog } from './correction-dialog';
import type { ClinicalEntryType } from './clinical-record-types';

/** Orquesta la corrección inmutable: abre el diálogo y usa el endpoint uniforme por tipo (8.2). */

export interface CorrectionFlowDeps {
  readonly patientId: string;
  readonly type: ClinicalEntryType;
  readonly entryId: string;
  /** Valores vigentes que el diálogo precarga; pieza, cara y turno no se exponen como editables. */
  readonly content?: CorrectionContent;
  readonly correct: (
    patientId: string, type: ClinicalEntryType, entryId: string, input: CorrectionInput,
  ) => Promise<ClinicalResult<unknown>>;
  /** Recibe la entrada nueva devuelta por el servidor para actualizar el estado local sin refetch. */
  readonly onChanged: (corrected: unknown) => void;
  readonly onSessionExpired?: () => void;
}

export interface CorrectionFlow {
  readonly element: HTMLElement;
}

export function createCorrectionFlow(deps: CorrectionFlowDeps): CorrectionFlow {
  const element = h(
    'div',
    { class: 'correction-flow' },
    h('button', {
      class: 'btn btn-secondary btn-sm',
      attrs: { type: 'button', 'data-action': 'open' },
      on: {
        click: () => {
          const dialog = createCorrectionDialog({
            entryLabel: `Corrección de entrada ${deps.entryId}`,
            type: deps.type,
            ...(deps.content === undefined ? {} : { content: deps.content }),
            onClose: () => {},
            ...(deps.onSessionExpired === undefined ? {} : { onSessionExpired: deps.onSessionExpired }),
            onSubmit: async (input: CorrectionInput) => {
              const result = await deps.correct(deps.patientId, deps.type, deps.entryId, input);
              if (result.ok) {
                deps.onChanged(result.data);
              }
              return result;
            },
          });
          document.body.appendChild(dialog.element);
        },
      },
    }, 'Corregir'),
  );
  return { element };
}
