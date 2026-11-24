import { describe, expect, it, vi } from 'vitest';
import { h } from '../core/dom/h';
import { renderPrivateLayout, type PrivateLayoutArgs } from './private-layout';

function render(overrides: Partial<PrivateLayoutArgs> = {}): HTMLElement {
  const layout = renderPrivateLayout({
    user: { fullName: 'Dra. Romero' },
    current: 'agenda',
    content: h('h1', {}, 'Agenda'),
    onLogout: () => {},
    ...overrides,
  });
  document.body.replaceChildren(layout);
  return layout;
}

describe('renderPrivateLayout - estructura', () => {
  it('header con logo (alt no vacio), marca, usuario y boton Salir', () => {
    render();

    const header = document.querySelector('header');
    expect(header).not.toBeNull();
    const logo = header?.querySelector('img');
    expect(logo?.getAttribute('alt')).toBe('La Muela (logo provisional)');
    expect(header?.querySelector('a.brand-link')?.getAttribute('aria-label')).toContain('La Muela');
    expect(header?.textContent).toContain('Dra. Romero');
    const logout = header?.querySelector('button');
    expect(logout?.textContent).toContain('Salir');
    expect(logout?.getAttribute('type')).toBe('button');
  });

  it('muestra un nombre con marcado como texto literal', () => {
    render({ user: { fullName: '<b>Dra</b> Romero' } });

    expect(document.querySelector('header')?.textContent).toContain('<b>Dra</b> Romero');
    expect(document.querySelector('header b')).toBeNull();
  });

  it('nav "Principal" con los cuatro enlaces en orden', () => {
    render();

    const nav = document.querySelector('nav');
    expect(nav?.getAttribute('aria-label')).toBe('Principal');
    const links = [...(nav?.querySelectorAll('a') ?? [])];
    expect(links.map((a) => [a.textContent, a.getAttribute('href')])).toEqual([
      ['Agenda', '/agenda'],
      ['Pacientes', '/pacientes'],
      ['Historia clínica', '/historia-clinica'],
      ['Configuración', '/configuracion'],
    ]);
  });

  it('main#main con el contenido', () => {
    render();

    const main = document.querySelector('main#main');
    expect(main?.querySelector('h1')?.textContent).toBe('Agenda');
    expect(main?.getAttribute('tabindex')).toBe('-1');
  });

  it('el primer enfocable es "Saltar al contenido" hacia #main', () => {
    render();

    const first = document.querySelector('a, button');
    expect(first?.textContent).toBe('Saltar al contenido');
    expect(first?.getAttribute('href')).toBe('#main');
  });

  it('los iconos son decorativos', () => {
    render();

    const icons = document.querySelectorAll('nav svg, header button svg');
    expect(icons.length).toBe(5);
    for (const icon of icons) {
      expect(icon.getAttribute('aria-hidden')).toBe('true');
    }
  });
});

describe('renderPrivateLayout - item actual', () => {
  it.each([
    ['agenda', 'Agenda'],
    ['pacientes', 'Pacientes'],
    ['historia-clinica', 'Historia clínica'],
    ['configuracion', 'Configuración'],
  ] as const)('solo %s lleva aria-current="page"', (current, label) => {
    render({ current });

    const marked = [...document.querySelectorAll('nav a[aria-current="page"]')];
    expect(marked.map((a) => a.textContent)).toEqual([label]);
  });

  it('sin seccion actual ningun enlace esta marcado', () => {
    render({ current: null });

    expect(document.querySelectorAll('[aria-current]').length).toBe(0);
  });
});

describe('renderPrivateLayout - Salir', () => {
  it('invoca onLogout al hacer clic', () => {
    const onLogout = vi.fn();
    render({ onLogout });

    document.querySelector<HTMLButtonElement>('header button')?.click();

    expect(onLogout).toHaveBeenCalledTimes(1);
  });
});
