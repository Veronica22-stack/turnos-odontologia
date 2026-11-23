import { afterEach, describe, expect, it } from 'vitest';
import { button, deferred, requireElement, text } from '../booking/test-harness';
import type { CancelResult, ViewResult } from './manage-types';
import {
  BOOKING,
  CANCELLED,
  PAST,
  TOKEN,
  makeManageGateway,
  settleAll,
  startManage,
  type ManageHarness,
} from './test-harness';

let harness: ManageHarness | undefined;

afterEach(() => {
  harness?.stop();
  harness = undefined;
  document.body.replaceChildren();
});

async function start(options: Parameters<typeof startManage>[0] = {}): Promise<ManageHarness> {
  harness = await startManage(options);
  return harness;
}

function buttonNames(root: ParentNode): string[] {
  return [...root.querySelectorAll('button, a.btn')].map((b) => b.textContent?.trim() ?? '');
}

async function click(element: HTMLElement): Promise<void> {
  element.click();
  await settleAll();
}

function problemProblem(kind: ViewResult['kind']): ViewResult {
  return { kind } as ViewResult;
}

describe('carga y detalle (specs "Consulta del turno por enlace")', () => {
  it('mientras consulta muestra "Cargando tu turno…" con role=status y ninguna acción', async () => {
    const pending = deferred<ViewResult>();
    const { root, gateway } = await start({ gateway: makeManageGateway({ view: () => pending.promise }) });

    expect(root.querySelector('h1')?.textContent).toBe('Tu turno');
    expect(requireElement(root, '[role="status"]').textContent).toContain('Cargando tu turno…');
    expect(root.querySelectorAll('button')).toHaveLength(0);
    expect(gateway.view).toHaveBeenCalledWith(TOKEN);
    pending.resolve({ kind: 'ok', booking: BOOKING });
  });

  it('con 200 muestra prestación, día, horario de Buenos Aires, consultorio, insignia y acciones', async () => {
    const { root } = await start();

    expect(root.querySelector('h1')?.textContent).toBe('Tu turno');
    const page = text(root);
    expect(page).toContain('Limpieza');
    expect(page).toContain('Confirmado');
    expect(page).toContain('martes 24 de noviembre');
    expect(page).toContain('10:30 – 11:15');
    expect(page).toContain('Consultorio La Muela');
    expect(page).toContain('Este enlace es personal');
    expect(buttonNames(root)).toEqual(expect.arrayContaining(['Reprogramar', 'Cancelar turno']));
    expect(page).not.toContain(TOKEN);
  });

  it('con practiceName vacío omite la línea del consultorio', async () => {
    const { root } = await start({
      gateway: makeManageGateway({ view: async () => ({ kind: 'ok', booking: { ...BOOKING, practiceName: '' } }) }),
    });

    expect(text(root)).not.toContain('Consultorio');
    expect(text(root)).toContain('martes 24 de noviembre');
  });

  it('las banderas del servidor mandan: sin banderas no hay botones aunque el reloj diga futuro', async () => {
    const { root } = await start({
      gateway: makeManageGateway({ view: async () => ({ kind: 'ok', booking: PAST }) }),
      clock: () => new Date('2026-11-01T00:00:00Z'),
    });

    expect(buttonNames(root)).not.toContain('Reprogramar');
    expect(buttonNames(root)).not.toContain('Cancelar turno');
  });

  it('solo una bandera: solo esa acción', async () => {
    const { root } = await start({
      gateway: makeManageGateway({ view: async () => ({ kind: 'ok', booking: { ...BOOKING, reschedulable: false } }) }),
    });

    expect(buttonNames(root)).toContain('Cancelar turno');
    expect(buttonNames(root)).not.toContain('Reprogramar');
  });
});

describe('solo lectura (spec "Turno en solo lectura")', () => {
  it('banderas en falso -> "Turno pasado", aviso y enlace para reservar, sin botones', async () => {
    const { root } = await start({ gateway: makeManageGateway({ view: async () => ({ kind: 'ok', booking: PAST }) }) });

    const page = text(root);
    expect(page).toContain('Turno pasado');
    expect(page).toContain('Este turno ya pasó.');
    expect(buttonNames(root)).not.toContain('Reprogramar');
    expect(buttonNames(root)).not.toContain('Cancelar turno');
    expect(root.querySelector('a[href="/reservar"]')?.textContent).toBe('Reservar un turno');
  });

  it('NO_SHOW se muestra como "Turno pasado" y no revela la ausencia', async () => {
    const { root } = await start({
      gateway: makeManageGateway({ view: async () => ({ kind: 'ok', booking: { ...PAST, status: 'NO_SHOW' } }) }),
    });

    expect(text(root)).toContain('Turno pasado');
    expect(text(root)).not.toMatch(/ausente/i);
  });

  it('CANCELLED -> "Cancelado", aviso y enlace para reservar otro', async () => {
    const { root } = await start({
      gateway: makeManageGateway({ view: async () => ({ kind: 'ok', booking: CANCELLED }) }),
    });

    const page = text(root);
    expect(page).toContain('Cancelado');
    expect(page).toContain('Este turno está cancelado.');
    expect(page).not.toContain('Turno pasado');
    expect(root.querySelector('a[href="/reservar"]')).not.toBeNull();
  });
});

