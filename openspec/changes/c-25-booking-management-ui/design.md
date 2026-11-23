# Diseño: C-25 `booking-management-ui`

## Context

Ver `proposal.md` §Why. Estado relevado el 2026-11-23 sobre `dev-mayra` (HEAD `0930140`, incluye C-14, C-15 y C-24 archivados):

- `frontend/src/app/routes.ts`: `/turno/:token` (`gestionar-turno`) es `placeholderView` en layout `public`, sin guard, título fijo "Gestionar turno". `routes.test.ts` ya verifica que la vista no muestra el token. El router (`router/router.ts`) pasa `context.params.token` **sin decodificar** (`match.ts`), fija `document.title = "${route.title} · ${marca}"` y enfoca el primer `h1` tras cada render; vuelve a invocar `view(context)` en cada navegación o `popstate`.
- `index.html` ya declara `<meta name="referrer" content="no-referrer">` (C-05) y el backend envía `Referrer-Policy: no-referrer` en todas las respuestas (`auth/infrastructure/SecurityHeadersFilter.java`). `handleUnauthorized` (`main.ts`) no navega desde rutas públicas, así que el token nunca termina en `next`.
- C-24 dejó en `features/booking/`: `createSchedulePicker({ gateway, serviceId, clock, initialDate?, selectedStartsAt?, suggestFrom?, onDateChange, onChoose, onTerminal })` (tira de días, carga, vacío, 429, conexión con "Reintentar", sugerencias cercanas; `onTerminal('service-gone' | 'no-services')`), `BookingGateway.availability()`/`listServices()` con el contrato de C-12/C-07, `booking-messages.ts` (`bookingNotice` para `rate-limited`, `slot-unavailable`, `stale-page`…), `booking-ui.ts` (`bookingHead`, `backButton`, `noticeAlert`, `emptyState`, `loadingStatus`, `present`) y `styles/booking.css` (`.booking`, `.confirm-card`, `.meta-list`, `.badge`, `.sticky-cta`, `.choice-summary`, `.empty`).
- `core/dom/confirm.ts` (C-19 D8): `confirmAction({ title, text, confirmLabel?, cancelLabel? })` con `<dialog>` nativo, Escape = cancelar, foco devuelto al origen; **hoy enfoca siempre el botón de aceptar**. Estilos `.confirm-dialog` y `.btn-danger(-outline)` en `styles/settings.css`, importado globalmente por `main.ts`.
- `core/http/client.ts`: `request(method, path, { body, decode, timeoutMs })` → `HttpResult<T>` con `problem{status, code, errors, retryAfterSeconds?}` / `network` / `timeout` / `invalid-response`; `cookieCsrfTransport` agrega `X-XSRF-TOKEN` en `POST`/`DELETE`. Sin `console.*` en producción.
- Backend C-15 implementado y archivado: el contrato de D2 se leyó del **código**.

Restricciones: reglas duras 6, 8, 9–12 (TS estricto sin `any`, sin `innerHTML`, solo tokens, sin dependencias nuevas), Strict TDD, governance MEDIO.

## Goals / Non-Goals

**Goals:**
- Pantalla real de gestión sobre las piezas de C-05/C-19/C-24, sin reescribir router, cliente HTTP, selector de horarios ni layout.
- Contrato con C-15 aislado en `features/manage-booking/manage-gateway.ts` + `manage-messages.ts`.
- Lógica (forma del token, clasificación, estado de la vista, URL de acción, etiquetas de estado) en funciones puras probadas sin DOM; vistas probadas con jsdom y `fetch` simulado.
- El token como dato sensible: solo en memoria y en la ruta de la API (spec `spa-routing`).

**Non-Goals:**
- Endpoints, rate limit, expiración/revocación, emails (C-15/C-13). Política de cancelación (RN-RS-07). Cabeceras CSP/HSTS (C-26).
- Mostrar quién canceló, notas, o historial del turno; modificar datos de contacto.

