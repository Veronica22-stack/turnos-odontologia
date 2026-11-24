# Tasks: C-25 `booking-management-ui`

> Change **[UI]**: NO empezar el apply hasta que los mockups de `mockups/` estén aprobados por Lucas (`Approval: approved` en `design.md` §UI Mockups y en `mockups/README.md`). Los mockups aprobados (`mockups/*.html` y sus capturas en `mockups/previews/`) son criterio de aceptación de los grupos 4 a 7.
> Governance **MEDIO**: implementar con checkpoints; reportar al usuario las decisiones no obvias y cualquier desvío de `design.md` (en especial del contrato D2, la resolución de la prestación D5 y las preguntas OQ-1..OQ-3).
> **C-15 está implementado y su contrato está confirmado contra el código (design D2)**: los tests usan un `fetch` simulado con ese contrato real (`BookingView` sin `serviceId`, 404 `INVALID_OR_EXPIRED_TOKEN`, 422 `BOOKING_MODIFICATION_NOT_ALLOWED`/`APPOINTMENT_IN_PAST`, 409 `SLOT_TAKEN`, 429 `RATE_LIMITED` + `Retry-After`, 403 `CSRF_REQUIRED`, CSRF en `DELETE`/`POST`). No tocar `backend/`.
> Strict TDD por tarea: RED (test que falla, ejecutado) → GREEN (mínimo código) → TRIANGULATE (segundo caso distinto) → REFACTOR (tests verdes tras cada paso). No escribir código de producción antes de ver fallar su test.
> Comandos (desde `frontend/`): `npx vitest run <ruta>` (puntual), `npm test`, `npm run typecheck`, `npm run build`.
> Reglas duras: TS estricto sin `any` (9), sin `innerHTML` (10), solo tokens (11), **ninguna dependencia nueva** (12); nunca escribir el token ni datos personales en consola/URL de otras peticiones/título/storage (6, spec `spa-routing`); instantes UTC mostrados en Buenos Aires (8). En tests usar solo datos ficticios, el token de prueba `tok_Abc-123_xyz456789012345` y reloj fijo `2026-11-23T13:00:00Z`.

## 0. Red de seguridad

- [x] 0.1 Ejecutar `npm test` y `npm run typecheck` desde `frontend/` y anotar la línea base (suite de C-05/C-06/C-19/C-24 verde); si algo falla, detenerse y reportarlo como falla preexistente

## 1. Funciones puras (specs `booking-management-ui`, `spa-routing`)

- [x] 1.1 RED→GREEN: `src/features/manage-booking/token.test.ts` + `token.ts` — `isPlausibleToken` acepta 43 caracteres Base64URL (con `-` y `_`) y 20/128 caracteres; rechaza 19 y 129, vacío, `abc$%`, `a/b`, `a.b`, espacios; verificar que falla antes y pasa después
- [x] 1.2 RED→GREEN: `src/features/manage-booking/manage-url.test.ts` + `manage-url.ts` — `parseAction(new URLSearchParams('accion=reprogramar'))` → `'reschedule'`; ausente, `accion=borrar`, `accion=REPROGRAMAR`, `accion=` → `'detail'`; `managePath(t)` → `/turno/{t}` y `managePath(t, 'reschedule')` → `/turno/{t}?accion=reprogramar` (token codificado como segmento)
- [x] 1.3 RED→GREEN: `src/features/manage-booking/manage-messages.test.ts` + `manage-messages.ts` — copy de design D8 por resultado (`not-modifiable`, `stale-page`, `unknown-outcome` de cancelar y de reprogramar, `reschedule-unavailable`, `connection`, conflicto con "Tu turno sigue el martes 24 de noviembre a las 10:30."); `rate-limited` reutiliza `bookingNotice` (40 s → "en 1 minuto"); insignia por estado: `CONFIRMED` modificable → "Confirmado", `CANCELLED` → "Cancelado", `CONFIRMED` no modificable, `COMPLETED` y `NO_SHOW` → "Turno pasado"; ningún texto contiene `title`/`detail` de un `problem`
- [x] 1.4 RED→GREEN: `src/features/manage-booking/manage-state.test.ts` + `manage-state.ts` — `loaded` de cada resultado de `view`; `openReschedule` solo si `reschedulable`; `choose`; `submitReschedule` → `busy` y un segundo submit no cambia el estado; `resolveReschedule` (`done` → `rescheduled{booking, previous}`, `slot-unavailable` → `rescheduling` con `rejectedStartsAt` y sin elección, `not-modifiable` → pide re-consulta, `unknown-outcome`, `stale-page`, `rate-limited`, `invalid`); `submitCancel`/`resolveCancel` análogos (`done` → `cancelled`); `back` desde `rescheduling` → `detail` con el turno original

