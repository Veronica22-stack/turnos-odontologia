import type { Session } from '../../core/auth/session';
import { alertBox, clearFieldErrors, field, formIcon, setFieldError, type FormIconName } from '../../core/dom/form';
import { h } from '../../core/dom/h';
import type { RouteContext } from '../../router/router';
import { safeNext, type LayoutRoute } from '../../router/safe-next';
import type { AuthGateway, ChangePasswordFailure } from './auth-gateway';
import { CURRENT_PASSWORD_INVALID, changePasswordFailureMessage, fieldErrorMessage } from './auth-messages';
import { passwordRules, validatePasswordChange, type PasswordChangeInput } from './validation';

export interface ChangePasswordViewDeps {
  readonly gateway: Pick<AuthGateway, 'changePassword'>;
  readonly session: Pick<Session, 'get' | 'endAfterPasswordChange'>;
  readonly navigate: (path: string, options?: { replace?: boolean }) => void;
  readonly routes: () => readonly LayoutRoute[];
  /** Mismo "Salir" que el del app shell privado (cierra la sesion y va a /login). */
  readonly onLogout: () => void;
}

type RuleState = 'pending' | 'ok' | 'bad';

const FIELD_ORDER = ['currentPassword', 'newPassword', 'confirmPassword'] as const;
const RULE_ICONS: Record<RuleState, FormIconName> = { pending: 'dot', ok: 'check', bad: 'cross' };

