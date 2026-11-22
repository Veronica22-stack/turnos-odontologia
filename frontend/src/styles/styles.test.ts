import { describe, expect, it } from 'vitest';

const NAMED_COLORS: ReadonlySet<string> = new Set([
  'red', 'blue', 'green', 'white', 'black', 'gray', 'grey', 'yellow', 'orange', 'purple', 'pink',
  'brown', 'cyan', 'magenta', 'navy', 'teal', 'silver', 'gold', 'maroon', 'lime', 'olive', 'aqua',
]);
const COLOR_FUNCTION = /\b(rgb|rgba|hsl|hsla|hwb|lab|lch|oklab|oklch)\(/i;
const HEX_COLOR = /#[0-9a-f]{3,8}\b/i;
const LENGTH_LITERAL = /(?<![\w.-])-?\d*\.?\d+(px|rem|em)\b/i;
const SPACING_PROPERTIES = /^(font-size|margin(-[a-z]+)?|padding(-[a-z]+)?|gap|row-gap|column-gap|border-radius|box-shadow)$/;
const LOCAL_CUSTOM_PROPERTY = /^--layout-/;

/** Verifica que los estilos usen solo tokens: devuelve "archivo: propiedad: valor" por cada infraccion. */
export function findStyleViolations(files: Readonly<Record<string, string>>): string[] {
  const violations: string[] = [];
  for (const [path, source] of Object.entries(files)) {
    const css = source.replace(/\/\*[\s\S]*?\*\//g, '');
    for (const match of css.matchAll(/([a-z-]+)\s*:\s*([^;{}]+)(?=[;}])/gi)) {
      const property = (match[1] ?? '').toLowerCase();
      const value = (match[2] ?? '').trim();
      if (LOCAL_CUSTOM_PROPERTY.test(property)) {
        continue;
      }
      const words = value.toLowerCase().split(/[^a-z]+/);
      const literalColor = HEX_COLOR.test(value) || COLOR_FUNCTION.test(value) || words.some((w) => NAMED_COLORS.has(w));
      const checksLength = SPACING_PROPERTIES.test(property) || property.startsWith('--');
      const literalLength = checksLength && LENGTH_LITERAL.test(value);
      const literalFont = property === 'font-family' && !value.includes('var(');
      if (literalColor || literalLength || literalFont) {
        violations.push(`${path}: ${property}: ${value}`);
      }
    }
  }
  return violations;
}

describe('findStyleViolations', () => {
  it.each([
    ['color: #333', 'a.css { color: #333; }', 'a.css: color: #333'],
    ['rgb(', 'a.css { background: rgb(1, 2, 3); }', 'a.css: background: rgb(1, 2, 3)'],
    ['padding en px', 'a.css { padding: 12px; }', 'a.css: padding: 12px'],
    ['font-size en px', 'a.css { font-size: 14px; }', 'a.css: font-size: 14px'],
    ['box-shadow con color', 'a.css { box-shadow: 0 1px red; }', 'a.css: box-shadow: 0 1px red'],
    ['margin-top en rem', 'a.css { margin-top: 1.5rem; }', 'a.css: margin-top: 1.5rem'],
    ['gap en em', 'a.css { gap: .5em; }', 'a.css: gap: .5em'],
    ['font-family literal', 'a.css { font-family: Arial, sans-serif; }', 'a.css: font-family: Arial, sans-serif'],
    ['border-radius en px', 'a.css { border-radius: 4px; }', 'a.css: border-radius: 4px'],
    ['custom property ajena a layout', 'a.css { :root { --mi-azul: #00f; } }', 'a.css: --mi-azul: #00f'],
  ])('detecta %s', (_label, css, expected) => {
    const [path, source] = [css.slice(0, css.indexOf(' ')), css.slice(css.indexOf(' ') + 1)];

    expect(findStyleViolations({ [path ?? '']: source })).toEqual([expected]);
  });

  it('acepta tokens, cero, constantes de layout y colores no literales', () => {
    const css = `
      /* comentario con color: #fff y padding: 12px no cuenta */
      :root { --layout-sidebar-width: 15rem; }
      a { color: var(--color-text); margin: 0; padding: var(--space-2) 0; background: transparent; }
      b { border: 1px solid var(--color-border); font-family: var(--font-sans); box-shadow: var(--shadow-md); }
      c { padding-bottom: env(safe-area-inset-bottom); width: calc(var(--space-4) * 2); border-color: currentColor; }
      @media (min-width: 64rem) { d { gap: var(--space-3); } }
    `;

    expect(findStyleViolations({ 'ok.css': css })).toEqual([]);
  });
});

describe('estilos de la SPA', () => {
  it('no declaran colores, espaciados, tamanos, radios ni sombras literales', () => {
    const sheets = import.meta.glob<string>('/src/**/*.css', { query: '?raw', import: 'default', eager: true });

    expect(Object.keys(sheets).length).toBeGreaterThan(0);
    // Vitest vacia los .css por defecto: si el texto llega vacio el escaneo seria vacuo.
    for (const source of Object.values(sheets)) {
      expect(source.length).toBeGreaterThan(0);
    }
    expect(findStyleViolations(sheets)).toEqual([]);
  });

  it('importan los tokens del design system', () => {
    const base = import.meta.glob<string>('/src/styles/base.css', { query: '?raw', import: 'default', eager: true });

    expect(Object.values(base).join('\n')).toContain('design-system/tokens.css');
  });
});