## 2. Gateway de gestión (contrato confirmado con C-15, design D2)

- [x] 2.1 RED: `src/features/manage-booking/manage-gateway.test.ts` (fetch falso) — `view(token)` → `GET /api/v1/public/bookings/<token>` (segmento codificado); 200 con `BookingView` válido → `ok{booking}` sin `bookingId` en el resultado y con `serviceId` solo si vino; `status` desconocido, instante sin zona o banderas no booleanas → `connection`; 404 (`INVALID_OR_EXPIRED_TOKEN` y otro código) → `invalid`; 429 con `Retry-After: 30` → `rate-limited{30}`; 500, red y timeout → `connection`
- [x] 2.2 GREEN: `manage-types.ts` + `view` en `manage-gateway.ts`; verificar que pasa
- [x] 2.3 RED→GREEN: `cancel(token)` → `DELETE` misma ruta con `X-XSRF-TOKEN` (cookie presente en el test); 200 → `done{booking}`; 404 → `invalid`; 422 `BOOKING_MODIFICATION_NOT_ALLOWED` → `not-modifiable`; 403 `CSRF_REQUIRED` → `stale-page`; 429 → `rate-limited`; 500, red, timeout y 200 ilegible → `unknown-outcome`
- [x] 2.4 RED→GREEN: `reschedule(token, startsAt)` → `POST …/<token>/reschedule` con `X-XSRF-TOKEN` y cuerpo exacto `{"startsAt":"2026-11-26T14:15:00Z"}` (sin reformatear); 200 → `done{booking}`; 409 `SLOT_TAKEN` y 422 `APPOINTMENT_IN_PAST` → `slot-unavailable`; 422 `BOOKING_MODIFICATION_NOT_ALLOWED` → `not-modifiable`; 400 `VALIDATION_FAILED`, 500, red y timeout → `unknown-outcome`; 404, 403, 429 como en 2.3
- [x] 2.5 RED→GREEN: `src/features/manage-booking/service-resolver.test.ts` + `service-resolver.ts` (design D5) — con `serviceId` en el turno → ese id sin llamar a `listServices`; sin `serviceId` y una prestación pública de nombre idéntico → su id; 0 coincidencias, 2 coincidencias, `Limpieza ` vs `Limpieza` (exacto), catálogo vacío, `rate-limited` y `connection` → `unavailable`

## 3. Confirmación accesible (core, C-19)

- [x] 3.1 RED→GREEN: `src/core/dom/confirm.test.ts` — `confirmAction({ …, initialFocus: 'cancel' })` enfoca el botón de cancelar; sin la opción sigue enfocando aceptar (C-19 sin cambios); Escape resuelve `false` y devuelve el foco al origen; `npx vitest run src/core/dom src/features/settings` verde

## 4. Detalle, solo lectura y errores de carga (acepta contra `mockups/loading.html`, `detail.html`, `past.html`, `cancelled-readonly.html`, `invalid-token.html`, `rate-limited.html`, `connection-error.html`)