/** Vista `/cambiar-contrasena` (design D6): modo obligatorio (primer ingreso) o voluntario. */
export function createChangePasswordView(deps: ChangePasswordViewDeps): (context: RouteContext) => HTMLElement {
  return (context) => {
    const state = deps.session.get();
    const mandatory = state.status === 'authenticated' && state.user.mustChangePassword;
    const next = safeNext(context.query.get('next'), deps.routes());

    const current = field({
      id: 'currentPassword',
      label: 'Contraseña actual',
      type: 'password',
      autocomplete: 'current-password',
      reveal: true,
    });
    const fresh = field({
      id: 'newPassword',
      label: 'Contraseña nueva',
      type: 'password',
      autocomplete: 'new-password',
      reveal: true,
      help: 'Mínimo 12 caracteres. Podés usar una frase con espacios.',
    });
    const confirm = field({
      id: 'confirmPassword',
      label: 'Repetí la contraseña nueva',
      type: 'password',
      autocomplete: 'new-password',
      reveal: true,
    });
    const fields = [current, fresh, confirm] as const;
    const inputs: Record<string, HTMLInputElement> = {
      currentPassword: current.input,
      newPassword: fresh.input,
      confirmPassword: confirm.input,
    };

    const ruleLabels = ['Al menos 12 caracteres', 'Distinta de la actual', 'Las dos contraseñas nuevas coinciden'];
    const ruleItems = ruleLabels.map(() => h('li', { class: 'pending' }));
    const rulesList = h('ul', { class: 'rules', aria: { label: 'Requisitos de la contraseña nueva' } }, ...ruleItems);

    const submitLabel = 'Guardar contraseña';
    const button = h('button', { class: 'btn btn-primary btn-lg btn-block', attrs: { type: 'submit' } }, submitLabel);
    const secondary = mandatory
      ? h(
          'button',
          { class: 'btn btn-secondary btn-lg btn-block', attrs: { type: 'button' }, on: { click: () => deps.onLogout() } },
          'Salir',
        )
      : h('a', { class: 'btn btn-secondary btn-lg btn-block', attrs: { href: '/agenda' } }, 'Cancelar');
    const alertSlot = h('div', { class: 'auth-alert' });
    const form = h(
      'form',
      { class: 'auth-form', attrs: { novalidate: '' } },
      current.element,
      fresh.element,
      rulesList,
      confirm.element,
      h('div', { class: 'auth-actions' }, button, secondary),
    );

    let submitting = false;
    let attempted = false;

    function values(): PasswordChangeInput {
      return {
        currentPassword: current.input.value,
        newPassword: fresh.input.value,
        confirmPassword: confirm.input.value,
      };
    }

    function renderRules(): void {
      const rules = passwordRules(values());
      const met = [rules.minLength, rules.differs, rules.matches];
      ruleItems.forEach((item, index) => {
        const ok = met[index] === true;
        const status: RuleState = ok ? 'ok' : attempted ? 'bad' : 'pending';
        item.className = status;
        item.replaceChildren(formIcon(RULE_ICONS[status]), h('span', {}, ruleLabels[index] ?? ''));
      });
    }

    function setSubmitting(value: boolean): void {
      submitting = value;
      for (const f of fields) {
        f.input.readOnly = value;
      }
      if (value) {
        button.setAttribute('aria-busy', 'true');
        button.setAttribute('aria-disabled', 'true');
        button.replaceChildren(h('span', { class: 'spinner', aria: { hidden: 'true' } }), 'Guardando…');
      } else {
        button.removeAttribute('aria-busy');
        button.removeAttribute('aria-disabled');
        button.replaceChildren(submitLabel);
      }
    }

    function showMessage(kind: 'connection' | 'stale-page'): void {
      alertSlot.replaceChildren(
        alertBox({ variant: 'danger', role: 'alert', icon: 'error', text: changePasswordFailureMessage(kind) }),
      );
    }

    function showFieldErrors(errors: ReadonlyArray<{ field: string; message: string }>): void {
      for (const error of errors) {
        const target = inputs[error.field];
        if (target !== undefined) {
          setFieldError(target, error.message);
        }
      }
      const first = FIELD_ORDER.find((name) => errors.some((error) => error.field === name));
      if (first !== undefined) {
        inputs[first]?.focus();
      }
    }

    function showFailure(failure: ChangePasswordFailure): void {
      switch (failure.kind) {
        case 'current-invalid':
          current.input.value = '';
          showFieldErrors([{ field: 'currentPassword', message: CURRENT_PASSWORD_INVALID }]);
          renderRules();
          return;
        case 'fields': {
          const mapped = failure.errors
            .filter((error) => inputs[error.field] !== undefined)
            .map((error) => ({ field: error.field, message: fieldErrorMessage(error.field, error.code) }));
          if (mapped.length === 0) {
            showMessage('connection');
          } else {
            showFieldErrors(mapped);
          }
          return;
        }
        case 'expired':
          deps.navigate('/login', { replace: true });
          return;
        case 'stale-page':
        case 'connection':
          showMessage(failure.kind);
          return;
      }
    }

    async function onSubmit(event: Event): Promise<void> {
      event.preventDefault();
      if (submitting) {
        return;
      }
      attempted = true;
      clearFieldErrors(form);
      alertSlot.replaceChildren();
      renderRules();

      const validation = validatePasswordChange(values());
      if (!validation.ok) {
        showFieldErrors(validation.errors);
        return;
      }

      setSubmitting(true);
      for (const f of fields) {
        f.conceal();
      }
      const result = await deps.gateway.changePassword(validation.value);
      setSubmitting(false);
      if (!result.ok) {
        showFailure(result.failure);
        return;
      }
      // El servidor cerro todas las sesiones: se vuelve a ingresar con la contraseña nueva.
      for (const f of fields) {
        f.input.value = '';
      }
      deps.session.endAfterPasswordChange();
      const suffix = next === null ? '' : `?next=${encodeURIComponent(next)}`;
      deps.navigate(`/login${suffix}`, { replace: true });
    }

    form.addEventListener('input', renderRules);
    form.addEventListener('submit', (event) => {
      void onSubmit(event);
    });
    renderRules();

    return h(
      'section',
      { class: 'auth-card', attrs: { 'aria-labelledby': 'cp-title' } },
      h(
        'div',
        { class: 'auth-head' },
        h('h1', { attrs: { id: 'cp-title' } }, mandatory ? 'Elegí una contraseña nueva' : 'Cambiar contraseña'),
        h(
          'p',
          { class: 'lead' },
          mandatory
            ? 'Es tu primer ingreso: por seguridad, reemplazá la contraseña de instalación antes de usar la agenda.'
            : 'Ingresá tu contraseña actual y elegí una nueva.',
        ),
      ),
      alertSlot,
      form,
    );
  };
}
