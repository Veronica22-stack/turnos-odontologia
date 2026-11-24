import { h } from '../../core/dom/h';
import type { PatientsGateway } from '../patients/patients-gateway';
import type { PatientSummary } from '../patients/patients-types';
import { createAnamnesisForm } from './anamnesis-form';
import { createAnamnesisSection } from './anamnesis-section';
import type {
  AnamnesisInput, ClinicalResult, CorrectionContent, CorrectionInput, EvolutionInput, OdontogramEntryInput,
} from './clinical-record-gateway';
import {
  decodeAnamnesisEntry, decodeEvolutionEntry, decodeOdontogramEntry,
  type AnamnesisEntry, type AnamnesisResponse, type ClinicalEntryType, type EvolutionEntry,
  type EvolutionsResponse, type OdontogramEntry, type OdontogramResponse,
} from './clinical-record-types';
import { createCorrectionFlow } from './correction-flow';
import { createEvolutionsForm } from './evolutions-form';
import { createEvolutionsSection } from './evolutions-section';
import { createOdontogramForm } from './odontogram-form';
import { createOdontogramView } from './odontogram-view';

/**
 * Vista de historia clínica. La selección y los datos clínicos viven solo en este estado en memoria (D1):
 * nunca en URL, título, consola, historial ni almacenamiento del navegador.
 */

export interface ClinicalRecordViewDeps {
  readonly patients: PatientsGateway;
  readonly getAnamnesis: (patientId: string) => Promise<ClinicalResult<AnamnesisResponse>>;
  readonly getOdontogram: (patientId: string) => Promise<ClinicalResult<OdontogramResponse>>;
  readonly getEvolutions: (patientId: string) => Promise<ClinicalResult<EvolutionsResponse>>;
  readonly registerAnamnesis: (patientId: string, input: AnamnesisInput) => Promise<ClinicalResult<AnamnesisEntry>>;
  readonly registerOdontogram: (patientId: string, entries: readonly OdontogramEntryInput[]) => Promise<ClinicalResult<readonly OdontogramEntry[]>>;
  readonly registerEvolution: (patientId: string, input: EvolutionInput) => Promise<ClinicalResult<EvolutionEntry>>;
  readonly correctEntry: (
    patientId: string, type: ClinicalEntryType, entryId: string, input: CorrectionInput,
  ) => Promise<ClinicalResult<unknown>>;
  readonly navigate: (path: string) => void;
  readonly onSessionExpired: () => void;
}

export interface ClinicalRecordView {
  readonly element: HTMLElement;
  selectPatient(patientId: string): Promise<void>;
}

const DEFAULT_TOOTH = '36';

function createSection(title: string, description: string): { element: HTMLElement; body: HTMLElement } {
  const body = h('div', { class: 'clinical-section__body' });
  const element = h(
    'section',
    { class: 'clinical-section' },
    h('h2', {}, title),
    h('p', { class: 'clinical-section__hint' }, description),
    body,
  );
  return { element, body };
}

/** Los componentes de sección traen su propio h2; el contenedor ya aporta el título. */
function withoutOwnHeading(element: HTMLElement): HTMLElement {
  element.querySelector('h2')?.remove();
  return element;
}

function innerBody(section: HTMLElement): HTMLElement {
  return section.querySelector<HTMLElement>('.anamnesis-section__body, .clinical-section__body') ?? section;
}

/** Inserta la versión devuelta por el servidor y marca la original como corregida, sin mutarla. */
function withCorrectedEntry<T extends { readonly id: string; readonly correctedByEntryId: string | null }>(
  entries: readonly T[], targetId: string, created: T,
): readonly T[] {
  return [
    created,
    ...entries
      .filter((entry) => entry.id !== created.id)
      .map((entry) => entry.id === targetId ? { ...entry, correctedByEntryId: created.id } : entry),
  ];
}

function loading(message: string): HTMLElement {
  return h('p', { attrs: { role: 'status' } }, message);
}

