import { h } from '../../core/dom/h';
import type { FieldError } from '../../core/http/errors';
import { today, type Clock } from '../../shared/utils/dates';
import {
  createFlow,
  flowStepOf,
  goTo,
  next,
  resolve,
  selectDate,
  selectService,
  selectSlot,
  setForm,
  submit,
  urlStepOf,
  type BookingFlowState,
} from './booking-flow';
import { bookingNotice, noServicesMessage, type BookingNotice } from './booking-messages';
import { BOOKING_PATH, parseStep, stepPath } from './booking-step-url';
import type { BookResult, BookingGateway, PublicService } from './booking-types';
import { bookingHead, emptyState, loadingStatus, noticeAlert } from './booking-ui';
import { clearConfirmation, setConfirmation } from './confirmation-store';
import { renderDetailsStep, type DetailsStep } from './details-step';
import { maskEmail } from './mask-email';
import { renderScheduleStep } from './schedule-step';
import { renderServiceStep } from './service-step';
import type { RouteContext } from '../../router/router';

export interface BookingViewDeps {
  readonly gateway: BookingGateway;
  readonly clock: Clock;
  /** Navegacion del router (apila una entrada salvo `replace`). */
  readonly navigate: (path: string, options?: { replace?: boolean }) => void;
  /** Corrige la URL sin volver a renderizar (normalizacion de `?paso=`). */
  readonly replaceUrl: (path: string) => void;
  /** Atras del historial: el paso anterior es la entrada anterior. */
  readonly goBack: () => void;
}

export const CONFIRMATION_PATH = '/reservar/confirmado';

function failureNotice(result: BookResult): BookingNotice | null {
  switch (result.kind) {
    case 'rate-limited':
      return bookingNotice(
        result.retryAfterSeconds === undefined
          ? { kind: 'rate-limited' }
          : { kind: 'rate-limited', retryAfterSeconds: result.retryAfterSeconds },
      );
    case 'contact-limit':
    case 'stale-page':
    case 'unknown-outcome':
      return bookingNotice({ kind: result.kind });
    default:
      return null;
  }
}

/**
 * Vista `/reservar` (design D3): el paso vive en la URL (`?paso=`) y el estado del flujo en memoria,
 * dentro de este *closure*. El router vuelve a invocar la vista en cada navegacion; el estado se
 * conserva mientras el elemento de la invocacion anterior siga conectado (seguimos en `/reservar`)
 * y se descarta al volver desde otra ruta.
 */
