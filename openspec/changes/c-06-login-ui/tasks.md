# Tasks: C-06 `login-ui`

> Change **[UI]**: NO empezar el apply hasta que los mockups de `mockups/` estén aprobados (`Approval: approved` en `design.md` §UI Mockups y en `mockups/README.md`). Los mockups aprobados (`mockups/*.html` y sus capturas en `mockups/previews/`) son criterio de aceptación de los grupos 6 y 7.
> Governance **MEDIO**: implementar con checkpoints; reportar al usuario las decisiones no obvias y cualquier desvío de `design.md` (en especial del contrato D2).
> **C-04 está implementado y su contrato está confirmado contra el código (design D2)**: los tests usan un `fetch` simulado con ese contrato real (`userId` en vez de `id`, sin `fullName`, `INVALID_CREDENTIALS`, `RATE_LIMITED`, `CSRF_REQUIRED`, change-password 200 que cierra la sesión). No tocar `backend/`.
> Strict TDD por tarea: RED (test que falla, ejecutado) → GREEN (mínimo código) → TRIANGULATE (segundo caso distinto) → REFACTOR (tests verdes tras cada paso). No escribir código de producción antes de ver fallar su test.
> Comandos (desde `frontend/`): `npx vitest run <ruta>` (puntual), `npm test`, `npm run typecheck`, `npm run build`.
> Reglas duras: TS estricto sin `any` (9), sin `innerHTML` (10), solo tokens (11), **ninguna dependencia nueva** (12); nunca escribir emails ni contraseñas en consola/URL/storage (6). En tests usar solo datos ficticios (`romero@ejemplo.com`).

## 0. Red de seguridad

- [ ] 0.1 Ejecutar `npm test` y `npm run typecheck` desde `frontend/` y anotar la línea base (suite de C-05 verde); si algo falla, detenerse y reportarlo como falla preexistente

## 1. Funciones puras de auth (specs `professional-login`, `password-change`)

- [ ] 1.1 RED: `src/features/auth/validation.test.ts` — `validateLogin({ email: '', password: '' })` → errores en `email` ("Ingresá tu email.") y `password` ("Ingresá tu contraseña."), orden email→password; `'romero@'` → "Ingresá un email válido."; `' romero@ejemplo.com '` válido (se recorta); verificar que falla (módulo inexistente)
- [ ] 1.2 GREEN: `validateLogin` en `src/features/auth/validation.ts`; verificar que pasa
- [ ] 1.3 RED: `validatePasswordChange` — vacíos → tres errores; nueva `corta123` → "Usá al menos 12 caracteres."; 12 caracteres exactos válida; 129 → "Usá como máximo 128 caracteres."; igual a la actual → "La contraseña nueva tiene que ser distinta de la actual."; confirmación distinta → "Las contraseñas no coinciden."; contraseña nueva de solo espacios → "Ingresá una contraseña nueva."; conteo en unidades UTF-16 como el servidor (`'ñ'.repeat(12)` válida; 6 emojis = 12 unidades válida; 65 emojis = 130 unidades → "Usá como máximo 128 caracteres."); `passwordRules(...)` devuelve `{ minLength, differs, matches }` booleanos para la lista en vivo
- [ ] 1.4 GREEN + REFACTOR: `validatePasswordChange` y `passwordRules` (design D2 política); verificar que pasa
- [ ] 1.5 RED→GREEN: `src/features/auth/retry-after.test.ts` + `retry-after.ts` — `840` → "en unos 14 minutos"; `61` → "en unos 2 minutos" (`ceil(seg/60)`); `30` y `60` → "en 1 minuto" (singular, sin "unos"); `undefined`, `0` o negativo → "en unos minutos"
- [ ] 1.6 RED→GREEN: `src/features/auth/auth-messages.test.ts` + `auth-messages.ts` — `INVALID_CREDENTIALS` y `VALIDATION_FAILED` de login → mismo texto genérico; `RATE_LIMITED` → título y cuerpo con `retry-after`; `network`/`timeout`/`invalid-response`/código desconocido → mensaje de conexión; `CSRF_REQUIRED` → "La página quedó desactualizada…"; `Size`/`NotBlank` de `newPassword` → texto por campo; aviso `password-changed`; ningún texto proviene de `title`/`detail`

