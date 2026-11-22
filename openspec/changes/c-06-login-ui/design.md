# Diseño: C-06 `login-ui`

## Context

Ver `proposal.md` §Why. Estado relevado el 2026-11-21 (tras C-05, merge `797f17c`):

- `frontend/src/app/routes.ts`: `/login` es un *placeholder* (`loginView`) que ya muestra el aviso `expired`/`offline` de la sesión; guard `redirectIfAuthenticated`. No existe `/cambiar-contrasena`.
- `router/guards.ts`: `requireSession` (anónimo → `/login?next=…`), `redirectIfAuthenticated` (→ `safeNext(next) ?? '/agenda'`), `rootRedirect`. `router/safe-next.ts` acepta solo rutas **privadas** declaradas.
- `core/auth/session.ts`: `SessionState` (`unknown` / `anonymous{notice?}` / `authenticated{user}`), `SessionUser { id, fullName, role, mustChangePassword? }` decodificado por `httpSessionGateway` con la forma **supuesta** de `/me` (no coincide con la real: ver D2/D4) y `mustChangePassword` **no aplicado**; `SessionGateway { me, logout }`.
- `core/http/client.ts`: `request()` devuelve `HttpResult<T>`; **todo 401 → `{ kind: 'unauthorized' }` + `onUnauthorized()`** salvo `sessionProbe`. Los errores `problem` no exponen cabeceras (no hay `Retry-After`).
- `main.ts` `handleUnauthorized`: solo actúa si la sesión estaba `authenticated` (→ `expire()`) y solo navega a `/login?next=` si la ruta actual es de layout `private`.
- `layouts/public-layout.ts`: header (logo + "Acceso profesionales" → `/login`), `main#main`, footer con `(TBD)`. Estilos en `src/styles/{base,layout}.css` solo con tokens (verificado por `styles.test.ts`); `architecture.test.ts` prohíbe `innerHTML` & cía.
- Design system Revision 1 (aprobado): `components.html` define `.field`, `.input` (altura `--control-height-lg`, `aria-invalid` con borde 2px), `.error-msg` (ícono + texto), `.help`, `.btn-primary`/`.btn-lg`/`.btn-block`, estado `aria-busy` con `.spinner`, `.alert-{info,warning,danger}`.
- **C-04 está implementado** (archivado 2026-11-21, `openspec/specs/auth-session`, código en `backend/.../auth/`); PA-04 resuelta (sesión de servidor + cookie + CSRF). El contrato de D2 se contrastó con el **código** (la fuente de verdad). KB: RN-AU-03 (≥ 12 caracteres, bloqueo temporal tras N intentos, sin N ni duración definidos), RN-AU-04 (30 min de inactividad), Flujo 1 (mensaje genérico; bloqueo "con indicación de reintentar luego"; sesión expirada → login conservando la ruta), C-03 (`must_change_password = true` en la cuenta de bootstrap).

Restricciones: reglas duras 6 (no loguear email), 9–12 (TS estricto sin `any`, sin `innerHTML`, solo tokens, sin dependencias nuevas), Strict TDD, governance MEDIO.

## Goals / Non-Goals

**Goals:**
- Pantallas reales de ingreso y cambio de contraseña sobre las piezas de C-05, sin reescribir router, cliente HTTP ni layouts.
- Contrato con C-04 explícito y concentrado en un solo módulo (`features/auth/auth-gateway.ts` + mapa de códigos), para que un ajuste de C-04 no toque las vistas.
- Todos los escenarios con lógica cubiertos por Vitest con `fetch` simulado (sin backend).

**Non-Goals:**
- Recuperación de contraseña olvidada, 2FA, "recordarme", medidor de fortaleza (PA-04).
- Lista de contraseñas comprometidas en el cliente (la hace C-04 si se decide; el cliente solo mapea el código).
- Entrada de menú para el cambio voluntario (change de Configuración); C-06 deja la ruta lista.
- Cuenta regresiva en vivo del bloqueo (se muestra un tiempo aproximado estático).

