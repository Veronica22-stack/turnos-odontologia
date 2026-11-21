# Tasks: C-05 `frontend-core-shell`

> Change **[UI]**: NO empezar el apply hasta que el mockup de `mockups/` esté aprobado (`Approval: approved` en `design.md` §UI Mockups y en `mockups/README.md`). Los mockups aprobados (`mockups/private-shell.html`, `mockups/public-shell.html` y sus capturas en `mockups/previews/`) son criterio de aceptación de los grupos 8 y 9.
> Governance **BAJO**: autonomía total si los tests pasan; reportar desvíos de `design.md`.
> Strict TDD por tarea: RED (test que falla, ejecutado) → GREEN (mínimo código) → TRIANGULATE (segundo caso distinto) → REFACTOR (tests verdes tras cada paso). No escribir código de producción antes de ver fallar su test.
> Comandos (desde `frontend/`): `npx vitest run <ruta>` (puntual), `npm test`, `npm run typecheck`, `npm run build`.
> Reglas duras: TS estricto sin `any` (9), sin `innerHTML` (10), solo tokens (11), **ninguna dependencia nueva** (12).

## 0. Red de seguridad

- [ ] 0.1 Ejecutar `npm test` y `npm run typecheck` desde `frontend/` y anotar la línea base (tests de `main.test.ts` y `vite.config.test.ts` verdes); si algo falla, detenerse y reportarlo como falla preexistente

## 1. Render seguro (spec `safe-rendering`)

- [ ] 1.1 RED: `src/core/dom/escape.test.ts` — `escapeHtml('<a href="x">O\'Hara & Co</a>')` = `&lt;a href=&quot;x&quot;&gt;O&#39;Hara &amp; Co&lt;/a&gt;` y `escapeHtml('')` = `''`; verificar que falla (módulo inexistente)
- [ ] 1.2 GREEN: `src/core/dom/escape.ts`; verificar que el test pasa
- [ ] 1.3 RED: `src/core/dom/h.test.ts` — `h('p', {}, '<img src=x onerror=alert(1)>')` muestra el texto literal y no contiene `img`; `'Pérez & Hijos'` se ve igual; `attrs: { onclick: 'x' }` lanza error que nombra `onclick`; `href: ' JavaScript:alert(1)'` lanza; `href: '/agenda'`, `https:`, `mailto:` y `tel:` se aceptan; `on: { click }` registra el listener (se dispara con `click()`); `dataset` y `aria-*` se aplican; verificar que falla
- [ ] 1.4 GREEN + REFACTOR: `src/core/dom/h.ts` (design D7, incluye `svg()` con `createElementNS` para íconos); verificar que `h.test.ts` pasa y `npm run typecheck` sin errores
- [ ] 1.5 RED→GREEN: `src/architecture.test.ts` — función `findForbiddenHtmlApis(files: Record<string, string>)` probada con un mapa de ejemplo (`{'x.ts': 'el.innerHTML = y'}` → `x.ts: innerHTML`; y con `outerHTML`, `insertAdjacentHTML`, `document.write`, `eval(`, `new Function`) y aplicada a `import.meta.glob('/src/**/*.ts', { query: '?raw', import: 'default', eager: true })` sin `*.test.ts` → lista vacía; verificar que pasa y que falla si se agrega temporalmente `innerHTML` a un archivo de producción (revertirlo)

## 2. Store pub/sub (spec `session-state` §Observación)

- [ ] 2.1 RED: `src/core/state/store.test.ts` — `get()` inicial; `set(valor)` y `set(prev => …)` notifican una vez con el nuevo estado; `set` con el mismo valor (`Object.is`) no notifica; `subscribe` devuelve `unsubscribe` y tras llamarlo no se notifica; un listener que se desuscribe durante la notificación no impide notificar al siguiente; verificar que falla
- [ ] 2.2 GREEN + REFACTOR: `src/core/state/store.ts` (design D6); verificar que pasa

## 3. Fechas en la zona del consultorio (spec `local-time-display`)

