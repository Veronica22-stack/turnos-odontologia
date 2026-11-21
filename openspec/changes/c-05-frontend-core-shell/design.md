# Diseño: C-05 `frontend-core-shell`

## Context

Ver `proposal.md` §Why. Estado actual del frontend (relevado el 2026-11-21):

- `frontend/` con Vite 7, TypeScript ~5.9, Vitest 3 + jsdom (solo devDependencies; **cero dependencias de runtime**). Node 22.
- `tsconfig.json` estricto: `strict`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `verbatimModuleSyntax`, `isolatedModules`, `types: ["vite/client"]`.
- `src/main.ts` exporta `mountApp(doc)` (idempotente, `[data-app-root]` dentro de `#app`) y lo ejecuta salvo en `MODE === 'test'`; `src/main.test.ts` cubre el spec `spa-shell` vigente.
- `vite.config.ts`: proxy `/api` y `/actuator` a `BACKEND_URL` (por defecto `http://localhost:8080`), `server.host = 'localhost'`, entorno de test `jsdom`.
- `design-system/` en la raíz del repo (fuera de la raíz de Vite): `tokens.css`, `brand.js` (IIFE clásico que publica `window.BRAND = { name, apply }`), `assets/logo.svg`. Revision 1 aprobada; política `system-and-mockups`.
- Backend: C-02 entrega errores `application/problem+json` con `status`, `title`, `code`, `errors[{field, code}]` y `errorId`. **C-04 (auth) no existe todavía** y PA-04 (método de autenticación) sigue abierta; KB 08 prefiere sesión servidor con cookie `HttpOnly; Secure; SameSite=Strict` + CSRF.

Restricciones: reglas duras 8–12 de `CLAUDE.md` (UTC/zona del consultorio; TS estricto sin `any`; sin `innerHTML`; solo tokens; sin framework ni dependencias de runtime nuevas), Strict TDD en el apply, governance BAJO.

## Goals / Non-Goals

**Goals:**
- Una base de frontend que C-06 y los changes [UI] usen sin reinventar router, HTTP, sesión, render ni fechas.
- Contrato explícito con C-04 y aislado en un adaptador, de modo que una decisión distinta de PA-04 cambie un archivo y no las pantallas.
- Todo comportamiento con lógica cubierto por Vitest (jsdom), sin navegador real ni backend.

**Non-Goals:**
- Pantallas reales (login, agenda, pacientes, configuración, reserva): solo vistas *placeholder* con su `h1`.
- Componentes del design system más allá del shell (botones, formularios, badges los traen los changes que los usan).
- CSP y cabeceras de seguridad del servidor (C-04 / C-27); servir la SPA compilada desde el backend (C-27).
- Modo oscuro, i18n, *service worker*, *lazy loading* de rutas.

## Decisions

### D1 — Estructura de carpetas
```
frontend/src/
├── main.ts                    # mountApp (existente) + bootstrap(): crea store, sesión, http, router
├── app/routes.ts              # tabla de rutas de la app (vistas placeholder + layout + guard)
├── router/
│   ├── match.ts               # compilación y matching de patrones (`/turno/:token`)
│   ├── router.ts              # createRouter: History API, interceptación de clics, popstate
│   ├── guards.ts              # requireSession, redirectIfAuthenticated, rootRedirect
│   └── safe-next.ts           # validación del parámetro `next`
├── core/
│   ├── dom/{h.ts,escape.ts}   # creación segura de nodos y escape para cadenas
│   ├── state/store.ts         # createStore<S>
│   ├── http/{client.ts,errors.ts,transport.ts,cookies.ts,decode.ts}
│   └── auth/{session.ts,session-gateway.ts}
├── layouts/{private-layout.ts,public-layout.ts,brand.ts}
├── shared/utils/dates.ts
└── styles/{base.css,layout.css}   # importan design-system/tokens.css
```
- **Por qué**: sigue KB 08 §Estructura de directorios. Los layouts van en `layouts/` (no en `shared/components/`) porque no son reutilizables por features sino el marco de todas.
- Las pruebas viven junto al código (`*.test.ts`), como `main.test.ts`.