- [x] 4.1 RED: `src/features/manage-booking/manage-view.test.ts` (jsdom, gateways y `navigate` falsos, reloj fijo) — al montar: "Cargando tu turno…" `role="status"` y sin acciones; 200 → `h1` "Tu turno", "Limpieza", insignia "Confirmado", "martes 24 de noviembre", "10:30 – 11:15", "Consultorio La Muela", botones "Reprogramar" y "Cancelar turno", nota "Este enlace es personal"; el texto no contiene el `bookingId` ni el token; `practiceName: ''` omite la línea
- [x] 4.2 GREEN: `booking-card.ts` + `manage-view.ts` con `h()` y piezas de `booking-ui.ts`; verificar que pasa
- [x] 4.3 RED→GREEN: banderas en falso → "Turno pasado" + aviso "Este turno ya pasó…" sin botones; `NO_SHOW` → "Turno pasado" y el texto no contiene "Ausente"; `CANCELLED` → "Cancelado" + aviso + enlace `/reservar`; 404 → `.empty` "Este enlace no es válido" con enlace a `/reservar`; token `abc$%` → mismo mensaje **sin** petición; 429 → aviso de espera sin datos; red → "No pudimos cargar tu turno." + "Reintentar" que repite la consulta y muestra el turno

## 5. Cancelación (acepta contra `mockups/cancel-confirm.html`, `cancelling.html`, `cancelled.html`, `not-modifiable.html`, `cancel-unknown.html`)

- [x] 5.1 RED: en `manage-view.test.ts` — "Cancelar turno" abre `dialog` con "¿Cancelar este turno?", "Limpieza · martes 24 de noviembre, 10:30", "No se puede deshacer", foco en "No, mantener turno"; "No, mantener turno" o Escape → sin petición y foco de vuelta en "Cancelar turno"
- [x] 5.2 GREEN: cancelación en `manage-view.ts` con `confirmAction({ initialFocus: 'cancel' })`; verificar que pasa
- [x] 5.3 RED→GREEN: "Sí, cancelar turno" → un `DELETE`; pendiente → "Cancelando…" con `aria-busy="true"`, "Reprogramar" deshabilitado y un segundo clic sin petición; 200 → `h1` "Turno cancelado" enfocado, insignia "Cancelado", aviso de email y de enlace que ya no sirve, enlace "Reservar otro turno", sin acciones; 422 → aviso "Este turno ya no se puede modificar." + nueva consulta mostrada en solo lectura; 403 → "La página quedó desactualizada…" con el turno intacto; 429 → aviso de espera con el botón habilitado; timeout → "No pudimos confirmar la cancelación…"; 404 → "Este enlace no es válido"

## 6. Reprogramación (acepta contra `mockups/reschedule.html`, `reschedule-submitting.html`, `reschedule-conflict.html`, `rescheduled.html`, `reschedule-unknown.html`, `reschedule-unavailable.html`)

- [x] 6.1 RED: `src/features/manage-booking/reschedule-step.test.ts` — "Reprogramar" → `navigate('/turno/<t>?accion=reprogramar')` (push); `h1` "Elegí el nuevo horario" enfocado, lead "Limpieza · 45 min · horarios de Buenos Aires", "Turno actual" con "mar 24/11 · 10:30 – 11:15"; la tira empieza en hoy (lunes 23) con martes 24 (día del turno) elegido y consulta `date=2026-11-24` con el `serviceId` resuelto; "Confirmar cambio" con `aria-disabled` y sin petición al tocarlo
- [x] 6.2 GREEN: `reschedule-step.ts` sobre `createSchedulePicker` (design D7); verificar que pasa
- [x] 6.3 RED→GREEN: elegir jueves 26, 11:15 → resumen "jue 26/11 · 11:15 – 12:00"; "Confirmar cambio" → un `POST` con el `startsAt` del slot; pendiente → "Reprogramando…", selector `inert`, segundo clic ignorado; 200 → `replaceUrl('/turno/<t>')`, `h1` "¡Turno reprogramado!" con "jueves 26 de noviembre", horario de la **respuesta** y "Antes: mar 24/11 · 10:30", aviso de email y de enlace que sigue sirviendo; "Ver mi turno" → detalle actualizado
- [x] 6.4 RED→GREEN: 409 → mismo día, aviso "Ese horario se acaba de ocupar." + "Tu turno sigue el martes 24 de noviembre a las 10:30.", disponibilidad del jueves 26 consultada de nuevo, "Horarios cercanos" sin preselección; luego otro horario + confirmar → éxito; timeout → "No pudimos confirmar el cambio de horario." + "Ver estado actual" que consulta y muestra el detalle; 422 `BOOKING_MODIFICATION_NOT_ALLOWED` → aviso + solo lectura; 403/429/404 como en 5.3
- [x] 6.5 RED→GREEN: resolución no disponible (D5: 0 o 2 coincidencias) y `onTerminal('service-gone' | 'no-services')` → "No se puede reprogramar este turno online." con "Volver" al detalle donde "Cancelar turno" sigue disponible; sin `serviceId` en el turno se llama a `listServices` una sola vez por apertura y nunca en la carga del detalle
- [x] 6.6 RED→GREEN: URL (spec `spa-routing`) — "Volver" → `goBack()`; `popstate` a `/turno/<t>` con la raíz conectada → detalle sin nueva consulta; carga directa de `?accion=reprogramar` con `reschedulable: true` → reprogramación; con `false` → `replaceUrl('/turno/<t>')` y solo lectura; `?accion=borrar` → detalle; salir a otra ruta y volver → nueva consulta
- [x] 6.7 Estilos: sección "Gestión de turno" en `src/styles/booking.css` (design D9); `styles.test.ts` verde sin nuevas excepciones de color/espaciado; sin scroll horizontal a 320 px (no verificado en el verify visual: queda como pendiente manual junto con 8.3)