describe('enlace no válido y errores de la consulta', () => {
  it('404 -> mensaje genérico con enlace a /reservar', async () => {
    const { root } = await start({ gateway: makeManageGateway({ view: async () => ({ kind: 'invalid' }) }) });

    expect(root.querySelector('h1')?.textContent).toBe('Este enlace no es válido');
    expect(root.querySelector('a[href="/reservar"]')).not.toBeNull();
    expect(text(root)).toContain('Puede que haya vencido');
  });

  it('token con forma imposible (símbolos) no llega a la API: el router ni siquiera monta la vista', async () => {
    const { gateway } = await start({ url: '/turno/abc$%25' });

    expect(gateway.view).not.toHaveBeenCalled();
  });

  it('token con forma imposible (corto) -> el mensaje genérico y ninguna petición', async () => {
    const { gateway, root } = await start({ url: '/turno/corto' });

    expect(root.querySelector('h1')?.textContent).toBe('Este enlace no es válido');
    expect(gateway.view).not.toHaveBeenCalled();
  });

  it('429 con Retry-After 40 -> aviso de espera y sin datos del turno', async () => {
    const { root } = await start({
      gateway: makeManageGateway({ view: async () => ({ kind: 'rate-limited', retryAfterSeconds: 40 }) }),
    });

    const page = text(root);
    expect(page).toContain('Demasiados intentos seguidos.');
    expect(page).toContain('Probá de nuevo en 1 minuto.');
    expect(page).not.toContain('Limpieza');
  });

  it('error de red -> "No pudimos cargar tu turno." y "Reintentar" repite la consulta', async () => {
    let calls = 0;
    const { root, gateway } = await start({
      gateway: makeManageGateway({
        view: async () => (++calls === 1 ? problemProblem('connection') : { kind: 'ok', booking: BOOKING }),
      }),
    });
    expect(text(root)).toContain('No pudimos cargar tu turno.');

    await click(button(root, 'Reintentar'));

    expect(gateway.view).toHaveBeenCalledTimes(2);
    expect(text(root)).toContain('Limpieza');
    expect(text(root)).not.toContain('No pudimos cargar');
  });
});

describe('cancelación: confirmación (spec "Cancelación con confirmación previa")', () => {
  it('"Cancelar turno" abre el diálogo con los datos, la advertencia y el foco en "No, mantener turno"', async () => {
    const { root } = await start();

    await click(button(root, 'Cancelar turno'));

    const dialog = requireElement<HTMLDialogElement>(document, 'dialog');
    expect(dialog.textContent).toContain('¿Cancelar este turno?');
    expect(dialog.textContent).toContain('Limpieza · martes 24 de noviembre, 10:30');
    expect(dialog.textContent).toContain('No se puede deshacer');
    expect(document.activeElement?.textContent).toBe('No, mantener turno');
    expect(document.activeElement?.closest('dialog')).toBe(dialog);
  });

  it('"No, mantener turno" no envía nada y devuelve el foco a "Cancelar turno"', async () => {
    const { root, gateway } = await start();
    const opener = button(root, 'Cancelar turno');
    opener.focus();

    opener.click();
    button(document, 'No, mantener turno').click();
    await settleAll();

    expect(gateway.cancel).not.toHaveBeenCalled();
    expect(document.querySelector('dialog')).toBeNull();
    expect(document.activeElement).toBe(opener);
  });

  it('Escape tampoco envía nada y devuelve el foco', async () => {
    const { root, gateway } = await start();
    const opener = button(root, 'Cancelar turno');
    opener.focus();

    opener.click();
    document.querySelector('dialog')?.dispatchEvent(new Event('cancel', { cancelable: true }));
    await settleAll();

    expect(gateway.cancel).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(opener);
  });
});