## 2. Cliente HTTP: intento de credenciales y `Retry-After` (spec `api-client`)

- [ ] 2.1 RED: `src/core/http/client.test.ts` — 401 `problem+json` `INVALID_CREDENTIALS` con `{ credentialAttempt: true }` → `problem{status:401, code:'INVALID_CREDENTIALS'}` y `onUnauthorized` **no** llamado; el mismo 401 sin la opción → `unauthorized` y `onUnauthorized` llamado (regresión C-05); mismo caso en `POST /auth/change-password` con `credentialAttempt` → `problem` con `INVALID_CREDENTIALS` y 401 `UNAUTHENTICATED` también como `problem` (sin `onUnauthorized`); 429 `RATE_LIMITED` con `Retry-After: 45` → `retryAfterSeconds: 45`; `Retry-After` con fecha HTTP o texto → sin `retryAfterSeconds`
- [ ] 2.2 GREEN + REFACTOR: opción `credentialAttempt` y `retryAfterSeconds` en `client.ts`/`errors.ts` (design D3); verificar que toda la suite de `client.test.ts` sigue verde

## 3. Sesión (spec `session-state`)

- [ ] 3.1 RED: `src/core/auth/session.test.ts` — `me` 200 con la forma real `{ userId, role, mustChangePassword, expiresAt }` (sin `id` ni `fullName`) → `authenticated` con esos datos (regresión: el decodificador de C-05 exigía `id`/`fullName`); sin `mustChangePassword` → `false`; con `true` → `true`; cuerpo sin `userId` → error; `fullName` presente se conserva; `signIn(user)` desde `anonymous{notice:'expired'}` → `authenticated` sin aviso y suscriptor notificado una vez; `endAfterPasswordChange()` desde `authenticated` → `anonymous{notice:'password-changed'}` y suscriptor notificado; `endAfterPasswordChange()` ya `anonymous` deja el aviso `password-changed`
- [ ] 3.2 GREEN + REFACTOR: `SessionUser` con la forma real (`userId`, `role`, `mustChangePassword: boolean` con `false` por defecto, `expiresAt?`, `fullName?`), notice `password-changed`, `signIn`, `endAfterPasswordChange` (design D4); actualizar `session-gateway.ts` y los fixtures de C-05 (`session.test.ts`, `client.test.ts`, `guards.test.ts`, `routes.test.ts`, `main.test.ts`) que usan `id`/`fullName`; `private-layout`/`main.ts` usan `fullName ?? 'Profesional'` (test: sin `fullName` se ve "Profesional"); `npm run typecheck` verde

## 4. Gateway de auth (contrato confirmado con C-04, design D2)

- [ ] 4.1 RED: `src/features/auth/auth-gateway.test.ts` (fetch falso) — `login({email,password})` envía `POST /api/v1/auth/login` con cuerpo JSON exacto y cabecera CSRF; 200 válido `{ userId, role, mustChangePassword, expiresAt }` → `{ ok, user }`; 200 sin `userId` → `invalid-response`; 401 `INVALID_CREDENTIALS` y 400 `VALIDATION_FAILED` → `{ kind: 'invalid-credentials' }`; 429 `RATE_LIMITED` → `{ kind: 'locked', retryAfterSeconds }`; 403 `CSRF_REQUIRED` → `{ kind: 'stale-page' }`; red → `{ kind: 'connection' }`
- [ ] 4.2 GREEN: `login` en `src/features/auth/auth-gateway.ts` usando `credentialAttempt: true`; verificar que pasa
- [ ] 4.3 RED→GREEN: `changePassword({currentPassword,newPassword})` → `POST /api/v1/auth/change-password`; usa `credentialAttempt: true`; 200 sin cuerpo (y 204) → ok; 401 `INVALID_CREDENTIALS` → `{ kind: 'current-invalid' }` (sin expirar la sesión); 400 `VALIDATION_FAILED` con `errors[{field:'newPassword', code:'Size'}]` → `{ kind: 'fields', errors }`; 401 `UNAUTHENTICATED` → `{ kind: 'expired' }` y callback `onSessionExpired` invocado una vez; 403 `CSRF_REQUIRED` → `stale-page`; red → `connection`

## 5. Guards y rutas (spec `spa-routing`)