- [ ] 3.1 RED: `src/shared/utils/dates.test.ts` — `parseInstant('2026-03-10T12:00:00Z')` ok; `parseInstant('2026-03-10T12:00:00')` lanza `InvalidInstantError` mencionando la zona; `'2026-03-10T09:00:00-03:00'` equivale a `12:00Z`; texto basura lanza; verificar que falla
- [ ] 3.2 GREEN: `parseInstant` e `InvalidInstantError`; verificar que pasa
- [ ] 3.3 RED: `formatDate`/`formatTime`: `2026-03-10T12:00:00Z` → `10/03/2026` y `09:00`; `2026-03-11T02:00:00Z` → `10/03/2026` y `23:00`; repetir con `process.env.TZ = 'Europe/Madrid'` (restaurar en `afterEach`; declarar `process` localmente como en `vite.config.ts`, sin `@types/node`) con el mismo resultado; verificar que falla
- [ ] 3.4 GREEN: `formatDate`, `formatTime`, `formatDateTime` con `Intl.DateTimeFormat('es-AR', { timeZone: PRACTICE_ZONE, hourCycle: 'h23' })` y `formatToParts`; verificar que pasa
- [ ] 3.5 RED: `localDateOf('2026-03-11T02:00:00Z')` = `2026-03-10`; `today(() => new Date('2026-03-11T02:30:00Z'))` = `2026-03-10` y con `2026-03-10T12:00:00Z` = `2026-03-10`; `dayBoundsUtc('2026-03-10')` = `[2026-03-10T03:00:00.000Z, 2026-03-11T03:00:00.000Z)`; `dayBoundsUtc('2026-02-30')` y `'2026-3-1'` lanzan fecha inválida; verificar que falla
- [ ] 3.6 GREEN + REFACTOR: `localDateOf`, `today`, `dayBoundsUtc` con offset calculado vía `formatToParts` (design D10, sin asumir -03:00); verificar que todo `dates.test.ts` pasa y que el archivo no usa métodos locales de `Date` (`getHours`, `getDate`, `toLocale*` sin `timeZone`)

## 4. Cliente HTTP (spec `api-client`)

- [ ] 4.1 RED: `src/core/http/transport.test.ts` — `readCookie('XSRF-TOKEN', 'a=1; XSRF-TOKEN=abc123')` = `abc123` (y `undefined` si falta, con valor URL-encoded decodificado); `cookieCsrfTransport` agrega `X-XSRF-TOKEN` en `POST/PUT/PATCH/DELETE`, no en `GET/HEAD`, no la agrega sin cookie, y siempre fija `credentials: 'same-origin'`; un `bearerTransport(() => 't')` de prueba agrega `Authorization: Bearer t` y no `X-XSRF-TOKEN`; verificar que falla
- [ ] 4.2 GREEN: `src/core/http/cookies.ts` y `src/core/http/transport.ts` (`AuthTransport`, `cookieCsrfTransport`, `bearerTransport`; design D4); verificar que pasa
- [ ] 4.3 RED: `src/core/http/client.test.ts` (con `fetch` falso que registra las peticiones) — `GET /auth/me` va a `/api/v1/auth/me` con `Accept: application/json`; ruta `https://evil.example/x` y `//evil` lanzan error de uso sin llamar a `fetch`; `POST` con `body` serializa JSON con `Content-Type`; 200 válido → `{ ok: true, data }` con `decode`; 200 `{"id":42}` → `invalid-response`; 200 con cuerpo no JSON → `invalid-response`; 204 → `{ ok: true, data: undefined }`; verificar que falla
- [ ] 4.4 GREEN: `src/core/http/decode.ts` (`isRecord`, `isString`, …), `src/core/http/errors.ts` (unión `ApiError`) y `src/core/http/client.ts`; verificar que pasa
- [ ] 4.5 RED: errores — 409 `problem+json` `code: SLOT_TAKEN` → `problem{status:409, code}`; 400 `VALIDATION_FAILED` con `errors[{field:'email', code:'Email'}]` → expuestos; 500 con `errorId` → expuesto; 502 HTML → `problem{status:502, code:'UNEXPECTED_RESPONSE'}`; `fetch` que rechaza → `network`; con `vi.useFakeTimers`, una petición que no resuelve en 15 s → `timeout` y la señal abortada; `timeoutMs` configurable (segundo caso con 5 s); verificar que falla
- [ ] 4.6 GREEN + REFACTOR: manejo de errores y timeout con `AbortController`; verificar que pasa
- [ ] 4.7 RED→GREEN: 401 en `GET /patients` → `unauthorized` y `onUnauthorized` llamado una vez; 401 con `{ sessionProbe: true }` → `unauthorized` sin llamar a `onUnauthorized`; con `vi.spyOn(console, …)` sobre `log/info/warn/error/debug`, un `POST /public/bookings` con `{"dni":"30123456"}` que responde 500 no escribe nada en consola; verificar que pasa

## 5. Sesión (spec `session-state`)

