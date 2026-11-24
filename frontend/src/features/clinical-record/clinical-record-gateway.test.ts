import { describe, expect, it, vi } from 'vitest';
import { createHttpClient } from '../../core/http/client';
import { cookieCsrfTransport } from '../../core/http/transport';
import {
  createClinicalRecordGateway,
  type AnamnesisInput,
  type CorrectionInput,
  type EvolutionInput,
  type OdontogramEntryInput,
} from './clinical-record-gateway';

interface Recorded {
  readonly url: string;
  readonly init: RequestInit;
  readonly body: unknown;
}

function setup(respond: (call: Recorded) => Response) {
  const calls: Recorded[] = [];
  const onUnauthorized = vi.fn();
  const client = createHttpClient({
    fetch: async (url, init) => {
      const body: unknown = typeof init.body === 'string' ? JSON.parse(init.body) as unknown : undefined;
      const call = { url, init, body };
      calls.push(call);
      return respond(call);
    },
    transport: cookieCsrfTransport({ readCookies: () => 'XSRF-TOKEN=tok' }),
    onUnauthorized,
  });
  return { gateway: createClinicalRecordGateway({ client }), calls, onUnauthorized };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const ANAMNESIS_RESPONSE = {
  current: {
    id: 'a1',
    version: 1,
    content: { schemaVersion: 1, allergies: 'Penicilina', medications: null, medicalConditions: null, habits: null, notes: null },
    authorId: 'u1',
    createdAt: '2026-11-20T15:00:00Z',
    correctsEntryId: null,
    correctionReason: null,
    correctedByEntryId: null,
  },
  history: [],
};
const ODONTOGRAM_RESPONSE = {
  current: [{
    id: 'o1', toothCode: '16', surface: 'OCCLUSAL', condition: 'CARIES', notes: null,
    authorId: 'u1', createdAt: '2026-11-20T15:00:00Z',
    correctsEntryId: null, correctionReason: null, correctedByEntryId: null,
  }],
  history: [],
};
const EVOLUTIONS_RESPONSE = {
  entries: [{
    id: 'e1', description: 'Control', appointmentId: null, authorId: 'u1', createdAt: '2026-11-20T15:00:00Z',
    correctsEntryId: null, correctionReason: null, correctedByEntryId: null,
  }],
};
const ENTRY_RESPONSE = {
  id: 'x1', authorId: 'u1', createdAt: '2026-11-20T15:00:00Z',
  correctsEntryId: null, correctionReason: null, correctedByEntryId: null,
};

const ANAMNESIS_INPUT: AnamnesisInput = {
  allergies: 'Penicilina', medications: '', medicalConditions: '', habits: '', notes: '',
};
const ODONTOGRAM_INPUT: readonly OdontogramEntryInput[] = [
  { toothCode: '16', condition: 'CARIES', surface: 'OCCLUSAL', notes: '' },
];
const EVOLUTION_INPUT: EvolutionInput = { description: 'Control', appointmentId: null };

describe('clinical record gateway: lecturas', () => {
  it('lee anamnesis, odontograma y evoluciones con un GET exacto por sección', async () => {
    const { gateway, calls } = setup((call) => {
      if (call.url.includes('/anamnesis')) return json(ANAMNESIS_RESPONSE);
      if (call.url.includes('/odontogram')) return json(ODONTOGRAM_RESPONSE);
      return json(EVOLUTIONS_RESPONSE);
    });

    expect((await gateway.getAnamnesis('pac-001')).ok).toBe(true);
    expect((await gateway.getOdontogram('pac-001')).ok).toBe(true);
    expect((await gateway.getEvolutions('pac-001')).ok).toBe(true);
    expect(calls.map((call) => call.url)).toEqual([
      '/api/v1/patients/pac-001/clinical-record/anamnesis',
      '/api/v1/patients/pac-001/clinical-record/odontogram',
      '/api/v1/patients/pac-001/clinical-record/evolutions',
    ]);
    expect(calls.map((call) => call.init.method)).toEqual(['GET', 'GET', 'GET']);
  });

  it('rechaza una respuesta inválida sin lanzar y sin exponer su contenido', async () => {
    const { gateway } = setup(() => json({ current: 'invalido', history: [] }));

    const result = await gateway.getAnamnesis('pac-001');

    expect(result.ok).toBe(false);
  });
});

describe('clinical record gateway: escrituras', () => {
  it('envía solo los campos del contrato en cada alta', async () => {
    const { gateway, calls } = setup(() => json({ ...ENTRY_RESPONSE, content: ANAMNESIS_RESPONSE.current.content }));

    await gateway.registerAnamnesis('pac-001', ANAMNESIS_INPUT);
    await gateway.registerOdontogram('pac-001', ODONTOGRAM_INPUT);
    await gateway.registerEvolution('pac-001', EVOLUTION_INPUT);

    expect(calls.map((call) => call.url)).toEqual([
      '/api/v1/patients/pac-001/clinical-record/anamnesis',
      '/api/v1/patients/pac-001/clinical-record/odontogram',
      '/api/v1/patients/pac-001/clinical-record/evolutions',
    ]);
    expect(calls.map((call) => call.init.method)).toEqual(['POST', 'POST', 'POST']);
    expect(calls[0]?.body).toEqual(ANAMNESIS_INPUT);
    expect(calls[1]?.body).toEqual({ entries: ODONTOGRAM_INPUT });
    expect(calls[2]?.body).toEqual({ description: 'Control' });
  });

  it('envía la corrección al tipo correcto sin toothCode, surface ni appointmentId', async () => {
    const { gateway, calls } = setup(() => json(ENTRY_RESPONSE));
    const anamnesisCorrection: CorrectionInput = {
      reason: 'Corregido', content: { allergies: 'Penicilina y amoxicilina' },
    };
    const odontogramCorrection: CorrectionInput = {
      reason: 'Corregido', content: { condition: 'FILLED', notes: '' },
    };
    const evolutionCorrection: CorrectionInput = {
      reason: 'Corregido', content: { description: 'Control corregido' },
    };

    await gateway.correctEntry('pac-001', 'anamnesis', 'a1', anamnesisCorrection);
    await gateway.correctEntry('pac-001', 'odontogram', 'o1', odontogramCorrection);
    await gateway.correctEntry('pac-001', 'evolutions', 'e1', evolutionCorrection);

    expect(calls.map((call) => call.url)).toEqual([
      '/api/v1/patients/pac-001/clinical-record/anamnesis/a1/corrections',
      '/api/v1/patients/pac-001/clinical-record/odontogram/o1/corrections',
      '/api/v1/patients/pac-001/clinical-record/evolutions/e1/corrections',
    ]);
    expect(calls.every((call) => call.init.method === 'POST')).toBe(true);
    expect(calls[0]?.body).toEqual({ reason: 'Corregido', content: anamnesisCorrection.content });
    expect(calls[1]?.body).toEqual({ reason: 'Corregido', condition: 'FILLED', notes: '' });
    expect(calls[2]?.body).toEqual({ reason: 'Corregido', description: 'Control corregido' });
  });

  it('no reintenta ni agrega campos de servidor en el cuerpo', async () => {
    const { gateway, calls } = setup(() => json({ ...ENTRY_RESPONSE, extra: 'no' }));

    const result = await gateway.correctEntry('pac-001', 'evolutions', 'e1', {
      reason: 'Motivo', content: { description: 'Texto' }, appointmentId: 'c1', authorId: 'u1', createdAt: 'ahora',
    } as CorrectionInput);

    expect(result.ok).toBe(false);
    expect(calls).toHaveLength(1);
    expect(calls[0]?.body).toEqual({ reason: 'Motivo', description: 'Texto' });
  });
});
