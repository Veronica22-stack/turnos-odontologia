import type { SessionUser } from '../../core/auth/session';
import { decodeSessionUser } from '../../core/auth/session-gateway';
import type { HttpClient } from '../../core/http/client';
import type { ApiError, FieldError } from '../../core/http/errors';
import type { LoginFailure } from './auth-messages';
import type { LoginInput } from './validation';

/** Contrato HTTP con C-04 (design D2). Unico lugar que conoce estados y codigos del backend. */

export type LoginResult =
  | { readonly ok: true; readonly user: SessionUser }
  | { readonly ok: false; readonly failure: LoginFailure };

export type ChangePasswordFailure =
  | { readonly kind: 'current-invalid' }
  | { readonly kind: 'fields'; readonly errors: readonly FieldError[] }
  | { readonly kind: 'expired' }
  | { readonly kind: 'stale-page' }
  | { readonly kind: 'connection' };

export type ChangePasswordResult =
  | { readonly ok: true }
  | { readonly ok: false; readonly failure: ChangePasswordFailure };

export interface ChangePasswordRequest {
  readonly currentPassword: string;
  readonly newPassword: string;
}

export interface AuthGateway {
  login(input: LoginInput): Promise<LoginResult>;
  changePassword(input: ChangePasswordRequest): Promise<ChangePasswordResult>;
}

export interface AuthGatewayOptions {
  readonly client: HttpClient;
  /** Se invoca una vez cuando el servidor informa sesion vencida durante el cambio de contrasena. */
  readonly onSessionExpired: () => void;
}

function toLoginFailure(error: ApiError): LoginFailure {
  if (error.kind !== 'problem') {
    return { kind: 'connection' };
  }
  if (error.status === 401 || (error.status === 400 && error.code === 'VALIDATION_FAILED')) {
    return { kind: 'invalid-credentials' };
  }
  if (error.status === 429) {
    return error.retryAfterSeconds === undefined
      ? { kind: 'locked' }
      : { kind: 'locked', retryAfterSeconds: error.retryAfterSeconds };
  }
  if (error.code === 'CSRF_REQUIRED') {
    return { kind: 'stale-page' };
  }
  return { kind: 'connection' };
}

function toChangeFailure(error: ApiError): ChangePasswordFailure {
  if (error.kind !== 'problem') {
    return { kind: 'connection' };
  }
  if (error.status === 401) {
    return error.code === 'INVALID_CREDENTIALS' ? { kind: 'current-invalid' } : { kind: 'expired' };
  }
  if (error.status === 400 && error.code === 'VALIDATION_FAILED' && error.errors.length > 0) {
    return { kind: 'fields', errors: error.errors };
  }
  if (error.code === 'CSRF_REQUIRED') {
    return { kind: 'stale-page' };
  }
  return { kind: 'connection' };
}

export function createAuthGateway(options: AuthGatewayOptions): AuthGateway {
  const { client, onSessionExpired } = options;
  return {
    async login(input) {
      const result = await client.request('POST', '/auth/login', {
        body: { email: input.email, password: input.password },
        decode: decodeSessionUser,
        credentialAttempt: true,
      });
      return result.ok ? { ok: true, user: result.data } : { ok: false, failure: toLoginFailure(result.error) };
    },
    async changePassword(input) {
      const result = await client.request('POST', '/auth/change-password', {
        body: { currentPassword: input.currentPassword, newPassword: input.newPassword },
        credentialAttempt: true,
      });
      if (result.ok) {
        return { ok: true };
      }
      const failure = toChangeFailure(result.error);
      if (failure.kind === 'expired') {
        onSessionExpired();
      }
      return { ok: false, failure };
    },
  };
}
