import { isBoolean, isRecord, isString } from '../http/decode';
import type { HttpClient } from '../http/client';
import type { SessionGateway, SessionUser } from './session';

function decodeSessionUser(value: unknown): SessionUser | undefined {
  if (!isRecord(value)) {
    return undefined;
  }
  const { id, fullName, role, mustChangePassword } = value;
  if (!isString(id) || !isString(fullName) || !isString(role)) {
    return undefined;
  }
  if (mustChangePassword !== undefined && !isBoolean(mustChangePassword)) {
    return undefined;
  }
  return { id, fullName, role, ...(isBoolean(mustChangePassword) ? { mustChangePassword } : {}) };
}

/** Contrato supuesto con C-04 (ver design D4): GET /auth/me y POST /auth/logout. */
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