## Decisions

### D1 — Estructura
```
frontend/src/features/manage-booking/
├── manage-types.ts         # BookingView, resultados clasificados, ManageGateway (solo tipos)
├── manage-gateway.ts       # view(), cancel(), reschedule(): contrato D2, decodificador y clasificación
├── service-resolver.ts     # resolveServiceId(view, listServices) — D5 (serviceId o nombre exacto único)
├── token.ts                # isPlausibleToken() — pura (D3)
├── manage-url.ts           # parseAction()/managePath(): `/turno/{t}`, `?accion=reprogramar` — puras (D4)
├── manage-state.ts         # máquina de estados de la vista — pura, sin DOM (D4)
├── manage-messages.ts      # resultado/estado → copy (única fuente de textos, D8)
├── booking-card.ts         # tarjeta del turno: prestación, insignia, meta-list (D6)
├── manage-view.ts          # /turno/:token: carga, detalle, cancelación, resultados
└── reschedule-step.ts      # pantalla de reprogramación sobre createSchedulePicker (D7)
src/core/dom/confirm.ts      # + opción `initialFocus?: 'accept' | 'cancel'` (default 'accept': C-19 igual)
src/styles/booking.css       # + sección "Gestión de turno" (solo tokens, D9)
src/app/routes.ts            # gestionar-turno → createManageView; RoutesDeps.manage
src/main.ts                  # createManageGateway({ client })
```
- Carpeta propia (no dentro de `booking/`): la gestión es otra capacidad (`booking-management-ui`) y otro contrato; importa de `booking/` solo `createSchedulePicker`, `BookingGateway` (tipo), `bookingNotice`/`booking-ui` y `slots`. Dependencia en un solo sentido (`manage-booking → booking`).
- **Alternativa**: ampliar `booking-gateway.ts` con las tres llamadas — mezcla dos contratos (C-14 y C-15) y dos capacidades; se descarta.

### D2 — Contrato confirmado con C-15 (contrastado con el código)

Fuentes: `reservas/infrastructure/BookingManagementController.java`, `reservas/api/{BookingView,RescheduleBookingRequest}.java`, `reservas/application/BookingManagementUseCase.java`, `reservas/application/BookingDetails.java`, `reservas/infrastructure/PublicBookingRateLimiter.java`, `reservas/domain/{BookingToken,BookingTokenGenerator,TokenHasher}.java`, `reservas/infrastructure/BookingManagementLinkProvider.java`, `agenda/api/AppointmentStatusView.java`, `auth/infrastructure/{SecurityConfig,SecurityHeadersFilter}.java`, `shared/api/ApiExceptionHandler.java`; specs `booking-token-management`, `availability`, `error-responses`; KB 07 §Flujo 4.

| Endpoint | Petición | Éxito | Errores reales |
|----------|----------|-------|----------------|
| `GET /api/v1/public/bookings/{token}` (sin sesión, sin CSRF) | — | **200** `BookingView` | **404** `INVALID_OR_EXPIRED_TOKEN` (inexistente, vencido, revocado, `TokenHasher` rechaza vacío/blanco; el mismo código si falta el turno) · **429** `RATE_LIMITED` + `Retry-After` · **500** `INTERNAL_ERROR` (p. ej. falla de auditoría: la consulta audita `BOOKING_TOKEN_USE`, spec "Fallo de auditoría") |
| `DELETE /api/v1/public/bookings/{token}` (**exige CSRF**) | — | **200** `BookingView` con `status: 'CANCELLED'`, banderas en `false` | **403** `CSRF_REQUIRED` · **404** `INVALID_OR_EXPIRED_TOKEN` · **422** `BOOKING_MODIFICATION_NOT_ALLOWED` (no `CONFIRMED` o ya comenzó) · **429** · **500** |
| `POST /api/v1/public/bookings/{token}/reschedule` (**exige CSRF**) | `{ startsAt }` (`@NotNull Instant`; el fin lo calcula el servidor con la **duración snapshot** del turno) | **200** `BookingView` con el nuevo `startsAt`/`endsAt` (mismo `bookingId`) | **400** `VALIDATION_FAILED` (`startsAt` nulo) / `MALFORMED_REQUEST` · **403** `CSRF_REQUIRED` · **404** `INVALID_OR_EXPIRED_TOKEN` · **409** `SLOT_TAKEN` (slot no ofrecido por la disponibilidad pública de la prestación —incluye anticipación/horizonte— u ocupado/bloqueado en carrera; **el turno original se conserva**) · **422** `BOOKING_MODIFICATION_NOT_ALLOWED` · **422** `APPOINTMENT_IN_PAST` (nuevo inicio no posterior a ahora) · **429** · **500** |