## Decisions

### D1 — Estructura
```
frontend/src/features/auth/
├── auth-gateway.ts         # login(), changePassword() sobre HttpClient; decodifica y clasifica errores
├── auth-messages.ts        # código/resultado → texto en español (única fuente de copy de error)
├── validation.ts           # validateLogin(), validatePasswordChange() — funciones puras
├── retry-after.ts          # segundos → "unos N minutos" (puro)
├── login-view.ts           # vista /login (h(), estados, foco)
└── change-password-view.ts # vista /cambiar-contrasena (modo obligatorio/voluntario)
src/core/dom/form.ts        # helpers de campo accesible: field(), setFieldError(), clearFieldErrors()
src/router/guards.ts        # + requirePasswordChanged / requireSession ampliado (D5)
src/core/auth/session.ts    # SessionUser real de C-04 + signIn(user), endAfterPasswordChange()
src/core/http/client.ts     # + opción credentialAttempt y retryAfterSeconds (D3)
src/core/auth/session-gateway.ts # decodificador de /me con la forma real (D2/D4)
src/styles/forms.css        # .auth-card, .field, .input, .btn, .alert, .spinner (solo tokens)
```
- **Por qué `features/auth/`**: KB 08 organiza por feature; `core/auth` queda como estado de sesión transversal y `features/auth` como pantallas y su contrato HTTP. Las funciones puras (validación, mensajes, retry) se prueban sin DOM.
- **Alternativa**: ampliar `SessionGateway` con `login/changePassword` — mezcla la consulta de sesión (usada por guards en toda la app) con flujos de pantalla y obliga a tocar el adaptador de C-05; se descarta. `session.signIn(user)` solo recibe el usuario ya decodificado.

### D2 — Contrato confirmado con C-04 (contrastado con el código)
Fuente: `backend/src/main/java/ar/turnosodontologia/consultorio/{auth,shared}` y los specs `auth-session`, `error-responses`, `api-access-policy`. Convenciones de C-05 D4 confirmadas: sesión de servidor con cookie, CSRF `XSRF-TOKEN` / `X-XSRF-TOKEN`, errores `application/problem+json` con `status`, `title`, `code` y, en validación, `errors[{field, code}]`.

| Endpoint | Petición | Éxito | Errores reales |
|----------|----------|-------|----------------|
| `POST /api/v1/auth/login` (CSRF-exento) | `{ "email", "password" }` | **200** `{ userId, role, mustChangePassword, expiresAt }` (mismo cuerpo que `/me`; **sin** `id` ni `fullName`) + cookie `SESSIONID` nueva (rotación de id) + cookie `XSRF-TOKEN` rotada (también cabecera `X-CSRF-TOKEN`) | **401** `INVALID_CREDENTIALS` para contraseña incorrecta, email inexistente **y cuenta bloqueada** (indistinguibles) · **429** `RATE_LIMITED` con `Retry-After` en segundos (10 intentos por minuto por IP+email, se evalúa antes de buscar la cuenta) · **400** `VALIDATION_FAILED` con `errors[{field:'email'\|'password', code:'NotBlank'\|'Email'\|'Size'}]` · 400 `MALFORMED_REQUEST` ante cuerpo ilegible. **No existe 423 ni `AUTH_ACCOUNT_LOCKED`** |
| `POST /api/v1/auth/change-password` (exige CSRF) | `{ "currentPassword", "newPassword" }` (`newPassword` 12–128, `currentPassword` ≤ 128; ambos `@NotBlank`) | **200 sin cuerpo** (no 204). **Invalida TODAS las sesiones de la cuenta, incluida la actual**, y borra la cookie `SESSIONID` (`Max-Age=0`): la persona debe volver a ingresar | **401** `INVALID_CREDENTIALS` si la contraseña actual es incorrecta (**el mismo 401 que una sesión vencida, pero con otro código**) · **401** `UNAUTHENTICATED` sesión vencida/ausente · **400** `VALIDATION_FAILED` con `errors[{field:'newPassword'\|'currentPassword', code:'Size'\|'NotBlank'}]` (un único código `Size` para corta y larga) · **403** `CSRF_REQUIRED` · 403 `PASSWORD_CHANGE_REQUIRED` no aplica a esta ruta |
| `GET /api/v1/auth/me` | — | **200** `{ userId, role, mustChangePassword, expiresAt }` (`expiresAt` ISO UTC; `role` = `DENTIST`) | **401** `UNAUTHENTICATED` sin sesión o vencida. La cookie `XSRF-TOKEN` se emite en **toda** respuesta que pase por la cadena (incluido este 401) |
| `POST /api/v1/auth/logout` (permitAll, **exige CSRF**) | — | **204**; borra `SESSIONID` y la cookie CSRF | **403** `CSRF_REQUIRED` si falta el token; sin sesión pero con CSRF válido → 204 |
| Cualquier otra ruta `/api/v1/**` con `mustChangePassword = true` | — | — | **403** `PASSWORD_CHANGE_REQUIRED` (confirmado: la barrera real está en el servidor) |

