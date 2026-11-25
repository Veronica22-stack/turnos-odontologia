/** Scroll-spy del índice lateral de Configuración (función pura + cableado al DOM). */

/**
 * Decide qué sección está activa. `intersecting` son los ids que tocan la banda superior
 * del viewport; con varios gana el último del documento. Al final de la página gana la
 * última sección (puede ser demasiado corta para llegar a la banda). Sin datos, se conserva.
 */
export function pickActiveSection(
  ids: readonly string[],
  intersecting: ReadonlySet<string>,
  current: string,
  atBottom: boolean,
): string {
  const last = ids[ids.length - 1];
  if (atBottom && last !== undefined) {
    return last;
  }
  for (let i = ids.length - 1; i >= 0; i -= 1) {
    const id = ids[i];
    if (id !== undefined && intersecting.has(id)) {
      return id;
    }
  }
  return current;
}

export interface SectionRect {
  readonly id: string;
  readonly top: number;
  readonly bottom: number;
}

/** Ids de las secciones que cruzan la línea horizontal `line` (px desde el borde superior del viewport). */
export function sectionsCrossing(rects: readonly SectionRect[], line: number): ReadonlySet<string> {
  const crossing = rects.filter((rect) => rect.top <= line && rect.bottom > line).map((rect) => rect.id);
  const first = rects[0];
  // Arriba de todo (encabezado de la página a la vista): todavía estamos en la primera sección.
  if (crossing.length === 0 && first !== undefined && rects.every((rect) => rect.top > line)) {
    return new Set([first.id]);
  }
  return new Set(crossing);
}

export interface SectionSpyOptions {
  readonly root: HTMLElement;
  readonly ids: readonly string[];
  readonly onChange: (id: string) => void;
  readonly initial: string;
}

const BOTTOM_TOLERANCE_PX = 2;
/** Línea de lectura: justo debajo del header fijo. */
const READING_LINE_PX = 96;

function isAtBottom(): boolean {
  const doc = document.documentElement;
  if (doc.scrollHeight <= window.innerHeight) {
    return false;
  }
  return window.innerHeight + window.scrollY >= doc.scrollHeight - BOTTOM_TOLERANCE_PX;
}

/** Observa las secciones; se autodestruye cuando `root` sale del documento. Devuelve el cleanup. */
export function startSectionSpy(options: SectionSpyOptions): () => void {
  const { root, ids, onChange } = options;
  const intersecting = new Set<string>();
  let current = options.initial;
  let stopped = false;
  let observer: IntersectionObserver | null = null;

  const stop = (): void => {
    if (stopped) {
      return;
    }
    stopped = true;
    observer?.disconnect();
    window.removeEventListener('scroll', onScroll);
  };

  /** En cada scroll se recalcula por posición: no depende de que el navegador pinte (pestaña oculta, etc.). */
  function onScroll(): void {
    if (!stopped && root.isConnected) {
      const rects: SectionRect[] = [];
      for (const id of ids) {
        const el = root.querySelector(`#${id}`);
        if (el !== null) {
          const box = el.getBoundingClientRect();
          rects.push({ id, top: box.top, bottom: box.bottom });
        }
      }
      const crossing = sectionsCrossing(rects, READING_LINE_PX);
      if (crossing.size > 0) {
        intersecting.clear();
        crossing.forEach((id) => intersecting.add(id));
      }
    }
    evaluate();
  }

  function evaluate(): void {
    if (stopped) {
      return;
    }
    if (!root.isConnected) {
      stop();
      return;
    }
    const next = pickActiveSection(ids, intersecting, current, isAtBottom());
    if (next !== current) {
      current = next;
      onChange(next);
    }
  }

  window.addEventListener('scroll', onScroll, { passive: true });
  if (typeof IntersectionObserver === 'undefined') {
    return stop;
  }

  // Banda: desde bajo el header fijo (~96px) hasta ~35% del alto; una sección "entra" al cruzarla.
  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const id = (entry.target as Element).id;
        if (entry.isIntersecting) {
          intersecting.add(id);
        } else {
          intersecting.delete(id);
        }
      }
      evaluate();
    },
    { rootMargin: '-96px 0px -65% 0px' },
  );
  for (const id of ids) {
    const el = root.querySelector(`#${id}`);
    if (el !== null) {
      observer.observe(el);
    }
  }
  return stop;
}