`BookingView` = `{ bookingId: uuid, serviceName: string, practiceName: string, startsAt: ISO UTC, endsAt: ISO UTC, status: 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW', cancellable: boolean, reschedulable: boolean }`. **Sin DNI, email, teléfono, notas ni `serviceId`**. `practiceName` puede ser `""` (`orElse("")`). `cancellable === reschedulable === (status == CONFIRMED && startsAt > now)` en el servidor.

- **Orden del servidor**: rate limit → resolución del token (404) → modificable (422) → [reprogramar: inicio futuro (422) → slot ofrecido (409) → `reschedule` (409 en carrera)] → auditoría. Toda operación, incluida la consulta, consume el limitador.
- **Rate limit**: `PublicBookingRateLimiter`, clave `token|{ip}` (separada de `booking|{ip}` de C-14), **30/min por IP** (`RATE_LIMIT_PUBLIC_PER_MIN`), ventana fija de 1 min. La disponibilidad y el catálogo usan el limitador de lecturas (120/min por IP, C-24 D2).
- **Token**: 32 bytes CSPRNG en **Base64URL sin padding (43 caracteres)** (`BookingTokenGenerator`); en BD solo el hash SHA-256 con comparación en tiempo constante. Vence `BOOKING_TOKEN_TTL_DAYS` (1 día) después del fin del turno; `revokeAllForAppointment` al **cancelar el paciente** (una cancelación del consultorio **no** revoca: el GET devuelve `CANCELLED` con banderas en falso). **No rota** al reprogramar (se extiende su vencimiento). Cada email (confirmación, recordatorio, reprogramación) emite **un token nuevo** (`BookingManagementLinkProvider`): pueden convivir varios enlaces válidos del mismo turno.
- **CSRF**: solo `POST /auth/login` está exento. El `GET` del turno pasa por la cadena de seguridad, que emite la cookie `XSRF-TOKEN`, y siempre precede a `DELETE`/`POST`, así que `cookieCsrfTransport` ya tiene el token. Si faltara → 403 `CSRF_REQUIRED` → `stale-page`.
- **Instantes**: el cliente envía `startsAt` exactamente como lo recibió en el slot de disponibilidad (string ISO UTC sin reformatear), igual que C-24.
- **Disponibilidad para reprogramar**: KB 07 Flujo 4 ("SPA consulta disponibilidad para la misma prestación") y el servidor (`requireOfferedSlot` con `AvailabilityAudience.PUBLIC`) usan `GET /api/v1/public/availability?serviceId=&date=`, pero **`BookingView` no trae `serviceId`** → D5 y pedido P1.

Clasificación en el gateway (las vistas solo ven estos tipos):

