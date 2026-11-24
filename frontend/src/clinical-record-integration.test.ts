import { describe, expect, it, vi } from 'vitest';
import type { HttpResult } from './core/http/errors';
import type { HttpClient, RequestOptions } from './core/http/client';
import type { HttpMethod } from './core/http/transport';
import type { PatientsGateway } from './features/patients/patients-gateway';
import type { PatientSummary } from './features/patients/patients-types';
import { createAnamnesisForm } from './features/clinical-record/anamnesis-form';
import { createAnamnesisSection } from './features/clinical-record/anamnesis-section';
import { createClinicalRecordGateway } from './features/clinical-record/clinical-record-gateway';
import { createClinicalRecordView } from './features/clinical-record/clinical-record-view';
import type {
  AnamnesisEntry, AnamnesisResponse, EvolutionEntry, EvolutionsResponse, OdontogramEntry, OdontogramResponse,
} from './features/clinical-record/clinical-record-types';
import { createCorrectionFlow } from './features/clinical-record/correction-flow';
import { createEvolutionsForm } from './features/clinical-record/evolutions-form';
import { createEvolutionsSection } from './features/clinical-record/evolutions-section';
import { createOdontogramForm } from './features/clinical-record/odontogram-form';
import { createOdontogramView } from './features/clinical-record/odontogram-view';

const PATIENT: PatientSummary = {
  id: 'pac-001', firstName: 'Julieta', lastName: 'Simulada', dni: '30.456.789', phone: '11 5555 0001', active: true,
};
const INSTANT = '2026-01-20T13:00:00Z';

function anamnesis(id: string, notes: string, correctedByEntryId: string | null = null): AnamnesisEntry {
  return {
    id, version: 1,
    content: { schemaVersion: 1, allergies: null, medications: null, medicalConditions: null, habits: null, notes },
    authorId: 'u1', createdAt: INSTANT,
    correctsEntryId: null, correctionReason: null, correctedByEntryId,
  };
}
function odontogram(id: string, condition: OdontogramEntry['condition']): OdontogramEntry {
  return {
    id, toothCode: '36', surface: null, condition, notes: null, authorId: 'u1', createdAt: INSTANT,
    correctsEntryId: null, correctionReason: null, correctedByEntryId: null,
  };
}
function evolution(id: string, description: string, correctedByEntryId: string | null = null): EvolutionEntry {
  return {
    id, description, appointmentId: null, authorId: 'u1', createdAt: INSTANT,
    correctsEntryId: null, correctionReason: null, correctedByEntryId,
  };
}

interface Call {
  readonly method: HttpMethod;
  readonly path: string;
  readonly body: unknown;
}

function recordingClient(handler: (method: HttpMethod, path: string, body: unknown) => HttpResult<unknown>): { client: HttpClient; calls: Call[] } {
  const calls: Call[] = [];
  const client: HttpClient = {
    request: async <T>(method: HttpMethod, path: string, options?: RequestOptions<T>): Promise<HttpResult<T>> => {
      calls.push({ method, path, body: options?.body });
      return handler(method, path, options?.body) as HttpResult<T>;
    },
  };
  return { client, calls };
}

function ok(data: unknown): HttpResult<unknown> {
  return { ok: true, data };
}