- **Cookies**: sesión `SESSIONID` (`HttpOnly`, `SameSite=Strict`, `Path=/`, `Secure` salvo perfil dev; el frontend no la lee). CSRF `XSRF-TOKEN` (legible por JS) y cabecera `X-XSRF-TOKEN` coinciden con `cookieCsrfTransport` de C-05. Como el login es CSRF-exento y la cookie se emite en cualquier respuesta, **el primer `POST /login` no necesita token previo** (el pedido de emitir la cookie en el 401 de `/me` ya no hace falta).
- **Forma del usuario**: `{ userId, role, mustChangePassword, expiresAt }`. `fullName` no viaja (la columna `app_user.full_name` es opcional y C-04 no la expone): el frontend lo trata como opcional con etiqueta de respaldo "Profesional" (ver Riesgos, pedido P1).
- **Política de contraseña** (servidor autoridad: `@Size(min=12,max=128)` + `@NotBlank`, `PasswordPolicy`): se mide en **unidades UTF-16** (`String.length()`). El cliente usa el mismo criterio (`s.length`, no puntos de código) para no aceptar ni rechazar de más; además rechaza una contraseña nueva compuesta solo por espacios (`NotBlank`). Sin reglas de composición. **El servidor no valida** "distinta de la actual" ni contraseñas comprometidas: la regla "distinta" queda solo en el cliente.
- **Bloqueo**: C-04 bloquea la cuenta (5 fallos → 15 min, 10 → 30, 15 → 60) pero **lo oculta**: durante el bloqueo responde el mismo 401 `INVALID_CREDENTIALS`, sin `Retry-After`. El único aviso de espera real es el 429 del rate limit (ventana de 1 minuto, `Retry-After` ≤ 60 s). 429 y 401 no se pueden usar como oráculo de existencia de cuentas (misma respuesta, el rate limit se aplica antes de buscar la cuenta, hash de relleno para emails inexistentes): **no hay fuga de enumeración**.
- Todo lo de esta tabla vive en `auth-gateway.ts` + `auth-messages.ts`.

### D3 — Cliente HTTP: intento de credenciales y `Retry-After`
- **Decisión**: nueva opción `credentialAttempt?: boolean` en `request()`. Con ella, un 401 se procesa como cualquier error (`problem` con `status: 401` y su `code`) y **no** llama `onUnauthorized`. Se usa en `POST /auth/login` (401 `INVALID_CREDENTIALS`) **y en `POST /auth/change-password`** (C-04 responde 401 `INVALID_CREDENTIALS` si la contraseña actual es incorrecta; sin la opción, C-05 lo trataría como sesión vencida y expulsaría a la persona). `ApiError` `problem` suma `retryAfterSeconds?: number`, tomado de `Retry-After` solo si es un entero de segundos ≥ 0 (la forma fecha HTTP se ignora).
- **Vencimiento real en `change-password`**: con `credentialAttempt` el 401 `UNAUTHENTICATED` también llega como `problem`; `auth-gateway.changePassword` lo traduce a `{ kind: 'expired' }` y llama a un callback inyectado `onSessionExpired` (= `session.expire()`), de modo que el aviso de vencida se emite igual, una sola vez.
- **Por qué no reutilizar `sessionProbe`**: devuelve `unauthorized` sin código; aquí se necesita el `code` para distinguir `INVALID_CREDENTIALS` de `UNAUTHENTICATED`.