| Llamada | Resultado |
|---------|-----------|
| `view(token)` | `ok{booking}` · `invalid` (cualquier 404) · `rate-limited{retryAfterSeconds?}` · `connection` (red, timeout, `invalid-response`, 5xx, otro `problem`) |
| `cancel(token)` | `done{booking}` · `invalid` · `not-modifiable` (422 `BOOKING_MODIFICATION_NOT_ALLOWED`) · `rate-limited` · `stale-page` (403 `CSRF_REQUIRED`) · `unknown-outcome` (red, timeout, 5xx, `invalid-response`, otro `problem`) |
| `reschedule(token, startsAt)` | `done{booking}` · `slot-unavailable` (409 `SLOT_TAKEN`, 422 `APPOINTMENT_IN_PAST`) · `invalid` · `not-modifiable` · `rate-limited` · `stale-page` · `unknown-outcome` (incluye 400) |

- `booking` decodificado: `serviceName`, `practiceName`, `startsAt`, `endsAt` (validados con `parseInstant`), `status` (enum; otro valor → `invalid-response`), `cancellable`, `reschedulable` y `serviceId?` **opcional** (si C-15 lo agrega, P1). `bookingId` se valida como string y **se descarta**: nunca llega a las vistas.
- Ruta: `/public/bookings/${encodeURIComponent(token)}` (y `/reschedule`), siempre vía `HttpClient` (base `/api/v1`, sin otro origen).

### D3 — Forma del token (`token.ts`)
- `isPlausibleToken(t)`: `^[A-Za-z0-9_-]{20,128}$`. Fuera de eso → pantalla `invalid` **sin** petición (no consume rate limit ni deja el valor en logs de acceso). Rango amplio y no exactamente 43 para no romper si C-15 cambia la longitud; el servidor sigue siendo la autoridad.

### D4 — Estado y URL (`manage-state.ts`, `manage-url.ts`)
- **El token queda en la URL** (`/turno/{token}`): es el formato del enlace del email, permite recargar y volver a abrirlo desde el historial. **Alternativa descartada**: leerlo y reemplazar la URL por `/turno` con `replaceState` — rompe la recarga y el botón atrás, y no borra el registro que el navegador ya hizo de la URL original (historial global, sincronización), así que no protege de verdad. Mitigaciones: `no-referrer` (meta + cabecera), título fijo, página sin recursos ni enlaces externos, token solo en memoria y en la ruta de la API, aviso en pantalla "Este enlace es personal: no lo compartas".
- **Pantallas**: `loading` → `detail{booking, notice?}` | `invalid` | `rate-limited{seconds?}` | `connection`; `detail` → `rescheduling{booking, chosen?, rejectedStartsAt?, notice?}` → `rescheduled{booking, previous}`; `detail` → `cancelled{booking}`; acciones en curso `busy: 'cancel' | 'reschedule'`; avisos `not-modifiable`, `rate-limited`, `stale-page`, `unknown-outcome-cancel`, `unknown-outcome-reschedule`, `reschedule-unavailable`. Transiciones puras (`loaded(result)`, `openReschedule`, `choose(slot)`, `submitReschedule`, `resolveReschedule(result)`, `submitCancel`, `resolveCancel(result)`, `back`) probadas sin DOM.
- **Reprogramar en la URL** (spec `spa-routing`, mismo criterio que C-24 OQ-3): "Reprogramar" = `navigate('/turno/{t}?accion=reprogramar')` (push); "Volver" = `goBack()`; atrás del navegador vuelve al detalle. `createManageView` guarda el estado en su *closure* y lo reutiliza si el elemento raíz anterior sigue conectado (re-render por `popstate` dentro de la ruta, sin volver a consultar); si no, arranca con `loading` y consulta. Carga directa de `?accion=reprogramar`: consulta y, si `reschedulable`, abre la reprogramación; si no, `replaceUrl('/turno/{t}')` + detalle. Éxito de la reprogramación: `replaceUrl('/turno/{t}')` y pantalla de éxito (atrás llega al detalle anterior, que se re-renderiza con el turno ya actualizado). `parseAction(query)` → `'reschedule' | 'detail'`.
- **Cancelación sin cambio de URL**: el éxito se muestra en `/turno/{t}`; al recargar, el token ya está revocado → "Este enlace no es válido" (por diseño de C-15; el texto de éxito lo anticipa).
- **Después de un `not-modifiable`**: se vuelve a consultar (`view`) y se muestra el resultado con el aviso.

