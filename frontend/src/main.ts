const APP_CONTAINER_SELECTOR = '#app';

/**
 * Monta el nodo raiz de la aplicacion dentro de `#app`.
 * Es idempotente: si ya existe un `[data-app-root]` lo devuelve sin crear otro.
 * Falla sin tocar el documento si el contenedor no existe.
 */
export function mountApp(doc: Document): HTMLElement {
  const container = doc.querySelector<HTMLElement>(APP_CONTAINER_SELECTOR);
  if (container === null) {
    throw new Error(`No se encontro el contenedor ${APP_CONTAINER_SELECTOR} en el documento`);
  }

  const existing = container.querySelector<HTMLElement>('[data-app-root]');
  if (existing !== null) {
    return existing;
  }

  const root = doc.createElement('div');
  root.setAttribute('data-app-root', '');
  container.appendChild(root);
  return root;
}

if (import.meta.env.MODE !== 'test') {
  mountApp(document);
}
