import { isBoolean, isRecord, isString } from '../http/decode';
import type { HttpClient } from '../http/client';
import type { SessionGateway, SessionUser } from './session';

/** Decodifica la forma real de C-04: `userId` y `role` obligatorios; `mustChangePassword` vale `false` si falta. */
export function decodeSessionUser(value: unknown): SessionUser | undefined {
  if (!isRecord(value)) {
    return undefined;
  }
  const { userId, role, mustChangePassword, expiresAt, fullName } = value;
  if (!isString(userId) || !isString(role)) {
    return undefined;
  }
  if (mustChangePassword !== undefined && !isBoolean(mustChangePassword)) {
    return undefined;
  }
  return {
    userId,
    role,
    mustChangePassword: mustChangePassword === true,
    ...(isString(expiresAt) ? { expiresAt } : {}),
    ...(isString(fullName) && fullName.trim() !== '' ? { fullName } : {}),
  };
}

/** Contrato de C-04 (design D2): GET /auth/me y POST /auth/logout. */
export function httpSessionGateway(client: HttpClient): SessionGateway {
  return {
    async me() {
      const result = await client.request('GET', '/auth/me', { decode: decodeSessionUser, sessionProbe: true });
      if (result.ok) {
        return { status: 'authenticated', user: result.data };
      }
      return result.error.kind === 'unauthorized' ? { status: 'anonymous' } : { status: 'error' };
    },
    async logout() {
      await client.request('POST', '/auth/logout');
    },
  };
}