### D5 — Prestación para la reprogramación (`service-resolver.ts`)
- Si `booking.serviceId` existe (P1 resuelto) → se usa.
- Si no (contrato actual): `listServices()` y se elige la prestación pública cuyo `name` coincide **exactamente** con `serviceName` y es **única**; 0 o > 1 coincidencias, catálogo vacío, 429 o error → `reschedule-unavailable` (pantalla con salida a cancelar o contactar). Se resuelve recién al abrir "Reprogramar" (no en la carga del detalle) para no gastar lecturas.
- `createSchedulePicker` con `serviceId` resuelto, `initialDate = localDateOf(booking.startsAt)` si es ≥ hoy (si no, hoy), `onTerminal('service-gone' | 'no-services')` → `reschedule-unavailable`.
- **Riesgo aceptado del workaround**: un nombre editado entre la reserva y la gestión (el turno guarda el nombre de la prestación) deja la reprogramación online no disponible; nunca elige una prestación equivocada (exigir unicidad). Si P1 se resuelve antes del apply, la tarea 3.3 se reduce a usar el `serviceId` (el gateway ya lo decodifica como opcional).

### D6 — Detalle y resultados (`booking-card.ts`, `manage-view.ts`)
- **Detalle**: `h1` "Tu turno"; `.confirm-card` con `h2` prestación + insignia; `.meta-list` Día ("martes 24 de noviembre"), Horario ("10:30 – 11:15"), Consultorio (si no vacío); nota "Horarios de Buenos Aires"; acciones `.manage-actions`: "Reprogramar" (`btn-primary btn-lg`) y "Cancelar turno" (`btn-danger-outline btn-lg`); nota `.manage-note` "Este enlace es personal: no lo compartas."
- **Insignias** (texto + ícono, nunca solo color): `CONFIRMED` modificable → `badge-confirmado` "Confirmado"; `CANCELLED` → `badge-cancelado` "Cancelado"; resto no modificable (`CONFIRMED` comenzado, `COMPLETED`, `NO_SHOW`) → `badge-neutral` "Turno pasado" (no se informa ausencia/asistencia al paciente). La lógica de solo lectura sale de las **banderas del servidor**, no del reloj del cliente.
- **Cancelar**: `confirmAction({ title: '¿Cancelar este turno?', text: 'Limpieza · martes 24 de noviembre, 10:30. El horario queda libre para otra persona y este enlace deja de funcionar. No se puede deshacer.', confirmLabel: 'Sí, cancelar turno', cancelLabel: 'No, mantener turno', initialFocus: 'cancel' })`. `confirm.ts` suma la opción aditiva `initialFocus` (default `'accept'`, C-19 sin cambios); el botón de aceptar ya es `btn-danger`. Tras aceptar: botón "Cancelando…" con `.spinner`, `aria-busy`, `aria-disabled`, "Reprogramar" deshabilitado; un segundo clic se ignora.
- **Turno cancelado**: `h1` "Turno cancelado", tarjeta con insignia "Cancelado", alerta info "Te enviamos un email con la cancelación. Este enlace ya no sirve para gestionar el turno." + enlace "Reservar otro turno" (`/reservar`).
- **¡Turno reprogramado!**: ícono de éxito, `h1`, tarjeta con día/horario **de la respuesta** (no del slot, por la duración snapshot), línea "Antes: mar 24/11 · 10:30" tachada visualmente (`<s>` con texto "Antes:" para lectores), alerta info "Te enviamos un email con los nuevos datos. Podés seguir usando este enlace para cancelar o volver a cambiar el turno." + botón "Ver mi turno" (→ detalle).
- **Enlace no válido**: `.empty` con ícono, `h1` "Este enlace no es válido", texto "Puede que haya vencido, que el turno ya se haya cancelado o que el enlace esté incompleto. Si necesitás ayuda con tu turno, comunicate con el consultorio (TBD)." + "Reservar un turno". Mismo texto para todos los casos.
- **Solo lectura**: aviso info "Este turno ya pasó, así que no se puede cancelar ni reprogramar desde acá." (o "Este turno está cancelado. Si necesitás otro, podés reservarlo online." para `CANCELLED`) + "Reservar un turno".

