import { h } from '../../core/dom/h';
import type { OdontogramEntry, OdontogramResponse, ToothCondition, ToothSurface } from './clinical-record-types';

/** Odontograma FDI: solo marcas derivadas del estado vigente (6.1–6.3). Sin asumir piezas sanas. */

export interface OdontogramViewDeps {
  readonly data: OdontogramResponse;
  readonly onToggle: (toothCode: string) => void;
}

export interface OdontogramView {
  readonly element: HTMLElement;
  select(toothCode: string): void;
  /** Misma descripción que el `aria-label`, para la región viva del odontograma. */
  describe(toothCode: string): string;
}

interface QuadDef {
  readonly quadrant: number;
  readonly position: string;
  readonly dentition: string;
  readonly permanent: boolean;
}

interface ToothView {
  readonly button: HTMLButtonElement;
  readonly code: string;
  readonly label: string;
  readonly surfaceLabel: string | null;
}

// Orden de lectura: cuadrantes 1-2 (arriba), 4-3 (abajo), 5-6 (temporales arriba), 8-7 (temporales abajo).
const QUADS: readonly QuadDef[] = [
  { quadrant: 1, position: 'superior derecho', dentition: 'permanente', permanent: true },
  { quadrant: 2, position: 'superior izquierdo', dentition: 'permanente', permanent: true },
  { quadrant: 4, position: 'inferior derecho', dentition: 'permanente', permanent: true },
  { quadrant: 3, position: 'inferior izquierdo', dentition: 'permanente', permanent: true },
  { quadrant: 5, position: 'superior derecho', dentition: 'temporal', permanent: false },
  { quadrant: 6, position: 'superior izquierdo', dentition: 'temporal', permanent: false },
  { quadrant: 8, position: 'inferior derecho', dentition: 'temporal', permanent: false },
  { quadrant: 7, position: 'inferior izquierdo', dentition: 'temporal', permanent: false },
];

const CONDITION: Readonly<Record<ToothCondition, { label: string; mark: string; css: string }>> = {
  HEALTHY: { label: 'sano', mark: 'S', css: '' },
  CARIES: { label: 'caries', mark: 'C', css: 't-caries' },
  FILLED: { label: 'obturado', mark: 'O', css: 't-obturado' },
  MISSING: { label: 'ausente', mark: '✕', css: 't-ausente' },
  CROWN: { label: 'corona', mark: 'Co', css: 't-corona' },
  ROOT_CANAL: { label: 'endodoncia', mark: 'E', css: 't-endodoncia' },
};

const SURFACE_LABEL: Readonly<Record<ToothSurface, string>> = {
  OCCLUSAL: 'cara oclusal',
  MESIAL: 'cara mesial',
  DISTAL: 'cara distal',
  VESTIBULAR: 'cara vestibular',
  LINGUAL: 'cara lingual',
};

/** Entrada vigente por pieza: se descarta la que fue corregida por otra del propio conjunto. */
function activeByTooth(entries: readonly OdontogramEntry[]): ReadonlyMap<string, OdontogramEntry> {
  const superseded = new Set<string>();
  for (const entry of entries) {
    if (entry.correctsEntryId !== null) {
      superseded.add(entry.correctsEntryId);
    }
  }
  const active = new Map<string, OdontogramEntry>();
  for (const entry of entries) {
    if (entry.correctedByEntryId !== null || superseded.has(entry.id)) {
      continue;
    }
    const previous = active.get(entry.toothCode);
    if (previous === undefined || entry.createdAt >= previous.createdAt) {
      active.set(entry.toothCode, entry);
    }
  }
  return active;
}

function codesFor(quad: QuadDef): readonly string[] {
  const count = quad.permanent ? 8 : 5;
  return Array.from({ length: count }, (_, index) => `${quad.quadrant}${index + 1}`);
}

