import { createStore, type Listener } from '../state/store';

/** Forma real de C-04 (`/auth/me` y `/auth/login`): `fullName` no viaja todavia (pedido P1). */
export interface SessionUser {
  readonly userId: string;
  readonly role: string;
  readonly mustChangePassword: boolean;
  readonly expiresAt?: string;
  readonly fullName?: string;
}

export type SessionState =
  | { readonly status: 'unknown' }
  | { readonly status: 'anonymous'; readonly notice?: 'expired' | 'offline' | 'password-changed' }
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
  /** Establece la sesion con el usuario devuelto por el login (sin llamadas HTTP). */
  signIn(user: SessionUser): void;
  /** El servidor cerro todas las sesiones al cambiar la contrasena: queda anonima con aviso. */
  endAfterPasswordChange(): void;
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
    signIn(user) {
      store.set({ status: 'authenticated', user });
    },
    endAfterPasswordChange() {
      store.set({ status: 'anonymous', notice: 'password-changed' });
    },
  };
}
