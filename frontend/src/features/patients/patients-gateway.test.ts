import { describe, expect, it, vi } from 'vitest';
import { createHttpClient } from '../../core/http/client';
import { cookieCsrfTransport } from '../../core/http/transport';
import { createPatientsGateway } from './patients-gateway';

const PROBLEM = 'application/problem+json';

interface Recorded {
  readonly url: string;
  readonly init: RequestInit;
}

function setup(respond: (call: Recorded) => Response | Promise<Response>) {
  const calls: Recorded[] = [];
  const onUnauthorized = vi.fn();
  const client = createHttpClient({
    fetch: async (url, init) => {
      const call = { url, init };
      calls.push(call);
      return respond(call);
    },
    transport: cookieCsrfTransport({ readCookies: () => 'XSRF-TOKEN=tok' }),
    onUnauthorized,
  });
  return { gateway: createPatientsGateway({ client }), calls };
}

function json(body: string, status = 200): Response {
  return new Response(body, { status, headers: { 'Content-Type': 'application/json' } });
}

function problem(status: number, code: string, errors: readonly { field: string; code: string }[] = []): Response {
  return new Response(JSON.stringify({ code, errors }), { status, headers: { 'Content-Type': PROBLEM } });
}

const SUMMARY = {
  id: 'pac-001',
  firstName: 'Julieta',
  lastName: 'Simulada',
  dni: '30.456.789',
  phone: '11 5555 0001',
  active: true,
};
const DETAIL = {
  ...SUMMARY,
  email: 'julieta@ejemplo.invalid',
  birthDate: '1992-04-18',
  address: 'Calle Falsa 123',
  source: 'MANUAL',
  createdAt: '2025-03-12T12:41:00Z',
  updatedAt: '2026-11-18T17:07:00Z',
};
const INPUT = {
  firstName: 'Julieta',
  lastName: 'Simulada',
  phone: '11 5555 0002',
  email: null,
  birthDate: '1992-04-18',
  address: null,
};

