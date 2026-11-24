import { h } from '../../core/dom/h';
import { formatClinicalInstant } from './clinical-record-dates';
import type { AnamnesisEntry, AnamnesisResponse } from './clinical-record-types';

/** Render de anamnesis: vigor, historial y estados (5.1/5.3). Solo nodos seguros, sin HTML. */

export interface AnamnesisSectionDeps {
  readonly load: () => Promise<AnamnesisResponse>;
}

export interface AnamnesisSection {
  readonly element: HTMLElement;
  render(data: AnamnesisResponse): void;
  reload(): Promise<void>;
}

const FIELDS: ReadonlyArray<readonly [keyof AnamnesisEntry['content'], string]> = [
  ['allergies', 'Alergias'],
  ['medications', 'Medicación'],
  ['medicalConditions', 'Condiciones médicas'],
  ['habits', 'Hábitos'],
  ['notes', 'Notas'],
];

function entryMeta(entry: AnamnesisEntry): HTMLElement {
  return h(
    'p',
    { class: 'clinical-entry__meta' },
    `Autor ${entry.authorId} · ${formatClinicalInstant(entry.createdAt)}`,
  );
}

function renderContent(entry: AnamnesisEntry): HTMLElement {
  return h(
    'dl',
    { class: 'anamnesis-content' },
    ...FIELDS.flatMap(([key, label]) => [
      h('dt', {}, label),
      h('dd', {}, entry.content[key] ?? '—'),
    ]),
  );
}

function renderCurrent(entry: AnamnesisEntry | null): HTMLElement {
  if (entry === null) {
    return h('div', { class: 'anamnesis-current' }, h('p', {}, 'Sin anamnesis registrada.'));
  }
  return h(
    'div',
    { class: 'anamnesis-current' },
    h('h3', {}, `Versión ${entry.version}`),
    entry.correctsEntryId === null
      ? null
      : h('p', { class: 'clinical-entry__correction' }, `Corrige la entrada ${entry.correctsEntryId} · ${entry.correctionReason ?? ''}`),
    renderContent(entry),
    entryMeta(entry),
  );
}

function renderHistory(history: readonly AnamnesisEntry[]): HTMLElement {
  return h(
    'ol',
    { class: 'anamnesis-history' },
    ...history.map((entry) =>
      h(
        'li',
        {},
        h('h4', {}, `Versión ${entry.version}`),
        entry.correctedByEntryId === null
          ? null
          : h('p', { class: 'clinical-entry__corrected' }, `Corregida por la entrada ${entry.correctedByEntryId}`),
        renderContent(entry),
        entryMeta(entry),
      ),
    ),
  );
}

export function createAnamnesisSection(deps: AnamnesisSectionDeps): AnamnesisSection {
  const body = h('div', { class: 'anamnesis-section__body' });
  const element = h('section', { class: 'anamnesis-section' }, h('h2', {}, 'Anamnesis'), body);

  function render(data: AnamnesisResponse): void {
    body.replaceChildren(
      renderCurrent(data.current),
      h('h3', {}, 'Historial'),
      renderHistory(data.history),
    );
  }

  async function reload(): Promise<void> {
    body.replaceChildren(h('p', { attrs: { role: 'status' } }, 'Cargando anamnesis…'));
    try {
      render(await deps.load());
    } catch {
      body.replaceChildren(
        h('div', { attrs: { role: 'alert' } },
          h('p', {}, 'No pudimos cargar la anamnesis.'),
          h('button', { attrs: { type: 'button', 'data-retry': '' }, on: { click: () => { void reload(); } } }, 'Reintentar'),
        ),
      );
    }
  }

  return { element, render, reload };
}