/** Un clic = una lectura; el botón se bloquea mientras la petición está en curso. */
function retryError(message: string, action: () => Promise<void>): HTMLElement {
  const button = h(
    'button',
    { attrs: { type: 'button', 'data-retry': '' }, on: { click: () => { void run(); } } },
    'Reintentar',
  );
  async function run(): Promise<void> {
    if (button.hasAttribute('disabled')) { return; }
    button.setAttribute('disabled', '');
    try {
      await action();
    } finally {
      button.removeAttribute('disabled');
    }
  }
  return h('div', { attrs: { role: 'alert' } }, h('p', {}, message), button);
}

export function createClinicalRecordView(deps: ClinicalRecordViewDeps): ClinicalRecordView {
  const heading = h('h1', { attrs: { tabindex: '-1' } }, 'Historia clínica');
  const picker = h('div', { class: 'clinical-picker', attrs: { role: 'status' } }, 'Cargando pacientes…');
  const selector = h('section', { class: 'clinical-selector' }, h('h2', {}, 'Elegí un paciente'), picker);
  const anamnesis = createSection('Anamnesis', 'Antecedentes y versiones registradas.');
  const odontogram = createSection('Odontograma', 'Estado actual de las piezas dentales.');
  const evolutions = createSection('Evoluciones', 'Atenciones registradas en orden cronológico.');
  const sections = h(
    'div',
    { class: 'clinical-sections', attrs: { hidden: '' } },
    anamnesis.element,
    odontogram.element,
    evolutions.element,
  );
  const element = h('section', { class: 'clinical-record-view' }, heading, selector, sections);
  queueMicrotask(() => heading.focus());

  let selectedId: string | null = null;
  let selectedTooth = DEFAULT_TOOTH;
  let odontogramView: ReturnType<typeof createOdontogramView> | null = null;
  let anamnesisData: AnamnesisResponse | null = null;
  let odontogramData: OdontogramResponse | null = null;
  let evolutionsData: EvolutionsResponse | null = null;
  const odontogramFormHost = h('div', { class: 'odontogram-form-host' });
  const odontogramCorrectionHost = h('div', { class: 'odontogram-correction' });
  const odontogramStatus = h('p', { class: 'clinical-section__hint', attrs: { role: 'status' } }, '');

  const anamnesisSection = createAnamnesisSection({
    load: async () => {
      const result = await deps.getAnamnesis(selectedId ?? '');
      if (!result.ok) {
        throw new Error('lectura de anamnesis fallida');
      }
      return result.data;
    },
  });
  const evolutionsSection = createEvolutionsSection({
    load: async () => {
      const result = await deps.getEvolutions(selectedId ?? '');
      if (!result.ok) {
        throw new Error('lectura de evoluciones fallida');
      }
      return result.data;
    },
  });

  function renderPicker(patients: readonly PatientSummary[]): void {
    picker.replaceChildren(
      ...patients.map((patient) =>
        h(
          'button',
          {
            class: 'clinical-picker__option',
            attrs: { type: 'button' },
            on: { click: () => { void selectPatient(patient.id); } },
          },
          `${patient.firstName} ${patient.lastName}`,
        ),
      ),
    );
  }

  function correctionControl(
    patientId: string, type: ClinicalEntryType, entryId: string, content: CorrectionContent,
  ): HTMLElement {
    return createCorrectionFlow({
      patientId,
      type,
      entryId,
      content,
      // Decisión de diseño 10: una confirmación = un POST. La entrada devuelta se aplica al estado
      // local para que el siguiente «Corregir» apunte a la nueva versión, sin GET que duplique HC_READ.
      onChanged: (corrected) => applyCorrectionLocally(type, entryId, corrected),
      onSessionExpired: handleUnauthorized,
      correct: (id, entryType, targetId, input) => deps.correctEntry(id, entryType, targetId, input),
    }).element;
  }

  function correctionControls<T extends { readonly id: string }>(
    patientId: string, type: ClinicalEntryType, entries: readonly T[], contentOf: (entry: T) => CorrectionContent,
  ): readonly HTMLElement[] {
    return entries.map((entry) => correctionControl(patientId, type, entry.id, contentOf(entry)));
  }

  /** Aplica la entrada nueva del POST a la copia local; la original solo se marca como corregida. */
  function applyCorrectionLocally(type: ClinicalEntryType, targetId: string, corrected: unknown): void {
    const patientId = selectedId ?? '';
    if (type === 'anamnesis') {
      const entry = decodeAnamnesisEntry(corrected);
      const previous = anamnesisData;
      if (entry === undefined || previous === null || previous.current === null || previous.current.id !== targetId) {
        return;
      }
      const next: AnamnesisResponse = {
        current: entry,
        history: withCorrectedEntry(previous.history, targetId, entry),
      };
      anamnesisData = next;
      renderAnamnesis(patientId, { ok: true, data: next });
      return;
    }
    if (type === 'evolutions') {
      const entry = decodeEvolutionEntry(corrected);
      const previous = evolutionsData;
      if (
        entry === undefined || previous === null ||
        !previous.entries.some((item) => item.id === targetId && item.correctedByEntryId === null)
      ) {
        return;
      }
      const next: EvolutionsResponse = {
        entries: withCorrectedEntry(previous.entries, targetId, entry),
      };
      evolutionsData = next;
      renderEvolutions(patientId, { ok: true, data: next });
      return;
    }
    const entry = decodeOdontogramEntry(corrected);
    const previous = odontogramData;
    if (
      entry === undefined || previous === null ||
      !previous.current.some((item) => item.id === targetId && item.correctedByEntryId === null)
    ) {
      return;
    }
    const next: OdontogramResponse = {
      current: withCorrectedEntry(previous.current, targetId, entry),
      history: withCorrectedEntry(previous.history, targetId, entry),
    };
    odontogramData = next;
    renderOdontogram(patientId, { ok: true, data: next });
  }

  function refreshOdontogramForm(patientId: string): void {
    odontogramFormHost.replaceChildren(
      createOdontogramForm({
        selectedTooth,
        submit: (entries) => deps.registerOdontogram(patientId, entries),
        onSessionExpired: handleUnauthorized,
      }).element,
    );
    odontogramStatus.textContent = odontogramView?.describe(selectedTooth) ?? `Pieza ${selectedTooth}, sin registro`;
  }

  /** Solo la pieza seleccionada ofrece corrección, y solo si su entrada es vigente. */
  function refreshOdontogramCorrection(patientId: string): void {
    const active = odontogramData?.current
      .filter((entry) => entry.toothCode === selectedTooth && entry.correctedByEntryId === null)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
    odontogramCorrectionHost.replaceChildren(
      ...(active === undefined ? [] : correctionControls(
        patientId, 'odontogram', [active],
        (entry) => ({ condition: entry.condition, notes: entry.notes ?? '' }),
      )),
    );
  }

  function renderAnamnesisError(patientId: string): void {
    anamnesisSection.render({ current: null, history: [] });
    const sectionElement = withoutOwnHeading(anamnesisSection.element);
    innerBody(sectionElement).replaceChildren(
      retryError('No pudimos cargar la anamnesis.', async () => {
        anamnesis.body.replaceChildren(loading('Cargando anamnesis…'));
        renderAnamnesis(patientId, await deps.getAnamnesis(patientId));
      }),
    );
    anamnesis.body.replaceChildren(sectionElement);
  }

  function renderAnamnesis(patientId: string, result: ClinicalResult<AnamnesisResponse>): void {
    if (!result.ok) {
      if (result.failure.kind === 'unauthorized') {
        handleUnauthorized();
        return;
      }
      renderAnamnesisError(patientId);
      return;
    }
    anamnesisData = result.data;
    anamnesisSection.render(result.data);
    anamnesis.body.replaceChildren(
      withoutOwnHeading(anamnesisSection.element),
      ...correctionControls(
        patientId, 'anamnesis', result.data.current === null ? [] : [result.data.current],
        (entry) => ({ notes: entry.content.notes ?? '' }),
      ),
      createAnamnesisForm({
        submit: (input) => deps.registerAnamnesis(patientId, input),
        onSessionExpired: handleUnauthorized,
      }).element,
    );
  }

  function renderOdontogramError(patientId: string): void {
    odontogramData = null;
    odontogramCorrectionHost.replaceChildren();
    odontogram.body.replaceChildren(
      retryError('No pudimos cargar el odontograma.', async () => {
        odontogram.body.replaceChildren(loading('Cargando odontograma…'));
        renderOdontogram(patientId, await deps.getOdontogram(patientId));
      }),
    );
  }

  function renderOdontogram(patientId: string, result: ClinicalResult<OdontogramResponse>): void {
    if (!result.ok) {
      if (result.failure.kind === 'unauthorized') {
        handleUnauthorized();
        return;
      }
      renderOdontogramError(patientId);
      return;
    }
    odontogramData = result.data;
    const view = createOdontogramView({
      data: result.data,
      onToggle: (toothCode) => {
        selectedTooth = toothCode;
        view.select(toothCode);
        refreshOdontogramForm(patientId);
        refreshOdontogramCorrection(patientId);
      },
    });
    odontogramView = view;
    view.select(selectedTooth);
    odontogram.body.replaceChildren(view.element, odontogramStatus, odontogramCorrectionHost, odontogramFormHost);
    refreshOdontogramForm(patientId);
    refreshOdontogramCorrection(patientId);
  }

  function renderEvolutionsError(patientId: string): void {
    evolutionsSection.render({ entries: [] });
    const sectionElement = withoutOwnHeading(evolutionsSection.element);
    innerBody(sectionElement).replaceChildren(
      retryError('No pudimos cargar las evoluciones.', async () => {
        evolutions.body.replaceChildren(loading('Cargando evoluciones…'));
        renderEvolutions(patientId, await deps.getEvolutions(patientId));
      }),
    );
    evolutions.body.replaceChildren(sectionElement);
  }

  function renderEvolutions(patientId: string, result: ClinicalResult<EvolutionsResponse>): void {
    if (!result.ok) {
      if (result.failure.kind === 'unauthorized') {
        handleUnauthorized();
        return;
      }
      renderEvolutionsError(patientId);
      return;
    }
    evolutionsData = result.data;
    evolutionsSection.render(result.data);
    evolutions.body.replaceChildren(
      withoutOwnHeading(evolutionsSection.element),
      ...correctionControls(
        patientId, 'evolutions', result.data.entries.filter((entry) => entry.correctedByEntryId === null),
        (entry) => ({ description: entry.description }),
      ),
      createEvolutionsForm({
        submit: (input) => deps.registerEvolution(patientId, input),
        onSessionExpired: handleUnauthorized,
      }).element,
    );
  }

  async function loadSections(patientId: string): Promise<void> {
    anamnesisData = null;
    odontogramData = null;
    evolutionsData = null;
    const placeholders = h('p', { attrs: { role: 'status' } }, 'Cargando historia clínica…');
    anamnesis.body.replaceChildren(placeholders.cloneNode(true));
    odontogram.body.replaceChildren(placeholders.cloneNode(true));
    evolutions.body.replaceChildren(placeholders.cloneNode(true));

    const [a, o, e] = await Promise.all([
      deps.getAnamnesis(patientId),
      deps.getOdontogram(patientId),
      deps.getEvolutions(patientId),
    ]);
    const unauthorized = [a, o, e].some((result) => !result.ok && result.failure.kind === 'unauthorized');
    if (unauthorized) {
      handleUnauthorized();
      return;
    }
    renderAnamnesis(patientId, a);
    renderOdontogram(patientId, o);
    renderEvolutions(patientId, e);
    odontogramView?.select(selectedTooth);
  }

  function handleUnauthorized(): void {
    selectedId = null;
    odontogramView = null;
    anamnesisData = null;
    odontogramData = null;
    evolutionsData = null;
    odontogramCorrectionHost.replaceChildren();
    sections.setAttribute('hidden', '');
    selector.removeAttribute('hidden');
    deps.onSessionExpired();
    deps.navigate('/login?next=%2Fhistoria-clinica');
  }

  async function selectPatient(patientId: string): Promise<void> {
    selectedId = patientId;
    selectedTooth = DEFAULT_TOOTH;
    selector.setAttribute('hidden', '');
    sections.removeAttribute('hidden');
    await loadSections(patientId);
  }

  void (async () => {
    const result = await deps.patients.search();
    if (!result.ok) {
      picker.replaceChildren(h('p', { attrs: { role: 'alert' } }, 'No pudimos cargar los pacientes.'));
      return;
    }
    renderPicker(result.data);
  })();

  return {
    element,
    selectPatient: (patientId) => selectPatient(patientId),
  };
}