describe('patients-gateway - contrato C-10 (D2)', () => {
  it('1.2 search: GET sin término y con q codificado; resultados y forbidden', async () => {
    const { gateway, calls } = setup(({ url }) =>
      url.endsWith('?q=Julieta+Simulada+%26+Co') ? json(JSON.stringify([SUMMARY])) : json(JSON.stringify([])),
    );

    expect(await gateway.search()).toEqual({ ok: true, data: [] });
    expect(calls[0]?.url).toBe('/api/v1/patients');
    expect(calls[0]?.init.method).toBe('GET');

    expect(await gateway.search('  Julieta Simulada & Co  ')).toEqual({ ok: true, data: [SUMMARY] });
    expect(calls[1]?.url).toBe('/api/v1/patients?q=Julieta+Simulada+%26+Co');
  });

  it('1.2 triangula 403 FORBIDDEN, red, timeout e ilegible en search', async () => {
    const forbidden = setup(() => problem(403, 'FORBIDDEN'));
    expect(await forbidden.gateway.search('x')).toEqual({ ok: false, failure: { kind: 'forbidden' } });

    const network = setup(() => Promise.reject(new Error('sin red')));
    expect(await network.gateway.search()).toEqual({ ok: false, failure: { kind: 'connection' } });

    const broken = setup(() => json('{'));
    expect(await broken.gateway.search()).toEqual({ ok: false, failure: { kind: 'connection' } });

    const unexpected = setup(() => problem(500, 'INTERNAL_ERROR'));
    expect(await unexpected.gateway.search()).toEqual({ ok: false, failure: { kind: 'connection' } });
  });

  it('1.3 get: GET por id codificado y 404 → not-found sin consultar historia clínica', async () => {
    const { gateway, calls } = setup(({ url }) =>
      url === '/api/v1/patients/pac%2F001' ? json(JSON.stringify({ ...DETAIL, reviewRequired: true })) : problem(404, 'PATIENT_NOT_FOUND'),
    );

    const result = await gateway.get('pac/001');
    expect(result).toEqual({ ok: true, data: { ...DETAIL, reviewRequired: true } });
    expect(calls).toHaveLength(1);
    expect(calls[0]?.url).toBe('/api/v1/patients/pac%2F001');
    expect(calls.some((call) => call.url.includes('historia'))).toBe(false);

    expect(await gateway.get('no-existe')).toEqual({ ok: false, failure: { kind: 'not-found' } });
  });

  it('1.3 triangula red e ilegible en get', async () => {
    const network = setup(() => Promise.reject(new DOMException('abortada', 'AbortError')));
    expect(await network.gateway.get('pac-001')).toEqual({ ok: false, failure: { kind: 'connection' } });
    const broken = setup(() => json(JSON.stringify({ ...DETAIL, source: 'OTRO' })));
    expect(await broken.gateway.get('pac-001')).toEqual({ ok: false, failure: { kind: 'connection' } });
  });

  it('1.4 update: PUT con cuerpo editable, CSRF y respuesta del servidor', async () => {
    const { gateway, calls } = setup(() => json(JSON.stringify({ ...DETAIL, phone: INPUT.phone, reviewRequired: true })));

    const result = await gateway.update('pac-001', INPUT);
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.data.reviewRequired).toBe(true);
      expect(result.data.phone).toBe('11 5555 0002');
    }
    expect(calls[0]?.url).toBe('/api/v1/patients/pac-001');
    expect(calls[0]?.init.method).toBe('PUT');
    expect(new Headers(calls[0]?.init.headers).get('X-XSRF-TOKEN')).toBe('tok');
    const body = JSON.parse(String(calls[0]?.init.body)) as Record<string, unknown>;
    expect(body).toEqual(INPUT);
    expect(body).not.toHaveProperty('dni');
    expect(body).not.toHaveProperty('source');
  });

  it('1.4 triangula 400, 404 y 403 CSRF en update', async () => {
    const validation = setup(() => problem(400, 'VALIDATION_FAILED', [{ field: 'phone', code: 'NotBlank' }]));
    expect(await validation.gateway.update('pac-001', INPUT)).toEqual({
      ok: false,
      failure: { kind: 'invalid', errors: [{ field: 'phone', code: 'NotBlank' }] },
    });
    const missing = setup(() => problem(404, 'PATIENT_NOT_FOUND'));
    expect(await missing.gateway.update('pac-001', INPUT)).toEqual({ ok: false, failure: { kind: 'not-found' } });
    const stale = setup(() => problem(403, 'CSRF_REQUIRED'));
    expect(await stale.gateway.update('pac-001', INPUT)).toEqual({ ok: false, failure: { kind: 'stale-page' } });
  });

  it('1.5 deactivate: DELETE con CSRF, 204 sin cuerpo y errores D2', async () => {
    const ok = setup(() => new Response(null, { status: 204 }));
    expect(await ok.gateway.deactivate('pac-001')).toEqual({ ok: true });
    expect(ok.calls[0]?.url).toBe('/api/v1/patients/pac-001');
    expect(ok.calls[0]?.init.method).toBe('DELETE');
    expect(new Headers(ok.calls[0]?.init.headers).get('X-XSRF-TOKEN')).toBe('tok');

    const missing = setup(() => problem(404, 'PATIENT_NOT_FOUND'));
    expect(await missing.gateway.deactivate('pac-001')).toEqual({ ok: false, failure: { kind: 'not-found' } });
    const stale = setup(() => problem(403, 'CSRF_REQUIRED'));
    expect(await stale.gateway.deactivate('pac-001')).toEqual({ ok: false, failure: { kind: 'stale-page' } });
    const network = setup(() => Promise.reject(new Error('sin red')));
    expect(await network.gateway.deactivate('pac-001')).toEqual({ ok: false, failure: { kind: 'connection' } });
  });
});