### D4 — Sesión: forma real del usuario, `signIn` y `endAfterPasswordChange`
- `SessionUser` pasa a `{ userId: string; role: string; mustChangePassword: boolean; expiresAt?: string; fullName?: string }`. El decodificador de `/me` y de la respuesta del login acepta la forma real (`userId` obligatorio, `role` obligatorio, `mustChangePassword` booleano con `false` por defecto, `expiresAt` y `fullName` opcionales). **Sin este ajuste la SPA nunca llegaría a `authenticated`**: el decodificador de C-05 exige `id` y `fullName` y responde `invalid-response`, lo que deja la sesión `anonymous{offline}`.
- `SessionState.anonymous.notice` suma `'password-changed'`.
- `session.signIn(user)`: `store.set({ status: 'authenticated', user })` (borra cualquier `notice`). `session.endAfterPasswordChange()`: `store.set({ status: 'anonymous', notice: 'password-changed' })` (el servidor ya cerró la sesión). Ninguno hace llamadas HTTP.
- `main.ts` y `private-layout` usan `user.fullName ?? 'Profesional'` hasta que C-04 exponga el nombre.
- **Alternativa**: tras login, `session.load()` contra `/me` — una petición extra sin ganancia (el login devuelve el mismo cuerpo); se descarta.

### D5 — Guards del cambio obligatorio
- `requireSession(session)` se amplía: autenticado con `mustChangePassword` → `{ redirect: '/cambiar-contrasena?next=<path>', replace: true }` (el `next` pasa por `safeNext`; si es inválido, se omite).
- `redirectIfAuthenticated` (/login): autenticado con `mustChangePassword` → `/cambiar-contrasena` con el `next` validado.
- Nuevo `requireAuthenticated(session)` para `/cambiar-contrasena`: solo exige sesión (sin redirigir por `mustChangePassword`, que sería un bucle).
- `/cambiar-contrasena` es ruta de layout **public** (pantalla enfocada, sin navegación privada): en modo obligatorio no debe haber salidas a vistas privadas; usar el mismo layout en modo voluntario evita lógica de layout por estado. Consecuencia: `safeNext` (solo privadas) nunca la acepta como `next`, lo cual es deseable.
- `handleUnauthorized` de `main.ts` no navega desde rutas públicas: la vista de cambio maneja el 401 `UNAUTHENTICATED` navegando a `/login` (spec `password-change` "Sesión vencida durante el cambio"); la sesión queda `anonymous{expired}` por el callback `onSessionExpired` de D3.

