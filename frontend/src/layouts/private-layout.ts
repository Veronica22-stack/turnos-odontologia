import { h } from '../core/dom/h';
import { brandName, logoAlt, logoUrl } from './brand';
import { icon, type IconName } from './icons';

export type PrivateSection = 'agenda' | 'pacientes' | 'historia-clinica' | 'configuracion';

export interface PrivateLayoutArgs {
  readonly user: { readonly fullName: string };
  readonly current: PrivateSection | null;
  readonly content: Node;
  readonly onLogout: () => void;
}

const NAV_ITEMS: ReadonlyArray<{ section: PrivateSection; label: string; href: string; icon: IconName }> = [
  { section: 'agenda', label: 'Agenda', href: '/agenda', icon: 'agenda' },
  { section: 'pacientes', label: 'Pacientes', href: '/pacientes', icon: 'pacientes' },
  { section: 'historia-clinica', label: 'Historia clínica', href: '/historia-clinica', icon: 'pacientes' },
  { section: 'configuracion', label: 'Configuración', href: '/configuracion', icon: 'config' },
];

function initialsOf(fullName: string): string {
  return fullName
    .split(/\s+/)
    .filter((word) => word !== '')
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join('');
}

export function renderPrivateLayout(args: PrivateLayoutArgs): HTMLElement {
  const { user, current, content, onLogout } = args;

  const header = h(
    'header',
    { class: 'app-header' },
    h(
      'a',
      { class: 'brand-link', attrs: { href: '/agenda', 'aria-label': `${brandName()} — ir a la agenda` } },
      h('img', { attrs: { src: logoUrl, alt: logoAlt(), width: '248', height: '48' } }),
    ),
    h(
      'div',
      { class: 'user-slot' },
      h('span', { class: 'avatar', aria: { hidden: 'true' } }, initialsOf(user.fullName)),
      h('span', { class: 'user-name' }, user.fullName),
      h('span', { class: 'sr-only' }, `Sesión iniciada como ${user.fullName}`),
      h(
        'button',
        { class: 'btn btn-ghost btn-logout', attrs: { type: 'button', 'aria-label': 'Salir' }, on: { click: onLogout } },
        icon('salir'),
        h('span', { class: 'label' }, 'Salir'),
      ),
    ),
  );

  const nav = h(
    'nav',
    { class: 'main-nav', aria: { label: 'Principal' } },
    h(
      'ul',
      {},
      ...NAV_ITEMS.map((item) =>
        h(
          'li',
          {},
          h(
            'a',
            {
              class: 'nav-link',
              attrs: { href: item.href, ...(item.section === current ? { 'aria-current': 'page' } : {}) },
            },
            icon(item.icon),
            h('span', {}, item.label),
          ),
        ),
      ),
    ),
  );

  const main = h('main', { class: 'private-main', attrs: { id: 'main', tabindex: '-1' } }, content);

  return h(
    'div',
    { class: 'private-shell' },
    h('a', { class: 'skip-link', attrs: { href: '#main' } }, 'Saltar al contenido'),
    header,
    nav,
    main,
  );
}
