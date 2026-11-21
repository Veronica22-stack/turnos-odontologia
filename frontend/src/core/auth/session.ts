import { createStore, type Listener } from '../state/store';

export interface SessionUser {
  readonly id: string;
  readonly fullName: string;
  readonly role: string;
  readonly mustChangePassword?: boolean;
}

export type SessionState =
  | { readonly status: 'unknown' }
  | { readonly status: 'anonymous'; readonly notice?: 'expired' | 'offline' }
  | { readonly status: 'authenticated'; readonly user: SessionUser };

export type SessionResult =
  | { readonly status: 'authenticated'; readonly user: SessionUser }
  | { readonly status: 'anonymous' }
  | { readonly status: 'error' };

/** Unico punto de contacto con el backend de auth; reemplazable (PA-04). */
export interface SessionGateway {
  me(): Promise<SessionResult>;
  logout(): Promise<void>;
}

export interface Session {
  get(): SessionState;
  subscribe(listener: Listener<SessionState>): () => void;
  load(): Promise<void>;
  ensureLoaded(): Promise<void>;
  logout(): Promise<void>;
  expire(): void;
}

export function createSession(gateway: SessionGateway): Session {
  const store = createStore<SessionState>({ status: 'unknown' });
  let inflight: Promise<void> | undefined;

  function load(): Promise<void> {
    inflight ??= gateway
      .me()
      .catch((): SessionResult => ({ status: 'error' }))
      .then((result) => {
        if (result.status === 'authenticated') {
          store.set({ status: 'authenticated', user: result.user });
        } else if (result.status === 'anonymous') {
          store.set({ status: 'anonymous' });
        } else {
          store.set({ status: 'anonymous', notice: 'offline' });
        }
      })
      .finally(() => {
        inflight = undefined;
      });
    return inflight;
  }

  return {
    get: store.get,
    subscribe: store.subscribe,
    load,
    ensureLoaded() {
      return store.get().status === 'unknown' ? load() : Promise.resolve();
    },
    async logout() {
      try {
        await gateway.logout();
      } catch {
        // El cierre local siempre se completa, aunque el servidor no responda.
      }
      store.set({ status: 'anonymous' });
    },
    expire() {
      if (store.get().status === 'authenticated') {
        store.set({ status: 'anonymous', notice: 'expired' });
      }
    },
  };
}