- [ ] 5.1 RED: `src/core/auth/session.test.ts` (con `SessionGateway` falso) — estado inicial `unknown` y nada en `localStorage`/`sessionStorage`; `load()` con `me` 200 `{id, fullName:'Dra. Romero', role:'DENTIST'}` → `authenticated`; 401 → `anonymous` sin aviso; red → `anonymous{notice:'offline'}`; dos `ensureLoaded()` concurrentes → una sola llamada a `me`; verificar que falla
- [ ] 5.2 GREEN: `src/core/auth/session.ts` (store de sesión, `load`, `ensureLoaded`) y `src/core/auth/session-gateway.ts` (`httpSessionGateway(client)` contra `GET /auth/me` con `sessionProbe` y `POST /auth/logout`, con decoder de `SessionUser`); verificar que pasa
- [ ] 5.3 RED→GREEN: `logout()` con 204 y con error de red → en ambos `anonymous`; `expire()` desde `authenticated` → `anonymous{notice:'expired'}` y llamado dos veces notifica una sola transición; un suscriptor recibe cada cambio y deja de recibirlos tras desuscribirse; verificar que pasa

## 6. Matching y `next` seguro (spec `spa-routing`)

- [ ] 6.1 RED: `src/router/match.test.ts` — `/turno/:token` con `/turno/AbC-123_x` → `{token:'AbC-123_x'}`; `/turno/a/b` y `/turno/` no coinciden; `/agenda/` normaliza a `/agenda`; `/agenda?x=1` coincide por `pathname`; parámetro con caracteres fuera de `[A-Za-z0-9_-]` no coincide; verificar que falla
- [ ] 6.2 GREEN: `src/router/match.ts` (segmentos, sin `RegExp` desde datos); verificar que pasa
- [ ] 6.3 RED→GREEN: `src/router/safe-next.test.ts` + `safe-next.ts` — acepta `/pacientes` y `/agenda`; rechaza `//evil.example/agenda`, `/\evil`, `https://evil.example`, `javascript:alert(1)`, `/turno/abc` (pública), `/reservar`, `/no-existe`, cadena vacía, caracteres de control; descarta query; verificar que pasa

## 7. Router y guards (spec `spa-routing`)

- [ ] 7.1 RED: `src/router/router.test.ts` (jsdom, rutas de prueba y `render` espía) — `start()` en `/reservar` renderiza esa ruta con layout público; `navigate('/pacientes')` hace `pushState` y renderiza; `navigate(…, {replace:true})` usa `replaceState`; `popstate` re-renderiza la ruta previa; `/no-existe` renderiza la vista "no encontrada" sin cambiar la URL y sin el texto `no-existe`; verificar que falla
- [ ] 7.2 GREEN: `src/router/router.ts` (design D2); verificar que pasa
- [ ] 7.3 RED: interceptación de clics — clic primario en `<a href="/pacientes">` → navegación sin recarga (`preventDefault` llamado); con `ctrlKey`/`metaKey`/`shiftKey`, `target="_blank"`, `download`, otro origen o ruta no declarada → no intercepta; título = `"Pacientes · <marca>"` y foco en el `h1`; para `/turno/AbC-123_x` el título no contiene el token y no hay escritura en consola; verificar que falla
- [ ] 7.4 GREEN + REFACTOR: interceptación delegada, títulos y foco; contador de navegación para descartar resultados obsoletos de guards `async` (test: dos navegaciones seguidas con guard lento → solo se renderiza la última); verificar que pasa
- [ ] 7.5 RED: `src/router/guards.test.ts` — `requireSession`: `anonymous` en `/agenda` → redirect `replace` a `/login?next=%2Fagenda`; `unknown` espera a `ensureLoaded()` y no renderiza contenido privado mientras tanto; `authenticated` → allow. `redirectIfAuthenticated` en `/login?next=%2Fpacientes` → `/pacientes`; sin `next` o con `next` inseguro (`%2F%2Fevil.example%2Fagenda`, `javascript%3Aalert(1)`) → `/agenda`; `anonymous` → allow. `rootRedirect`: anónimo → `/reservar`, autenticado → `/agenda`; verificar que falla
- [ ] 7.6 GREEN: `src/router/guards.ts` y `src/app/routes.ts` (rutas de KB 03 + privadas, vistas placeholder con `h1`: "Reservá tu turno", "Reserva confirmada", "Gestionar turno", "Ingresar", "Agenda", "Pacientes", "Configuración", "Página no encontrada"); verificar que `guards.test.ts` y `router.test.ts` pasan

## 8. Layouts, marca y estilos (spec `app-layout`; criterio: mockups aprobados)