describe('cancelación: envío y resultados (spec "Resultado de la cancelación")', () => {
  async function confirmCancel(root: HTMLElement): Promise<void> {
    button(root, 'Cancelar turno').click();
    button(document, 'Sí, cancelar turno').click();
    await settleAll();
  }

  it('confirmar envía un DELETE; pendiente: "Cancelando…" con aria-busy, Reprogramar deshabilitado y segundo clic ignorado', async () => {
    const pending = deferred<CancelResult>();
    const { root, gateway } = await start({ gateway: makeManageGateway({ cancel: () => pending.promise }) });

    await confirmCancel(root);

    const cancelButton = button(root, 'Cancelando…');
    expect(gateway.cancel).toHaveBeenCalledTimes(1);
    expect(gateway.cancel).toHaveBeenCalledWith(TOKEN);
    expect(cancelButton.getAttribute('aria-busy')).toBe('true');
    expect(button(root, 'Reprogramar').getAttribute('aria-disabled')).toBe('true');

    cancelButton.click();
    button(root, 'Reprogramar').click();
    await settleAll();
    expect(document.querySelector('dialog')).toBeNull();
    expect(gateway.cancel).toHaveBeenCalledTimes(1);
    pending.resolve({ kind: 'unknown-outcome' });
    await settleAll();
  });

  it('200 -> "Turno cancelado" enfocado, insignia, avisos, enlace para reservar otro y sin acciones', async () => {
    const { root } = await start({
      gateway: makeManageGateway({ cancel: async () => ({ kind: 'done', booking: CANCELLED }) }),
    });

    await confirmCancel(root);

    const heading = root.querySelector('h1');
    expect(heading?.textContent).toBe('Turno cancelado');
    expect(document.activeElement).toBe(heading);
    const page = text(root);
    expect(page).toContain('Cancelado');
    expect(page).toContain('Te enviamos un email con la cancelación.');
    expect(page).toContain('Este enlace ya no sirve');
    expect(root.querySelector('a[href="/reservar"]')?.textContent).toBe('Reservar otro turno');
    expect(buttonNames(root)).not.toContain('Reprogramar');
    expect(buttonNames(root)).not.toContain('Cancelar turno');
  });

  it('422 -> aviso, nueva consulta y el turno en solo lectura', async () => {
    let views = 0;
    const { root, gateway } = await start({
      gateway: makeManageGateway({
        view: async () => (++views === 1 ? { kind: 'ok', booking: BOOKING } : { kind: 'ok', booking: PAST }),
        cancel: async () => ({ kind: 'not-modifiable' }),
      }),
    });

    await confirmCancel(root);

    expect(gateway.view).toHaveBeenCalledTimes(2);
    const page = text(root);
    expect(page).toContain('Este turno ya no se puede modificar.');
    expect(page).toContain('Turno pasado');
    expect(buttonNames(root)).not.toContain('Cancelar turno');
  });

  it('403 -> "La página quedó desactualizada" con el turno intacto y las acciones disponibles', async () => {
    const { root } = await start({ gateway: makeManageGateway({ cancel: async () => ({ kind: 'stale-page' }) }) });

    await confirmCancel(root);

    expect(text(root)).toContain('La página quedó desactualizada.');
    expect(text(root)).toContain('Limpieza');
    expect(buttonNames(root)).toContain('Cancelar turno');
  });

  it('429 -> aviso de espera y el botón vuelve a estar habilitado', async () => {
    const { root } = await start({
      gateway: makeManageGateway({ cancel: async () => ({ kind: 'rate-limited', retryAfterSeconds: 40 }) }),
    });

    await confirmCancel(root);

    expect(text(root)).toContain('Probá de nuevo en 1 minuto.');
    expect(button(root, 'Cancelar turno').getAttribute('aria-busy')).toBeNull();
  });

  it('timeout -> "No pudimos confirmar la cancelación…" sin afirmar ni negar', async () => {
    const { root } = await start({ gateway: makeManageGateway({ cancel: async () => ({ kind: 'unknown-outcome' }) }) });

    await confirmCancel(root);

    expect(text(root)).toContain('No pudimos confirmar la cancelación.');
    expect(text(root)).toContain('Puede que se haya registrado igual');
    expect(buttonNames(root)).toContain('Cancelar turno');
  });

  it('404 -> "Este enlace no es válido"', async () => {
    const { root } = await start({ gateway: makeManageGateway({ cancel: async () => ({ kind: 'invalid' }) }) });

    await confirmCancel(root);

    expect(root.querySelector('h1')?.textContent).toBe('Este enlace no es válido');
  });
});