export function createOdontogramView(deps: OdontogramViewDeps): OdontogramView {
  const active = activeByTooth(deps.data.current);
  const toothViews = new Map<string, ToothView>();
  let selected: string | null = null;

  function ariaLabel(view: ToothView): string {
    const state = view.label === 'sin registro' ? view.label : `${view.label}${view.surfaceLabel === null ? '' : `, ${view.surfaceLabel}`}`;
    return `Pieza ${view.code}, ${state}${selected === view.code ? ', seleccionada' : ''}`;
  }

  function updatePressed(): void {
    for (const view of toothViews.values()) {
      view.button.setAttribute('aria-pressed', String(selected === view.code));
      view.button.setAttribute('aria-label', ariaLabel(view));
    }
  }

  function select(toothCode: string): void {
    selected = toothViews.has(toothCode) ? toothCode : null;
    updatePressed();
  }

  function createTooth(code: string): HTMLButtonElement {
    const entry = active.get(code);
    const condition = entry === undefined ? null : CONDITION[entry.condition];
    const label = condition === null ? 'sin registro' : condition.label;
    const surfaceLabel = entry?.surface == null ? null : SURFACE_LABEL[entry.surface];
    const mark = condition === null ? '–' : condition.mark;
    const css = condition === null ? 't-sin-registro' : condition.css;
    const button = h(
      'button',
      {
        class: `tooth ${css}`.trim(),
        dataset: { tooth: code },
        attrs: { type: 'button', 'aria-pressed': 'false' },
        on: { click: () => deps.onToggle(code) },
      },
      h('span', {}, code),
      h('span', { class: 'mark', attrs: { 'aria-hidden': 'true' } }, mark),
    );
    const view: ToothView = { button, code, label, surfaceLabel };
    toothViews.set(code, view);
    button.setAttribute('aria-label', ariaLabel(view));
    return button;
  }

  function createQuad(quad: QuadDef): HTMLElement {
    return h(
      'div',
      {
        class: 'odo-quad',
        attrs: { role: 'group', 'aria-label': `Cuadrante ${quad.quadrant}, ${quad.position}, ${quad.dentition}` },
      },
      ...codesFor(quad).map((code) => createTooth(code)),
      h('span', { class: 'odo-quad-label' }, `Cuadrante ${quad.quadrant} · ${quad.position}`),
    );
  }

  function quadAt(index: number): HTMLElement {
    return createQuad(QUADS[index] as QuadDef);
  }

  const permanentSuperior = h(
    'section',
    { class: 'odo-arch', attrs: { 'aria-label': 'Dentición permanente, arcada superior' } },
    quadAt(0), quadAt(1),
  );
  const permanentInferior = h(
    'section',
    { class: 'odo-arch', attrs: { 'aria-label': 'Dentición permanente, arcada inferior' } },
    quadAt(2), quadAt(3),
  );
  const temporalSuperior = h('div', { class: 'odo-arch' }, quadAt(4), quadAt(5));
  const temporalInferior = h('div', { class: 'odo-arch' }, quadAt(6), quadAt(7));

  const legend = h(
    'div',
    { class: 'odo-legend', attrs: { 'aria-label': 'Referencias: el color siempre se acompaña de una marca textual' } },
    ...(['HEALTHY', 'CARIES', 'FILLED', 'CROWN', 'ROOT_CANAL', 'MISSING'] as const).map((condition) =>
      h('span', {},
        h('span', { class: `tooth ${CONDITION[condition].css}`.trim() },
          h('span', { class: 'mark' }, CONDITION[condition].mark)),
        CONDITION[condition].label,
      ),
    ),
  );

  const element = h(
    'div',
    { class: 'odo' },
    h('div', { class: 'arch-row' }, permanentSuperior, permanentInferior),
    h('section', { class: 'arch-row', attrs: { 'aria-label': 'Dentición temporal (sujeta a validación)' } },
      temporalSuperior, temporalInferior),
    legend,
  );

  function describe(toothCode: string): string {
    const view = toothViews.get(toothCode);
    return view === undefined ? `Pieza ${toothCode}, sin registro` : ariaLabel(view);
  }

  return { element, select, describe };
}
