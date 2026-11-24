import { h } from '../../core/dom/h';
import { formatClinicalInstant } from './clinical-record-dates';
import type { EvolutionEntry, EvolutionsResponse } from './clinical-record-types';

/** Render de evoluciones: cronología descendente, corregidas marcadas y estados (7.1). */

export interface EvolutionsSectionDeps {
  readonly load: () => Promise<EvolutionsResponse>;
}

export interface EvolutionsSection {
  readonly element: HTMLElement;
  render(data: EvolutionsResponse): void;
  reload(): Promise<void>;
}

function renderEntry(entry: EvolutionEntry): HTMLElement {
  return h(
    'li',
    { class: `entry${entry.correctedByEntryId === null ? '' : ' corrected'}` },
    h('header', {},
      h('span', {}, `Autor ${entry.authorId} · ${formatClinicalInstant(entry.createdAt)}`),
      entry.correctedByEntryId === null ? null : h('span', { class: 'badge badge-corrected' }, 'Corregida'),
    ),
    h('div', { class: 'body' }, entry.description),
    entry.correctsEntryId === null
      ? null
      : h('p', { class: 'reason' }, `Corrige la entrada ${entry.correctsEntryId} · ${entry.correctionReason ?? ''}`),
    entry.correctedByEntryId === null
      ? null
      : h('p', { class: 'reason' }, `Corregida por la entrada ${entry.correctedByEntryId}`),
    entry.appointmentId === null
      ? null
      : h('p', { class: 'help' }, `Turno asociado: ${entry.appointmentId}`),
  );
}

export function createEvolutionsSection(deps: EvolutionsSectionDeps): EvolutionsSection {
  const body = h('div', { class: 'clinical-section__body' });
  const element = h('section', { class: 'evolutions-section' }, h('h2', {}, 'Evoluciones'), body);

  function render(data: EvolutionsResponse): void {
    if (data.entries.length === 0) {
      body.replaceChildren(h('p', {}, 'Sin evoluciones registradas.'));
      return;
    }
    const sorted = [...data.entries].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    body.replaceChildren(h('ol', { class: 'entry-list' }, ...sorted.map(renderEntry)));
  }

  async function reload(): Promise<void> {
    body.replaceChildren(h('p', { attrs: { role: 'status' } }, 'Cargando evoluciones…'));
    try {
      render(await deps.load());
    } catch {
      body.replaceChildren(
        h('div', { attrs: { role: 'alert' } },
          h('p', {}, 'No pudimos cargar las evoluciones.'),
          h('button', { attrs: { type: 'button', 'data-retry': '' }, on: { click: () => { void reload(); } } }, 'Reintentar'),
        ),
      );
    }
  }

  return { element, render, reload };
}
