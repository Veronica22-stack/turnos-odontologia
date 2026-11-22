import { describe, expect, it } from 'vitest';
import { h } from '../core/dom/h';
import { markProLink, renderPublicLayout } from './public-layout';

function render(): HTMLElement {
  const layout = renderPublicLayout({ content: h('h1', {}, 'Reservá tu turno') });
  document.body.replaceChildren(layout);
  return layout;
}

describe('renderPublicLayout', () => {
  it('header con marca y enlace "Acceso profesionales" a /login', () => {
    render();

    const header = document.querySelector('header');
    expect(header?.querySelector('img')?.getAttribute('alt')).toBe('La Muela (logo provisional)');
    const pro = [...(header?.querySelectorAll('a') ?? [])].find((a) => a.textContent === 'Acceso profesionales');
    expect(pro?.getAttribute('href')).toBe('/login');
  });

  it('no tiene la navegacion privada "Principal"', () => {
    render();

    expect(document.querySelector('nav[aria-label="Principal"]')).toBeNull();
    expect(document.querySelector('nav')).toBeNull();
  });

  it('main#main contiene la vista y es enfocable por el skip link', () => {
    render();

    const main = document.querySelector('main#main');
    expect(main?.querySelector('h1')?.textContent).toBe('Reservá tu turno');
    expect(main?.getAttribute('tabindex')).toBe('-1');
  });

  it('footer con Politica de privacidad y Terminos y condiciones', () => {
    render();

    const footer = document.querySelector('footer');
    const labels = [...(footer?.querySelectorAll('a') ?? [])].map((a) => a.textContent);
    expect(labels).toEqual(['Política de privacidad', 'Términos y condiciones']);
  });

  it('el primer enfocable es "Saltar al contenido" hacia #main', () => {
    render();

    const first = document.querySelector('a');
    expect(first?.textContent).toBe('Saltar al contenido');
    expect(first?.getAttribute('href')).toBe('#main');
  });
});

describe('enlace "Acceso profesionales" (C-06)', () => {
  const proLink = () => document.querySelector('a.pro-link');

  it('marca aria-current="page" cuando la ruta es /login', () => {
    document.body.replaceChildren(renderPublicLayout({ content: h('h1', {}, 'Ingresar'), currentPath: '/login' }));

    expect(proLink()?.getAttribute('aria-current')).toBe('page');
  });

  it('no lo marca en otras rutas ni sin ruta', () => {
    document.body.replaceChildren(renderPublicLayout({ content: h('h1', {}, 'x'), currentPath: '/reservar' }));
    expect(proLink()?.hasAttribute('aria-current')).toBe(false);

    document.body.replaceChildren(renderPublicLayout({ content: h('h1', {}, 'x') }));
    expect(proLink()?.hasAttribute('aria-current')).toBe(false);
  });

  it('markProLink actualiza el enlace al navegar sin recrear el layout', () => {
    const layout = renderPublicLayout({ content: h('h1', {}, 'x'), currentPath: '/reservar' });
    document.body.replaceChildren(layout);

    markProLink(layout, '/login');
    expect(proLink()?.getAttribute('aria-current')).toBe('page');

    markProLink(layout, '/reservar');
    expect(proLink()?.hasAttribute('aria-current')).toBe(false);
  });
});