### D7 — Reprogramación (`reschedule-step.ts`)
- `backButton` "Volver"; `h1` "Elegí el nuevo horario"; lead "{prestación} · {duración} min · horarios de Buenos Aires" (duración = `endsAt − startsAt` del turno); `.choice-summary` "Turno actual · mar 24/11 · 10:30 – 11:15"; selector de C-24; `.sticky-cta` con resumen del nuevo horario ("jue 26/11 · 11:15 – 12:00", fin del slot) y "Confirmar cambio" (`aria-disabled` sin horario elegido).
- **Enviando**: "Reprogramando…" con spinner, `aria-busy`; el selector queda `inert` (no se cambia el horario durante el envío); segundo clic ignorado.
- **409**: se vuelve a montar el selector en el mismo día con `suggestFrom = startsAt rechazado` (recarga y "Horarios cercanos" de C-24) y aviso warning "Ese horario se acaba de ocupar." · "Tu turno sigue el {día largo} a las {hora}. Elegí otro horario." (texto propio, no el de C-24 que menciona datos guardados).
- **Resultado desconocido**: aviso danger "No pudimos confirmar el cambio de horario." · "Puede que se haya registrado igual. Revisá el estado antes de intentar de nuevo." + botón "Ver estado actual" (→ `view` y detalle).

### D8 — Mensajes (`manage-messages.ts`; voseo, nunca `title`/`detail` del servidor)
| Resultado | Variante | Título · texto |
|-----------|----------|----------------|
| `invalid` | `.empty` | "Este enlace no es válido" · ver D6 `(TBD · datos del consultorio)` |
| `connection` (consulta) | danger | "No pudimos cargar tu turno." · "Revisá tu conexión." + "Reintentar" |
| `rate-limited` | warning | reutiliza `bookingNotice({ kind: 'rate-limited' })` ("Demasiados intentos seguidos." · "… Probá de nuevo {retry-after}.") |
| `not-modifiable` | warning | "Este turno ya no se puede modificar." · "Puede que ya haya empezado o que el consultorio lo haya cambiado. Te mostramos su estado actual." |
| `slot-unavailable` | warning | ver D7 |
| `stale-page` | danger | "La página quedó desactualizada." · "Recargala e intentá de nuevo. No se hizo ningún cambio." |
| `unknown-outcome` (cancelar) | danger | "No pudimos confirmar la cancelación." · "Puede que se haya registrado igual: si se canceló, te va a llegar un email y este enlace va a dejar de funcionar. Si no, probá de nuevo." |
| `unknown-outcome` (reprogramar) | danger | ver D7 |
| `reschedule-unavailable` | info | "No se puede reprogramar este turno online." · "La prestación ya no está disponible para reservar desde la web. Comunicate con el consultorio para cambiarlo o cancelalo desde acá." `(TBD · datos del consultorio)` |

### D9 — Estilos (`src/styles/booking.css`, sección "Gestión de turno")
- Reutiliza `.booking`, `.booking-head`, `.confirm-card`, `.meta-list`, `.badge`, `.choice-summary`, `.sticky-cta`, `.empty`, alertas y spinner de C-24; `.confirm-dialog` y `.btn-danger(-outline)` de `settings.css` (C-19).
- Agrega `.badge-cancelado` (`--status-cancelado-fg/bg`, texto tachado como `components.html`), `.badge-neutral`, `.manage-actions` (columna en mobile con botones a ancho completo; fila en desktop), `.manage-note`, `.previous-time` (texto secundario con `<s>`), `.picker-busy` (opacidad reducida mientras se envía). Solo tokens; mismas excepciones de bordes `1px`/`2px` que C-24 D8. Revision 1 sin cambios.
- Desktop (≥ 64rem): tarjeta `.booking` de C-24 (40rem); acciones lado a lado. `prefers-reduced-motion` ya cubierto por C-24.