## 7. Rutas, integración y rastros (`routes.test.ts`, `manage-integration.test.ts`; specs `booking-management-ui`, `spa-routing`)

- [x] 7.1 RED→GREEN: `src/app/routes.test.ts` — `gestionar-turno` usa la vista real (no `placeholderView`), layout `public`, sin guard, título "Gestionar turno"; `RoutesDeps.manage` cableado en `main.ts` con `createManageGateway({ client })`
- [x] 7.2 RED→GREEN: `src/manage-integration.test.ts` con `bootstrap` y `fetch` simulado (`/auth/me` 401, turno, servicios, disponibilidad, `POST` 409 y luego 200, `DELETE` 200): `/turno/<t>` → reprogramar → conflicto → reprogramar OK → cancelar; atrás desde `?accion=reprogramar` vuelve al detalle; el título es siempre "Gestionar turno · <marca>"
- [x] 7.3 RED→GREEN: sin rastros — espiar `console.*`; tras el recorrido de 7.2 y tras un 404, ni la consola, ni `document.title`, ni el texto de la página, ni `localStorage`/`sessionStorage`, ni los `href` de la página, ni la URL o el cuerpo de las peticiones a `/public/services` y `/public/availability` contienen el token; solo las peticiones a `/public/bookings/` lo llevan, en la ruta

## 8. Verificación integral

- [x] 8.1 Ejecutar `npm test`, `npm run typecheck` y `npm run build` desde `frontend/`; todo verde y `git diff frontend/package.json` vacío
- [x] 8.2 Revisión de reglas duras sobre el diff: `grep -rnE "innerHTML|outerHTML|insertAdjacentHTML|: any|as any" frontend/src --include=*.ts` sin resultados en producción; sin `console.*` en `features/manage-booking`; sin colores ni medidas literales nuevas en `src/styles/booking.css` salvo las documentadas en design D9
- [ ] 8.3 Verificación manual con `npm run dev` y backend de C-15 (reservar con C-24, abrir el enlace registrado por el proveedor de email de desarrollo): ver, reprogramar, forzar 409 reservando el mismo slot en otra pestaña, cancelar y recargar (→ enlace no válido); registrar el resultado en el resumen del apply — pendiente: prueba manual contra backend real, a realizar por un integrante con el entorno completo

## Workflow follow-up

- Verify: comparar `/turno/{token}` (cargando, detalle, solo lectura, cancelado por el consultorio, enlace no válido, 429, conexión, diálogo de cancelación, cancelando, cancelado, 422, resultado desconocido, reprogramación, reprogramando, 409, reprogramado, no disponible) con los mockups aprobados a 390x844 y 1440x900 (capturas en `mockups/verify/`; estados forzados interceptando `/api/v1/public/*`), con consola y red inspeccionadas; registrar `Visual verify` en `design.md`.
- Archive: sincronizar `booking-management-ui` y `spa-routing`; marcar C-25 en `CHANGES.md`; avisar a C-26 que la gestión por token está lista para la revisión de seguridad.
