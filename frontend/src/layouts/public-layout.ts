import { h } from '../core/dom/h';
import { brandName, logoAlt, logoUrl } from './brand';

export interface PublicLayoutArgs {
  readonly content: Node;
  /** Ruta actual: en /login el enlace "Acceso profesionales" se marca con aria-current. */
  readonly currentPath?: string;
}

const LOGIN_PATH = '/login';

/** Actualiza `aria-current` del enlace de acceso al cambiar de ruta sin recrear el layout. */
export function markProLink(layout: ParentNode, currentPath: string): void {
  const link = layout.querySelector('a.pro-link');
  if (currentPath === LOGIN_PATH) {
    link?.setAttribute('aria-current', 'page');
  } else {
    link?.removeAttribute('aria-current');
  }
}

export function renderPublicLayout(args: PublicLayoutArgs): HTMLElement {
  const header = h(
    'header',
    { class: 'app-header public-header' },
    h(
      'a',
      { class: 'brand-link', attrs: { href: '/reservar', 'aria-label': `${brandName()} — reservar turno` } },
      h('img', { attrs: { src: logoUrl, alt: logoAlt(), width: '248', height: '48' } }),
    ),
    h(
      'a',
      {
        class: 'pro-link',
        attrs: { href: LOGIN_PATH, ...(args.currentPath === LOGIN_PATH ? { 'aria-current': 'page' } : {}) },
      },
      'Acceso profesionales',
    ),
  );

  const main = h('main', { class: 'public-main', attrs: { id: 'main', tabindex: '-1' } }, args.content);

  const footer = h(
    'footer',
    { class: 'public-footer' },
    h(
      'div',
      { class: 'inner' },
      h('p', {}, brandName()),
      h(
        'ul',
        {},
        h('li', {}, h('a', { attrs: { href: '/privacidad' } }, 'Política de privacidad')),
      ),
    ),
  );

  return h(
    'div',
    { class: 'public-shell' },
    h('a', { class: 'skip-link', attrs: { href: '#main' } }, 'Saltar al contenido'),
    header,
    main,
    footer,
  );
}