export function createBookingView(deps: BookingViewDeps): (context: RouteContext) => HTMLElement {
  let flow: BookingFlowState = createFlow();
  let services: readonly PublicService[] | undefined;
  let unavailable = false;
  let serviceGone = false;
  let lastRoot: HTMLElement | null = null;
  let generation = 0;
  // Resultado del ultimo envio fallido: se aplica al paso 3 aunque la vista se haya vuelto a montar.
  let detailsNotice: BookingNotice | null = null;
  let detailsErrors: readonly FieldError[] = [];
  let rerender: () => void = () => {};

  return (context) => {
    const continuing = lastRoot !== null && lastRoot.isConnected;
    if (!continuing) {
      flow = createFlow();
      services = undefined;
      unavailable = false;
      serviceGone = false;
      detailsNotice = null;
      detailsErrors = [];
    }
    const gen = ++generation;
    const root = h('div', { class: 'booking-root' });
    lastRoot = root;

    const requested = parseStep(context.query);
    if (flow.step !== 'submitting') {
      flow = goTo(flow, flowStepOf(requested));
      const actual = urlStepOf(flow.step);
      const canonical = actual === 1 ? '' : `paso=${actual}`;
      if (context.query.toString() !== canonical) {
        deps.replaceUrl(stepPath(actual));
      }
    }

    const isCurrent = (): boolean => gen === generation;

    function show(content: HTMLElement, focusHeading: boolean): void {
      root.replaceChildren(content);
      if (focusHeading) {
        root.querySelector('h1')?.focus();
      }
    }

    function showCatalogProblem(content: HTMLElement): void {
      show(h('section', { class: 'booking' }, content), false);
    }

    function loadServices(focusHeading: boolean): void {
      show(h('section', { class: 'booking' }, loadingStatus('Cargando prestaciones…')), false);
      void deps.gateway.listServices().then((result) => {
        if (!isCurrent()) {
          return;
        }
        switch (result.kind) {
          case 'ok':
            services = result.services;
            unavailable = result.services.length === 0;
            renderCurrent(focusHeading);
            return;
          case 'rate-limited':
            showCatalogProblem(
              noticeAlert(
                bookingNotice(
                  result.retryAfterSeconds === undefined
                    ? { kind: 'rate-limited' }
                    : { kind: 'rate-limited', retryAfterSeconds: result.retryAfterSeconds },
                ),
              ),
            );
            return;
          case 'connection':
            showCatalogProblem(
              h(
                'div',
                {},
                noticeAlert(bookingNotice({ kind: 'connection', what: 'services' })),
                h(
                  'button',
                  { class: 'btn btn-secondary', attrs: { type: 'button' }, on: { click: () => loadServices(true) } },
                  'Reintentar',
                ),
              ),
            );
            return;
        }
      });
    }

    function advance(): void {
      flow = next(flow);
      deps.navigate(stepPath(urlStepOf(flow.step)));
    }

    function renderCurrent(focusHeading: boolean): void {
      if (unavailable) {
        show(h('section', { class: 'booking' }, bookingHead('Reservar turno'), emptyState(noServicesMessage())), focusHeading);
        return;
      }
      if (flow.step === 'service') {
        if (services === undefined) {
          loadServices(focusHeading);
          return;
        }
        show(
          renderServiceStep({
            services,
            selected: flow.service,
            serviceGone,
            onSelect: (service) => {
              flow = selectService(flow, service, today(deps.clock));
              serviceGone = false;
            },
            onContinue: advance,
          }),
          focusHeading,
        );
        return;
      }
      if (flow.step === 'schedule' && flow.service !== undefined) {
        show(
          renderScheduleStep({
            gateway: deps.gateway,
            clock: deps.clock,
            service: flow.service,
            slotTaken: flow.notice === 'slot-taken',
            initialDate: flow.date,
            ...(flow.slot === undefined ? {} : { selectedSlot: flow.slot }),
            ...(flow.takenStartsAt === undefined ? {} : { suggestFrom: flow.takenStartsAt }),
            onDateChange: (date) => {
              flow = selectDate(flow, date);
            },
            onChoose: (slot) => {
              flow = selectSlot(flow, slot);
            },
            onTerminal: (kind) => {
              if (!isCurrent()) {
                return;
              }
              if (kind === 'no-services') {
                unavailable = true;
                renderCurrent(true);
                return;
              }
              flow = resolve(flow, { kind: 'service-gone' });
              services = undefined;
              serviceGone = true;
              deps.navigate(BOOKING_PATH, { replace: true });
            },
            onBack: deps.goBack,
            onContinue: advance,
          }),
          focusHeading,
        );
        return;
      }
      if ((flow.step === 'details' || flow.step === 'submitting') && flow.service !== undefined && flow.slot !== undefined) {
        const { service, slot } = flow;
        const step = renderDetailsStep({
          service,
          slot,
          values: flow.form,
          submitting: flow.step === 'submitting',
          onChange: (values) => {
            flow = setForm(flow, values);
          },
          onBack: deps.goBack,
          onSubmit: (values) => {
            void send(values, step, service, slot.startsAt);
          },
        });
        if (detailsNotice !== null) {
          step.showNotice(detailsNotice);
        }
        if (detailsErrors.length > 0) {
          step.showServerErrors(detailsErrors);
        }
        show(step.element, focusHeading);
        return;
      }
      show(h('section', { class: 'booking' }), false);
    }

    async function send(
      values: BookingFlowState['form'],
      step: DetailsStep,
      service: PublicService,
      startsAt: string,
    ): Promise<void> {
      detailsNotice = null;
      detailsErrors = [];
      flow = submit(setForm(flow, values));
      step.setSubmitting(true);
      const email = values.email;
      const result = await deps.gateway.book({ serviceId: service.id, startsAt, form: values });
      flow = resolve(flow, result);
      switch (result.kind) {
        case 'confirmed':
          setConfirmation({
            serviceName: result.serviceName,
            startsAt: result.startsAt,
            endsAt: result.endsAt,
            practiceName: result.practiceName,
            maskedEmail: maskEmail(email),
          });
          deps.navigate(CONFIRMATION_PATH, { replace: true });
          return;
        case 'accepted':
          clearConfirmation();
          deps.navigate(CONFIRMATION_PATH, { replace: true });
          return;
        case 'slot-unavailable':
          deps.navigate(stepPath(2), { replace: true });
          return;
        case 'service-gone':
          services = undefined;
          serviceGone = true;
          deps.navigate(BOOKING_PATH, { replace: true });
          return;
        default:
          detailsNotice = failureNotice(result);
          detailsErrors = result.kind === 'fields' ? result.errors : [];
          if (isCurrent()) {
            step.setSubmitting(false);
            step.showNotice(detailsNotice);
            step.showServerErrors(detailsErrors);
          } else {
            rerender();
          }
      }
    }

    rerender = () => renderCurrent(false);
    renderCurrent(false);
    return root;
  };
}
