export type Child = Node | string | number | null | false | undefined;

export interface Props {
  readonly class?: string;
  readonly attrs?: Readonly<Record<string, string>>;
  readonly dataset?: Readonly<Record<string, string>>;
  readonly aria?: Readonly<Record<string, string>>;
  readonly on?: Readonly<Record<string, (event: Event) => void>>;
}

const SVG_NS = 'http://www.w3.org/2000/svg';
const URL_ATTRIBUTES: ReadonlySet<string> = new Set(['href', 'src', 'action', 'formaction', 'xlink:href']);
const SAFE_SCHEMES: ReadonlySet<string> = new Set(['http:', 'https:', 'mailto:', 'tel:']);

function isSafeUrl(raw: string): boolean {
  // Los navegadores ignoran espacios y caracteres de control dentro del esquema.
  // eslint-disable-next-line no-control-regex
  const cleaned = raw.replace(/[\u0000-\u0020\u007f]/g, '').toLowerCase();
  const match = /^([a-z][a-z0-9+.-]*:)/.exec(cleaned);
  return match === null || SAFE_SCHEMES.has(match[1] ?? '');
}

/** Assets de imagen inlineados por el bundler (data:image/*) solo como `src` de <img>. */
function isInlineImage(el: Element, attribute: string, value: string): boolean {
  return (
    attribute === 'src' &&
    el.localName === 'img' &&
    /^data:image\/(png|jpeg|gif|webp|svg\+xml)[;,]/i.test(value.trimStart())
  );
}

function setAttribute(el: Element, name: string, value: string): void {
  const lower = name.toLowerCase();
  if (lower.startsWith('on')) {
    throw new Error(`Atributo no permitido: ${name} (usar la propiedad "on" con un listener)`);
  }
  if (URL_ATTRIBUTES.has(lower) && !isSafeUrl(value) && !isInlineImage(el, lower, value)) {
    throw new Error(`Esquema de URL no permitido en el atributo ${name}`);
  }
  el.setAttribute(name, value);
}

function applyProps(el: Element, props: Props): void {
  if (props.class !== undefined) {
    el.setAttribute('class', props.class);
  }
  for (const [name, value] of Object.entries(props.attrs ?? {})) {
    setAttribute(el, name, value);
  }
  for (const [name, value] of Object.entries(props.dataset ?? {})) {
    const attr = `data-${name.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`;
    el.setAttribute(attr, value);
  }
  for (const [name, value] of Object.entries(props.aria ?? {})) {
    el.setAttribute(`aria-${name}`, value);
  }
  for (const [type, handler] of Object.entries(props.on ?? {})) {
    el.addEventListener(type, handler);
  }
}

function appendChildren(el: Element, children: readonly Child[]): void {
  for (const child of children) {
    if (child === null || child === false || child === undefined) {
      continue;
    }
    el.append(typeof child === 'string' || typeof child === 'number' ? document.createTextNode(String(child)) : child);
  }
}

/** Crea un elemento HTML. Las cadenas se insertan siempre como texto. */
export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Props = {},
  ...children: Child[]
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  applyProps(el, props);
  appendChildren(el, children);
  return el;
}

/** Crea un elemento SVG (namespace correcto) con las mismas garantias que `h`. */
export function svg(tag: string, props: Props = {}, ...children: Child[]): SVGElement {
  const el = document.createElementNS(SVG_NS, tag);
  applyProps(el, props);
  appendChildren(el, children);
  return el;
}