function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe('historia clínica integrada (10.1)', () => {
  it('elegir paciente, registrar, corregir y ver el historial cuenta una petición por operación', async () => {
    const anamnesisV1 = anamnesis('a-1', 'Nota original', 'a-2');
    const anamnesisV2 = anamnesis('a-2', 'Nota corregida');
    const odoV1 = odontogram('o-1', 'CARIES');
    const evoV1 = evolution('e-1', 'Evolución original', 'e-2');
    const evoV2 = evolution('e-2', 'Evolución corregida');

    const { client, calls } = recordingClient((method, path, body) => {
      if (method === 'GET' && path.endsWith('/anamnesis')) {
        return ok({ current: anamnesisV2, history: [anamnesisV2, anamnesisV1] } satisfies AnamnesisResponse);
      }
      if (method === 'GET' && path.endsWith('/odontogram')) {
        return ok({ current: [odoV1], history: [odoV1] } satisfies OdontogramResponse);
      }
      if (method === 'GET' && path.endsWith('/evolutions')) {
        return ok({ entries: [evoV2, evoV1] } satisfies EvolutionsResponse);
      }
      if (method === 'POST' && path.endsWith('/anamnesis')) { return ok(anamnesisV1); }
      if (method === 'POST' && path.endsWith('/odontogram')) { return ok({ entries: [odoV1] }); }
      if (method === 'POST' && path.endsWith('/evolutions')) { return ok(evoV1); }
      if (method === 'POST' && path.includes('/corrections')) { return ok(evoV2); }
      return { ok: false, error: { kind: 'network' } };
    });
    const gateway = createClinicalRecordGateway({ client });
    const patients = {
      search: vi.fn(async () => ({ ok: true as const, data: [PATIENT] })),
      get: vi.fn(async () => ({ ok: true as const, data: undefined })),
      update: vi.fn(async () => ({ ok: true as const, data: undefined })),
      deactivate: vi.fn(async () => ({ ok: true as const, data: undefined })),
    } as unknown as PatientsGateway;
    const navigate = vi.fn();
    const view = createClinicalRecordView({
      patients,
      getAnamnesis: (id) => gateway.getAnamnesis(id),
      getOdontogram: (id) => gateway.getOdontogram(id),
      getEvolutions: (id) => gateway.getEvolutions(id),
      registerAnamnesis: () => gateway.registerAnamnesis(PATIENT.id, {
        allergies: '', medications: '', medicalConditions: '', habits: '', notes: 'Nota nueva',
      }),
      registerOdontogram: () => gateway.registerOdontogram(PATIENT.id, [{ toothCode: '36', condition: 'CARIES', surface: 'OCCLUSAL' }]),
      registerEvolution: () => gateway.registerEvolution(PATIENT.id, { description: 'Evolución nueva' }),
      correctEntry: () => gateway.correctEntry(PATIENT.id, 'evolutions', 'e-1', { reason: 'Motivo', content: {} }),
      navigate,
      onSessionExpired: vi.fn(),
    });
    document.body.replaceChildren(view.element);
    await settle();

    // Elegir paciente: una única lectura por sección.
    [...view.element.querySelectorAll('button')][0]?.click();
    await settle();
    expect(calls.filter((call) => call.method === 'GET')).toHaveLength(3);
    expect(navigate).not.toHaveBeenCalled();

    // Alta de anamnesis: una única escritura con campos del contrato.
    const anamnesisForm = createAnamnesisForm({
      submit: (input) => gateway.registerAnamnesis(PATIENT.id, input),
    });
    document.body.append(anamnesisForm.element);
    const notes = anamnesisForm.element.querySelector<HTMLTextAreaElement>('#anamnesis-notes');
    if (notes !== null) { notes.value = 'Nota nueva'; }
    anamnesisForm.element.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await settle();
    const afterAnamnesis = calls.filter((call) => call.method === 'POST');
    expect(afterAnamnesis).toHaveLength(1);
    expect(afterAnamnesis[0]?.body).toEqual({
      allergies: '', medications: '', medicalConditions: '', habits: '', notes: 'Nota nueva',
    });

    // Registro de lote odontográfico: una única escritura.
    const odontogramForm = createOdontogramForm({
      submit: (entries) => gateway.registerOdontogram(PATIENT.id, entries), selectedTooth: '36',
    });
    document.body.append(odontogramForm.element);
    odontogramForm.element.querySelector<HTMLButtonElement>('[data-action="add"]')?.click();
    odontogramForm.element.querySelector<HTMLButtonElement>('[data-action="submit"]')?.click();
    await settle();
    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(2);

    // Alta de evolución: una única escritura.
    const evoForm = createEvolutionsForm({ submit: (input) => gateway.registerEvolution(PATIENT.id, input) });
    document.body.append(evoForm.element);
    const description = evoForm.element.querySelector<HTMLTextAreaElement>('#evo-description');
    if (description !== null) { description.value = 'Evolución nueva'; }
    evoForm.element.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await settle();
    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(3);

    // Corrección: una única escritura sin campos de servidor.
    const changed = vi.fn();
    const flow = createCorrectionFlow({
      patientId: PATIENT.id, type: 'evolutions', entryId: 'e-1',
      correct: (patientId, type, entryId, input) => gateway.correctEntry(patientId, type, entryId, input),
      onChanged: changed,
    });
    document.body.append(flow.element);
    flow.element.querySelector<HTMLButtonElement>('[data-action="open"]')?.click();
    const reason = document.querySelector<HTMLTextAreaElement>('#correction-reason');
    if (reason !== null) { reason.value = 'Motivo'; }
    document.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();
    await vi.waitFor(() => expect(changed).toHaveBeenCalledTimes(1));
    const corrections = calls.filter((call) => call.method === 'POST' && call.path.includes('/corrections'));
    expect(corrections).toHaveLength(1);
    expect(corrections[0]?.body).toEqual({ reason: 'Motivo', description: '' });
    expect(calls.every((call) => call.method === 'GET' || call.method === 'POST')).toBe(true);

    // El historial presentado marca la entrada original como corregida.
    const anamnesisSection = createAnamnesisSection({ load: () => gateway.getAnamnesis(PATIENT.id).then((r) => (r.ok ? r.data : { current: null, history: [] })) });
    await anamnesisSection.reload();
    document.body.append(anamnesisSection.element);
    expect(anamnesisSection.element.querySelector('.clinical-entry__corrected')?.textContent).toContain('Corregida');

    const evolutionsSection = createEvolutionsSection({ load: () => gateway.getEvolutions(PATIENT.id).then((r) => (r.ok ? r.data : { entries: [] })) });
    await evolutionsSection.reload();
    document.body.append(evolutionsSection.element);
    expect(evolutionsSection.element.querySelector('.entry.corrected')?.textContent).toContain('Corregida');

    const odontogramView = createOdontogramView({
      data: { current: [odoV1], history: [odoV1] }, onToggle: vi.fn(),
    });
    document.body.append(odontogramView.element);
    expect(odontogramView.element.querySelectorAll('button.tooth')).toHaveLength(52);
  });

  it('una escritura rechazada no duplica la petición y conserva el mensaje propio', async () => {
    let firstWrite = true;
    const { client, calls } = recordingClient((method, path) => {
      if (method === 'GET' && path.endsWith('/evolutions')) { return ok({ entries: [] } satisfies EvolutionsResponse); }
      if (method === 'POST' && path.endsWith('/evolutions')) {
        if (firstWrite) {
          firstWrite = false;
          return { ok: false, error: { kind: 'network' } };
        }
        return ok(evolution('e-1', 'Evolución nueva'));
      }
      return { ok: false, error: { kind: 'network' } };
    });
    const gateway = createClinicalRecordGateway({ client });
    const form = createEvolutionsForm({ submit: (input) => gateway.registerEvolution(PATIENT.id, input) });
    document.body.replaceChildren(form.element);
    const description = form.element.querySelector<HTMLTextAreaElement>('#evo-description');
    if (description !== null) { description.value = 'Evolución nueva'; }

    const button = form.element.querySelector<HTMLButtonElement>('button[type="submit"]');
    button?.click();
    button?.click();
    await settle();

    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(1);
    expect(form.element.querySelector('[role="alert"]')?.textContent).toContain('No pudimos completar la operación.');
    expect(description?.value).toBe('Evolución nueva');
  });
});