- [ ] 5.1 RED: `src/router/guards.test.ts` — `requireSession` con `mustChangePassword: true` en `/pacientes` → redirect `/cambiar-contrasena?next=%2Fpacientes` (replace); con `false` → allow; `redirectIfAuthenticated` con cambio pendiente y `next=%2Fpacientes` → `/cambiar-contrasena?next=%2Fpacientes`, con `next` inválido → `/cambiar-contrasena`; `requireAuthenticated` anónimo → `/login`, autenticado con cambio pendiente → allow (sin bucle)
- [ ] 5.2 GREEN + REFACTOR: guards de design D5; verificar que la suite de C-05 de `guards.test.ts` sigue verde
- [ ] 5.3 RED→GREEN: `src/app/routes.test.ts` — existe la ruta `change-password` (`/cambiar-contrasena`, layout `public`, título "Cambiar contraseña", guard `requireAuthenticated`); `/login` usa la vista real (no el *placeholder*); `safeNext('/cambiar-contrasena')` → `null`

## 6. Vista de ingreso (spec `professional-login`; acepta contra `mockups/login*.html`)

- [ ] 6.1 RED: `src/core/dom/form.test.ts` — helper de campo: `label[for]`↔`input[id]`; `setFieldError` pone `aria-invalid="true"`, crea `.error-msg` con `id` y lo agrega a `aria-describedby` sin borrar la ayuda; `clearFieldErrors` lo revierte; botón "Mostrar contraseña" alterna `type` y `aria-pressed`
- [ ] 6.2 GREEN: `src/core/dom/form.ts` con `h()`; verificar que pasa
- [ ] 6.3 RED: `src/features/auth/login-view.test.ts` (jsdom, gateway y navegación falsos) — vista inicial (título, `autocomplete`, botón habilitado, sin errores); envío vacío → sin llamada, `aria-invalid` en ambos y foco en email; `romero@` → mensaje de email
- [ ] 6.4 GREEN: `src/features/auth/login-view.ts` (estado `idle`, validación local, foco); verificar que pasa
- [ ] 6.5 RED→GREEN: envío válido con respuesta pendiente → botón "Ingresando…" `aria-busy="true"`, inputs `readonly`, segundo `submit` ignorado (1 llamada); credenciales inválidas → alerta `role="alert"` genérica, contraseña vacía y enfocada, email conservado; email inexistente → texto idéntico; cuenta bloqueada (401 `INVALID_CREDENTIALS`) → mismo texto genérico; 400 `VALIDATION_FAILED` → mismo texto; 429 `RATE_LIMITED` con `Retry-After: 45` → "en 1 minuto"; 429 sin cabecera → "en unos minutos"; red → mensaje de conexión y botón habilitado
- [ ] 6.6 RED→GREEN: éxito con `next=%2Fpacientes` → `session.signIn` y `navigate('/pacientes', { replace: true })`; `next` externo → `/agenda`; `mustChangePassword: true` → `/cambiar-contrasena?next=%2Fpacientes`; sesión `anonymous{expired}` → aviso `role="status"` "Tu sesión venció. Ingresá de nuevo." sobre el formulario habilitado; `offline` → aviso de conexión; `password-changed` → "Contraseña actualizada. Ingresá de nuevo con la contraseña nueva."
- [ ] 6.7 RED→GREEN: sin rastros — espiar `console.*`; tras un intento fallido ni la consola, ni `location.href`, ni `localStorage`/`sessionStorage` contienen el email ni la contraseña
- [ ] 6.8 Estilos: `src/styles/forms.css` (design D7) importado desde `main.ts`, `--layout-auth-width` en `layout.css`; `styles.test.ts` verde sin nuevas excepciones; `aria-current="page"` en "Acceso profesionales" cuando la ruta es `/login` (test en `public-layout.test.ts`)

## 7. Vista de cambio de contraseña (spec `password-change`; acepta contra `mockups/change-password*.html`)

