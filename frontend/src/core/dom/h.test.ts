import { describe, expect, it, vi } from 'vitest';
import { h, svg } from './h';

describe('h - texto seguro', () => {
  it('muestra literal un texto con marcado y no crea elementos', () => {
    const p = h('p', {}, '<img src=x onerror=alert(1)>');

    expect(p.textContent).toBe('<img src=x onerror=alert(1)>');
    expect(p.querySelector('img')).toBeNull();
    expect(p.children).toHaveLength(0);
  });

  it('muestra igual un texto con ampersand', () => {
    expect(h('span', {}, 'Pérez & Hijos').textContent).toBe('Pérez & Hijos');
  });

  it('acepta nodos, numeros y omite null/false', () => {
    const el = h('div', {}, h('b', {}, 'a'), 7, null, false, 'z');

    expect(el.innerHTML).toBe('<b>a</b>7z');
  });
});

describe('h - atributos', () => {
  it('rechaza atributos on* nombrando el atributo pero no el valor', () => {
    expect(() => h('button', { attrs: { onclick: 'secreto()' } })).toThrowError(/onclick/);
    expect(() => h('button', { attrs: { onclick: 'secreto()' } })).not.toThrowError(/secreto/);
    expect(() => h('img', { attrs: { onerror: 'x' } })).toThrowError(/onerror/);
  });

  it('rechaza href con esquema javascript aunque tenga espacios, mayusculas o control', () => {
    expect(() => h('a', { attrs: { href: ' JavaScript:alert(1)' } })).toThrowError(/href/);
    expect(() => h('a', { attrs: { href: 'java\tscript:alert(1)' } })).toThrowError(/href/);
    expect(() => h('a', { attrs: { href: 'data:text/html,x' } })).toThrowError(/href/);
  });

  it('acepta href relativo, https, mailto y tel', () => {
    for (const href of ['/agenda', 'https://example.org/x', 'mailto:a@b.c', 'tel:+5491100000000', '#']) {
      expect(h('a', { attrs: { href } }).getAttribute('href')).toBe(href);
    }
  });

  it('rechaza src/action/formaction peligrosos', () => {
    expect(() => h('img', { attrs: { src: 'javascript:x' } })).toThrowError(/src/);
    expect(() => h('form', { attrs: { action: 'javascript:x' } })).toThrowError(/action/);
    expect(() => h('button', { attrs: { formaction: 'javascript:x' } })).toThrowError(/formaction/);
  });

  it('acepta data:image/* solo en src (assets inlineados por el bundler), no en href ni otros tipos', () => {
    const uri = 'data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=';

    expect(h('img', { attrs: { src: uri } }).getAttribute('src')).toBe(uri);
    expect(() => h('a', { attrs: { href: uri } })).toThrowError(/href/);
    expect(() => h('img', { attrs: { src: 'data:text/html;base64,AAAA' } })).toThrowError(/src/);
    expect(() => h('iframe', { attrs: { src: uri } })).toThrowError(/src/);
  });

  it('aplica class, dataset y aria', () => {
    const el = h('div', {
      class: 'a b',
      dataset: { appRoot: '1' },
      aria: { label: 'Principal', hidden: 'true' },
      attrs: { id: 'x' },
    });

    expect(el.className).toBe('a b');
    expect(el.getAttribute('data-app-root')).toBe('1');
    expect(el.getAttribute('aria-label')).toBe('Principal');
    expect(el.getAttribute('aria-hidden')).toBe('true');
    expect(el.id).toBe('x');
  });
});

describe('h - eventos', () => {
  it('registra listeners con on', () => {
    const click = vi.fn();
    const button = h('button', { on: { click } }, 'ok');

    button.click();
    button.click();

    expect(click).toHaveBeenCalledTimes(2);
  });
});

describe('svg', () => {
  it('crea elementos en el namespace SVG con atributos', () => {
    const icon = svg('svg', { attrs: { viewBox: '0 0 24 24' }, aria: { hidden: 'true' } }, svg('path', { attrs: { d: 'M0 0' } }));

    expect(icon.namespaceURI).toBe('http://www.w3.org/2000/svg');
    expect(icon.getAttribute('aria-hidden')).toBe('true');
    expect(icon.firstElementChild?.namespaceURI).toBe('http://www.w3.org/2000/svg');
    expect(icon.firstElementChild?.getAttribute('d')).toBe('M0 0');
  });
});