### D6 — Vistas y flujo de envío
- Vistas construidas con `h()`; textos siempre como nodos de texto (regla 10). Formulario `<form novalidate>` (la validación nativa del navegador no es accesible de forma consistente y su texto no es controlable); validación con `validation.ts`.
- **Estados del login**: `idle` → `submitting` → (`success` → navegar) | `error{kind}`. Con `submitting`: botón `aria-busy="true"` + `aria-disabled="true"` + `.spinner` + "Ingresando…", inputs `readonly` (no `disabled`, para no perder el foco ni el valor en lectores de pantalla); un `submit` en `submitting` se ignora.
- **Mensajes** (`auth-messages.ts`; nunca `title`/`detail` del servidor):
  - `INVALID_CREDENTIALS` (401) / `VALIDATION_FAILED` (400) del login → "Email o contraseña incorrectos." (incluye la cuenta bloqueada, que C-04 devuelve como `INVALID_CREDENTIALS`).
  - `RATE_LIMITED` (429) → título "Ingreso bloqueado temporalmente" + "Por seguridad, hubo demasiados intentos. Probá de nuevo en unos N minutos." (`N = ceil(seg/60)`; con N = 1, "en 1 minuto"; sin `Retry-After` o con 0: "en unos minutos"). Con el rate limit real de C-04 (ventana de 1 minuto) el texto efectivo será casi siempre "en 1 minuto".
  - `network` / `timeout` / `invalid-response` / otro `problem` → "No pudimos conectar con el servidor. Revisá tu conexión e intentá de nuevo."
  - `CSRF_REQUIRED` (403) → "La página quedó desactualizada. Recargala e intentá de nuevo." (en login no puede ocurrir: es CSRF-exento; aplica al cambio de contraseña).
  - Aviso `password-changed` en `/login` (`role="status"`): "Contraseña actualizada. Ingresá de nuevo con la contraseña nueva."
- **Foco y anuncio**: error de servidor → alerta `role="alert"` arriba del formulario (anunciada) y foco en el campo contraseña (vaciado); errores locales → mensaje por campo (`.error-msg` con `id`, `aria-describedby`, `aria-invalid`) y foco en el primer campo inválido. El aviso `expired`/`offline` de la sesión es `role="status"` (informativo, no error).
- **"Mostrar contraseña"**: botón con `aria-pressed` y `aria-controls`, alterna `type`; al enviar vuelve a `password`.
- **Cambio de contraseña**: mismos patrones; texto de ayuda "Mínimo 12 caracteres. Podés usar una frase con espacios." vinculado por `aria-describedby`. Debajo, lista de requisitos (al menos 12, distinta de la actual, coinciden) calculada por `validation.ts` (longitud en unidades UTF-16, D2) en cada `input`: punto = pendiente, ✓ = cumple; ✕ solo después de un intento de envío (no se marca rojo mientras la persona todavía escribe). La lista se lee como lista normal, sin `aria-live` (no anuncia en cada tecla); el error accesible es el mensaje por campo. Errores de servidor mapeados por `field` a su campo (`Size` en `newPassword` → "Usá entre 12 y 128 caracteres."; `NotBlank` → "Ingresá la contraseña…"); 401 `INVALID_CREDENTIALS` vacía y enfoca "Contraseña actual". Éxito (200 sin cuerpo; el servidor **cierra la sesión**) → `session.endAfterPasswordChange()`, campos vaciados, `navigate(next válido ? '/login?next=…' : '/login', { replace: true })`; la persona vuelve a ingresar con la contraseña nueva y el login la lleva al destino. "Salir" reutiliza el `onLogout` de `main.ts` (se inyecta a la vista).
- Copy en voseo rioplatense, consistente con C-05.

### D7 — Estilos
- `src/styles/forms.css` (importado por `main.ts`): porta `.field`, `.input`, `.error-msg`, `.help`, `.btn`/`.btn-primary`/`.btn-lg`/`.btn-block`, `.spinner`, `.alert-*` de `components.html` y agrega `.auth-card` (superficie, borde, `--radius-lg`, `--shadow-sm`, ancho máx. `--layout-auth-width: 26rem` local en `layout.css`, documentada como candidata a token en Revision 2). Mismas excepciones que C-05 D8 (`1px` en bordes); `styles.test.ts` cubre el archivo nuevo sin cambios (escanea `src/**/*.css`).
- `components.html` usa literales en `.spinner` (`1rem`, `50%`, `2px`, `700ms`) y en el `:hover` de `.btn-danger`: en `forms.css` se reemplazan por tokens (`--space-4`, `--radius-full`, `--duration-slow`) y `.btn-danger` no se porta. El spinner respeta `prefers-reduced-motion` (los tokens de duración ya se anulan ahí; se agrega `animation: none`).
- `.app-header .pro-link` en `/login` apunta a la misma página: se deja como está (layout aprobado en C-05) con `aria-current="page"` para que no parezca un enlace a otra parte; no cambia el spec `app-layout`.

