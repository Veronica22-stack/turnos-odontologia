# Diseño: C-06 `login-ui`

## Context

Ver `proposal.md` §Why. Estado relevado el 2026-11-21 (tras C-05, merge `797f17c`):

- `frontend/src/app/routes.ts`: `/login` es un *placeholder* (`loginView`) que ya muestra el aviso `expired`/`offline` de la sesión; guard `redirectIfAuthenticated`. No existe `/cambiar-contrasena`.
- `router/guards.ts`: `requireSession` (anónimo → `/login?next=…`), `redirectIfAuthenticated` (→ `safeNext(next) ?? '/agenda'`), `rootRedirect`. `router/safe-next.ts` acepta solo rutas **privadas** declaradas.
- `core/auth/session.ts`: `SessionState` (`unknown` / `anonymous{notice?}` / `authenticated{user}`), `SessionUser.mustChangePassword?` decodificado por `httpSessionGateway` pero **no aplicado**; `SessionGateway { me, logout }`.
- `core/http/client.ts`: `request()` devuelve `HttpResult<T>`; **todo 401 → `{ kind: 'unauthorized' }` + `onUnauthorized()`** salvo `sessionProbe`. Los errores `problem` no exponen cabeceras (no hay `Retry-After`).
- `main.ts` `handleUnauthorized`: solo actúa si la sesión estaba `authenticated` (→ `expire()`) y solo navega a `/login?next=` si la ruta actual es de layout `private`.
- `layouts/public-layout.ts`: header (logo + "Acceso profesionales" → `/login`), `main#main`, footer con `(TBD)`. Estilos en `src/styles/{base,layout}.css` solo con tokens (verificado por `styles.test.ts`); `architecture.test.ts` prohíbe `innerHTML` & cía.
- Design system Revision 1 (aprobado): `components.html` define `.field`, `.input` (altura `--control-height-lg`, `aria-invalid` con borde 2px), `.error-msg` (ícono + texto), `.help`, `.btn-primary`/`.btn-lg`/`.btn-block`, estado `aria-busy` con `.spinner`, `.alert-{info,warning,danger}`.
- **C-04 no está implementado**; PA-04 abierta. KB: RN-AU-03 (≥ 12 caracteres, bloqueo temporal tras N intentos, sin N ni duración definidos), RN-AU-04 (30 min de inactividad), Flujo 1 (mensaje genérico; bloqueo "con indicación de reintentar luego"; sesión expirada → login conservando la ruta), C-03 (`must_change_password = true` en la cuenta de bootstrap).

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
src/core/auth/session.ts    # + signIn(user), markPasswordChanged()
src/core/http/client.ts     # + opción credentialAttempt y retryAfterSeconds (D3)
src/styles/forms.css        # .auth-card, .field, .input, .btn, .alert, .spinner (solo tokens)
```
- **Por qué `features/auth/`**: KB 08 organiza por feature; `core/auth` queda como estado de sesión transversal y `features/auth` como pantallas y su contrato HTTP. Las funciones puras (validación, mensajes, retry) se prueban sin DOM.
- **Alternativa**: ampliar `SessionGateway` con `login/changePassword` — mezcla la consulta de sesión (usada por guards en toda la app) con flujos de pantalla y obliga a tocar el adaptador de C-05; se descarta. `session.signIn(user)` solo recibe el usuario ya decodificado.

### D2 — Contrato supuesto con C-04 (**a confirmar con C-04**)
Mismas convenciones de C-05 D4 (sesión servidor con cookie `HttpOnly; Secure; SameSite=Strict`, CSRF `XSRF-TOKEN`/`X-XSRF-TOKEN`, errores `application/problem+json` de C-02 con `code` y `errors[{field, code}]`).

| Endpoint | Petición | Éxito | Errores esperados |
|----------|----------|-------|-------------------|
| `POST /api/v1/auth/login` | `{ "email": string, "password": string }` | **200** `{ id, fullName, role, mustChangePassword }` (mismo cuerpo que `GET /auth/me`) + cookie de sesión nueva (rotación de id) + cookie CSRF renovada | **401** `AUTH_INVALID_CREDENTIALS` (email inexistente **o** contraseña incorrecta: misma respuesta, mismo tiempo) · **423** `AUTH_ACCOUNT_LOCKED` con `Retry-After: <segundos>` · **429** `RATE_LIMITED` con `Retry-After` (rate limit IP+email) · **400** `VALIDATION_FAILED` (`errors[{field:'email'\|'password', code:'REQUIRED'\|'INVALID_FORMAT'}]`) · **403** `CSRF_INVALID` |
| `POST /api/v1/auth/change-password` | `{ "currentPassword": string, "newPassword": string }` | **204** (la sesión sigue válida; `GET /auth/me` pasa a `mustChangePassword: false`) | **400** `AUTH_CURRENT_PASSWORD_INVALID` (**no 401**, para no confundirse con sesión vencida) · **400** `VALIDATION_FAILED` con `errors[{field:'newPassword', code:'PASSWORD_TOO_SHORT'\|'PASSWORD_TOO_LONG'\|'PASSWORD_SAME_AS_CURRENT'\|'PASSWORD_COMPROMISED'}]` · **401** sesión vencida · **423/429** si C-04 limita intentos de contraseña actual |
| `GET /api/v1/auth/me` | — | 200 como hoy (C-05) | 401 sin sesión; **debe emitir la cookie `XSRF-TOKEN` también en la respuesta 401**, para que el primer `POST /auth/login` lleve el token |
| `POST /api/v1/auth/logout` | — | 204 (C-05) | — |

- **Política de contraseña** (RN-AU-03 + supuestos): mínimo **12** caracteres (decidido en RN-AU-03 y C-03); máximo **128** (supuesto, acota el costo de Argon2id); distinta de la actual; sin reglas de composición (criterio NIST SP 800-63B; frases permitidas, espacios incluidos); se cuenta en puntos de código (`[...s].length`), no en unidades UTF-16. El cliente valida mínimo, máximo, igualdad con la actual y confirmación; el servidor es la autoridad.
- **Bloqueo**: N y duración no están definidos (RN-AU-03); el cliente no asume valores, solo muestra `Retry-After` si llega. **423 y 429 se muestran con el mismo mensaje**: distinguirlos le diría a un atacante si el email existe. C-04 debe además aplicar el bloqueo/limitación también a emails inexistentes (clave IP+email) para que 423/429 no funcionen como oráculo de existencia.
- **Cambio obligatorio en el servidor**: con `mustChangePassword = true`, C-04 debería rechazar todo `/api/v1/**` salvo `me`, `logout` y `change-password` con **403** `PASSWORD_CHANGE_REQUIRED`. El guard del cliente (D5) es la experiencia; la barrera real es el servidor.
- Todo lo de esta tabla vive en `auth-gateway.ts` + `auth-messages.ts`: si C-04 cambia un código o status se ajustan esas dos tablas y sus tests.

### D3 — Cliente HTTP: intento de credenciales y `Retry-After`
- **Decisión**: nueva opción `credentialAttempt?: boolean` en `request()`. Con ella, un 401 se procesa como cualquier error (`problem` con `status: 401` y su `code`) y **no** llama `onUnauthorized`. Se usa solo en `POST /auth/login`. `ApiError` `problem` suma `retryAfterSeconds?: number`, tomado de la cabecera `Retry-After` solo si es un entero de segundos ≥ 0 (la forma fecha HTTP se ignora: sin reloj confiable y sin necesidad).
- **Por qué no reutilizar `sessionProbe`**: `sessionProbe` devuelve `unauthorized` sin código; el login necesita el código del *problem* para distinguir credenciales de otros 401 futuros.
- `change-password` **no** es intento de credenciales: su 401 es vencimiento real (D2) y sigue el camino de C-05.

### D4 — Sesión: `signIn` y `markPasswordChanged`
- `SessionUser.mustChangePassword` pasa a obligatorio (`boolean`; el decodificador usa `false` si falta) → spec `session-state` "Estados de la sesión".
- `session.signIn(user)`: `store.set({ status: 'authenticated', user })` (borra cualquier `notice`). `session.markPasswordChanged()`: si está autenticada, copia el usuario con `mustChangePassword: false`. Ambos sin llamadas HTTP (la respuesta del login ya trae el usuario: ahorra un `GET /me`).
- **Alternativa**: tras login, `session.load()` contra `/me` — una petición extra y una ventana con estado inconsistente; se descarta, pero si C-04 no devuelve el usuario en el login, `auth-gateway` hace el `me()` sin cambiar las vistas.

### D5 — Guards del cambio obligatorio
- `requireSession(session)` se amplía: autenticado con `mustChangePassword` → `{ redirect: '/cambiar-contrasena?next=<path>', replace: true }` (el `next` pasa por `safeNext`; si es inválido, se omite).
- `redirectIfAuthenticated` (/login): autenticado con `mustChangePassword` → `/cambiar-contrasena` con el `next` validado.
- Nuevo `requireAuthenticated(session)` para `/cambiar-contrasena`: solo exige sesión (sin redirigir por `mustChangePassword`, que sería un bucle).
- `/cambiar-contrasena` es ruta de layout **public** (pantalla enfocada, sin navegación privada): en modo obligatorio no debe haber salidas a vistas privadas; usar el mismo layout en modo voluntario evita lógica de layout por estado. Consecuencia: `safeNext` (solo privadas) nunca la acepta como `next`, lo cual es deseable.
- `handleUnauthorized` de `main.ts` no navega desde rutas públicas: la vista de cambio maneja su propio `unauthorized` navegando a `/login` (spec `password-change` "Sesión vencida durante el cambio"); la sesión ya quedó `anonymous{expired}` por `onUnauthorized`.

### D6 — Vistas y flujo de envío
- Vistas construidas con `h()`; textos siempre como nodos de texto (regla 10). Formulario `<form novalidate>` (la validación nativa del navegador no es accesible de forma consistente y su texto no es controlable); validación con `validation.ts`.
- **Estados del login**: `idle` → `submitting` → (`success` → navegar) | `error{kind}`. Con `submitting`: botón `aria-busy="true"` + `aria-disabled="true"` + `.spinner` + "Ingresando…", inputs `readonly` (no `disabled`, para no perder el foco ni el valor en lectores de pantalla); un `submit` en `submitting` se ignora.
- **Mensajes** (`auth-messages.ts`; nunca `title`/`detail` del servidor):
  - `AUTH_INVALID_CREDENTIALS` / `VALIDATION_FAILED` del login → "Email o contraseña incorrectos."
  - `AUTH_ACCOUNT_LOCKED` / `RATE_LIMITED` → título "Ingreso bloqueado temporalmente" + "Por seguridad, hubo demasiados intentos. Probá de nuevo en unos N minutos." (`N = ceil(seg/60)`; con N = 1, "en 1 minuto"; sin `Retry-After` o con 0: "en unos minutos").
  - `network` / `timeout` / `invalid-response` / otro `problem` → "No pudimos conectar con el servidor. Revisá tu conexión e intentá de nuevo."
  - `CSRF_INVALID` → "La página quedó desactualizada. Recargala e intentá de nuevo."
- **Foco y anuncio**: error de servidor → alerta `role="alert"` arriba del formulario (anunciada) y foco en el campo contraseña (vaciado); errores locales → mensaje por campo (`.error-msg` con `id`, `aria-describedby`, `aria-invalid`) y foco en el primer campo inválido. El aviso `expired`/`offline` de la sesión es `role="status"` (informativo, no error).
- **"Mostrar contraseña"**: botón con `aria-pressed` y `aria-controls`, alterna `type`; al enviar vuelve a `password`.
- **Cambio de contraseña**: mismos patrones; texto de ayuda "Mínimo 12 caracteres. Podés usar una frase con espacios." vinculado por `aria-describedby`. Debajo, lista de requisitos (al menos 12, distinta de la actual, coinciden) calculada por `validation.ts` en cada `input`: punto = pendiente, ✓ = cumple; ✕ solo después de un intento de envío (no se marca rojo mientras la persona todavía escribe). La lista se lee como lista normal, sin `aria-live` (no anuncia en cada tecla); el error accesible es el mensaje por campo. Errores de servidor mapeados por `field` a su campo; `AUTH_CURRENT_PASSWORD_INVALID` vacía y enfoca "Contraseña actual". Éxito → `session.markPasswordChanged()`, campos vaciados, `navigate(safeNext(next) ?? '/agenda', { replace: true })`. "Salir" reutiliza el `onLogout` de `main.ts` (se inyecta a la vista).
- Copy en voseo rioplatense, consistente con C-05.

### D7 — Estilos
- `src/styles/forms.css` (importado por `main.ts`): porta `.field`, `.input`, `.error-msg`, `.help`, `.btn`/`.btn-primary`/`.btn-lg`/`.btn-block`, `.spinner`, `.alert-*` de `components.html` y agrega `.auth-card` (superficie, borde, `--radius-lg`, `--shadow-sm`, ancho máx. `--layout-auth-width: 26rem` local en `layout.css`, documentada como candidata a token en Revision 2). Mismas excepciones que C-05 D8 (`1px` en bordes); `styles.test.ts` cubre el archivo nuevo sin cambios (escanea `src/**/*.css`).
- `components.html` usa literales en `.spinner` (`1rem`, `50%`, `2px`, `700ms`) y en el `:hover` de `.btn-danger`: en `forms.css` se reemplazan por tokens (`--space-4`, `--radius-full`, `--duration-slow`) y `.btn-danger` no se porta. El spinner respeta `prefers-reduced-motion` (los tokens de duración ya se anulan ahí; se agrega `animation: none`).
- `.app-header .pro-link` en `/login` apunta a la misma página: se deja como está (layout aprobado en C-05) con `aria-current="page"` para que no parezca un enlace a otra parte; no cambia el spec `app-layout`.

### D8 — Estrategia de tests (Vitest + jsdom, `fetch` simulado)
| Archivo | Cubre (spec) |
|---------|--------------|
| `features/auth/validation.test.ts` | professional-login (validación), password-change (política, 12 exactos, 128, puntos de código) |
| `features/auth/retry-after.test.ts`, `auth-messages.test.ts` | professional-login (bloqueo, genéricos) |
| `features/auth/auth-gateway.test.ts` | contrato D2: cuerpos, códigos, `Retry-After`, `mustChangePassword` |
| `core/http/client.test.ts` (+casos) | api-client (401 en intento de credenciales, `retryAfterSeconds`) |
| `core/auth/session.test.ts` (+casos) | session-state (`signIn`, `markPasswordChanged`, `mustChangePassword` por defecto) |
| `router/guards.test.ts` (+casos) | spa-routing (cambio pendiente, /login con cambio, /cambiar-contrasena sin sesión) |
| `features/auth/login-view.test.ts` | professional-login (vista, estados, foco, doble envío, rastro de credenciales) |
| `features/auth/change-password-view.test.ts` | password-change (modos, errores, destino, salir) |
| `main.test.ts` (+casos de integración con `bootstrap`) | flujos completos: `/agenda` → login → `/agenda`; primer ingreso → cambio → destino; 401 en privada → login con aviso → reingreso; logout + atrás |
- El `fetch` falso es una tabla `método + ruta → Response` construida en cada test; **ningún test usa emails reales** (`romero@ejemplo.com`).
- "Sin rastros" se prueba espiando `console.*` y leyendo `localStorage`/`sessionStorage`/`location.href` tras el intento.
- Responsive y apariencia: verificación visual del verify contra los mockups aprobados (390x844 y 1440x900).

## Risks / Trade-offs

- [C-04 elige otros status/códigos o JWT (PA-04)] → contrato concentrado en `auth-gateway.ts`/`auth-messages.ts` y transporte reemplazable de C-05; C-04 debe leer D2 y, si diverge, se actualizan esas tablas y sus tests (no las vistas ni los specs, salvo los códigos citados en escenarios).
- [Primer `POST /login` sin cookie CSRF (Spring 6 difiere el token)] → requisito explícito a C-04 (emitir `XSRF-TOKEN` en `GET /auth/me` aun con 401); si falla, el usuario ve "Recargala…" en vez de un error confuso.
- [423/429 como oráculo de existencia de cuentas] → mismo mensaje en el cliente; la mitigación real (bloquear por IP+email también para emails inexistentes) queda pedida a C-04.
- [Guard del cliente evitable] → es solo UX; la barrera es el 403 `PASSWORD_CHANGE_REQUIRED` de C-04 (D2).
- [C-04 responde 401 a la contraseña actual incorrecta] → expiraría la sesión; D2 pide 400. Si C-04 no puede, se marca `change-password` como `credentialAttempt` (cambio de una línea + test).
- [Contraseña visible con "Mostrar"] → decisión del usuario, se vuelve a ocultar al enviar.
- [Sin recuperación de contraseña] → con un único usuario y sin admin, olvidar la contraseña exige intervención técnica (re-bootstrap o SQL) hasta resolver PA-04; se documenta en la ayuda del login como `(TBD)`.
- [`--layout-auth-width` local] → candidato a token en Revision 2, como las medidas locales de C-05.

## Migration Plan

Sin migración de datos. Mientras C-04 no exista, `/login` en `npm run dev` siempre mostrará el error de conexión (el proxy a `/api` falla): es lo esperado. Rollback: revertir el commit del change; vuelve el *placeholder* de C-05.

## Open Questions

- Nombres exactos de códigos de error y uso de 423 vs 429 los confirma C-04 (D2); cambian tablas de mapeo, no el enfoque.
- Texto final de ayuda para "¿Olvidaste tu contraseña?" depende de PA-04 (recuperación); hoy `(TBD)`.
- Duración del bloqueo y N intentos (RN-AU-03) se definen en C-04; la UI solo refleja `Retry-After`.
- En el cambio voluntario (sesión activa, layout público) el header sigue mostrando "Acceso profesionales"; aceptable mientras la entrada al cambio voluntario no exista (llega con Configuración). Si molesta, ese change puede mover la ruta al layout privado en modo voluntario.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-21)
- Mockups: openspec/changes/c-06-login-ui/mockups/
- Approval: approved (2026-11-21, by Lucas)
- Preview: rendered (2026-11-21, `mockups/previews/<pantalla>-390.png` y `<pantalla>-1440.png` para las 10 pantallas de `mockups/` (20 capturas); Playwright MCP sobre servidor local en 127.0.0.1:8765, página completa; sin scroll horizontal a 390 px, botones de 48 px de alto, botón "Mostrar" de 44 px, tokens cargados, consola sin errores ni requests fallidos)
- Criterio de aceptación: los HTML de `mockups/` y sus capturas (ver `tasks.md` grupos 6 y 7).
