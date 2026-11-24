import { afterEach, describe, expect, it, vi } from 'vitest';
import { createClinicalRecordView, type ClinicalRecordViewDeps } from './features/clinical-record/clinical-record-view';
import type { PatientsGateway } from './features/patients/patients-gateway';
import { bootstrap, type App } from './main';

/**
 * B-1 (C-22): la ruta real `/historia-clinica` debe montar los componentes clínicos.
 * Este archivo exercise bootstrap → router → routes → vista, con `fetch` simulado;
 * no arma las secciones a mano como `clinical-record-integration.test.ts`.
 * Todos los datos son ficticios (PACIENTE DEMO-C22) y viven solo en memoria.
 */

const ME = '{"userId":"u-c22","role":"DENTIST","mustChangePassword":false,"fullName":"Dra. Ficticia"}';
const PATIENT = {
  id: 'pac-c22', firstName: 'Julieta', lastName: 'Ficticia', dni: 'C22-DEMO-0001', phone: '11 5555 0101', active: true,
};
const NOW = '2026-11-20T13:00:00Z'; // 20/11/2026 10:00 en America/Argentina/Buenos_Aires
const BEFORE = '2026-11-18T14:40:00Z'; // 18/11/2026 11:40 en America/Argentina/Buenos_Aires

const ANAMNESIS_CURRENT = {
  id: 'an-2', version: 2,
  content: {
    schemaVersion: 1, allergies: 'Penicilina ficticia', medications: null,
    medicalConditions: null, habits: null, notes: 'Nota vigente C22 <b>literal</b>',
  },
  authorId: 'u-c22', createdAt: NOW,
  correctsEntryId: 'an-1', correctionReason: 'Se aclara la nota', correctedByEntryId: null,
};
const ANAMNESIS_OLD = {
  id: 'an-1', version: 1,
  content: {
    schemaVersion: 1, allergies: null, medications: null,
    medicalConditions: null, habits: null, notes: 'Nota original C22',
  },
  authorId: 'u-c22', createdAt: BEFORE,
  correctsEntryId: null, correctionReason: null, correctedByEntryId: 'an-2',
};
const ODO_CURRENT = {
  id: 'od-2', toothCode: '36', surface: 'OCCLUSAL', condition: 'FILLED', notes: 'Obturación ficticia',
  authorId: 'u-c22', createdAt: NOW,
  correctsEntryId: 'od-1', correctionReason: 'Tratamiento registrado', correctedByEntryId: null,
};
const ODO_OTHER = (id: string, toothCode: string, condition: string): Record<string, unknown> => ({
  id, toothCode, surface: null, condition, notes: null,
  authorId: 'u-c22', createdAt: BEFORE,
  correctsEntryId: null, correctionReason: null, correctedByEntryId: null,
});
const EVO_CURRENT = {
  id: 'ev-2', description: 'Control ficticio C22', appointmentId: 'turno-c22',
  authorId: 'u-c22', createdAt: NOW,
  correctsEntryId: 'ev-1', correctionReason: 'Se aclara la pieza', correctedByEntryId: null,
};
const EVO_OLD = {
  id: 'ev-1', description: 'Descripción original C22', appointmentId: null,
  authorId: 'u-c22', createdAt: BEFORE,
  correctsEntryId: null, correctionReason: null, correctedByEntryId: 'ev-2',
};

interface FetchCall {
  readonly url: string;
  readonly init: RequestInit;
}

type Respond = (url: string, init: RequestInit) => Response | Promise<Response>;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function fullRespond(): Respond {
  return (url) => {
    if (url === '/api/v1/auth/me') { return json(JSON.parse(ME)); }
    if (url === '/api/v1/patients') { return json([PATIENT]); }
    if (url.endsWith('/anamnesis')) {
      return json({ current: ANAMNESIS_CURRENT, history: [ANAMNESIS_CURRENT, ANAMNESIS_OLD] });
    }
    if (url.endsWith('/odontogram')) {
      return json({
        current: [ODO_CURRENT, ODO_OTHER('od-3', '11', 'HEALTHY'), ODO_OTHER('od-4', '12', 'MISSING'),
          ODO_OTHER('od-5', '21', 'CROWN'), ODO_OTHER('od-6', '47', 'ROOT_CANAL')],
        history: [ODO_CURRENT],
      });
    }
    if (url.endsWith('/evolutions')) { return json({ entries: [EVO_CURRENT, EVO_OLD] }); }
    return new Response(null, { status: 404 });
  };
}

/** Escrituras válidas para que la ruta real pueda completar una confirmación. */
function writesRespond(overrides: Readonly<Record<string, Respond>> = {}): Respond {
  const base = fullRespond();
  return (url, init) => {
    if ((init.method ?? 'GET').toUpperCase() !== 'POST') { return base(url, init); }
    const override = Object.entries(overrides).find(([fragment]) => url.includes(fragment));
    if (override !== undefined) { return override[1](url, init); }
    if (url.includes('/anamnesis')) { return json(ANAMNESIS_CURRENT, 201); }
    if (url.includes('/odontogram/')) { return json(ODO_CURRENT, 201); }
    if (url.includes('/odontogram')) { return json({ entries: [ODO_CURRENT] }, 201); }
    if (url.includes('/evolutions')) { return json(EVO_CURRENT, 201); }
    return new Response(null, { status: 404 });
  };
}

function posts(): readonly FetchCall[] {
  return calls.filter((call) => (call.init.method ?? 'GET').toUpperCase() === 'POST');
}

function bodyOf(call: FetchCall): Record<string, unknown> {
  return typeof call.init.body === 'string' ? JSON.parse(call.init.body) as Record<string, unknown> : {};
}