### D8 — Estrategia de tests (Vitest + jsdom, `fetch` simulado)
| Archivo | Cubre (spec) |
|---------|--------------|
| `features/auth/validation.test.ts` | professional-login (validación), password-change (política, 12 exactos, 128, unidades UTF-16, solo espacios) |
| `features/auth/retry-after.test.ts`, `auth-messages.test.ts` | professional-login (bloqueo, genéricos) |
| `features/auth/auth-gateway.test.ts` | contrato D2 real: cuerpos, códigos (`INVALID_CREDENTIALS`, `RATE_LIMITED`, `CSRF_REQUIRED`, `UNAUTHENTICATED`), `Retry-After`, `mustChangePassword` |
| `core/http/client.test.ts` (+casos) | api-client (401 en intento de credenciales de login y change-password, `retryAfterSeconds`) |
| `core/auth/session.test.ts` (+casos) | session-state (forma real de `/me`, `fullName` opcional, `signIn`, `endAfterPasswordChange`, `mustChangePassword` por defecto) |
| `router/guards.test.ts` (+casos) | spa-routing (cambio pendiente, /login con cambio, /cambiar-contrasena sin sesión) |
| `features/auth/login-view.test.ts` | professional-login (vista, estados, foco, doble envío, rastro de credenciales) |
| `features/auth/change-password-view.test.ts` | password-change (modos, errores, destino, salir) |
| `main.test.ts` (+casos de integración con `bootstrap`) | flujos completos: `/agenda` → login → `/agenda`; primer ingreso → cambio → `/login` con aviso → reingreso → destino; 401 en privada → login con aviso → reingreso; logout + atrás |
- El `fetch` falso es una tabla `método + ruta → Response` construida en cada test; **ningún test usa emails reales** (`romero@ejemplo.com`).
- "Sin rastros" se prueba espiando `console.*` y leyendo `localStorage`/`sessionStorage`/`location.href` tras el intento.
- Responsive y apariencia: verificación visual del verify contra los mockups aprobados (390x844 y 1440x900).

## Risks / Trade-offs

- [Contrato ya confirmado] Los códigos y status de D2 son los del código de C-04; si C-04 cambia, se ajustan `auth-gateway.ts`/`auth-messages.ts` y sus tests, no las vistas.
- [Bloqueo de cuenta invisible] C-04 bloquea 15/30/60 min pero devuelve el mismo 401 `INVALID_CREDENTIALS`: con la contraseña correcta la persona seguirá viendo "Email o contraseña incorrectos." sin saber que debe esperar. No se puede resolver sin filtrar la existencia de la cuenta; mitigación solo de copy en el cliente (p. ej. sumar "Si el problema sigue, esperá unos minutos") que **cambia el texto de un mockup aprobado** y queda como decisión pendiente de Lucas/Mayra.
- [Cambio de contraseña cierra la sesión] Es el diseño de C-04 (D8): el flujo obligatorio es login → cambio → login de nuevo. Se absorbió en D4/D6 (aviso `password-changed`); los mockups no tienen ese estado de `/login`.
- [Validación de contraseña distinta entre cliente y servidor] El servidor cuenta unidades UTF-16 y no valida "distinta de la actual" ni contraseñas comprometidas; el cliente replica la medida UTF-16 y valida "distinta" solo en el cliente.
- [Guard del cliente evitable] Es solo UX; la barrera es el 403 `PASSWORD_CHANGE_REQUIRED` de C-04 (confirmado).
- [Contraseña visible con "Mostrar"] Decisión del usuario, se vuelve a ocultar al enviar.
- [Sin recuperación de contraseña] Con un único usuario y sin admin, olvidar la contraseña exige intervención técnica hasta resolver la recuperación (OQ-2 de C-04); se documenta en la ayuda del login como `(TBD)`.
- [`--layout-auth-width` local] Candidato a token en Revision 2.

