import { describe, expect, it } from 'vitest';
import indexHtml from '../index.html?raw';
import { mountApp } from './main';

function createDocument(markup: string): Document {
  return new DOMParser().parseFromString(markup, 'text/html');
}

describe('mountApp', () => {
  it('monta un unico [data-app-root] dentro de #app y lo devuelve', () => {
    const doc = createDocument('<body><div id="app"></div></body>');

    const root = mountApp(doc);

    const roots = doc.querySelectorAll('#app [data-app-root]');
    expect(roots).toHaveLength(1);
    expect(roots[0]).toBe(root);
  });

  it('es idempotente: un arranque repetido no duplica el contenedor raiz', () => {
    const doc = createDocument('<body><div id="app"></div></body>');

    const first = mountApp(doc);
    const second = mountApp(doc);

    expect(second).toBe(first);
    expect(doc.querySelectorAll('[data-app-root]')).toHaveLength(1);
  });

  it('falla mencionando #app y no modifica el body cuando falta el contenedor', () => {
    const doc = createDocument('<body><p>otro contenido</p></body>');
    const before = doc.body.innerHTML;

    expect(() => mountApp(doc)).toThrowError(/#app/);
    expect(doc.body.innerHTML).toBe(before);
  });
});

describe('index.html', () => {
  it('declara el idioma es-AR', () => {
    const doc = createDocument(indexHtml);

    expect(doc.documentElement.getAttribute('lang')).toBe('es-AR');
  });

  it('contiene el contenedor #app y carga el modulo /src/main.ts', () => {
    const doc = createDocument(indexHtml);

    expect(doc.querySelector('#app')).not.toBeNull();
    expect(doc.querySelector('script[type="module"][src="/src/main.ts"]')).not.toBeNull();
  });
});
