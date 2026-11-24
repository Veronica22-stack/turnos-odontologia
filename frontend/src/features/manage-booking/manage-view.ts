import { confirmAction } from '../../core/dom/confirm';
import { formIcon } from '../../core/dom/form';
import { h, svg } from '../../core/dom/h';
import type { RouteContext } from '../../router/router';
import { localDateOf, type Clock } from '../../shared/utils/dates';
import type { BookingGateway } from '../booking/booking-types';
import { bookingHead, loadingStatus, noticeAlert } from '../booking/booking-ui';
import { renderBookingCard } from './booking-card';
import {
  back,
  choose,
  clearChoice,
  initialState,
  loaded,
  openReschedule,
  rescheduleUnavailable,
  resolveCancel,
  resolveReschedule,
  submitCancel,
  submitReschedule,
  type ManageState,
} from './manage-state';
import {
  cancelConfirmation,
  cancelledNotice,
  invalidLinkMessage,
  manageNotice,
  readOnlyNotice,
  rescheduledNotice,
  type ManageNoticeKind,
} from './manage-messages';
import type { ManageGateway, ManagedBooking } from './manage-types';
import { managePath, parseAction, type ManageAction } from './manage-url';
import { renderRescheduleLoading, renderRescheduleStep, renderRescheduleUnavailable, type RescheduleStep } from './reschedule-step';
import { resolveServiceId } from './service-resolver';
import { isPlausibleToken } from './token';

export interface ManageViewDeps {
  readonly gateway: ManageGateway;
  readonly booking: Pick<BookingGateway, 'availability' | 'listServices'>;
  readonly clock: Clock;
  /** Navegacion del router (apila una entrada salvo `replace`). */
  readonly navigate: (path: string, options?: { replace?: boolean }) => void;
  /** Corrige la URL sin volver a renderizar. */
  readonly replaceUrl: (path: string) => void;
  /** Atras del historial: de la reprogramacion al detalle. */
  readonly goBack: () => void;
}

const BOOKING_PATH = '/reservar';

function reserveLink(label: string, primary: boolean): HTMLElement {
  return h(
    'div',
    { class: 'actions' },
    h('a', { class: primary ? 'btn btn-primary btn-lg' : 'btn btn-secondary btn-lg', attrs: { href: BOOKING_PATH } }, label),
  );
}