### D10 — Rutas y arranque
- `routes.ts`: `gestionar-turno` → `createManageView({ gateway: deps.manage, booking: deps.booking, navigate, replaceUrl, goBack, clock })`; título fijo "Gestionar turno". `RoutesDeps` suma `manage: ManageGateway` (los tests existentes reciben un gateway falso).
- `main.ts`: `createManageGateway({ client })` con el mismo `HttpClient`.

### D11 — Estrategia de tests (Vitest + jsdom, `fetch` simulado con D2)
| Archivo | Cubre (spec) |
|---------|--------------|
| `manage-booking/token.test.ts` | Forma del token (43 Base64URL, `-`/`_`, corto, largo, `$`, `%`, `/`, vacío) |
| `manage-booking/manage-url.test.ts` | `parseAction` (`reprogramar`, ausente, `borrar`, mayúsculas) y `managePath` |
| `manage-booking/manage-gateway.test.ts` | Rutas exactas y codificadas, `X-XSRF-TOKEN` en `DELETE`/`POST`, cuerpo `{"startsAt": …}` sin reformatear, cada código de D2, `Retry-After`, decodificación inválida, `bookingId` descartado, `serviceId` opcional |
| `manage-booking/service-resolver.test.ts` | `serviceId` presente; nombre único; 0 y 2 coincidencias; catálogo 429/red |
| `manage-booking/manage-state.test.ts` | Transiciones de D4 (cada resultado, doble envío, volver) |
| `manage-booking/manage-messages.test.ts` | Copy por resultado; insignias por estado (`NO_SHOW` → "Turno pasado") |
| `manage-booking/manage-view.test.ts` | Detalle, solo lectura, inválido (con y sin petición), 429, conexión + reintento, cancelar (desistir/Escape/confirmar/doble toque/422/timeout/403) |
| `manage-booking/reschedule-step.test.ts` | Abrir (push `?accion=reprogramar`), día inicial, elegir, confirmar OK, 409 con sugerencias, timeout + "Ver estado actual", no disponible, volver/atrás, recarga de `?accion=reprogramar` |
| `core/dom/confirm.test.ts` (+casos) | `initialFocus: 'cancel'`; default sin cambios |
| `app/routes.test.ts`, `manage-integration.test.ts` | Ruta real, título fijo en todos los estados, sin rastros del token (título, consola, texto, storages, `href`, otras peticiones) |
- Datos ficticios; token de prueba `tok_Abc-123_xyz456789012345` (no real); reloj fijo `2026-11-23T13:00:00Z`.

## Risks / Trade-offs

- [Token en la URL] Queda en el historial del navegador y es visible a quien mire la pantalla → es el diseño del enlace por email (KB 08); vence 1 día después del turno; `no-referrer`; aviso "no lo compartas". Alternativa descartada en D4.
- [Sin `serviceId` en `BookingView`] La reprogramación depende de encontrar la prestación por nombre exacto y único → P1; si falla, "no se puede reprogramar online" (nunca una prestación equivocada).
- [Duración distinta] La disponibilidad calcula los slots con la duración **actual** de la prestación y el servidor reprograma con la duración **snapshot** → el fin mostrado en el resumen del selector puede diferir del final real; la pantalla de éxito usa `endsAt` de la respuesta. P3.
- [Slots que se superponen con el turno propio] La disponibilidad trata el turno actual como ocupado: no se puede correr el turno a un horario que se superpone con él (p. ej. 15 minutos más tarde) → P2, informativo.
- [Recarga tras cancelar] El token queda revocado: recargar muestra "Este enlace no es válido" → el texto de éxito lo anticipa.
- [Timeout ambiguo] Una cancelación/reprogramación pudo aplicarse sin respuesta → mensajes `unknown-outcome` que no afirman ni niegan; "Ver estado actual" para reprogramar; para cancelar, el email o el 404 posterior lo confirman (P4).
- [Rate limit compartido] La consulta también consume los 30/min por IP de operaciones con token (compartido con quien comparta IP) → aviso con `Retry-After`, sin reintento automático.
- [Varias pestañas/enlaces] Cada email trae un token distinto del mismo turno; cancelar revoca todos; una pestaña vieja recibe 404/422 en su próxima acción → mensajes genéricos y re-consulta.
- [`confirmAction` modificado] Cambio aditivo en `core/dom/confirm.ts`; los tests de C-19 cubren el default.