function submitForm(form: HTMLFormElement): void {
  form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

function sectionOf(content: HTMLElement, selector: string): HTMLElement | undefined {
  const node = content.querySelector(selector);
  return node === null ? undefined : node.closest<HTMLElement>('.clinical-section') ?? undefined;
}

function correctButton(content: HTMLElement, section: 'anamnesis' | 'odontogram' | 'evolutions'): HTMLButtonElement | undefined {
  const host = sectionOf(content, section === 'odontogram' ? '.odo' : `.${section}-section`) ?? content;
  return [...host.querySelectorAll<HTMLButtonElement>('button[data-action="open"]')]
    .find((button) => (button.textContent ?? '').includes('Corregir'));
}

function odontogramCorrectionButtons(content: HTMLElement): readonly HTMLButtonElement[] {
  const section = sectionOf(content, '.odo');
  if (section === undefined) { return []; }
  return [...section.querySelectorAll<HTMLButtonElement>('button[data-action="open"]')]
    .filter((button) => (button.textContent ?? '').includes('Corregir'));
}

function retryIn(content: HTMLElement, selector: string): HTMLButtonElement | undefined {
  return sectionOf(content, selector)?.querySelector<HTMLButtonElement>('[data-retry]') ?? undefined;
}

function readsFor(fragment: string): readonly FetchCall[] {
  return calls.filter((call) =>
    call.url.includes('/clinical-record/') && call.url.includes(fragment) &&
    (call.init.method ?? 'GET').toUpperCase() === 'GET');
}

async function openCorrection(
  content: HTMLElement, section: 'anamnesis' | 'odontogram' | 'evolutions',
): Promise<HTMLElement> {
  correctButton(content, section)?.click();
  const dialog = document.querySelector<HTMLElement>('[role="dialog"]');
  await vi.waitFor(() => expect(dialog?.querySelector('#correction-reason')).not.toBeNull());
  return dialog as HTMLElement;
}

function forbiddenCorrectionFields(body: Record<string, unknown>): void {
  const serialized = JSON.stringify(body);
  for (const field of ['authorId', 'createdAt', 'clinicalRecordId', '"id"']) {
    expect(serialized).not.toContain(field);
  }
}

function odontogramStatusOf(content: HTMLElement): HTMLElement | undefined {
  return sectionOf(content, '.odo')?.querySelector<HTMLElement>('[role="status"]') ?? undefined;
}

interface CorrectionValues {
  readonly reason: string;
  readonly content?: string;
  readonly condition?: string;
}

function fillAndSubmitCorrection(dialog: HTMLElement, values: CorrectionValues): void {
  const reason = dialog.querySelector<HTMLTextAreaElement>('#correction-reason');
  if (reason !== null) { reason.value = values.reason; }
  const content = dialog.querySelector<HTMLTextAreaElement>('#correction-content');
  if (content !== null && values.content !== undefined) { content.value = values.content; }
  const condition = dialog.querySelector<HTMLSelectElement>('#correction-condition');
  if (condition !== null && values.condition !== undefined) { condition.value = values.condition; }
  dialog.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();
}

let app: App | undefined;
let calls: FetchCall[];

function start(respond: Respond): App {
  history.replaceState(null, '', '/historia-clinica');
  document.body.replaceChildren();
  const container = document.createElement('div');
  container.id = 'app';
  document.body.append(container);
  calls = [];
  app = bootstrap({
    doc: document,
    win: window,
    fetch: async (url, init) => {
      calls.push({ url, init });
      return respond(url, init);
    },
  });
  return app;
}

async function selectDemoPatient(): Promise<HTMLElement> {
  const { ready } = app as App;
  await ready;
  await vi.waitFor(() => {
    const option = [...document.querySelectorAll<HTMLButtonElement>('.clinical-picker__option')]
      .find((button) => button.textContent?.includes('Julieta Ficticia'));
    expect(option).toBeDefined();
  });
  const option = [...document.querySelectorAll<HTMLButtonElement>('.clinical-picker__option')]
    .find((button) => button.textContent?.includes('Julieta Ficticia'));
  expect(option).toBeDefined();
  option?.click();
  return document.querySelector<HTMLElement>('.clinical-record-view') as HTMLElement;
}

afterEach(() => {
  app?.router.stop();
  app = undefined;
  document.body.replaceChildren();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('/historia-clinica monta los componentes reales (B-1)', () => {
  it('con datos en la API renderiza anamnesis, 52 piezas FDI, evoluciones y el diálogo de corrección', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {});
    start(fullRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.textContent).toContain('Nota vigente C22'));
    expect(content.querySelector('.anamnesis-current')?.textContent).toContain('Nota vigente C22');
    expect(content.querySelector('.anamnesis-history .clinical-entry__corrected')?.textContent).toContain('Corregida');
    expect(content.textContent).toContain('Se aclara la nota');

    expect(content.querySelectorAll('button.tooth')).toHaveLength(52);
    expect(content.querySelectorAll('[role="group"]')).toHaveLength(8);
    expect(content.querySelectorAll('button.tooth[aria-label]')).toHaveLength(52);
    expect(content.querySelectorAll('button.tooth[aria-pressed]')).toHaveLength(52);
    expect(content.querySelector('button.tooth[data-tooth="36"]')?.getAttribute('aria-label'))
      .toContain('Pieza 36, obturado, cara oclusal');
    expect(content.querySelector('button.tooth[data-tooth="36"]')?.getAttribute('aria-pressed')).toBe('true');

    expect(content.querySelectorAll('.entry-list .entry')).toHaveLength(2);
    expect(content.textContent).toContain('Autor u-c22 · 20/11/2026 10:00');
    expect(content.textContent).toContain('Turno asociado: turno-c22');
    expect(content.querySelector('.entry.corrected')?.textContent).toContain('Corregida');

    expect(content.querySelectorAll('form')).toHaveLength(3);

    const open = [...content.querySelectorAll<HTMLButtonElement>('button[data-action="open"]')]
      .find((button) => button.textContent?.includes('Corregir'));
    expect(open).not.toBeUndefined();
    open?.click();
    const dialog = document.querySelector<HTMLElement>('[role="dialog"]');
    expect(dialog?.querySelector('#correction-reason')).not.toBeNull();
    dialog?.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();
    expect(dialog?.textContent).toContain('El motivo es obligatorio.');

    const clinicalReads = calls.filter((call) => call.url.includes('/clinical-record/') && call.init.method === 'GET');
    expect(clinicalReads).toHaveLength(3);
    expect(location.pathname + location.search).toBe('/historia-clinica');
    expect(document.title).not.toContain('C22-DEMO-0001');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect([...document.querySelectorAll('[href]')].some((el) => (el.getAttribute('href') ?? '').includes('pac-c22'))).toBe(false);
    expect(log).not.toHaveBeenCalled();
    log.mockRestore();
  });

  it('con arreglos vacíos muestra estados vacíos Verdaderos sin afirmar datos clínicos', async () => {
    const respond = fullRespond();
    start((url, init) => {
      if (url.endsWith('/anamnesis')) { return json({ current: null, history: [] }); }
      if (url.endsWith('/odontogram')) { return json({ current: [], history: [] }); }
      if (url.endsWith('/evolutions')) { return json({ entries: [] }); }
      return respond(url, init);
    });
    const content = await selectDemoPatient();

    await vi.waitFor(() => expect(content.textContent).toContain('Sin evoluciones registradas.'));
    expect(content.querySelector('.anamnesis-current')?.textContent).toContain('Sin anamnesis registrada.');
    expect(content.querySelectorAll('button.tooth')).toHaveLength(52);
    expect([...content.querySelectorAll('button.tooth')].every((tooth) =>
      (tooth.getAttribute('aria-label') ?? '').includes('sin registro'))).toBe(true);
    expect(content.textContent).not.toContain('Nota vigente C22');
    expect(content.textContent).not.toContain('Control ficticio C22');
    expect(content.textContent).not.toContain('Obturación ficticia');
    expect(content.querySelectorAll('form')).toHaveLength(3);
  });

  it('con 401 en la carga muestra sesión vencida, redirige sin guardar borradores ni datos', async () => {
    const respond = fullRespond();
    start((url, init) => {
      if (url.includes('/clinical-record/')) { return new Response(null, { status: 401 }); }
      return respond(url, init);
    });
    await selectDemoPatient();

    await vi.waitFor(() => expect(location.pathname + location.search).toBe('/login?next=%2Fhistoria-clinica'));
    await vi.waitFor(() =>
      expect(document.querySelector('[role="status"]')?.textContent).toBe('Tu sesión venció. Ingresá de nuevo.'));
    expect(document.querySelector('.clinical-record-view')).toBeNull();
    expect(document.body.textContent).not.toContain('Nota vigente C22');
    expect(document.body.textContent).not.toContain('Control ficticio C22');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('ofrece corrección solo sobre entradas vigentes, sin controles de edición ni borrado', async () => {
    start(fullRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    const correctionButtons = [...content.querySelectorAll<HTMLButtonElement>('button')]
      .filter((button) => (button.textContent ?? '').includes('Corregir'));
    expect(correctionButtons.length).toBeGreaterThan(0);
    expect(correctionButtons.every((button) => button.closest('.entry, .anamnesis-current, .correction-flow') !== null)).toBe(true);
    expect([...content.querySelectorAll('button, a, [role="button"]')].some((el) =>
      /editar|borrar|eliminar|modificar/i.test(el.textContent ?? ''))).toBe(false);
    expect(calls.some((call) => ['PUT', 'PATCH', 'DELETE'].includes(call.init.method ?? 'GET'))).toBe(false);
  });

  it('MA-1: confirmar el alta de anamnesis emite un único POST real al contrato', async () => {
    start(writesRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelector('form.anamnesis-form')).not.toBeNull());

    const form = content.querySelector<HTMLFormElement>('form.anamnesis-form');
    const allergies = form?.querySelector<HTMLTextAreaElement>('#anamnesis-allergies');
    if (allergies !== null && allergies !== undefined) { allergies.value = 'Alergia MA1 ficticia'; }
    submitForm(form as HTMLFormElement);

    await vi.waitFor(() => expect(posts()).toHaveLength(1));
    expect(posts()[0]?.url).toBe('/api/v1/patients/pac-c22/clinical-record/anamnesis');
    expect(posts()[0]?.init.method).toBe('POST');
    expect(bodyOf(posts()[0] as FetchCall)).toEqual({
      allergies: 'Alergia MA1 ficticia', medications: '', medicalConditions: '', habits: '', notes: '',
    });
    expect(content.textContent).not.toContain('No pudimos completar la operación');
    expect(content.textContent).not.toContain('No se pudo');
  });

  it('MA-1: confirmar el alta de evolución emite un único POST real sin failure copy', async () => {
    start(writesRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelector('.evolutions-form form')).not.toBeNull());

    const form = content.querySelector<HTMLFormElement>('.evolutions-form form');
    const description = form?.querySelector<HTMLTextAreaElement>('#evo-description');
    if (description !== null && description !== undefined) { description.value = 'Control MA1 ficticio'; }
    submitForm(form as HTMLFormElement);

    await vi.waitFor(() => expect(posts()).toHaveLength(1));
    expect(posts()[0]?.url).toBe('/api/v1/patients/pac-c22/clinical-record/evolutions');
    expect(posts()[0]?.init.method).toBe('POST');
    expect(bodyOf(posts()[0] as FetchCall)).toEqual({ description: 'Control MA1 ficticio' });
    expect(content.querySelector('.evolutions-form [role="alert"]')).toBeNull();
  });

  it('MA-1: confirmar una corrección emite un único POST real a la ruta del tipo', async () => {
    start(writesRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    correctButton(content, 'anamnesis')?.click();
    const dialog = document.querySelector<HTMLElement>('[role="dialog"]');
    await vi.waitFor(() => expect(dialog?.querySelector('#correction-reason')).not.toBeNull());
    const reason = dialog?.querySelector<HTMLTextAreaElement>('#correction-reason');
    if (reason !== null && reason !== undefined) { reason.value = 'Motivo MA1'; }
    const correctedContent = dialog?.querySelector<HTMLTextAreaElement>('#correction-content');
    if (correctedContent !== null && correctedContent !== undefined) { correctedContent.value = 'Contenido MA1 corregido'; }
    dialog?.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();

    await vi.waitFor(() => expect(posts()).toHaveLength(1));
    expect(posts()[0]?.url).toBe('/api/v1/patients/pac-c22/clinical-record/anamnesis/an-2/corrections');
    expect(posts()[0]?.init.method).toBe('POST');
    expect(bodyOf(posts()[0] as FetchCall)).toEqual({
      reason: 'Motivo MA1', content: { notes: 'Contenido MA1 corregido' },
    });
    expect(document.body.textContent).not.toContain('No pudimos completar la operación');
  });

  it('MA-1 TRIANGULATE: un 400 con detalle clínico conserva el borrador, muestra copy propio y no reintenta', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {});
    const MARK = 'DETALLE-CLINICO-MA1';
    start(writesRespond({
      '/evolutions': () => new Response(JSON.stringify({
        status: 400, code: 'VALIDATION_FAILED', title: 'Error interno', detail: MARK,
      }), { status: 400, headers: { 'Content-Type': 'application/problem+json' } }),
    }));
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelector('.evolutions-form form')).not.toBeNull());

    const form = content.querySelector<HTMLFormElement>('.evolutions-form form');
    const description = form?.querySelector<HTMLTextAreaElement>('#evo-description');
    if (description !== null && description !== undefined) { description.value = 'Borrador MA1 conservado'; }
    submitForm(form as HTMLFormElement);

    await vi.waitFor(() => expect(posts()).toHaveLength(1));
    await vi.waitFor(() => expect(content.querySelector('.evolutions-form [role="alert"]')).not.toBeNull());
    const alert = content.querySelector('.evolutions-form [role="alert"]');
    expect(alert?.textContent).toContain('Revisá el campo señalado.');
    expect(alert?.textContent).not.toContain(MARK);
    expect(alert?.textContent).not.toContain('Error interno');
    expect(description?.value).toBe('Borrador MA1 conservado');
    expect(document.body.textContent).not.toContain('Guardada correctamente');
    expect(document.body.textContent).not.toContain(MARK);
    expect(document.title).not.toContain(MARK);
    expect(log).not.toHaveBeenCalled();
    log.mockRestore();
  });

  it('MA-1 TRIANGULATE: doble confirmación con respuesta pendiente envía un único POST', async () => {
    let release: (() => void) | undefined;
    const pending = new Promise<Response>((resolve) => {
      release = () => resolve(json(EVO_CURRENT, 201));
    });
    start(writesRespond({ '/evolutions': () => pending }));
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelector('.evolutions-form form')).not.toBeNull());

    const form = content.querySelector<HTMLFormElement>('.evolutions-form form');
    const description = form?.querySelector<HTMLTextAreaElement>('#evo-description');
    const button = form?.querySelector<HTMLButtonElement>('[type="submit"]');
    if (description !== null && description !== undefined) { description.value = 'Doble envío MA1'; }
    button?.click();
    button?.click();

    expect(posts()).toHaveLength(1);
    expect(button?.hasAttribute('disabled')).toBe(true);
    release?.();
    await vi.waitFor(() => expect(button?.hasAttribute('disabled')).toBe(false));
    expect(posts()).toHaveLength(1);
  });

  it('MA-1 TRIANGULATE: el lote del odontograma llega con su cuerpo y una sola petición', async () => {
    start(writesRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelector('.odontogram-form form')).not.toBeNull());

    const form = content.querySelector<HTMLFormElement>('.odontogram-form form');
    form?.querySelector<HTMLButtonElement>('[data-action="add"]')?.click();
    form?.querySelector<HTMLButtonElement>('[data-action="submit"]')?.click();

    await vi.waitFor(() => expect(posts()).toHaveLength(1));
    expect(posts()[0]?.url).toBe('/api/v1/patients/pac-c22/clinical-record/odontogram');
    expect(bodyOf(posts()[0] as FetchCall)).toEqual({
      entries: [{ toothCode: '36', condition: 'HEALTHY' }],
    });
  });

  it('MA-2: el diálogo de anamnesis expone contenido corregido y lo envía sin campos de servidor', async () => {
    start(writesRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    const dialog = await openCorrection(content, 'anamnesis');
    const field = dialog.querySelector<HTMLTextAreaElement>('#correction-content');
    expect(field).not.toBeNull();
    if (field !== null) { field.value = 'Nota corregida MA2'; }
    const reason = dialog.querySelector<HTMLTextAreaElement>('#correction-reason');
    if (reason !== null) { reason.value = 'Motivo MA2'; }
    dialog.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();

    await vi.waitFor(() => expect(posts()).toHaveLength(1));
    const body = bodyOf(posts()[0] as FetchCall);
    expect(posts()[0]?.url).toBe('/api/v1/patients/pac-c22/clinical-record/anamnesis/an-2/corrections');
    expect(body).toEqual({ reason: 'Motivo MA2', content: { notes: 'Nota corregida MA2' } });
    forbiddenCorrectionFields(body);
  });

  it('MA-2: en evoluciones el contenido corregido viaja como description, sin turno ni autoría', async () => {
    start(writesRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    const dialog = await openCorrection(content, 'evolutions');
    const field = dialog.querySelector<HTMLTextAreaElement>('#correction-content');
    expect(field?.value).toContain('Control ficticio C22');
    if (field !== null) { field.value = 'Descripción corregida MA2'; }
    const reason = dialog.querySelector<HTMLTextAreaElement>('#correction-reason');
    if (reason !== null) { reason.value = 'Motivo evolución MA2'; }
    dialog.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();

    await vi.waitFor(() => expect(posts()).toHaveLength(1));
    const body = bodyOf(posts()[0] as FetchCall);
    expect(posts()[0]?.url).toBe('/api/v1/patients/pac-c22/clinical-record/evolutions/ev-2/corrections');
    expect(body).toEqual({ reason: 'Motivo evolución MA2', description: 'Descripción corregida MA2' });
    forbiddenCorrectionFields(body);
    expect(JSON.stringify(body)).not.toContain('appointmentId');
  });

  it('MA-2 TRIANGULATE: un motivo de más de 500 caracteres no envía ni se repite', async () => {
    start(writesRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    const dialog = await openCorrection(content, 'anamnesis');
    const reason = dialog.querySelector<HTMLTextAreaElement>('#correction-reason');
    const field = dialog.querySelector<HTMLTextAreaElement>('#correction-content');
    const longReason = 'R'.repeat(501);
    if (reason !== null) { reason.value = longReason; }
    if (field !== null) { field.value = 'Contenido válido MA2'; }
    dialog.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();

    expect(posts()).toHaveLength(0);
    expect(dialog.querySelector('[role="alert"]')?.textContent).toContain('500');
    expect(dialog.textContent).not.toContain(longReason);
  });

  it('MA-2 TRIANGULATE: un 400 con detalle clínico conserva el borrador y no hace eco', async () => {
    const MARK = 'SECRETO-CLINICO-MA2';
    start(writesRespond({
      '/corrections': () => new Response(JSON.stringify({
        status: 400, code: 'VALIDATION_FAILED', title: 'Error interno', detail: MARK,
      }), { status: 400, headers: { 'Content-Type': 'application/problem+json' } }),
    }));
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    const dialog = await openCorrection(content, 'anamnesis');
    const reason = dialog.querySelector<HTMLTextAreaElement>('#correction-reason');
    const field = dialog.querySelector<HTMLTextAreaElement>('#correction-content');
    if (reason !== null) { reason.value = 'Motivo rechazado MA2'; }
    if (field !== null) { field.value = 'Contenido rechazado MA2'; }
    dialog.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();

    await vi.waitFor(() => expect(posts()).toHaveLength(1));
    await vi.waitFor(() => expect(dialog.querySelector('[role="alert"]')).not.toBeNull());
    const alert = dialog.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Revisá el campo señalado.');
    expect(alert?.textContent).not.toContain('Error interno');
    expect(alert?.textContent).not.toContain(MARK);
    expect(reason?.value).toBe('Motivo rechazado MA2');
    expect(field?.value).toBe('Contenido rechazado MA2');
    expect(document.body.textContent).not.toContain(MARK);
    expect(document.title).not.toContain(MARK);
  });

  it('MA-2 TRIANGULATE: Escape cierra sin enviar y el doble clic confirma una sola vez', async () => {
    let release: (() => void) | undefined;
    const pending = new Promise<Response>((resolve) => {
      release = () => resolve(json(ANAMNESIS_CURRENT, 201));
    });
    start(writesRespond({ '/corrections': () => pending }));
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    const dismissed = await openCorrection(content, 'anamnesis');
    const draft = dismissed.querySelector<HTMLTextAreaElement>('#correction-content');
    if (draft !== null) { draft.value = 'Borrador sin enviar MA2'; }
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(posts()).toHaveLength(0);
    expect(document.querySelector('[role="dialog"]')).toBeNull();

    const dialog = await openCorrection(content, 'anamnesis');
    const reason = dialog.querySelector<HTMLTextAreaElement>('#correction-reason');
    const confirm = dialog.querySelector<HTMLButtonElement>('[data-action="confirm"]');
    if (reason !== null) { reason.value = 'Motivo doble clic MA2'; }
    confirm?.click();
    confirm?.click();

    expect(posts()).toHaveLength(1);
    expect(confirm?.hasAttribute('disabled')).toBe(true);
    release?.();
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(posts()).toHaveLength(1);
  });

  it('MA-3: el odontograma ofrece Corregir solo en la entrada vigente y fija pieza y cara', async () => {
    start(writesRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    expect(odontogramCorrectionButtons(content)).toHaveLength(1);
    const dialog = await openCorrection(content, 'odontogram');
    const condition = dialog.querySelector<HTMLSelectElement>('#correction-condition');
    const notes = dialog.querySelector<HTMLTextAreaElement>('#correction-content');
    expect(condition).not.toBeNull();
    expect(condition?.value).toBe('FILLED');
    expect(notes?.value).toBe('Obturación ficticia');
    expect(dialog.querySelector('[name="toothCode"], [data-field="toothCode"]')).toBeNull();
    expect(dialog.querySelector('[name="surface"], [data-field="surface"]')).toBeNull();
  });

  it('MA-3: corregir el odontograma envía motivo y contenido al endpoint del tipo', async () => {
    start(writesRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    const dialog = await openCorrection(content, 'odontogram');
    const condition = dialog.querySelector<HTMLSelectElement>('#correction-condition');
    const notes = dialog.querySelector<HTMLTextAreaElement>('#correction-content');
    const reason = dialog.querySelector<HTMLTextAreaElement>('#correction-reason');
    if (condition !== null) { condition.value = 'CARIES'; }
    if (notes !== null) { notes.value = 'Nota corregida MA3'; }
    if (reason !== null) { reason.value = 'Motivo odontograma MA3'; }
    dialog.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();

    await vi.waitFor(() => expect(posts()).toHaveLength(1));
    expect(posts()[0]?.url).toBe('/api/v1/patients/pac-c22/clinical-record/odontogram/od-2/corrections');
    expect(posts()[0]?.init.method).toBe('POST');
    const body = bodyOf(posts()[0] as FetchCall);
    expect(body).toEqual({
      reason: 'Motivo odontograma MA3',
      condition: 'CARIES',
      notes: 'Nota corregida MA3',
    });
    expect(JSON.stringify(body)).not.toContain('toothCode');
    expect(JSON.stringify(body)).not.toContain('surface');
    forbiddenCorrectionFields(body);
  });

  it('MA-3 TRIANGULATE: una entrada corregida del odontograma no ofrece Corregir', async () => {
    const respond = fullRespond();
    const corrected = { ...ODO_CURRENT, id: 'od-corregida', correctedByEntryId: 'od-nueva' };
    start((url, init) => {
      if (url.endsWith('/odontogram')) {
        return json({ current: [corrected], history: [corrected] });
      }
      return respond(url, init);
    });
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    expect(odontogramCorrectionButtons(content)).toHaveLength(0);
  });

  it('MA-3 TRIANGULATE: otro estado corregido viaja sin pieza ni cara y sin refetch', async () => {
    start(writesRespond());
    const readsBefore = calls.filter((call) => call.url.includes('/clinical-record/') && call.init.method === 'GET').length;
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    const dialog = await openCorrection(content, 'odontogram');
    const condition = dialog.querySelector<HTMLSelectElement>('#correction-condition');
    const reason = dialog.querySelector<HTMLTextAreaElement>('#correction-reason');
    if (condition !== null) { condition.value = 'MISSING'; }
    if (reason !== null) { reason.value = 'Motivo alternativo MA3'; }
    dialog.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();

    await vi.waitFor(() => expect(posts()).toHaveLength(1));
    expect(bodyOf(posts()[0] as FetchCall)).toEqual({
      reason: 'Motivo alternativo MA3',
      condition: 'MISSING',
      notes: 'Obturación ficticia',
    });
    expect(calls.filter((call) => call.url.includes('/clinical-record/') && call.init.method === 'GET'))
      .toHaveLength(readsBefore + 3);
  });

  it('MA-4: reintentar anamnesis hace una sola lectura nueva y reemplaza el error', async () => {
    let attempts = 0;
    const respond = fullRespond();
    start((url, init) => {
      if (url.includes('/clinical-record/anamnesis') && (init.method ?? 'GET') === 'GET') {
        attempts += 1;
        if (attempts === 1) { return json({ status: 500, code: 'UNEXPECTED' }, 500); }
      }
      return respond(url, init);
    });
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelector('.anamnesis-section [role="alert"]')).not.toBeNull());

    expect(content.textContent).not.toContain('Nota vigente C22');
    expect(readsFor('/anamnesis')).toHaveLength(1);
    const retry = retryIn(content, '.anamnesis-section');
    expect(retry).toBeDefined();
    retry?.click();

    await vi.waitFor(() => expect(content.textContent).toContain('Nota vigente C22'));
    expect(readsFor('/anamnesis')).toHaveLength(2);
    expect(readsFor('/odontogram')).toHaveLength(1);
    expect(readsFor('/evolutions')).toHaveLength(1);
    expect(content.querySelector('.anamnesis-section [role="alert"]')).toBeNull();
    expect(content.textContent).not.toContain('No pudimos cargar la anamnesis.');
  });

  it('MA-4: reintentar evoluciones hace una sola lectura nueva sin datos parciales', async () => {
    let attempts = 0;
    const respond = fullRespond();
    start((url, init) => {
      if (url.includes('/clinical-record/evolutions') && (init.method ?? 'GET') === 'GET') {
        attempts += 1;
        if (attempts === 1) { return json({ status: 500, code: 'UNEXPECTED' }, 500); }
      }
      return respond(url, init);
    });
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelector('.evolutions-section [role="alert"]')).not.toBeNull());

    expect(content.textContent).not.toContain('Control ficticio C22');
    expect(readsFor('/evolutions')).toHaveLength(1);
    retryIn(content, '.evolutions-section')?.click();

    await vi.waitFor(() => expect(content.textContent).toContain('Control ficticio C22'));
    expect(readsFor('/evolutions')).toHaveLength(2);
    expect(readsFor('/anamnesis')).toHaveLength(1);
    expect(readsFor('/odontogram')).toHaveLength(1);
    expect(content.querySelector('.evolutions-section [role="alert"]')).toBeNull();
  });

  it('MA-4: reintentar el odontograma muestra el error y luego la lectura válida', async () => {
    let attempts = 0;
    const respond = fullRespond();
    start((url, init) => {
      if (url.includes('/clinical-record/odontogram') && (init.method ?? 'GET') === 'GET') {
        attempts += 1;
        if (attempts === 1) { return json({ status: 500, code: 'UNEXPECTED' }, 500); }
      }
      return respond(url, init);
    });
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelector('.clinical-section [role="alert"]')).not.toBeNull());

    expect(content.querySelectorAll('button.tooth')).toHaveLength(0);
    expect(readsFor('/odontogram')).toHaveLength(1);
    const section = [...content.querySelectorAll<HTMLElement>('.clinical-section')]
      .find((node) => node.textContent?.includes('Odontograma') === true);
    expect(section?.querySelector('[data-retry]')).not.toBeNull();
    section?.querySelector<HTMLButtonElement>('[data-retry]')?.click();

    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));
    expect(readsFor('/odontogram')).toHaveLength(2);
    expect(section?.querySelector('[role="alert"]')).toBeNull();
    expect(content.querySelector('button.tooth[data-tooth="36"]')?.getAttribute('aria-label'))
      .toContain('obturado');
  });

  it('MA-4 TRIANGULATE: un nuevo fallo mantiene el error sin datos parciales', async () => {
    let attempts = 0;
    const respond = fullRespond();
    start((url, init) => {
      if (url.includes('/clinical-record/anamnesis') && (init.method ?? 'GET') === 'GET') {
        attempts += 1;
        return json({ status: 500, code: 'UNEXPECTED' }, 500);
      }
      return respond(url, init);
    });
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(retryIn(content, '.anamnesis-section')).toBeDefined());
    expect(attempts).toBe(1);

    retryIn(content, '.anamnesis-section')?.click();
    await vi.waitFor(() => expect(attempts).toBe(2));
    await vi.waitFor(() => expect(content.querySelector('.anamnesis-section [role="alert"]')).not.toBeNull());

    expect(readsFor('/anamnesis')).toHaveLength(2);
    expect(content.textContent).not.toContain('Nota vigente C22');
    expect(retryIn(content, '.anamnesis-section')).toBeDefined();
  });

  it('MA-4 TRIANGULATE: doble clic en Reintentar emite una sola lectura', async () => {
    let attempts = 0;
    let release: (() => void) | undefined;
    const pending = new Promise<Response>((resolve) => {
      release = () => resolve(fullRespond()('/api/v1/patients/pac-c22/clinical-record/anamnesis', {} as RequestInit));
    });
    const respond = fullRespond();
    start((url, init) => {
      if (url.includes('/clinical-record/anamnesis') && (init.method ?? 'GET') === 'GET') {
        attempts += 1;
        return attempts === 1 ? json({ status: 500, code: 'UNEXPECTED' }, 500) : pending;
      }
      return respond(url, init);
    });
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(retryIn(content, '.anamnesis-section')).toBeDefined());

    const retry = retryIn(content, '.anamnesis-section');
    retry?.click();
    retry?.click();

    expect(attempts).toBe(2);
    expect(retry?.hasAttribute('disabled')).toBe(true);
    release?.();
    await vi.waitFor(() => expect(content.textContent).toContain('Nota vigente C22'));
    expect(readsFor('/anamnesis')).toHaveLength(2);
  });
});