### Pedidos a C-04

Ninguno bloquea a C-06 en el backend; todos tienen salida del lado del frontend. No se inventan cambios de backend: quedan como pedidos para un change posterior de C-04.

| # | Pedido | Tipo | Workaround en C-06 |
|---|--------|------|--------------------|
| P1 | Incluir `fullName` (nullable; la columna `app_user.full_name` existe) en `/auth/me` y `/auth/login` para el encabezado privado | No bloqueante | `fullName` opcional; etiqueta "Profesional" |
| P2 | Contraseña actual incorrecta devuelve 401 `INVALID_CREDENTIALS`, igual en status que la sesión vencida; preferible 400/403 | No bloqueante (el spec de C-04 lo fija en 401) | `credentialAttempt` en `change-password` y distinción por `code` (D3) |
| P3 | `Size` no distingue corta de larga y cuenta unidades UTF-16; "distinta de la actual" y contraseña comprometida no se validan | No bloqueante | Validación en el cliente con la misma medida; mensaje único para `Size` |
| P4 | Cuenta bloqueada indistinguible del 401 normal (sin aviso de espera) | No bloqueante, decisión de producto: cualquier señal revelaría que la cuenta existe | Copy del cliente (ver riesgos) |
| P5 | `change-password` responde 200 sin cuerpo (no 204) | Informativo | El cliente acepta 200 y 204 |

## Migration Plan

Sin migración de datos. Rollback: revertir el commit del change; vuelve el *placeholder* de C-05. Con el backend de C-04 corriendo, la prueba de punta a punta (login, cambio obligatorio, reingreso, logout) es posible en `npm run dev` con el proxy a `/api`; sin backend, `/login` muestra el error de conexión.

## Open Questions

- Si Lucas/Mayra aprueban agregar en el mockup de error de login una indicación de esperar unos minutos (riesgo "Bloqueo de cuenta invisible").
- Estado de mockup para `/login` con el aviso "Contraseña actualizada" (hoy inexistente).
- Texto final de ayuda para "¿Olvidaste tu contraseña?" depende de la recuperación de contraseña (OQ-2 de C-04); hoy `(TBD)`.
- En el cambio voluntario (sesión activa, layout público) el header sigue mostrando "Acceso profesionales"; aceptable hasta que exista la entrada al cambio voluntario (change de Configuración).

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-21)
- Mockups: openspec/changes/c-06-login-ui/mockups/
- Mockup revision: 2
- Approval: approved (2026-11-21, by Lucas; revisión 2)
- Preview: rendered (2026-11-21, revisión 2, `mockups/previews/<pantalla>-390.png` y `-1440.png`; regeneradas login-locked, login-error, change-password-current-invalid, login-password-changed (nueva) y change-password; las otras 6 pantallas conservan las capturas de la revisión 1; Chrome headless por CDP con emulación de viewport 390x844 y 1440x900, página completa, servidor local 127.0.0.1:8765; sin scroll horizontal a 390 px; inspeccionadas visualmente)
- Visual verify: differences (2026-11-21; Chrome headless por CDP con API interceptada, 390x844 y 1440x900, capturas en `mockups/verify/`; 10 estados verificados, sin scroll horizontal ni errores de consola; diferencias solo minor: padding superior de `.public-main` en escritorio 112 px vs 128 px del mockup y color de fondo del shell; no verificados: login-expired y login-offline-de-sesión)
- Nota (revisión 2, 2026-11-21): los mockups se ajustaron al contrato real de C-04: `login-locked.html` ahora es 429 `RATE_LIMITED` ("Probá de nuevo en 1 minuto."), `login-error.html` y `change-password-current-invalid.html` usan `INVALID_CREDENTIALS` (401), `change-password.html` anota que el éxito lleva a `/login`, y se agregó `login-password-changed.html`. Requiere nueva aprobación de Lucas.
- Criterio de aceptación: los HTML de `mockups/` y sus capturas (ver `tasks.md` grupos 6 y 7).
