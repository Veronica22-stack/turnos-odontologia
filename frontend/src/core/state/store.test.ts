import { describe, expect, it, vi } from 'vitest';
import { createStore } from './store';

describe('createStore', () => {
  it('get devuelve el estado inicial', () => {
    expect(createStore({ n: 1 }).get()).toEqual({ n: 1 });
  });

  it('set con valor notifica una vez con el nuevo estado', () => {
    const store = createStore(1);
    const listener = vi.fn();
    store.subscribe(listener);

    store.set(2);

    expect(store.get()).toBe(2);
    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenCalledWith(2);
  });

  it('set con funcion recibe el estado previo', () => {
    const store = createStore(10);
    const listener = vi.fn();
    store.subscribe(listener);

    store.set((prev) => prev + 5);

    expect(store.get()).toBe(15);
    expect(listener).toHaveBeenCalledWith(15);
  });

  it('no notifica si el valor es el mismo (Object.is)', () => {
    const initial = { a: 1 };
    const store = createStore(initial);
    const listener = vi.fn();
    store.subscribe(listener);

    store.set(initial);
    store.set((prev) => prev);

    expect(listener).not.toHaveBeenCalled();
  });

  it('notifica si cambia la referencia aunque el contenido sea igual', () => {
    const store = createStore({ a: 1 });
    const listener = vi.fn();
    store.subscribe(listener);

    store.set({ a: 1 });

    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('unsubscribe deja de notificar', () => {
    const store = createStore(0);
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);

    store.set(1);
    unsubscribe();
    store.set(2);

    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('un listener que se desuscribe durante la notificacion no impide notificar al siguiente', () => {
    const store = createStore(0);
    const second = vi.fn();
    const unsubscribeFirst = store.subscribe(() => unsubscribeFirst());
    store.subscribe(second);

    store.set(1);

    expect(second).toHaveBeenCalledWith(1);
  });
});