### D2 — Router propio sobre History API
- **Decisión**: `createRouter({ routes, window, session, render })`. Cada ruta: `{ name, path: '/turno/:token', layout: 'public'|'private', title, guard?, view }`. Los patrones se compilan a segmentos (sin `RegExp` construida desde texto del usuario); `:param` acepta `[A-Za-z0-9_-]+` y se decodifica una vez. Se normaliza la barra final. Orden de resolución: normalizar → *match* → guard → render. Un guard devuelve `{ allow } | { redirect: string, replace: true }` y puede ser `async` (espera a la sesión cuando está `unknown`). Navegación por `navigate(path, { replace })` → `history.pushState/replaceState` + render; `popstate` re-resuelve. Interceptación delegada de clics en `document` solo para `<a>` del mismo origen, botón primario, sin `ctrl/meta/shift/alt`, sin `target` ni `download`, y cuyo destino coincide con una ruta declarada.
- Tras renderizar: `document.title = "<título de la vista> · <marca>"` y foco en el `h1` (`tabindex="-1"`). El título de `/turno/:token` es fijo ("Gestionar turno"): los parámetros nunca se usan en títulos ni en `next`.
- Se descarta una navegación obsoleta: cada `resolve` lleva un contador y si al volver de un guard `async` hubo otra navegación, no renderiza.
- **Alternativas**: rutas *hash* (`#/agenda`) — más simples sin configurar el servidor, pero KB 03 define URLs limpias (`/reservar`, `/turno/{token}`) que se envían por email; un *fallback* del servidor a `index.html` ya lo da Vite en dev y se configura en C-27. Librerías (navigo, page.js) — prohibidas sin aprobación (regla 12) y triviales de evitar.

### D3 — `next` seguro (anti open-redirect)
- **Decisión**: `safeNext(raw)` acepta solo cadenas que empiezan con `/`, no con `//` ni `/\`, sin `\`, sin caracteres de control, que el *matcher* resuelva a una ruta **privada** declarada; si no, devuelve `null` y se usa `/agenda`. Se guarda solo el `pathname` (sin query), así nunca se arrastra un token ni datos personales.
- **Por qué privada**: el único uso de `next` es volver tras login; aceptar `/turno/...` permitiría arrastrar un token en la URL de login.

### D4 — Contrato con C-04 y transporte reemplazable (PA-04)
- **Supuesto (a confirmar en C-04)**: sesión servidor con cookie `HttpOnly; Secure; SameSite=Strict` y CSRF de Spring Security con `CookieCsrfTokenRepository` (cookie legible `XSRF-TOKEN`, cabecera `X-XSRF-TOKEN`). Endpoints: `GET /api/v1/auth/me` → 200 `{ id, fullName, role, mustChangePassword }` o 401; `POST /api/v1/auth/logout` → 204. El login (`POST /api/v1/auth/login`) lo usa C-06.
- **Decisión**: interfaz `AuthTransport { decorate(init: RequestInit, method: HttpMethod): RequestInit }`. Implementación por defecto `cookieCsrfTransport({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })` → `credentials: 'same-origin'` + cabecera en métodos no seguros. Si PA-04 elige JWT, se agrega `bearerTransport(getToken)` y se cambia **una línea** en `bootstrap()`; router, guards y pantallas dependen solo de `SessionGateway { me(): Promise<SessionResult>; logout(): Promise<void> }`.
- Nombres de cookie/cabecera configurables: si C-04 usa otros, se cambian en un solo lugar.
- `mustChangePassword` se decodifica (opcional) pero C-05 no actúa sobre él: la redirección a cambio de contraseña es de C-06, que agregará un guard.
- **Alternativas**: acoplar `fetch` directo a la cookie (más simple, pero obliga a tocar todo si PA-04 cambia); guardar un JWT en `localStorage` (expuesto a XSS; descartado por el spec `session-state`).

### D5 — Cliente HTTP tipado sin `any`
- **Decisión**: `createHttpClient({ fetch, transport, baseUrl: '/api/v1', timeoutMs: 15000, onUnauthorized })` con `request<T>(method, path, { body?, decode })` que **no lanza**: devuelve `HttpResult<T> = { ok: true; data: T } | { ok: false; error: ApiError }`. `decode: (value: unknown) => T | undefined` (type guard escrito a mano; helpers `isRecord`, `isString`). `ApiError` es unión discriminada: `problem { status, code, errors, errorId? }` · `unauthorized` · `network` · `timeout` · `invalid-response`. `path` debe empezar con `/` y no con `//` ni contener `://` (si no, error de uso lanzado: es un bug del programador). Timeout con `AbortController` + `setTimeout` (en tests, `vi.useFakeTimers`). Respuestas no-`problem+json` con error → `problem { code: 'UNEXPECTED_RESPONSE' }`. Body JSON con `Content-Type: application/json`.
- 401 → `unauthorized` y llamada a `onUnauthorized()` salvo que la petición se marque `{ sessionProbe: true }` (la consulta inicial de `/auth/me`). La sesión deduplica: solo notifica una vez por transición `authenticated → anonymous`.
- **Sin `console.*`** en `core/http` (spec `api-client`). Los mensajes al usuario salen de un mapa `code → texto` de cada feature, nunca de `title`/`detail` del servidor.
- **Alternativa**: lanzar excepciones tipadas — obliga a `try/catch` con `unknown` en cada pantalla; el resultado discriminado fuerza a tratar el error y se testea más fácil.

