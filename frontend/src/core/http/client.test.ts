import { afterEach, describe, expect, it, vi } from 'vitest';
import { createHttpClient } from './client';
import { isRecord, isString } from './decode';
import { cookieCsrfTransport } from './transport';

interface Recorded {
  url: string;
  init: RequestInit;
}

function jsonResponse(body: string, status = 200, contentType = 'application/json'): Response {
  return new Response(body, { status, headers: { 'Content-Type': contentType } });
}

function setup(respond: (call: Recorded) => Response | Promise<Response>, extra: { timeoutMs?: number; onUnauthorized?: () => void } = {}) {
  const calls: Recorded[] = [];
  const fetchFn = vi.fn(async (url: string, init: RequestInit) => {
    const call = { url, init };
    calls.push(call);
    return respond(call);
  });
  const client = createHttpClient({
    fetch: fetchFn,
    transport: cookieCsrfTransport({ readCookies: () => 'XSRF-TOKEN=abc123' }),
    ...extra,
  });
  return { client, calls, fetchFn };
}

interface User {
  id: string;
  fullName: string;
}
function decodeUser(value: unknown): User | undefined {
  if (isRecord(value) && isString(value['id']) && isString(value['fullName'])) {
    return { id: value['id'], fullName: value['fullName'] };
  }
  return undefined;
}

describe('createHttpClient - peticion', () => {
  it('GET /auth/me va a /api/v1/auth/me con Accept y credenciales del mismo origen', async () => {
    const { client, calls } = setup(() => jsonResponse('{"id":"u1","fullName":"Dra. Romero"}'));

    const result = await client.request('GET', '/auth/me', { decode: decodeUser });

    expect(calls[0]?.url).toBe('/api/v1/auth/me');
    expect(new Headers(calls[0]?.init.headers).get('Accept')).toBe('application/json');
    expect(calls[0]?.init.credentials).toBe('same-origin');
    expect(calls[0]?.init.method).toBe('GET');
    expect(result).toEqual({ ok: true, data: { id: 'u1', fullName: 'Dra. Romero' } });
  });

  it.each(['https://evil.example/x', '//evil', 'auth/me', '/a\\b', '/x://y'])(
    'rechaza la ruta %s sin llamar a fetch',
    async (path) => {
      const { client, fetchFn } = setup(() => jsonResponse('{}'));

      await expect(client.request('GET', path, { decode: decodeUser })).rejects.toThrowError(/ruta/i);
      expect(fetchFn).not.toHaveBeenCalled();
    },
  );

  it('POST con body serializa JSON con Content-Type y agrega la cabecera CSRF', async () => {
    const { client, calls } = setup(() => new Response(null, { status: 204 }));

    await client.request('POST', '/auth/logout', { body: { a: 1 } });

    const headers = new Headers(calls[0]?.init.headers);
    expect(calls[0]?.init.body).toBe('{"a":1}');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('X-XSRF-TOKEN')).toBe('abc123');
  });

  it('POST sin body no envia Content-Type', async () => {
    const { client, calls } = setup(() => new Response(null, { status: 204 }));

    await client.request('POST', '/auth/logout');

    expect(new Headers(calls[0]?.init.headers).has('Content-Type')).toBe(false);
    expect(calls[0]?.init.body).toBeUndefined();
  });
});

describe('createHttpClient - respuestas exitosas', () => {
  it('200 con forma invalida produce invalid-response', async () => {
    const { client } = setup(() => jsonResponse('{"id":42}'));

    const result = await client.request('GET', '/auth/me', { decode: decodeUser });

    expect(result).toEqual({ ok: false, error: { kind: 'invalid-response' } });
  });

  it('200 con cuerpo no JSON produce invalid-response', async () => {
    const { client } = setup(() => jsonResponse('<html>', 200, 'text/html'));

    const result = await client.request('GET', '/auth/me', { decode: decodeUser });

    expect(result).toEqual({ ok: false, error: { kind: 'invalid-response' } });
  });

  it('204 es exitoso sin datos', async () => {
    const { client } = setup(() => new Response(null, { status: 204 }));

    expect(await client.request('POST', '/auth/logout')).toEqual({ ok: true, data: undefined });
  });
});

