import { describe, expect, it } from 'vitest';

const FORBIDDEN: ReadonlyArray<readonly [string, RegExp]> = [
  ['innerHTML', /\binnerHTML\b/],
  ['outerHTML', /\bouterHTML\b/],
  ['insertAdjacentHTML', /\binsertAdjacentHTML\b/],
  ['document.write', /\bdocument\s*\.\s*write(ln)?\s*\(/],
  ['eval(', /\beval\s*\(/],
  ['new Function', /\bnew\s+Function\b/],
];

export function findForbiddenHtmlApis(files: Readonly<Record<string, string>>): string[] {
  const found: string[] = [];
  for (const [path, source] of Object.entries(files)) {
    for (const [label, pattern] of FORBIDDEN) {
      if (pattern.test(source)) {
        found.push(`${path}: ${label}`);
      }
    }
  }
  return found;
}

describe('findForbiddenHtmlApis', () => {
  it('detecta cada API prohibida con archivo y nombre', () => {
    expect(findForbiddenHtmlApis({ 'x.ts': 'el.innerHTML = y' })).toEqual(['x.ts: innerHTML']);
    expect(findForbiddenHtmlApis({ 'a.ts': 'el.outerHTML = y' })).toEqual(['a.ts: outerHTML']);
    expect(findForbiddenHtmlApis({ 'b.ts': 'el.insertAdjacentHTML("a", y)' })).toEqual(['b.ts: insertAdjacentHTML']);
    expect(findForbiddenHtmlApis({ 'c.ts': 'document.write(y)' })).toEqual(['c.ts: document.write']);
    expect(findForbiddenHtmlApis({ 'd.ts': 'eval(y)' })).toEqual(['d.ts: eval(']);
    expect(findForbiddenHtmlApis({ 'e.ts': 'new Function("return 1")' })).toEqual(['e.ts: new Function']);
  });

  it('no marca codigo limpio', () => {
    expect(findForbiddenHtmlApis({ 'ok.ts': 'el.textContent = y; el.replaceChildren(a)' })).toEqual([]);
  });
});

describe('codigo de produccion', () => {
  it('no usa APIs que interpretan HTML o codigo', () => {
    const modules = import.meta.glob<string>('/src/**/*.ts', { query: '?raw', import: 'default', eager: true });
    const production = Object.fromEntries(Object.entries(modules).filter(([path]) => !path.endsWith('.test.ts')));

    expect(Object.keys(production).length).toBeGreaterThan(0);
    expect(findForbiddenHtmlApis(production)).toEqual([]);
  });
});