- [ ] 7.1 RED: `src/features/auth/change-password-view.test.ts` — modo obligatorio: título "Elegí una contraseña nueva", explicación, tres campos con `autocomplete` correctos, "Mínimo 12 caracteres." vinculado por `aria-describedby`, botón "Salir" y sin "Cancelar"; modo voluntario: título "Cambiar contraseña" y "Cancelar" → `/agenda`
- [ ] 7.2 GREEN: `src/features/auth/change-password-view.ts`; verificar que pasa
- [ ] 7.3 RED→GREEN: validación local (corta, distinta, igual a la actual) sin llamada y foco en el primer inválido; 12 exactos → una llamada; lista de requisitos en vivo (punto/✓ al escribir, ✕ solo tras intentar enviar)
- [ ] 7.4 RED→GREEN: servidor — 401 `INVALID_CREDENTIALS` → campo actual vacío, `aria-invalid` y mensaje, URL sin cambios, sesión intacta (sin `onUnauthorized`); 400 `Size` en `newPassword` → "Usá entre 12 y 128 caracteres." en ese campo; 401 `UNAUTHENTICATED` → navega a `/login` (sesión `anonymous{expired}`); 403 `CSRF_REQUIRED` → mensaje de página desactualizada
- [ ] 7.5 RED→GREEN: éxito (200) obligatorio con `next=%2Fpacientes` → `endAfterPasswordChange`, campos vacíos, `navigate('/login?next=%2Fpacientes', { replace: true })`; voluntario → `/login`; `next=%2F%2Fevil.example` → `/login`; "Salir" → `onLogout` invocado; sin rastros de contraseñas en consola/URL/storage

## 8. Integración (`main.test.ts` con `bootstrap` y `fetch` simulado)

- [ ] 8.1 RED→GREEN: `/agenda` sin sesión → `/login?next=%2Fagenda` → login OK → `/agenda` con layout privado y una sola petición a `/auth/me` (la del arranque)
- [ ] 8.2 RED→GREEN: primer ingreso (`mustChangePassword: true`) → `/cambiar-contrasena?next=%2Fagenda` → 200 → `/login?next=%2Fagenda` con aviso de contraseña actualizada → login con `mustChangePassword: false` → `/agenda`; luego `/pacientes` no redirige
- [ ] 8.3 RED→GREEN: sesión autenticada en `/pacientes`, 401 en una llamada → `/login?next=%2Fpacientes` con aviso de vencida → reingreso → `/pacientes`
- [ ] 8.4 RED→GREEN: `/agenda` → `/pacientes` → "Salir" → `/login`; `history.back()` → `/login?next=%2Fagenda` sin contenido privado
- [ ] 8.5 RED→GREEN: login con 401 en `/login` no dispara `handleUnauthorized` ni cambia la URL

## 9. Verificación integral

- [ ] 9.1 Ejecutar `npm test`, `npm run typecheck` y `npm run build` desde `frontend/`; todo verde y `git diff frontend/package.json` vacío
- [ ] 9.2 Revisión de reglas duras sobre el diff: `grep -rnE "innerHTML|outerHTML|insertAdjacentHTML|: any|as any" frontend/src --include=*.ts` sin resultados en producción; sin `console.*` en `features/auth` ni `core/`; sin colores ni medidas literales en `src/styles` (salvo las documentadas)
- [ ] 9.3 Verificación manual con `npm run dev` (backend apagado): `/login` muestra el formulario; enviar → mensaje de conexión; `/agenda` → `/login?next=%2Fagenda`; consola sin errores salvo los fallos esperados de `/api/v1/auth/*` (registrar el resultado en el resumen del apply)

## Workflow follow-up

- Verify: comparar `/login` (estados inicial, error, bloqueo, vencida, enviando, conexión) y `/cambiar-contrasena` (obligatorio, errores, actual inválida) con los mockups aprobados a 390x844 y 1440x900 (capturas en `mockups/verify/`; estados de error forzados interceptando `/api/v1/auth/*` en Playwright), con consola y red inspeccionadas; registrar `Visual verify` en `design.md`.
- Contrato de design D2 ya confirmado con el código de C-04; pedidos pendientes P1–P5 en design §Riesgos (ninguno bloqueante). Si C-04 cambia, actualizar `auth-gateway.ts`/`auth-messages.ts` y sus tests.
- Verify visual: los mockups `login-locked`, `login-error` y `change-password-current-invalid` usan códigos y tiempos supuestos (423, "14 minutos", 400); comparar solo apariencia, no el texto de espera; el estado "Contraseña actualizada" de `/login` no tiene mockup (design §UI Mockups).
- Prueba E2E real contra el backend de C-04 (login, cambio obligatorio con cierre de sesión, reingreso, logout con CSRF).
- Archivar el change tras la aprobación del verify.