### Pedidos a C-15

Ninguno bloquea el apply: todos tienen salida en el frontend. No se cambia el backend en este change.

| # | Pedido | Tipo | Workaround en C-25 |
|---|--------|------|--------------------|
| P1 | Agregar `serviceId` (UUID de la prestación) a `BookingView`: cambio aditivo; el id de una prestación pública ya es público en `GET /public/services` | **Recomendado** (quita el workaround más frágil) | Resolución por nombre exacto y único (D5); el gateway ya decodifica `serviceId` opcional |
| P2 | Disponibilidad para reprogramar que ignore el propio turno (p. ej. `GET /public/bookings/{token}/availability?date=`), para permitir correrlo a un horario que se superpone con el actual | Informativo | No se ofrecen esos horarios |
| P3 | Exponer la duración del turno (o calcular los slots con la duración snapshot) para que el fin mostrado al elegir coincida con el final real | Informativo | Fin real en la pantalla de éxito |
| P4 | Sin forma de saber si un `DELETE`/`POST` con timeout se aplicó (no hay idempotencia); tras cancelar el token queda revocado y la re-consulta da 404 | Informativo | Mensajes `unknown-outcome` |

## Migration Plan

Sin migración de datos ni cambios de backend. Rollback: revertir el commit del change; vuelve el *placeholder* de C-05 en `/turno/:token`. Con backend y datos de ejemplo (`npm run dev` con proxy a `/api`), se prueba reservando un turno (C-24), tomando el enlace del email registrado por el proveedor de desarrollo (C-13) y abriéndolo.

## Open Questions

- OQ-1 (equipo / C-15): ¿se implementa P1 (`serviceId` en `BookingView`) antes o después del apply? No cambia las tareas: el gateway acepta ambos casos (D5).
- OQ-2 (PA-14 / producto): datos de contacto del consultorio en "Enlace no válido" y "No se puede reprogramar online" siguen `(TBD)` (design system TBD 8).
- OQ-3 (PA-21): nivel WCAG objetivo; se aplica la línea base del design system.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-23)
- Mockups: openspec/changes/c-25-booking-management-ui/mockups/
- Mockup revision: 1
- Approval: pending (aprobador: Lucas)
- Preview: rendered (2026-11-23, revisión 1, `mockups/previews/<pantalla>-390.png` y `-1440.png`, 18 pantallas / 36 capturas; Chrome headless por CDP (script Node sin dependencias) con `Emulation.setDeviceMetricsOverride` 390x844 (mobile) y 1440x900, página completa salvo `cancel-confirm` (viewport, diálogo fijo); servidor local `python -m http.server` en 127.0.0.1:8765, detenido al terminar; sin scroll horizontal de página a 390 px ni errores de consola; inspeccionadas visualmente: se agregó el anillo de foco en "No, mantener turno" del diálogo para mostrar el foco inicial)
- Visual verify: pendiente (tras el apply)
- Criterio de aceptación: los HTML de `mockups/` y sus capturas (ver `tasks.md` grupos 4 a 7).
