import { h } from '../../core/dom/h';
import { createBlocksSection } from './blocks-section';
import { createPracticeSection } from './practice-section';
import type { SettingsGateway } from './settings-gateway';
import { startSectionSpy } from './section-spy';
import { createServicesSection } from './services-section';
import { createWorkingHoursSection } from './working-hours-section';

/** Vista `/configuracion` (C-19 D1/D3/D8): cuatro secciones que cargan y guardan por separado. */

export interface SettingsViewDeps {
  readonly gateway: SettingsGateway;
}

/** Solo si no se pudo leer la granularidad; el backend confirma el valor en cada guardado (D3). */
const FALLBACK_GRANULARITY = 15;

const SECTION_INDEX: readonly { readonly id: string; readonly label: string }[] = [
  { id: 'practice', label: 'Consultorio y reserva' },
  { id: 'hours', label: 'Horarios de atención' },
  { id: 'services', label: 'Prestaciones' },
  { id: 'blocks', label: 'Bloqueos' },
];

const SECTION_IDS = SECTION_INDEX.map((section) => section.id);

export function createSettingsView(deps: SettingsViewDeps): HTMLElement {
  const content = h('div', { class: 'settings-content' });

  function markCurrent(id: string): void {
    for (const link of root.querySelectorAll<HTMLAnchorElement>('.section-index a')) {
      if (link.getAttribute('href') === `#${id}`) {
        link.setAttribute('aria-current', 'true');
      } else {
        link.removeAttribute('aria-current');
      }
    }
  }

  /** El router escucha popstate (que dispara el salto de ancla) y re-renderizaría la vista: se resuelve acá. */
  function onIndexClick(event: Event): void {
    const href = (event.currentTarget as HTMLAnchorElement).getAttribute('href') ?? '';
    const target = root.querySelector<HTMLElement>(href);
    if (target === null) {
      return;
    }
    event.preventDefault();
    markCurrent(target.id);
    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    target.setAttribute('tabindex', '-1');
    target.focus({ preventScroll: true });
  }
  const root = h(
    'div',
    { class: 'settings-page' },
    h(
      'div',
      { class: 'page-head' },
      h('h1', { attrs: { tabindex: '-1' } }, 'Configuración'),
      h(
        'p',
        { class: 'lead' },
        'Datos del consultorio, horarios de atención, prestaciones y bloqueos. Cada sección se guarda por separado.',
      ),
    ),
    h(
      'div',
      { class: 'settings-layout', attrs: { id: 'settings-layout' } },
      h(
        'aside',
        { class: 'section-index', aria: { label: 'Secciones de esta página' } },
        h('h2', {}, 'En esta página'),
        h(
          'ol',
          {},
          ...SECTION_INDEX.map((section, index) =>
            h(
              'li',
              {},
              h(
                'a',
                index === 0
                  ? { attrs: { href: `#${section.id}` }, aria: { current: 'true' }, on: { click: onIndexClick } }
                  : { attrs: { href: `#${section.id}` }, on: { click: onIndexClick } },
                section.label,
              ),
            ),
          ),
        ),
      ),
      content,
    ),
  );

  // Un único GET compartido: la sección lo usa para pintar y la vista para derivar la granularidad.
  const practicePromise = deps.gateway.getPractice();
  let granularity = FALLBACK_GRANULARITY;
  void practicePromise.then((result) => {
    if (result.ok && result.data !== undefined) {
      granularity = result.data.slotGranularityMin;
    }
  });

  content.replaceChildren(
    createPracticeSection({ gateway: deps.gateway, initialPromise: practicePromise }),
    createWorkingHoursSection({ gateway: deps.gateway }),
    createServicesSection({ gateway: deps.gateway, getGranularity: () => granularity }),
    createBlocksSection({ gateway: deps.gateway }),
  );

  startSectionSpy({ root, ids: SECTION_IDS, initial: SECTION_IDS[0] ?? '', onChange: markCurrent });

  return root;
}