describe('R-1 / ME-2: objetivo vigente tras corregir y región viva del odontograma', () => {
  it('R-1: tres correcciones seguidas de anamnesis apuntan a la entrada devuelta, sin refetch', async () => {
    const NEW = {
      ...ANAMNESIS_CURRENT,
      id: 'an-nueva', version: 3,
      content: { ...ANAMNESIS_CURRENT.content, notes: 'Nota R1 nueva' },
      correctsEntryId: 'an-2', correctionReason: 'Motivo R1', correctedByEntryId: null,
      createdAt: '2026-11-21T13:00:00Z',
    };
    const NEWER = {
      ...NEW, id: 'an-nueva-2', version: 4,
      content: { ...NEW.content, notes: 'Nota R1 segunda' },
      correctsEntryId: 'an-nueva', createdAt: '2026-11-22T13:00:00Z',
    };
    const NEWEST = {
      ...NEWER, id: 'an-nueva-3', version: 5,
      content: { ...NEWER.content, notes: 'Nota R1 tercera' },
      correctsEntryId: 'an-nueva-2', createdAt: '2026-11-23T13:00:00Z',
    };
    start(writesRespond({
      '/anamnesis/an-2/corrections': () => json(NEW, 201),
      '/anamnesis/an-nueva/corrections': () => json(NEWER, 201),
      '/anamnesis/an-nueva-2/corrections': () => json(NEWEST, 201),
    }));
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));
    const readsBefore = readsFor('/anamnesis').length;
    expect(readsBefore).toBe(1);

    fillAndSubmitCorrection(await openCorrection(content, 'anamnesis'), {
      reason: 'Motivo R1', content: 'Nota R1 nueva',
    });
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(posts()).toHaveLength(1);
    expect(posts()[0]?.url).toContain('/anamnesis/an-2/corrections');
    expect(content.querySelector('.anamnesis-current')?.textContent).toContain('Nota R1 nueva');
    expect(content.querySelector('.anamnesis-history')?.textContent).toContain('Corregida por la entrada an-nueva');
    expect(readsFor('/anamnesis')).toHaveLength(readsBefore);

    const second = await openCorrection(content, 'anamnesis');
    expect(second.querySelector<HTMLTextAreaElement>('#correction-content')?.value).toContain('Nota R1 nueva');
    fillAndSubmitCorrection(second, { reason: 'Motivo R1b', content: 'Nota R1 segunda' });
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(posts()).toHaveLength(2);
    expect(posts()[1]?.url).toContain('/anamnesis/an-nueva/corrections');
    expect(posts()[1]?.url).not.toContain('/an-2/');
    expect(content.querySelector('.anamnesis-current')?.textContent).toContain('Nota R1 segunda');
    expect(readsFor('/anamnesis')).toHaveLength(readsBefore);

    fillAndSubmitCorrection(await openCorrection(content, 'anamnesis'), {
      reason: 'Motivo R1c', content: 'Nota R1 tercera',
    });
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(posts()).toHaveLength(3);
    expect(posts()[2]?.url).toContain('/anamnesis/an-nueva-2/corrections');
    expect(content.querySelector('.anamnesis-current')?.textContent).toContain('Nota R1 tercera');
    expect(readsFor('/anamnesis')).toHaveLength(readsBefore);
    expect(calls.some((call) => ['PUT', 'PATCH', 'DELETE'].includes(call.init.method ?? 'GET'))).toBe(false);
  });

  it('R-1: corregir evoluciones dos veces sin recargar usa el id devuelto por el servidor', async () => {
    const NEW = {
      ...EVO_CURRENT, id: 'ev-nueva', correctsEntryId: 'ev-2', correctionReason: 'Motivo R1 evo',
      description: 'Descripción R1 nueva', createdAt: '2026-11-21T13:00:00Z',
    };
    const NEWER = {
      ...NEW, id: 'ev-nueva-2', correctsEntryId: 'ev-nueva', correctionReason: 'Motivo R1 evo b',
      description: 'Descripción R1 segunda', createdAt: '2026-11-22T13:00:00Z',
    };
    start(writesRespond({
      '/evolutions/ev-2/corrections': () => json(NEW, 201),
      '/evolutions/ev-nueva/corrections': () => json(NEWER, 201),
    }));
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));
    const readsBefore = readsFor('/evolutions').length;
    expect(readsBefore).toBe(1);

    fillAndSubmitCorrection(await openCorrection(content, 'evolutions'), {
      reason: 'Motivo R1 evo', content: 'Descripción R1 nueva',
    });
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(posts()).toHaveLength(1);
    expect(posts()[0]?.url).toContain('/evolutions/ev-2/corrections');
    expect(content.querySelector('.entry-list')?.textContent).toContain('Descripción R1 nueva');
    expect(content.querySelector('.entry.corrected')?.textContent).toContain('Corregida por la entrada ev-nueva');
    expect(readsFor('/evolutions')).toHaveLength(readsBefore);

    const second = await openCorrection(content, 'evolutions');
    expect(second.querySelector<HTMLTextAreaElement>('#correction-content')?.value).toContain('Descripción R1 nueva');
    fillAndSubmitCorrection(second, { reason: 'Motivo R1 evo b', content: 'Descripción R1 segunda' });
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(posts()).toHaveLength(2);
    expect(posts()[1]?.url).toContain('/evolutions/ev-nueva/corrections');
    expect(posts()[1]?.url).not.toContain('/ev-2/');
    expect(content.querySelector('.entry-list')?.textContent).toContain('Descripción R1 segunda');
    expect(readsFor('/evolutions')).toHaveLength(readsBefore);
    expect(calls.some((call) => ['PUT', 'PATCH', 'DELETE'].includes(call.init.method ?? 'GET'))).toBe(false);
  });

  it('R-1: corregir el odontograma dos veces reemplaza el estado visible y el objetivo', async () => {
    const NEW = {
      ...ODO_CURRENT, id: 'od-nueva', correctsEntryId: 'od-2', correctionReason: 'Motivo R1 odo',
      condition: 'CARIES', surface: 'OCCLUSAL', notes: 'Nota R1 odo',
      correctedByEntryId: null, createdAt: '2026-11-21T13:00:00Z',
    };
    const NEWER = {
      ...NEW, id: 'od-nueva-2', correctsEntryId: 'od-nueva', correctionReason: 'Motivo R1 odo b',
      condition: 'HEALTHY', surface: null, notes: 'Nota R1 odo 2', createdAt: '2026-11-22T13:00:00Z',
    };
    start(writesRespond({
      '/odontogram/od-2/corrections': () => json(NEW, 201),
      '/odontogram/od-nueva/corrections': () => json(NEWER, 201),
    }));
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));
    const readsBefore = readsFor('/odontogram').length;
    expect(readsBefore).toBe(1);

    fillAndSubmitCorrection(await openCorrection(content, 'odontogram'), {
      reason: 'Motivo R1 odo', content: 'Nota R1 odo', condition: 'CARIES',
    });
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(posts()).toHaveLength(1);
    expect(posts()[0]?.url).toContain('/odontogram/od-2/corrections');
    expect(content.querySelector('button.tooth[data-tooth="36"]')?.getAttribute('aria-label')).toContain('caries');
    expect(odontogramStatusOf(content)?.textContent).toContain('caries');
    expect(odontogramCorrectionButtons(content)).toHaveLength(1);
    expect(readsFor('/odontogram')).toHaveLength(readsBefore);

    fillAndSubmitCorrection(await openCorrection(content, 'odontogram'), {
      reason: 'Motivo R1 odo b', content: 'Nota R1 odo 2', condition: 'HEALTHY',
    });
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(posts()).toHaveLength(2);
    expect(posts()[1]?.url).toContain('/odontogram/od-nueva/corrections');
    expect(posts()[1]?.url).not.toContain('/od-2/');
    expect(content.querySelector('button.tooth[data-tooth="36"]')?.getAttribute('aria-label')).toContain('sano');
    expect(readsFor('/odontogram')).toHaveLength(readsBefore);
  });

  it('R-1 TRIANGULATE: un 409 no cambia el objetivo; la corrección aceptada sí lo hace', async () => {
    let attempts = 0;
    const NEW = {
      ...EVO_CURRENT, id: 'ev-nueva', correctsEntryId: 'ev-2', description: 'Descripción R1 tras fallo',
      createdAt: '2026-11-21T13:00:00Z',
    };
    const NEWER = {
      ...NEW, id: 'ev-nueva-2', correctsEntryId: 'ev-nueva', description: 'Descripción R1 definitiva',
      createdAt: '2026-11-22T13:00:00Z',
    };
    start(writesRespond({
      '/evolutions/ev-2/corrections': () => {
        attempts += 1;
        return attempts === 1
          ? new Response(JSON.stringify({
            status: 409, code: 'CLINICAL_ENTRY_ALREADY_CORRECTED', title: 'Conflicto',
          }), { status: 409, headers: { 'Content-Type': 'application/problem+json' } })
          : json(NEW, 201);
      },
      '/evolutions/ev-nueva/corrections': () => json(NEWER, 201),
    }));
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    const failed = await openCorrection(content, 'evolutions');
    fillAndSubmitCorrection(failed, { reason: 'Motivo fallido', content: 'Contenido fallido' });
    await vi.waitFor(() => expect(posts()).toHaveLength(1));
    await vi.waitFor(() => expect(failed.querySelector('[role="alert"]')).not.toBeNull());
    expect(content.querySelector('.entry-list')?.textContent).toContain('Control ficticio C22');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(document.querySelector('[role="dialog"]')).toBeNull();

    fillAndSubmitCorrection(await openCorrection(content, 'evolutions'), {
      reason: 'Motivo R1', content: 'Descripción R1 tras fallo',
    });
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(posts()).toHaveLength(2);
    expect(posts()[1]?.url).toContain('/evolutions/ev-2/corrections');
    expect(content.querySelector('.entry-list')?.textContent).toContain('Descripción R1 tras fallo');
    expect(readsFor('/evolutions')).toHaveLength(1);

    fillAndSubmitCorrection(await openCorrection(content, 'evolutions'), {
      reason: 'Motivo R1 b', content: 'Descripción R1 definitiva',
    });
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(posts()).toHaveLength(3);
    expect(posts()[2]?.url).toContain('/evolutions/ev-nueva/corrections');
    expect(content.querySelector('.entry-list')?.textContent).toContain('Descripción R1 definitiva');
    expect(readsFor('/evolutions')).toHaveLength(1);
  });

  it('ME-2: la región viva anuncia código, estado y cara de la pieza con superficie', async () => {
    start(fullRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    const status = odontogramStatusOf(content);
    expect(status?.textContent).toContain('36');
    expect(status?.textContent).toContain('obturado');
    expect(status?.textContent).toContain('cara oclusal');
    expect(status?.textContent).toContain('seleccionada');
    expect(status?.textContent).not.toBe('Pieza seleccionada 36');
    expect(status?.textContent).toBe(content.querySelector('button.tooth[data-tooth="36"]')?.getAttribute('aria-label'));
  });

  it('ME-2 TRIANGULATE: una pieza sin cara y otra sin registro también se anuncian completas', async () => {
    start(fullRespond());
    const content = await selectDemoPatient();
    await vi.waitFor(() => expect(content.querySelectorAll('button.tooth')).toHaveLength(52));

    content.querySelector<HTMLButtonElement>('button.tooth[data-tooth="47"]')?.click();
    const withCondition = odontogramStatusOf(content)?.textContent;
    expect(withCondition).toContain('47');
    expect(withCondition).toContain('endodoncia');
    expect(withCondition).toContain('seleccionada');
    expect(withCondition).not.toContain('cara');

    content.querySelector<HTMLButtonElement>('button.tooth[data-tooth="13"]')?.click();
    const withoutRecord = odontogramStatusOf(content)?.textContent;
    expect(withoutRecord).toContain('13');
    expect(withoutRecord).toContain('sin registro');
    expect(withoutRecord).toContain('seleccionada');
    expect(withoutRecord).not.toBe('Pieza seleccionada 13');
    expect(withoutRecord).toBe(content.querySelector('button.tooth[data-tooth="13"]')?.getAttribute('aria-label'));
  });
});