### D6 — Store pub/sub
- **Decisión**: `createStore<S>(initial)` → `{ get(): S; set(next: S | ((prev: S) => S)): void; subscribe(listener): () => void }`. Notifica solo si `!Object.is(prev, next)`, en orden de suscripción, con copia de la lista (un listener que se desuscribe durante la notificación no rompe la iteración). Estado inmutable por convención (`Readonly`).
- La sesión es un `Store<SessionState>` con `SessionState = { status: 'unknown' } | { status: 'anonymous'; notice?: 'expired' | 'offline' } | { status: 'authenticated'; user: SessionUser }`.
- **Alternativa**: `EventTarget` nativo — sin tipado de payload; un store genérico de 30 líneas es más claro.

### D7 — Render seguro (`core/dom`)
- **Decisión**: `h(tag, props?, ...children)` donde `children: Array<Node | string | number | null | false>`; las cadenas se insertan con `document.createTextNode`. `props` admite `attrs` (cadenas), `class`, `dataset`, `aria`, y `on: { click: handler }` (listeners con `addEventListener`, nunca atributos). Un atributo `on*` o un `href/src/action/formaction` cuyo esquema (tras `trim` y minúsculas, ignorando caracteres de control) no sea `http:`, `https:`, `mailto:`, `tel:` o relativo → `Error` con el nombre del atributo (no el valor). `escapeHtml(s)` para el caso excepcional de cadenas (p. ej. contenido de email de prueba); `replaceChildren` para re-render.
- **Verificación**: `src/architecture.test.ts` lee todo `src/**/*.ts` (excepto `*.test.ts`) con `import.meta.glob('/src/**/*.ts', { query: '?raw', import: 'default', eager: true })` y falla con `archivo: API` si encuentra `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `eval(` o `new Function`. El mismo test cubre "uso detectado" con una cadena de ejemplo pasada a la función de escaneo (no un archivo real).
- **Alternativa**: plantillas con `<template>` + `innerHTML` escapado — contradice la regla 10 literal y es frágil.

### D8 — Layouts y CSS
- **Decisión**: `renderPrivateLayout({ user, current, content, onLogout })` y `renderPublicLayout({ content })` devuelven un `HTMLElement` construido con `h()`. El router reutiliza el layout si no cambió de tipo y solo reemplaza el contenido de `main` (evita perder foco/scroll del shell).
- **Privado**: grid; < 64rem (1024 px): `header` arriba (logo + marca, usuario, "Salir"), `main`, `nav` fija abajo con 3 ítems ícono+texto de ≥ `--touch-target`, `padding-bottom` en `main` para no tapar contenido, respetando `env(safe-area-inset-bottom)`. ≥ 64rem: `nav` como columna lateral de 15rem bajo el header, contenido con `--container-max`. Sin *drawer*: 3 destinos caben en una barra inferior (patrón táctil más rápido que un menú oculto; menos JS y foco más simple).
- **Público**: `header` con logo + marca y enlace discreto "Acceso profesionales" (`/login`); `main` centrado (máx. 40rem, `--control-height-lg` para el flujo de reserva); `footer` con "Política de privacidad", "Términos y condiciones" (destinos `(TBD)`, PA-10) y datos del consultorio `(TBD)`.
- **Tokens**: `src/styles/base.css` hace `@import '../../../design-system/tokens.css';`. Los *breakpoints* (`64rem`) y medidas de layout (`15rem`, `40rem`) no son tokens en Revision 1: se declaran como custom properties locales en `layout.css` (`--layout-sidebar-width`, `--layout-reading-width`) y se documentan; proponer subirlas al design system en una Revision 2 si otro change las necesita. Los *media queries* no admiten `var()`, por eso el `64rem` es literal en un único lugar.
- **Verificación de tokens** (`src/styles/styles.test.ts`): lee `src/**/*.css` como `?raw`, quita comentarios, y falla si encuentra colores literales (`#hex`, `rgb(`, `hsl(`, nombres de color salvo `transparent`/`currentColor`/`inherit`) o `px`/`rem` en `font-size`, `margin*`, `padding*`, `gap`, `border-radius`, `box-shadow`, `font-family`, salvo `0` y las custom properties locales declaradas en `:root` de `layout.css`.
- **Íconos**: SVG inline mínimos (agenda, pacientes, configuración, salir) creados con `document.createElementNS`, `aria-hidden="true"` y siempre con texto visible (librería de íconos: TBD del design system).

### D9 — Marca (`brand.js`) y logo dentro de Vite
- **Decisión**: `layouts/brand.ts` hace `import '../../../design-system/brand.js'` (efecto lateral: publica `window.BRAND`) y expone `brandName(): string` leyendo `window.BRAND` con *type guard* (fallback `"La Muela"` solo si falta, con test). El logo se importa con `import logoUrl from '../../../design-system/assets/logo.svg?url'` (Vite lo copia con hash al build). `alt = "<marca> (logo provisional)"` mientras `README.md` del design system lo marque provisional.
- `vite.config.ts`: `server.fs.allow: [searchForWorkspaceRoot(process.cwd()), '../design-system']` para servir esos archivos en dev. Se agrega un caso a `vite.config.test.ts`.
- **Por qué no copiar**: `brand.js`/`tokens.css` son la fuente única (README del design system §Instrucciones de reemplazo); copiarlos rompería el reemplazo en un solo lugar.
- **Alternativa**: cargar `brand.js` como `<script>` clásico en `index.html` — Vite no lo procesa fuera de `public/` y en build quedaría una ruta rota.

### D10 — Fechas con `Intl`, sin librerías
- **Decisión**: `shared/utils/dates.ts` con `PRACTICE_ZONE = 'America/Argentina/Buenos_Aires'`, `parseInstant(iso)` (regex estricta `YYYY-MM-DDTHH:mm(:ss(.sss)?)?(Z|±HH:mm)`; si no, `InvalidInstantError`), `formatDate`, `formatTime`, `formatDateTime` con `Intl.DateTimeFormat('es-AR', { timeZone, hourCycle: 'h23' })` vía `formatToParts` (estable entre motores), `localDateOf(instant)`, `today(clock)`, `dayBoundsUtc('YYYY-MM-DD')`. El offset de la zona para una fecha se calcula con `formatToParts` (no se asume -03:00 fijo, aunque Argentina hoy no tenga horario de verano), así el código sirve si cambia la zona del consultorio en multi-tenant.
- `Clock = () => Date`; en tests se pasa un reloj fijo (y `process.env.TZ`/zona del navegador no influye porque toda conversión pasa por `timeZone`). El escenario "navegador en Europe/Madrid" se prueba fijando `process.env.TZ = 'Europe/Madrid'` en el test (Node aplica el cambio en caliente; restaurar en `afterEach`) y, además, no se usa ningún método local de `Date` (`getHours`, `toLocaleString` sin `timeZone`, etc.).
- **Alternativas**: date-fns-tz / Luxon / Temporal polyfill — dependencias de runtime nuevas (regla 12). `Temporal` nativo aún no está en todos los navegadores objetivo.

### D11 — Bootstrap
- **Decisión**: `main.ts` conserva `mountApp` y agrega `bootstrap({ doc, window, fetch, clock })`: monta, crea store de sesión, transporte, cliente HTTP (con `onUnauthorized` → sesión `anonymous{expired}` + `navigate('/login?next=…', { replace: true })`), *gateway* de sesión, router; renderiza un indicador `role="status"` mientras la sesión es `unknown` y lanza `session.load()` en paralelo. Todo inyectable para tests con jsdom y `fetch` falso. `index.html` suma `<meta name="referrer" content="no-referrer">` y el título usa la marca.

### D12 — Estrategia de tests
| Archivo | Cubre (spec) |
|---------|--------------|
| `core/dom/escape.test.ts`, `core/dom/h.test.ts` | safe-rendering |
| `architecture.test.ts` | safe-rendering (APIs prohibidas) |
| `core/state/store.test.ts` | session-state (observación) |
| `core/http/client.test.ts`, `core/http/transport.test.ts` | api-client, session-state (transporte) |
| `core/auth/session.test.ts` | session-state |
| `router/match.test.ts`, `router/safe-next.test.ts`, `router/router.test.ts`, `router/guards.test.ts` | spa-routing |
| `shared/utils/dates.test.ts` | local-time-display |
| `layouts/private-layout.test.ts`, `layouts/public-layout.test.ts`, `layouts/brand.test.ts` | app-layout |
| `styles/styles.test.ts` | app-layout (tokens) |
| `main.test.ts` | spa-shell |
- Responsive (barra inferior vs lateral, sin scroll horizontal, 44 px): jsdom no calcula layout; se verifica en la comparación visual del verify (Playwright, 390x844 y 1440x900) contra los mockups aprobados.

## Risks / Trade-offs

- [C-04 elige nombres o método distintos (PA-04)] → contrato aislado en `AuthTransport`/`SessionGateway` con nombres configurables; C-04 debe leer D4 y, si diverge, actualizar este diseño (o C-06) en vez de las pantallas.
- [Cookie `XSRF-TOKEN` legible por JS] → es el patrón estándar de Spring Security para SPA; el riesgo real es XSS, mitigado por `safe-rendering` y la CSP de C-04/C-27.
- [El router propio crece sin control] → alcance acotado a lo de D2; *lazy loading*, rutas anidadas o query tipada se agregan con un change propio si hacen falta.
- [`import.meta.glob` en tests acopla el test a Vite] → Vitest ya corre sobre Vite; es aceptable.
- [Breakpoint literal `64rem`] → un único lugar y documentado; candidato a token en Revision 2.
- [jsdom no verifica responsive] → cubierto por la verificación visual con capturas en verify.
- [`server.fs.allow` amplía lo que sirve el dev server] → solo agrega `../design-system` y `host` sigue en `localhost`.

## Migration Plan

Sin migración de datos. El deploy es estático (C-27). Rollback: revertir el commit del change; C-01 queda como estaba.

## Open Questions

- Nombres finales de cookie/cabecera CSRF y del endpoint `me` los confirma C-04 (D4); cambiar los valores por defecto no cambia specs ni tareas.
- Destinos reales de "Política de privacidad" y "Términos y condiciones" y datos de contacto del consultorio (PA-10, design system TBD 8): placeholders `(TBD)` hasta el change de reserva pública / cumplimiento.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-21)
- Mockups: openspec/changes/c-05-frontend-core-shell/mockups/
- Approval: approved (2026-11-21, by Lucas)
- Preview: rendered (2026-11-21, `mockups/previews/private-390.png`, `mockups/previews/private-1440.png`, `mockups/previews/public-390.png`, `mockups/previews/public-1440.png`; Playwright MCP vía servidor local en 127.0.0.1, sin scroll horizontal a 390 px, enlaces de la barra inferior de 50 px de alto, consola sin errores salvo `favicon.ico` del servidor de prueba antes de agregar el `<link rel="icon">`)
- Visual verify: passed (2026-11-21; Playwright sobre `npm run dev`, capturas en `mockups/verify/` private-390/1440 y public-390/1440; sin scroll horizontal a 390 px, barra inferior fija con enlaces de 50 px, lateral de 240 px a 1440 px, foco en h1, titulos "<vista> · La Muela"; sesion privada simulada solo interceptando `GET /api/v1/auth/me` en Playwright, guards intactos; consola solo con 500 del proxy a `/auth/me` por backend apagado; diferencias menores vs mockup: sin contenido ilustrativo del mockup (bajada, pasos, boton Continuar, aviso de ejemplo, fecha, nota, TBD del nombre))
- Criterio de aceptación: `mockups/private-shell.html`, `mockups/public-shell.html` y sus capturas (ver `tasks.md` grupos 8 y 9).