- [ ] 8.1 RED→GREEN: `src/layouts/brand.test.ts` + `brand.ts` — `brandName()` devuelve el `BRAND_NAME` de `design-system/brand.js` (importado por efecto lateral); con `window.BRAND` ausente o de forma inválida devuelve el fallback; `logoAlt()` = `"<marca> (logo provisional)"`; `vite.config.test.ts` suma un caso: `server.fs.allow` incluye `../design-system`; actualizar `vite.config.ts` (design D9); verificar que pasan
- [ ] 8.2 RED: `src/layouts/private-layout.test.ts` — `header` con logo (`alt` no vacío), marca, "Dra. Romero" (como texto, probando también un nombre con `<b>` que se ve literal) y botón "Salir" que invoca `onLogout`; `nav` con nombre accesible "Principal" y enlaces "Agenda", "Pacientes", "Configuración" en orden a `/agenda`, `/pacientes`, `/configuracion`; solo el actual con `aria-current="page"` (probar `agenda` y `configuracion`); `main#main` con el contenido; primer enfocable "Saltar al contenido" a `#main`; verificar que falla
- [ ] 8.3 GREEN: `src/layouts/private-layout.ts` con íconos SVG vía `svg()` (`aria-hidden`); verificar que pasa
- [ ] 8.4 RED→GREEN: `src/layouts/public-layout.test.ts` + `public-layout.ts` — `header` con marca y enlace "Acceso profesionales" a `/login`; sin `nav` "Principal"; `main#main`; `footer` con "Política de privacidad" y "Términos y condiciones" (destinos `(TBD)` como `#` documentado) y "Saltar al contenido"; verificar que pasa
- [ ] 8.5 RED: `src/styles/styles.test.ts` — función `findStyleViolations(files)` probada con ejemplos (`color: #333`, `padding: 12px`, `font-size: 14px`, `box-shadow: 0 1px red`, `rgb(` → violación con archivo y valor; `color: var(--color-text)`, `margin: 0` → ok) y aplicada a `import.meta.glob('/src/**/*.css', { query: '?raw', import: 'default', eager: true })` → sin violaciones; verificar que falla (función inexistente)
- [ ] 8.6 GREEN: `src/styles/base.css` (`@import` de `../../../design-system/tokens.css`, reset mínimo, tipografía y foco con `--focus-ring`, `.sr-only`, skip link) y `src/styles/layout.css` (design D8: barra inferior < 64rem con `--touch-target` y `env(safe-area-inset-bottom)`, sidebar ≥ 64rem, público centrado) replicando la estructura de los mockups aprobados; importarlos desde `main.ts`; verificar que `styles.test.ts` pasa y `npm run build` resuelve los `@import`

## 9. Bootstrap (spec `spa-shell`)

- [ ] 9.1 RED: ampliar `src/main.test.ts` — `index.html` contiene `<meta name="referrer" content="no-referrer">`; `bootstrap()` (jsdom, `fetch` falso) en `/reservar` con `me` 401 renderiza el layout público con "Reservá tu turno"; en `/agenda` con `me` pendiente muestra `role="status"` sin `nav` "Principal" y, al resolver 200, el layout privado con "Agenda" actual; en `/agenda` con `me` 401 termina en `/login?next=%2Fagenda`; un 401 posterior en una llamada privada estando en `/pacientes` lleva a `/login?next=%2Fpacientes` con el aviso "Tu sesión venció. Ingresá de nuevo."; "Salir" llama `POST /api/v1/auth/logout` con `X-XSRF-TOKEN` (cookie de prueba) y termina en `/login`; los tests existentes de `mountApp` siguen verdes; verificar que falla
- [ ] 9.2 GREEN + REFACTOR: `bootstrap()` en `main.ts` (design D11) e `index.html` (meta referrer; `<title>` con la marca); verificar que todo `npm test` pasa

## 10. Verificación integral

- [ ] 10.1 Ejecutar `npm test`, `npm run typecheck` y `npm run build` desde `frontend/`; todo verde y `package.json` sin dependencias nuevas (`git diff frontend/package.json` vacío salvo que el usuario haya aprobado algo)
- [ ] 10.2 Revisión de reglas duras sobre el diff: `grep -rnE "innerHTML|outerHTML|insertAdjacentHTML|: any|as any" frontend/src --include=*.ts` sin resultados en producción; sin colores ni medidas literales en `src/styles` (salvo las documentadas en design D8); sin `console.*` en `core/`
- [ ] 10.3 Verificación manual en navegador con `npm run dev` (backend apagado → sesión anónima): `/` → `/reservar`; `/agenda` → `/login?next=%2Fagenda`; `/no-existe` → "Página no encontrada"; atrás/adelante funcionan; consola sin errores salvo el fallo esperado de `/api/v1/auth/me` (registrar resultado en el resumen del apply)

## Workflow follow-up

- Verify: comparar el shell implementado con `mockups/private-shell.html` y `mockups/public-shell.html` aprobados a 390x844 y 1440x900 (capturas en `mockups/verify/`), con consola y red inspeccionadas; registrar `Visual verify` en `design.md`.
- C-04 debe confirmar o ajustar el contrato de design D4 (endpoints `me`/`logout`, cookie `XSRF-TOKEN`, cabecera `X-XSRF-TOKEN`).
- Archivar el change tras la aprobación del verify.