describe('createHttpClient - errores', () => {
  it('409 problem+json expone status y code', async () => {
    const { client } = setup(() =>
      jsonResponse('{"status":409,"title":"x","code":"SLOT_TAKEN"}', 409, 'application/problem+json'),
    );

    const result = await client.request('POST', '/public/bookings', { body: {} });

    expect(result).toEqual({ ok: false, error: { kind: 'problem', status: 409, code: 'SLOT_TAKEN', errors: [] } });
  });

  it('400 VALIDATION_FAILED expone los errores por campo', async () => {
    const { client } = setup(() =>
      jsonResponse(
        '{"code":"VALIDATION_FAILED","errors":[{"field":"email","code":"Email"},{"bad":1}]}',
        400,
        'application/problem+json; charset=utf-8',
      ),
    );

    const result = await client.request('POST', '/x', { body: {} });

    expect(result).toEqual({
      ok: false,
      error: { kind: 'problem', status: 400, code: 'VALIDATION_FAILED', errors: [{ field: 'email', code: 'Email' }] },
    });
  });

  it('500 expone errorId', async () => {
    const { client } = setup(() =>
      jsonResponse('{"code":"INTERNAL_ERROR","errorId":"e-77"}', 500, 'application/problem+json'),
    );

    const result = await client.request('GET', '/x');

    expect(result).toMatchObject({ ok: false, error: { kind: 'problem', status: 500, errorId: 'e-77' } });
  });

  it('502 con HTML produce UNEXPECTED_RESPONSE', async () => {
    const { client } = setup(() => jsonResponse('<h1>Bad gateway</h1>', 502, 'text/html'));

    const result = await client.request('GET', '/x');

    expect(result).toEqual({ ok: false, error: { kind: 'problem', status: 502, code: 'UNEXPECTED_RESPONSE', errors: [] } });
  });

  it('problem+json sin code valido produce UNEXPECTED_RESPONSE', async () => {
    const { client } = setup(() => jsonResponse('{"code":5}', 500, 'application/problem+json'));

    const result = await client.request('GET', '/x');

    expect(result).toMatchObject({ ok: false, error: { code: 'UNEXPECTED_RESPONSE' } });
  });

  it('fetch que rechaza produce network', async () => {
    const { client } = setup(() => {
      throw new TypeError('Failed to fetch');
    });

    expect(await client.request('GET', '/x')).toEqual({ ok: false, error: { kind: 'network' } });
  });
});

describe('createHttpClient - timeout', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  function hangingFetch(call: Recorded): Promise<Response> {
    return new Promise((_resolve, reject) => {
      call.init.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
    });
  }

  it('aborta y devuelve timeout a los 15 s por defecto', async () => {
    vi.useFakeTimers();
    const { client, calls } = setup(hangingFetch);

    const pending = client.request('GET', '/x');
    await vi.advanceTimersByTimeAsync(14_999);
    expect(calls[0]?.init.signal?.aborted).toBe(false);
    await vi.advanceTimersByTimeAsync(1);

    expect(calls[0]?.init.signal?.aborted).toBe(true);
    expect(await pending).toEqual({ ok: false, error: { kind: 'timeout' } });
  });

  it('respeta timeoutMs configurable (5 s)', async () => {
    vi.useFakeTimers();
    const { client } = setup(hangingFetch, { timeoutMs: 5000 });

    const pending = client.request('GET', '/x');
    await vi.advanceTimersByTimeAsync(5000);

    expect(await pending).toEqual({ ok: false, error: { kind: 'timeout' } });
  });
});

describe('createHttpClient - sesion vencida y consola', () => {
  it('401 produce unauthorized y llama onUnauthorized una vez', async () => {
    const onUnauthorized = vi.fn();
    const { client } = setup(() => new Response(null, { status: 401 }), { onUnauthorized });

    const result = await client.request('GET', '/patients');

    expect(result).toEqual({ ok: false, error: { kind: 'unauthorized' } });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('401 con sessionProbe no llama onUnauthorized', async () => {
    const onUnauthorized = vi.fn();
    const { client } = setup(() => new Response(null, { status: 401 }), { onUnauthorized });

    const result = await client.request('GET', '/auth/me', { sessionProbe: true });

    expect(result).toEqual({ ok: false, error: { kind: 'unauthorized' } });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('un 500 con datos personales no escribe nada en la consola', async () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((method) => vi.spyOn(console, method));
    const { client } = setup(() => jsonResponse('{"code":"INTERNAL_ERROR"}', 500, 'application/problem+json'));

    await client.request('POST', '/public/bookings', { body: { dni: '30123456' } });

    for (const spy of spies) {
      expect(spy).not.toHaveBeenCalled();
    }
  });
});
