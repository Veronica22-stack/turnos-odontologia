import type { Session } from '../../core/auth/session';
import { alertBox, clearFieldErrors, field, setFieldError } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import type { RouteContext } from '../../router/router';
import { safeNext, type LayoutRoute } from '../../router/safe-next';
import type { AuthGateway } from './auth-gateway';
import { loginFailureMessage, noticeMessage, type LoginFailure } from './auth-messages';
import { validateLogin } from './validation';

export interface LoginViewDeps {
  readonly gateway: Pick<AuthGateway, 'login'>;
  readonly session: Pick<Session, 'get' | 'signIn'>;
  readonly navigate: (path: string, options?: { replace?: boolean }) => void;
  readonly routes: () => readonly LayoutRoute[];
}

const DEFAULT_PRIVATE_HOME = '/agenda';
const CHANGE_PASSWORD_PATH = '/cambiar-contrasena';

function failureAlert(failure: LoginFailure): HTMLElement {
  const message = loginFailureMessage(failure);
  const locked = failure.kind === 'locked';
  return alertBox({
    variant: locked ? 'warning' : 'danger',
    role: 'alert',
    icon: locked ? 'lock' : 'error',
    title: message.title,
    ...(message.detail === undefined ? {} : { text: message.detail }),
  });
}

/** Vista `/login` (design D6): validacion local, envio unico, mensajes genericos y destino seguro. */
export function createLoginView(deps: LoginViewDeps): (context: RouteContext) => HTMLElement {
  return (context) => {
    const next = safeNext(context.query.get('next'), deps.routes());

    const email = field({
      id: 'email',
      label: 'Email',
      type: 'email',
      autocomplete: 'username',
      inputmode: 'email',
    });
    const password = field({
      id: 'password',
      label: 'Contraseña',
      type: 'password',
      autocomplete: 'current-password',
      reveal: true,
    });
    const submitLabel = 'Ingresar';
    const button = h('button', { class: 'btn btn-primary btn-lg btn-block', attrs: { type: 'submit' } }, submitLabel);
    const alertSlot = h('div', { class: 'auth-alert' });
    const form = h(
      'form',
      { class: 'auth-form', attrs: { novalidate: '' } },
      email.element,
      password.element,
      h('div', { class: 'auth-actions' }, button),
    );

    let submitting = false;

    function setSubmitting(value: boolean): void {
      submitting = value;
      email.input.readOnly = value;
      password.input.readOnly = value;
      if (value) {
        button.setAttribute('aria-busy', 'true');
        button.setAttribute('aria-disabled', 'true');
        button.replaceChildren(h('span', { class: 'spinner', aria: { hidden: 'true' } }), 'Ingresando…');
      } else {
        button.removeAttribute('aria-busy');
        button.removeAttribute('aria-disabled');
        button.replaceChildren(submitLabel);
      }
    }

    function showFailure(failure: LoginFailure): void {
      alertSlot.replaceChildren(failureAlert(failure));
      if (failure.kind === 'invalid-credentials') {
        password.input.value = '';
      }
      password.input.focus();
    }

    function succeed(user: Parameters<Session['signIn']>[0]): void {
      deps.session.signIn(user);
      if (user.mustChangePassword) {
        const suffix = next === null ? '' : `?next=${encodeURIComponent(next)}`;
        deps.navigate(`${CHANGE_PASSWORD_PATH}${suffix}`, { replace: true });
        return;
      }
      deps.navigate(next ?? DEFAULT_PRIVATE_HOME, { replace: true });
    }

    async function onSubmit(event: Event): Promise<void> {
      event.preventDefault();
      if (submitting) {
        return;
      }
      clearFieldErrors(form);
      alertSlot.replaceChildren();

      const validation = validateLogin({ email: email.input.value, password: password.input.value });
      if (!validation.ok) {
        const inputs: Record<string, HTMLInputElement> = { email: email.input, password: password.input };
        for (const error of validation.errors) {
          const target = inputs[error.field];
          if (target !== undefined) {
            setFieldError(target, error.message);
          }
        }
        const first = validation.errors[0];
        (first === undefined ? undefined : inputs[first.field])?.focus();
        return;
      }

      setSubmitting(true);
      password.conceal();
      const result = await deps.gateway.login(validation.value);
      setSubmitting(false);
      if (result.ok) {
        succeed(result.user);
      } else {
        showFailure(result.failure);
      }
    }

    form.addEventListener('submit', (event) => {
      void onSubmit(event);
    });

    const state = deps.session.get();
    const notice =
      state.status === 'anonymous' && state.notice !== undefined
        ? alertBox({ variant: 'info', role: 'status', icon: 'info', text: noticeMessage(state.notice) })
        : null;

    return h(
      'section',
      { class: 'auth-card', attrs: { 'aria-labelledby': 'login-title' } },
      h(
        'div',
        { class: 'auth-head' },
        h('h1', { attrs: { id: 'login-title' } }, 'Ingresar'),
        h('p', { class: 'lead' }, 'Acceso para el profesional del consultorio. Los pacientes reservan sin cuenta.'),
      ),
      notice,
      alertSlot,
      form,
      h(
        'div',
        { class: 'auth-aside' },
        h('p', {}, '¿Olvidaste tu contraseña? Comunicate con el consultorio para restablecerla.'),
      ),
    );
  };
}