function lockIcon(): SVGElement {
  return svg(
    'svg',
    { attrs: { viewBox: '0 0 16 16', focusable: 'false' }, aria: { hidden: 'true' } },
    svg('rect', { attrs: { x: '3', y: '7', width: '10', height: '7', rx: '1.5', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.4' } }),
    svg('path', { attrs: { d: 'M5.5 7V5a2.5 2.5 0 015 0v2', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.4' } }),
  );
}

function noticeRole(variant: 'info' | 'warning' | 'danger'): 'alert' | 'status' {
  return variant === 'info' ? 'status' : 'alert';
}

function section(...children: ReadonlyArray<Node | null>): HTMLElement {
  return h(
    'section',
    { class: 'booking', attrs: { 'aria-labelledby': 'step-title' } },
    ...children.filter((child): child is Node => child !== null),
  );
}

function skeletonCard(): HTMLElement {
  return h(
    'div',
    { class: 'confirm-card', aria: { hidden: 'true' } },
    h('div', { class: 'slot-skeleton' }),
    h('div', { class: 'slot-skeleton' }),
    h('div', { class: 'slot-skeleton' }),
  );
}

/**
 * Vista `/turno/:token` (design D4). El token vive solo en la ruta y en este *closure*; el estado se
 * conserva mientras el elemento de la invocacion anterior siga conectado (seguimos en el mismo turno,
 * p. ej. atras desde `?accion=reprogramar`) y se descarta al volver desde otra ruta.
 */
export function createManageView(deps: ManageViewDeps): (context: RouteContext) => HTMLElement {
  let state: ManageState = initialState();
  let token = '';
  let root: HTMLElement | null = null;
  let generation = 0;
  let urlAction: ManageAction = 'detail';
  // Sesion de reprogramacion: se descarta al salir de esa pantalla.
  let pushedReschedule = false;
  let resolvedServiceId: string | undefined;
  let resolving = false;
  let pickerDate: string | undefined;
  let session = 0;
  let confirmingCancel = false;
  let step: RescheduleStep | null = null;

  function resetSession(): void {
    pushedReschedule = false;
    resolvedServiceId = undefined;
    resolving = false;
    pickerDate = undefined;
    step = null;
    session += 1;
  }

  /** Aplica un estado nuevo: limpia la sesion de reprogramacion y normaliza la URL al salir de ella. */
  function settle(next: ManageState): void {
    if (next.screen !== 'rescheduling') {
      resetSession();
    }
    if (next.screen !== 'rescheduling' && next.screen !== 'loading' && urlAction === 'reschedule') {
      deps.replaceUrl(managePath(token));
      urlAction = 'detail';
    }
    state = next;
  }

  function commit(next: ManageState): void {
    settle(next);
    paint();
  }

  // ---------- consulta ----------

  async function load(notice?: ManageNoticeKind): Promise<void> {
    const gen = generation;
    const result = await deps.gateway.view(token);
    if (gen !== generation) {
      return;
    }
    const next = loaded(result, notice);
    if (urlAction === 'reschedule' && next.screen === 'detail') {
      const opened = openReschedule(next);
      commit(opened === next ? next : opened);
      return;
    }
    commit(next);
  }

  function reload(): void {
    generation += 1;
    commit(initialState());
    void load();
  }

  /** "Ver estado actual": consulta de nuevo y vuelve al detalle (la URL de la accion se normaliza). */
  function viewCurrent(): void {
    urlAction = 'detail';
    deps.replaceUrl(managePath(token));
    reload();
  }

  // ---------- acciones ----------

  function openRescheduleScreen(): void {
    if (state.screen !== 'detail' || state.busy !== undefined || !state.booking.reschedulable) {
      return;
    }
    pushedReschedule = true;
    deps.navigate(managePath(token, 'reschedule'));
  }

  function leaveReschedule(): void {
    if (pushedReschedule) {
      deps.goBack();
    } else {
      deps.navigate(managePath(token), { replace: true });
    }
  }

  function applyCancelBusy(): void {
    const cancel = root?.querySelector<HTMLElement>('[data-action="cancel"]');
    const reschedule = root?.querySelector<HTMLElement>('[data-action="reschedule"]');
    reschedule?.setAttribute('aria-disabled', 'true');
    if (cancel !== null && cancel !== undefined) {
      cancel.replaceChildren(h('span', { class: 'spinner', aria: { hidden: 'true' } }), 'Cancelando…');
      cancel.setAttribute('aria-busy', 'true');
      cancel.setAttribute('aria-disabled', 'true');
    }
  }

  async function startCancel(): Promise<void> {
    if (confirmingCancel || state.screen !== 'detail' || state.busy !== undefined || !state.booking.cancellable) {
      return;
    }
    confirmingCancel = true;
    const confirmation = cancelConfirmation(state.booking);
    const accepted = await confirmAction({ ...confirmation, initialFocus: 'cancel' });
    confirmingCancel = false;
    if (!accepted) {
      return;
    }
    const next = submitCancel(state);
    if (next === state) {
      return;
    }
    state = next;
    applyCancelBusy();
    const gen = generation;
    const result = await deps.gateway.cancel(token);
    if (gen !== generation) {
      return;
    }
    const transition = resolveCancel(state, result);
    commit(transition.state);
    if (transition.requery) {
      void load({ kind: 'not-modifiable' });
    }
  }

  async function startReschedule(): Promise<void> {
    const next = submitReschedule(state);
    if (next === state || next.screen !== 'rescheduling' || next.chosen === undefined) {
      return;
    }
    const { chosen } = next;
    state = next;
    step?.setBusy(true);
    const gen = generation;
    const result = await deps.gateway.reschedule(token, chosen.startsAt);
    if (gen !== generation) {
      return;
    }
    const transition = resolveReschedule(state, result);
    commit(transition.state);
    if (transition.requery) {
      void load({ kind: 'not-modifiable' });
    }
  }

  // ---------- pantallas ----------

  function loadingScreen(): HTMLElement {
    return section(bookingHead('Tu turno'), loadingStatus('Cargando tu turno…'), skeletonCard());
  }

  function invalidScreen(): HTMLElement {
    const message = invalidLinkMessage();
    return section(
      h(
        'div',
        { class: 'empty' },
        h(
          'div',
          { class: 'ico ico-neutral' },
          svg(
            'svg',
            { attrs: { viewBox: '0 0 24 24', focusable: 'false' }, aria: { hidden: 'true' } },
            svg('path', {
              attrs: {
                d: 'M10 14a4 4 0 005.66 0l3-3a4 4 0 00-5.66-5.66l-1 1M14 10a4 4 0 00-5.66 0l-3 3a4 4 0 005.66 5.66l1-1',
                fill: 'none',
                stroke: 'currentColor',
                'stroke-width': '1.7',
                'stroke-linecap': 'round',
              },
            }),
            svg('path', { attrs: { d: 'M4 4l16 16', stroke: 'currentColor', 'stroke-width': '1.7', 'stroke-linecap': 'round' } }),
          ),
        ),
        h('h1', { attrs: { id: 'step-title', tabindex: '-1' } }, message.title),
        h('p', {}, message.detail),
        h('a', { class: 'btn btn-primary btn-lg', attrs: { href: BOOKING_PATH } }, 'Reservar un turno'),
      ),
    );
  }

  function problemScreen(notice: ManageNoticeKind): HTMLElement {
    const message = manageNotice(notice);
    return section(
      bookingHead('Tu turno'),
      noticeAlert(message),
      h('div', { class: 'actions' }, h('button', { class: 'btn btn-secondary btn-lg', attrs: { type: 'button' }, on: { click: reload } }, 'Reintentar')),
    );
  }

  function detailScreen(booking: ManagedBooking, notice: ManageNoticeKind | undefined, busy: boolean): HTMLElement {
    const showReschedule = booking.reschedulable;
    const showCancel = booking.cancellable;
    const readOnly = !showReschedule && !showCancel;
    const alert =
      notice !== undefined
        ? noticeAlert(manageNotice(notice), noticeRole(manageNotice(notice).variant))
        : readOnly
          ? noticeAlert(readOnlyNotice(booking), 'status')
          : null;
    const actions = readOnly
      ? reserveLink('Reservar un turno', booking.status === 'CANCELLED')
      : h(
          'div',
          { class: 'manage-actions' },
          showReschedule
            ? h(
                'button',
                {
                  class: 'btn btn-primary btn-lg',
                  attrs: { type: 'button', ...(busy ? { 'aria-disabled': 'true' } : {}) },
                  dataset: { action: 'reschedule' },
                  on: { click: openRescheduleScreen },
                },
                'Reprogramar',
              )
            : null,
          showCancel
            ? h(
                'button',
                { class: 'btn btn-danger-outline btn-lg', attrs: { type: 'button' }, dataset: { action: 'cancel' }, on: { click: () => void startCancel() } },
                'Cancelar turno',
              )
            : null,
        );
    const screen = section(
      bookingHead('Tu turno'),
      alert,
      renderBookingCard(booking),
      actions,
      readOnly
        ? null
        : h('p', { class: 'manage-note' }, lockIcon(), h('span', {}, 'Este enlace es personal: no lo compartas. Con él se puede cancelar o cambiar tu turno.')),
    );
    if (busy) {
      queueMicrotask(applyCancelBusy);
    }
    return screen;
  }

  function cancelledScreen(booking: ManagedBooking): HTMLElement {
    return section(
      bookingHead('Turno cancelado'),
      renderBookingCard(booking),
      noticeAlert(cancelledNotice(), 'status'),
      reserveLink('Reservar otro turno', true),
    );
  }

  function rescheduledScreen(booking: ManagedBooking, previous: { startsAt: string; endsAt: string }): HTMLElement {
    return section(
      h(
        'div',
        { class: 'confirm-hero' },
        h('div', { class: 'confirm-icon' }, formIcon('check')),
        h('h1', { attrs: { id: 'step-title', tabindex: '-1' } }, '¡Turno reprogramado!'),
      ),
      renderBookingCard(booking, { previous }),
      noticeAlert(rescheduledNotice(), 'status'),
      h(
        'div',
        { class: 'actions' },
        h(
          'button',
          { class: 'btn btn-secondary btn-lg', attrs: { type: 'button' }, on: { click: () => commit({ screen: 'detail', booking }) } },
          'Ver mi turno',
        ),
      ),
    );
  }

  function ensureServiceResolved(booking: ManagedBooking): void {
    if (resolving) {
      return;
    }
    resolving = true;
    const current = session;
    void resolveServiceId(booking, () => deps.booking.listServices()).then((resolution) => {
      if (current !== session || state.screen !== 'rescheduling') {
        return;
      }
      resolving = false;
      if (resolution.kind === 'resolved') {
        resolvedServiceId = resolution.serviceId;
        paint();
      } else {
        commit(rescheduleUnavailable(state));
      }
    });
  }

  function reschedulingScreen(current: Extract<ManageState, { screen: 'rescheduling' }>): HTMLElement {
    const { booking } = current;
    if (current.unavailable === true) {
      return renderRescheduleUnavailable(booking, leaveReschedule);
    }
    const serviceId = booking.serviceId ?? resolvedServiceId;
    if (serviceId === undefined) {
      ensureServiceResolved(booking);
      return renderRescheduleLoading(booking, leaveReschedule);
    }
    const initialDate =
      current.rejectedStartsAt !== undefined
        ? localDateOf(current.rejectedStartsAt)
        : (pickerDate ?? localDateOf(booking.startsAt));
    const built = renderRescheduleStep({
      booking,
      serviceId,
      gateway: deps.booking,
      clock: deps.clock,
      initialDate,
      ...(current.chosen === undefined ? {} : { chosen: current.chosen }),
      ...(current.rejectedStartsAt === undefined ? {} : { suggestFrom: current.rejectedStartsAt }),
      ...(current.notice === undefined ? {} : { notice: current.notice }),
      busy: current.busy,
      onBack: leaveReschedule,
      onDateChange: (date) => {
        pickerDate = date;
        state = clearChoice(state);
      },
      onChoose: (slot) => {
        state = choose(state, slot);
      },
      onConfirm: () => void startReschedule(),
      onViewCurrent: viewCurrent,
      onTerminal: () => {
        commit(rescheduleUnavailable(state));
      },
    });
    step = built;
    return built.element;
  }

  function screen(): HTMLElement {
    switch (state.screen) {
      case 'loading':
        return loadingScreen();
      case 'invalid':
        return invalidScreen();
      case 'rate-limited':
        return problemScreen(
          state.retryAfterSeconds === undefined
            ? { kind: 'rate-limited' }
            : { kind: 'rate-limited', retryAfterSeconds: state.retryAfterSeconds },
        );
      case 'connection':
        return problemScreen({ kind: 'connection' });
      case 'detail':
        return detailScreen(state.booking, state.notice, state.busy !== undefined);
      case 'rescheduling':
        return reschedulingScreen(state);
      case 'rescheduled':
        return rescheduledScreen(state.booking, state.previous);
      case 'cancelled':
        return cancelledScreen(state.booking);
    }
  }

  function paint(): void {
    if (root === null) {
      return;
    }
    root.replaceChildren(screen());
    if (root.isConnected) {
      root.querySelector('h1')?.focus();
    }
  }

  // ---------- invocacion del router ----------

  return (context) => {
    const nextToken = context.params['token'] ?? '';
    const continuing = root !== null && root.isConnected && nextToken === token;
    const fresh = h('div', { class: 'manage-root' });
    urlAction = parseAction(context.query);
    root = fresh;

    if (!continuing) {
      token = nextToken;
      generation += 1;
      resetSession();
      confirmingCancel = false;
      if (!isPlausibleToken(token)) {
        state = { screen: 'invalid' };
        paint();
        return fresh;
      }
      state = initialState();
      paint();
      void load();
      return fresh;
    }

    // Misma pantalla de turno: el estado en memoria se reconcilia con la URL (atras/adelante del navegador).
    if (state.screen === 'rescheduled' && urlAction === 'detail') {
      state = { screen: 'detail', booking: state.booking };
    }
    if (urlAction === 'detail' && state.screen === 'rescheduling') {
      state = back(state);
      resetSession();
    } else if (urlAction === 'reschedule' && state.screen === 'detail') {
      const opened = openReschedule(state);
      if (opened === state) {
        deps.replaceUrl(managePath(token));
        urlAction = 'detail';
      } else {
        state = opened;
      }
    }
    paint();
    return fresh;
  };
}