/**
 * La ruta real no puede completar la corrección: `routes.ts` tiene los deps de escritura como stubs
 * preexistentes (fuera del alcance de B-1). Este caso exercise el mismo control ya montado por la
 * vista con un gateway simulado, para cubrir la corrección inmutable de punta a punta.
 */
describe('corrección sobre la vista montada (TRIANGULATE)', () => {
  it('crea una entrada nueva con motivo, marca la original y no dispara refetch ni UPDATE/DELETE', async () => {
    const NEW = { ...EVO_CURRENT, id: 'ev-nueva', correctsEntryId: 'ev-1', correctionReason: 'Motivo C22' };
    const ORIGINAL = { ...EVO_OLD, correctedByEntryId: null };
    const MARKED = { ...EVO_OLD, correctedByEntryId: 'ev-nueva' };
    let corrected = false;
    const reads = vi.fn();
    const writes: { readonly method: string; readonly path: string; readonly body: unknown }[] = [];
    const patients = {
      search: vi.fn(async () => ({ ok: true as const, data: [PATIENT] })),
      get: vi.fn(async () => ({ ok: true as const, data: undefined })),
      update: vi.fn(async () => ({ ok: true as const, data: undefined })),
      deactivate: vi.fn(async () => ({ ok: true as const, data: undefined })),
    } as unknown as PatientsGateway;
    const deps: ClinicalRecordViewDeps = {
      patients,
      getAnamnesis: async () => { reads(); return { ok: true, data: { current: null, history: [] } }; },
      getOdontogram: async () => { reads(); return { ok: true, data: { current: [], history: [] } }; },
      getEvolutions: async () => {
        reads();
        return { ok: true, data: corrected ? { entries: [NEW, MARKED] } : { entries: [ORIGINAL] } };
      },
      registerAnamnesis: async () => ({ ok: true, data: ANAMNESIS_CURRENT }),
      registerOdontogram: async () => ({ ok: true, data: [] }),
      registerEvolution: async () => ({ ok: true, data: EVO_CURRENT }),
      correctEntry: async (patientId, type, entryId, input) => {
        writes.push({ method: 'POST', path: `${patientId}/${type}/${entryId}/corrections`, body: input });
        corrected = true;
        return { ok: true, data: NEW };
      },
      navigate: vi.fn(),
      onSessionExpired: vi.fn(),
    };
    const view = createClinicalRecordView(deps);
    document.body.replaceChildren(view.element);
    await vi.waitFor(() => expect(view.element.querySelectorAll('.clinical-picker__option')).toHaveLength(1));
    view.element.querySelector<HTMLButtonElement>('.clinical-picker__option')?.click();
    await vi.waitFor(() => expect(view.element.querySelectorAll('.entry')).toHaveLength(1));
    expect(reads).toHaveBeenCalledTimes(3);

    const section = [...view.element.querySelectorAll<HTMLElement>('.clinical-section')]
      .find((node) => node.querySelector('.evolutions-section') !== null);
    expect(section).toBeDefined();
    section?.querySelector<HTMLButtonElement>('.correction-flow [data-action="open"]')?.click();
    const dialog = document.querySelector<HTMLElement>('[role="dialog"]');
    expect(dialog?.querySelector('#correction-reason')).not.toBeNull();
    const reason = dialog?.querySelector<HTMLTextAreaElement>('#correction-reason');
    if (reason !== null && reason !== undefined) { reason.value = 'Motivo C22'; }
    dialog?.querySelector<HTMLButtonElement>('[data-action="confirm"]')?.click();

    await vi.waitFor(() => expect(writes).toHaveLength(1));
    expect(writes[0]).toEqual({
      method: 'POST', path: 'pac-c22/evolutions/ev-1/corrections',
      body: { reason: 'Motivo C22', content: { description: 'Descripción original C22' } },
    });
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(reads).toHaveBeenCalledTimes(3); // decisión 10: sin refetch tras la mutación

    await view.selectPatient(PATIENT.id);
    await vi.waitFor(() => expect(view.element.querySelectorAll('.entry')).toHaveLength(2));
    expect(view.element.querySelector('.entry')?.textContent).toContain('Motivo C22');
    expect(view.element.querySelector('.entry.corrected')?.textContent).toContain('Corregida');
    expect(writes.every((write) => write.method === 'POST')).toBe(true);
    expect(reads).toHaveBeenCalledTimes(6); // solo la nueva apertura explícita de sección
  });
});
