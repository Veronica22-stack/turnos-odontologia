# Diseño: C-24 `public-booking-ui`

## Context

Ver `proposal.md` §Why. Estado relevado el 2026-11-23 sobre `dev-mayra` con `master` mergeado (HEAD `dbe1567`, incluye C-14 y C-15):

- `frontend/src/app/routes.ts`: `/reservar` (`reservar`) y `/reservar/confirmado` (`reserva-confirmada`) son `placeholderView` en layout `public`, sin guard. `createRoutes(deps)` recibe `{ session, gateway, settings, navigate, onLogout }`; `main.ts` arma `createHttpClient` con `cookieCsrfTransport` y los gateways de auth y configuración.
- `core/http/client.ts`: `request(method, path, { body, decode, credentialAttempt, timeoutMs })` → `HttpResult<T>`; errores `problem{status, code, errors[{field, code}], errorId?, retryAfterSeconds?}` / `unauthorized` / `network` / `timeout` / `invalid-response`. Timeout por defecto 15 s. CSRF: el transporte toma la cookie `XSRF-TOKEN` y la envía como `X-XSRF-TOKEN` en métodos que modifican estado (`api-client`).
- `core/dom/form.ts` (C-06): `field()`, `setFieldError()`, `clearFieldErrors()`, `alertBox()`, `formIcon()`; `core/dom/h.ts` crea nodos sin `innerHTML` (`architecture.test.ts` lo vigila). `features/auth/retry-after.ts` ya traduce segundos a "en 1 minuto" / "en unos N minutos" / "en unos minutos".
- `shared/utils/dates.ts` (C-05): `PRACTICE_ZONE`, `parseInstant`, `formatDate`, `formatTime`, `localDateOf`, `today(clock)`, `dayBoundsUtc`. No hay suma de días ni etiquetas de día de la semana (`Intl` da "martes, 24 de noviembre", con coma).
- Estilos: `styles/{base,layout,forms,settings}.css` solo con tokens (`styles.test.ts` escanea `src/**/*.css`). Design system Revision 1 (aprobado) define el patrón de reserva pública en `components.html` §Reserva: `.service-option` (radio en tarjeta), `.stepper` ("Paso 2 de 4"), `.day-strip`/`.day`, `.slot-group`/`.slots`/`.slot`, `.sticky-cta`, `.summary-line`, `.empty`, `.check`, `.meta-list`, `.badge-confirmado`.
- Backend: C-07, C-12, **C-14 y C-15 implementados y archivados** (2026-11-23). El contrato de D2 se leyó del **código** (fuente de verdad), no del roadmap.

Restricciones: reglas duras 6 (no loguear datos personales), 8 (UTC guardado, Buenos Aires mostrado), 9–12 (TS estricto sin `any`, sin `innerHTML`, solo tokens, sin dependencias nuevas), Strict TDD, governance MEDIO. Lección de C-06: los nombres de campos y códigos salen del código real y se concentran en un solo módulo.

## Goals / Non-Goals

**Goals:**
- Flujo real de reserva sobre las piezas de C-05/C-06, sin reescribir router, cliente HTTP ni layout público.
- Contrato con C-07/C-12/C-14 aislado en `features/booking/booking-gateway.ts` + `booking-messages.ts`: un ajuste del backend no toca las vistas.
- Toda la lógica (validación, agrupado, sugerencias, mapeo de errores, estado del flujo) en funciones puras probadas sin DOM; vistas probadas con jsdom y `fetch` simulado.
- Selector de día/horario reutilizable por C-25 (reprogramación).

**Non-Goals:**
- Endpoints, rate limit, honeypot del lado servidor, límite por contacto (C-14).
- `/turno/{token}` (C-25); el texto legal del aviso (PA-10); CAPTCHA/verificación de email (PA-05); WCAG formal (PA-21).
- Calendario mensual, marcar días sin horarios antes de consultarlos (requiere un endpoint que no existe, ver P2), modo oscuro.

## Decisions

### D1 — Estructura
```
frontend/src/features/booking/
├── booking-gateway.ts      # listServices(), availability(), book(): contrato D2, decodificadores y clasificación de errores
├── booking-messages.ts     # resultado/código → copy en español (única fuente de textos de error)
├── validation.ts           # validateBookingForm(), normalizeDni() — puras, mismas reglas que el servidor
├── slots.ts                # groupSlots() (Mañana/Tarde), suggestNearest() — puras
├── mask-email.ts           # maskEmail() — pura: `ma•••@gmail.com` (D6)
├── booking-flow.ts         # máquina de estados del flujo (paso, elección, datos) — pura, sin DOM
├── booking-step-url.ts     # parseStep()/stepPath(): `/reservar`, `?paso=2|3` — puras (D3)
├── confirmation-store.ts   # confirmación en memoria (con email ya enmascarado), se consume una vez (D6)
├── booking-view.ts         # /reservar: monta los pasos según el estado
├── service-step.ts         # paso 1
├── schedule-step.ts        # paso 2 (exporta createSchedulePicker para C-25)
├── details-step.ts         # paso 3 (formulario + honeypot)
└── confirmation-view.ts    # /reservar/confirmado
src/shared/utils/dates.ts   # + addLocalDays(), dayLabels() (spec local-time-display)
src/styles/booking.css      # patrón de reserva de components.html, solo tokens
src/app/routes.ts           # vistas reales de reservar y reserva-confirmada; deps.booking
src/main.ts                 # createBookingGateway({ client, clock })
```
- `retry-after.ts` se mueve de `features/auth/` a `shared/utils/retry-after.ts` (lo usan auth y booking; se ajustan los imports de auth, sin cambio de comportamiento).
- **Alternativa**: un único `booking-view.ts` grande — más difícil de probar por paso; se descarta.

### D2 — Contrato confirmado con C-07 / C-12 / C-14 (contrastado con el código)

Fuentes: `agenda/infrastructure/PublicServicesController.java`, `agenda/api/PublicServiceResponse.java`, `reservas/infrastructure/AvailabilityPublicController.java`, `reservas/infrastructure/AvailabilityParams.java`, `reservas/api/{AvailabilityResult,AvailabilityStatus,AvailableSlot}.java`, `reservas/application/AvailabilityUseCase.java`, `reservas/infrastructure/PublicBookingsController.java`, `reservas/api/{CreateBookingRequest,CreateBookingResponse}.java`, `reservas/application/PublicBookingUseCase.java`, `reservas/infrastructure/PublicBookingRateLimiter.java`, `shared/infrastructure/PublicReadRateLimiter.java`, `shared/api/ApiExceptionHandler.java`, `auth/infrastructure/SecurityConfig.java`, `pacientes/domain/Patient.java` (`normalizeAndValidateDni`); specs `public-services`, `availability`, `public-booking`, `error-responses`.

| Endpoint | Petición | Éxito | Errores reales |
|----------|----------|-------|----------------|
| `GET /api/v1/public/services` (sin sesión, sin CSRF) | — | **200** `[{ id: uuid, name, description: string\|null, durationMin: int }]` (solo activas y visibles; `Cache-Control: public, max-age=60`) | **429** `RATE_LIMITED` + `Retry-After` (lecturas públicas: 120/min por IP, `app.public-read.rate-limit.*`) |
| `GET /api/v1/public/availability?serviceId=&date=` (sin sesión, sin CSRF) | `serviceId` UUID, `date` `aaaa-mm-dd` (día local del consultorio) | **200** `{ status: 'OK' \| 'NO_ACTIVE_SERVICES', slots: [{ startsAt, endsAt }] }` con instantes ISO UTC (`2026-03-10T12:00:00Z`). Día sin tramos, fuera del horizonte o todo dentro de la anticipación mínima → `slots: []` con `OK` (no es error). Catálogo sin prestaciones públicas → `NO_ACTIVE_SERVICES` con `[]` | **400** `VALIDATION_FAILED` `errors[{field:'serviceId'\|'date', code:'REQUIRED'\|'UUID'\|'ISO_LOCAL_DATE'}]` · **404** `SERVICE_NOT_FOUND` (inexistente o **no pública**) · **422** `SERVICE_INACTIVE` · **429** `RATE_LIMITED` + `Retry-After` (mismo limitador de lecturas) |
| `POST /api/v1/public/bookings` (sin sesión, **exige CSRF**: solo `/auth/login` está exento) | `{ serviceId, startsAt, firstName, lastName, dni, phone, email, website, privacyAccepted }` — `firstName`/`lastName` `@NotBlank` ≤ 100; `dni` `@NotBlank` ≤ 20; `phone` `@NotBlank` ≤ 50; `email` `@NotBlank @Email` ≤ 255; `website` ≤ 100 (honeypot); `privacyAccepted` `@NotNull @AssertTrue`; campos de fin/duración se ignoran | **201** `{ bookingId, serviceName, startsAt, endsAt, status: 'CONFIRMED', practiceName }` (**sin token**: viaja solo por email). **Honeypot no vacío → 200** `{ bookingId: null, serviceName: null, startsAt: null, endsAt: null, status: 'ACCEPTED', practiceName: null }` sin efectos | **400** `VALIDATION_FAILED` `errors[{field, code}]` con códigos de Bean Validation (`NotBlank`, `NotNull`, `Size`, `Email`, `AssertTrue`) · **400** `VALIDATION_FAILED` `errors[{field:'dni', code:'dni length must be between 6 and 9 digits'}]` (DNI sin 6–9 dígitos tras quitar no-dígitos; **el `code` es texto libre**, P4) · **400** `MALFORMED_REQUEST` (cuerpo ilegible) · **403** `CSRF_REQUIRED` · **404** `SERVICE_NOT_FOUND` · **409** `SLOT_TAKEN` (slot no ofrecido o tomado en carrera; traduce `APPOINTMENT_SLOT_TAKEN`/`APPOINTMENT_SLOT_BLOCKED`/`OUTSIDE_WORKING_HOURS`) · **422** `APPOINTMENT_IN_PAST`, `BOOKING_TOO_SOON`, `BOOKING_BEYOND_HORIZON`, `BOOKING_CONTACT_LIMIT` (≥ 3 turnos futuros activos por DNI/email/teléfono, provisional PA-05), `SERVICE_INACTIVE` · **429** `RATE_LIMITED` + `Retry-After` (30/min por IP, `RATE_LIMIT_PUBLIC_PER_MIN`, ventana de 1 min) · **500** `INTERNAL_ERROR` con `errorId` |

- **Orden de validaciones del servidor** (importa para la UX): Bean Validation (400) → honeypot (200) → rate limit (429) → pasado/anticipación/horizonte (422) → prestación (404/422) y slot ofrecido (409) → DNI (400 texto libre, al normalizar para buscar contactos) → límite por contacto (422) → alta (409 en carrera).
- **CSRF**: `GET /public/services` y `/public/availability` pasan por la cadena de seguridad, que emite la cookie `XSRF-TOKEN` (y la cabecera `X-CSRF-TOKEN`); el flujo siempre hace esos GET antes del POST, así que `cookieCsrfTransport` ya tiene el token. Si igual faltara → 403 `CSRF_REQUIRED` → mensaje "recargá la página" (sin reintento automático).
- **Instantes**: el cliente envía `startsAt` exactamente como lo recibió en el slot (string ISO UTC, sin reformatear), así coincide con la comparación `slot.startsAt().equals(startsAt)` de `PublicSlotLookupAdapter`.
- **Respuesta 201**: `bookingId` se decodifica pero **no se muestra ni se guarda** (no aporta al paciente y es un identificador interno). `practiceName` puede venir vacío (`""`): se omite la línea.
- Todo lo de esta tabla vive en `booking-gateway.ts` (decodificadores + clasificación en un tipo `BookingFailure`) y `booking-messages.ts`.

Clasificación en el gateway (las vistas solo ven estos tipos):

| Llamada | Resultado |
|---------|-----------|
| `listServices()` | `{ ok, services }` · `rate-limited{retryAfterSeconds?}` · `connection` (red, timeout, `invalid-response`, otro `problem`) |
| `availability(serviceId, date)` | `{ ok, slots }` · `no-services` (`NO_ACTIVE_SERVICES`) · `service-gone` (404 `SERVICE_NOT_FOUND`, 422 `SERVICE_INACTIVE`) · `rate-limited` · `connection` |
| `book(input)` | `confirmed{serviceName, startsAt, endsAt, practiceName}` · `accepted` (200 genérico) · `slot-unavailable` (409 `SLOT_TAKEN`; 422 `BOOKING_TOO_SOON`/`BOOKING_BEYOND_HORIZON`/`APPOINTMENT_IN_PAST`) · `contact-limit` · `service-gone` · `fields{errors}` (400 `VALIDATION_FAILED` con `errors[]` de campos conocidos) · `rate-limited` · `stale-page` (403 `CSRF_REQUIRED`) · `unknown-outcome` (red, timeout, 5xx, `invalid-response`, 400 sin campos conocidos) |

### D3 — Estado del flujo (`booking-flow.ts`)
- Estado puro: `{ step: 'service' | 'schedule' | 'details' | 'submitting', service?, date, slot?, form: BookingFormValues, notice?: 'slot-taken' }` con transiciones `chooseService`, `chooseDate`, `chooseSlot`, `back`, `submit`, `resolve(result)`. Las vistas renderizan el estado; los efectos (llamadas al gateway, navegación, foco) viven en `booking-view.ts`.
- **Un paso por entrada de historial (decisión de Mayra, 2026-11-23, resuelve OQ-3)**: la URL refleja el paso: `/reservar` (paso 1), `/reservar?paso=2`, `/reservar?paso=3`. Avanzar de paso usa `navigate(path)` del router (`history.pushState`), así el "atrás" del celular vuelve al paso anterior sin perder las elecciones en memoria, y "adelante" también funciona. La URL **no** lleva prestación, horario ni datos personales: solo el número de paso (`booking-step-url.ts`: `parseStep(query)` → 1|2|3, cualquier otro valor → 1).
- **Estado entre renders**: el router de C-05 vuelve a invocar `view(context)` en cada navegación o `popstate` (también dentro de `/reservar`). `createBookingView` guarda el estado del flujo en su *closure* (memoria, nunca storage) y lo conserva si el elemento raíz de la invocación anterior sigue conectado al documento (re-render dentro de `/reservar`); si ya no lo está (se pasó por otra ruta) o es la primera invocación, arranca vacío. Así los datos se descartan al abandonar `/reservar`.
- **Normalización**: el paso pedido por la URL se valida contra el estado en memoria (2 exige prestación elegida; 3 exige además horario). Si no alcanza —recarga o enlace directo a `?paso=2|3` sin estado— se hace `history.replaceState` a `/reservar` y se muestra el paso 1 (si hay estado parcial, al paso más alto válido). Nunca se restauran datos desde la URL.
- **Navegación interna**: "Continuar" = `navigate('/reservar?paso=N')` (push); "Volver" = `history.back()` (no apila entradas repetidas); el retorno por conflicto 409 (paso 3 → 2) usa `history.back()` hacia la entrada `?paso=2` ya existente (no duplica entradas, así "Volver" llega al paso 1 de a un paso) y `service-gone` (→ 1) usa `replace`, para no dejar entradas con estado inválido. La confirmación sigue en `/reservar/confirmado` con `replace` (D6).
- Al cambiar de prestación se descartan día y slot; al cambiar de día se descarta el slot. Los datos del formulario se conservan en memoria mientras la vista `/reservar` esté montada.

### D4 — Paso 2: días y horarios
- **Tira de días**: 14 días locales desde `today(clock)` (`addLocalDays`), botones `.day` con `aria-pressed` y `aria-label` largo ("martes 24 de noviembre"); "Más días" agrega 14 más (tope 98 días = 7 tandas; más allá del horizonte real la API responde `slots: []`). El primer día queda elegido al entrar. **No se marcan días "sin horarios" por adelantado** (el patrón de `components.html` los deshabilita): saberlo exige una consulta por día (14 GET por entrada, contra 120/min de lectura); queda como pedido P2.
- **Horarios**: `groupSlots(slots)` → `{ morning, afternoon }` según la hora local de inicio (< 13:00 = Mañana), cada uno en `.slot-group` con `h2` visual de grupo y `role="group"`; los botones muestran `formatTime(startsAt)`, `aria-pressed`, y en el resumen `.sticky-cta` "mar 24/11" + "09:30 – 10:00" con el `endsAt` del servidor (no calculado).
- **Cargando**: lista reemplazada por un esqueleto de 6 `.slot` inertes con `aria-hidden` + texto `role="status"` "Buscando horarios…"; las consultas viejas se descartan si llega una respuesta de un día que ya no está seleccionado (contador de pedido).
- **Sin horarios**: `.empty` "No hay horarios disponibles este día." + botón "Ver el día siguiente" (elige el día siguiente de la tira, ampliándola si hace falta).
- **Sugerencias tras conflicto** (`suggestNearest(slots, takenStartsAt, 3)`): los 3 slots con menor distancia absoluta al inicio rechazado, en orden cronológico; se muestran en un bloque "Horarios cercanos" encima de la grilla (además de la grilla completa), sin preselección. El slot rechazado no aparece si la API ya no lo ofrece; si lo ofreciera (carrera inversa), igual se excluye de las sugerencias.
- `createSchedulePicker({ gateway, serviceId, durationMin, clock, initialDate?, onChoose })` es exportable para que C-25 lo reutilice en la reprogramación.

### D5 — Paso 3: formulario, validación y honeypot
- Campos con `field()` de C-06: Nombre (`given-name`), Apellido (`family-name`), DNI (`type="text"`, `inputmode="numeric"`, `autocomplete="off"`, ayuda "Sin puntos ni espacios, o como figura en el documento."), Teléfono (`type="tel"`, `tel`, ayuda "Con código de área, por ejemplo 11 2345 6789."), Email (`type="email"`, `email`, ayuda "Te enviamos ahí la confirmación y el enlace para cancelar o reprogramar."). Casilla `.check` "Leí y acepto el [aviso de privacidad]" — enlace `(TBD · PA-10)` en pestaña nueva con `rel="noopener noreferrer"`.
- **Validación** (`validation.ts`, misma medida que el servidor: longitudes en unidades UTF-16 como `String.length()`): recorte de espacios; nombre/apellido 1–100; DNI: quitar todo lo que no sea dígito → 6–9 dígitos (idéntico a `Patient.normalizeAndValidateDni`); teléfono 1–50 caracteres y al menos 6 dígitos (el servidor solo exige `NotBlank` + 50: la regla de 6 dígitos es **solo del cliente**, para atrapar errores de tipeo); email: `^[^\s@]+@[^\s@]+\.[^\s@]+$` y ≤ 255 (más estricto que `@Email` de Hibernate, que acepta `a@b`; se acepta la divergencia porque un email sin dominio real nunca recibiría el enlace); aviso marcado. Se envía el DNI tal como lo escribió la persona (recortado): el servidor normaliza.
- **Honeypot**: `<div class="hp" aria-hidden="true">` con `<label for="website">Sitio web</label><input id="website" name="website" type="text" tabindex="-1" autocomplete="off">`; `.hp` lo saca de pantalla (`position:absolute; inline-size:1px; block-size:1px; overflow:hidden; clip-path: inset(50%)`, con tokens donde aplica) — **no** `display:none` (algunos bots lo detectan). El valor se envía tal cual.
- **Errores**: locales → mensaje por campo (`setFieldError`) y foco en el primero; del servidor `fields{errors}` → mapa `field → mensaje` (`firstName`, `lastName`, `dni`, `phone`, `email`, `privacyAccepted`; cualquier `code` de `dni` → "Ingresá un DNI de 6 a 9 números."). Avisos generales (`rate-limited`, `contact-limit`, `stale-page`, `unknown-outcome`) como `alertBox` arriba del formulario, `role="alert"`.
- **Enviando**: botón "Reservando…" con `.spinner`, `aria-busy="true"`, `aria-disabled="true"`; inputs `readonly` y casilla `aria-disabled` (patrón C-06); un `submit` en `submitting` se ignora.

### D6 — Confirmación y relación con C-25
- `confirmation-store.ts`: variable de módulo `{ serviceName, startsAt, endsAt, practiceName, email } | 'generic' | null`; `book()` exitoso la escribe y `navigate('/reservar/confirmado', { replace: true })`; la vista la **consume** (lee y borra) al montarse. Recargar, abrir directo o volver a entrar → confirmación genérica. Nunca `sessionStorage`/`localStorage` ni query string (spec `public-booking-ui` "Datos personales sin rastros").
- **Por qué `replace`**: atrás desde la confirmación no vuelve al formulario enviado (no hay reenvío posible); vuelve a la página anterior a `/reservar` o, si `/reservar` fue la primera, sale del sitio. Aceptado (la confirmación tiene "Reservar otro turno").
- **Contenido**: ícono de éxito, `h1` "¡Turno reservado!", `.meta-list` (Prestación, Día "martes 24 de noviembre", Horario "10:30 – 11:15", Consultorio si `practiceName` no está vacío), badge "Confirmado", y un aviso `alert-info`: "Te enviamos la confirmación a **{email enmascarado}**. Ahí está el enlace para cancelar o reprogramar el turno; guardalo." + "Si no llega en unos minutos, revisá la carpeta de spam." El email se muestra **enmascarado** (decisión de Mayra, 2026-11-23, resuelve OQ-1) por privacidad —la pantalla puede quedar a la vista de terceros— pero suficiente para detectar un error de tipeo en el dominio. `maskEmail(email)` (`mask-email.ts`, pura): parte local de ≥ 3 caracteres → primeros 2 + `•••` + `@dominio` completo (`maria@gmail.com` → `ma•••@gmail.com`; `ana@ejemplo.com` → `an•••@ejemplo.com`); parte local de 1 o 2 caracteres → primer carácter + `•••` (`a@x.com` → `a•••@x.com`, `jo@x.com` → `j•••@x.com`). El enmascarado se aplica al escribir en `confirmation-store`: el email completo no se conserva tras el envío. Si el email estuviera mal, hoy no hay forma de corregirlo desde la SPA.
- **Genérica** (recarga u honeypot 200 `ACCEPTED`): `h1` "Revisá tu email", texto "Si completaste una reserva, te enviamos la confirmación con el enlace para gestionarla." + enlace "Reservar un turno" → `/reservar`. Para un bot, indistinguible de un éxito.
- **Token**: la respuesta 201 no trae token (C-14 lo manda solo por email, `APP_BASE_URL/turno/{token}`). La confirmación nunca lo conoce, no enlaza a `/turno/...` y el título es fijo "Reserva confirmada". Datos de C-15 relevantes para C-25 (no usados aquí): `GET/DELETE /api/v1/public/bookings/{token}` → `BookingView{ bookingId, serviceName, practiceName, startsAt, endsAt, status, cancellable, reschedulable }`; `POST …/{token}/reschedule` `{ startsAt }` → 409 `SLOT_TAKEN` conserva el original; token inválido/expirado/revocado → 404 `INVALID_OR_EXPIRED_TOKEN` genérico; turno no modificable → 422 `BOOKING_MODIFICATION_NOT_ALLOWED`; mutaciones con CSRF; el token vence `BOOKING_TOKEN_TTL_DAYS` (1 día) después del fin del turno y no rota al reprogramar. C-25 reutiliza `createSchedulePicker` (D4) y `booking-messages.ts` para `SLOT_TAKEN`/`RATE_LIMITED`.

### D7 — Mensajes (`booking-messages.ts`; voseo, nunca `title`/`detail` del servidor)
| Resultado | Variante | Título · texto |
|-----------|----------|----------------|
| `slot-unavailable` | warning (paso 2) | "Ese horario se acaba de ocupar" · "Elegí otro. Te mostramos los más cercanos." |
| `rate-limited` | warning | "Demasiados intentos seguidos" · "Por seguridad, esperá un momento. Probá de nuevo {retry-after}." |
| `contact-limit` | warning | "Ya tenés turnos reservados" · "Con estos datos ya hay varios turnos próximos. Para cambiarlos usá el enlace del email de confirmación o comunicate con el consultorio." `(TBD · PA-05)` |
| `service-gone` | info (paso 1) | "Esa prestación ya no está disponible para reservar online" · "Elegí otra." |
| `no-services` | `.empty` | "Reservas online no disponibles" · "Por ahora no se pueden reservar turnos desde acá. Comunicate con el consultorio." `(TBD · datos del consultorio)` |
| `stale-page` | danger | "La página quedó desactualizada" · "Recargala e intentá de nuevo. Tus datos no se enviaron." |
| `unknown-outcome` | danger | "No pudimos confirmar tu reserva" · "Puede que se haya registrado igual. Revisá tu email antes de intentar de nuevo; si no te llegó nada, volvé a confirmar." |
| `connection` (GET) | danger | "No pudimos cargar los horarios" / "…las prestaciones" · "Revisá tu conexión." + botón "Reintentar" |
- `{retry-after}` reutiliza `shared/utils/retry-after.ts` (D1).
- El texto de `contact-limit` revela que esos datos ya tienen turnos (ver P3).

### D8 — Estilos (`src/styles/booking.css`)
- Porta de `components.html` `.stepper`, `.service-option`, `.day-strip`/`.day`, `.slot-group`/`.slots`/`.slot`, `.sticky-cta`, `.summary-line`, `.empty`, `.check`, `.meta-list` y `.badge-confirmado`; agrega `.booking` (contenedor de pasos, ancho `--layout-reading-width` del layout público), `.hp` (honeypot) y `.slot-skeleton`. Reemplaza literales de `components.html` (`4px` de la barra del stepper, `3.5rem`/`4rem` del día, `1.125rem` de la casilla, `3rem` del ícono vacío) por tokens o medidas locales documentadas en `:root` de `booking.css` (`--booking-day-width: 3.5rem`, `--booking-day-height: 4rem`, `--booking-stepper-bar: var(--space-1)`), mismas excepciones que C-05 D8/C-06 D7 (bordes `1px`/`2px`). Ninguna medida o color nuevo en tokens: Revision 1 sin cambios.
- Mobile (< 64rem): la acción principal de pasos 1 y 2 vive en `.sticky-cta` pegada al pie; paso 3 con botón a ancho completo al final del formulario (el teclado tapa una barra fija). Desktop (≥ 64rem): contenido en tarjeta (`.booking-card`, como `.auth-card`) de `--layout-reading-width`, grilla de slots de 4 columnas y `.sticky-cta` como pie estático de la tarjeta.
- `prefers-reduced-motion`: sin animación del spinner ni `transform` de `:active`.

### D9 — Rutas y arranque
- `routes.ts`: `reservar` → `createBookingView({ gateway: deps.booking, navigate, clock })`; `reserva-confirmada` → `createConfirmationView()`. Títulos fijos ("Reservar turno", "Reserva confirmada"). Sin guards (públicas). `RoutesDeps` suma `booking: BookingGateway` (los tests de rutas existentes reciben un gateway falso).
- `main.ts` crea `createBookingGateway({ client })` con el mismo `HttpClient`. Los 401 no ocurren en `/public/**`; si ocurrieran, `handleUnauthorized` ya no navega desde rutas públicas (C-06 D5).

### D10 — Estrategia de tests (Vitest + jsdom, `fetch` simulado con el contrato D2)
| Archivo | Cubre (spec) |
|---------|--------------|
| `shared/utils/dates.test.ts` (+casos) | local-time-display (días consecutivos, etiquetas, otra zona del navegador vía `TZ`) |
| `features/booking/validation.test.ts` | Validación local (vacíos, DNI con puntos/corto/largo, teléfono, email, aviso, longitudes UTF-16) |
| `features/booking/mask-email.test.ts` | Enmascarado del email (≥ 3, 2, 1 caracteres locales; dominio completo) |
| `features/booking/booking-step-url.test.ts` | `parseStep` (`paso=2`, `3`, ausente, `0`, `9`, `abc`) y `stepPath` |
| `features/booking/slots.test.ts` | Horarios agrupados (borde 13:00 local), sugerencias (empates, menos de 3, excluye el rechazado) |
| `features/booking/booking-flow.test.ts` | Pasos, volver conserva, cambio de prestación/día descarta, resolución de resultados |
| `features/booking/booking-gateway.test.ts` | Contrato D2 completo: rutas y query exactas, cuerpo exacto (`startsAt` sin reformatear, `website`, `privacyAccepted`), cabecera CSRF en POST, 201/200 `ACCEPTED`, cada código de error, `Retry-After`, decodificación inválida |
| `features/booking/booking-messages.test.ts` | Copy por resultado, nunca `title`/`detail` |
| `features/booking/booking-view.test.ts` | Flujo completo con `fetch` simulado; estados de carga/vacío/error; 409 con sugerencias y datos conservados; 429; sin prestaciones; doble envío; foco por paso; URL por paso (`pushState`), atrás/adelante conservan elecciones, recarga de `?paso=2|3` normaliza a `/reservar`, salir de `/reservar` y volver descarta el estado |
| `features/booking/details-step.test.ts` | Honeypot no perceptible (`aria-hidden`, `tabindex=-1`, `autocomplete=off`, clase `.hp`), teclados, errores por campo |
| `features/booking/confirmation-view.test.ts` | Confirmación con datos y email enmascarado (nunca completo), genérica, consumo único, `replace` |
| `app/routes.test.ts`, `main.test.ts` (+casos) | Rutas reales, título, flujo integrado; sin rastros (consola, URL, título, storages) |
- Datos ficticios únicamente (`Ana Pérez`, DNI `30456789`, `ana@ejemplo.com`, `11 2345 6789`); reloj fijo (`2026-11-23T13:00:00Z`).
- Apariencia y responsive: verify visual contra los mockups aprobados a 390x844 y 1440x900.

## Risks / Trade-offs

- [Pasos en la URL] Recargar `?paso=2|3` pierde la memoria y vuelve al paso 1 (por diseño: no hay datos en la URL ni storage); atrás/adelante conservan las elecciones mientras la pestaña no se recargue ni se salga de `/reservar`.
- [Timeout ambiguo] El POST pudo crear la reserva aunque la respuesta no llegó; reintentar daría `SLOT_TAKEN` sobre el propio turno o un segundo turno en otro horario → el mensaje `unknown-outcome` pide revisar el email antes de reintentar. No hay forma de consultarlo sin token (P5).
- [Email mal escrito] Sin verificación (RN-RS-02) la persona no recibe el enlace y no puede gestionar el turno → la confirmación muestra el email enmascarado (D6, decisión de Mayra), que permite detectar un dominio mal tipeado pero no un error dentro del nombre; la corrección queda en el consultorio.
- [Días sin horarios no marcados] La tira no anticipa qué días están completos; la persona puede tocar varios días vacíos → "Ver el día siguiente"; pedido P2.
- [Horizonte desconocido] El cliente no conoce `maxAdvanceDays` (default 30): la tira ofrece hasta 98 días y los posteriores al horizonte salen vacíos → pedido P1.
- [Rate limit compartido de lectura] 120 GET/min por IP compartidos entre catálogo y disponibilidad (y con cualquier otra persona detrás de la misma IP pública, p. ej. el wifi del consultorio) → aviso `rate-limited` sin reintento automático.
- [Divergencias de validación] Teléfono (≥ 6 dígitos) y email (con dominio) son más estrictos en el cliente que en el servidor; DNI idéntico. Documentado en D5.
- [Mensaje del límite por contacto] Confirma que un DNI/email/teléfono tiene turnos futuros (oráculo leve) → decisión de C-14/PA-05 (P3); el copy no menciona cuál de los tres datos coincidió.

### Pedidos a C-14

Ninguno bloquea a C-24: todos tienen salida en el frontend. No se cambia el backend en este change.

| # | Pedido | Tipo | Workaround en C-24 |
|---|--------|------|--------------------|
| P1 | Exponer públicamente el horizonte (`maxAdvanceDays`) o el último día reservable, p. ej. en `GET /public/services` o un `GET /public/booking-window` | No bloqueante | Tira de hasta 98 días; los posteriores al horizonte salen vacíos |
| P2 | Endpoint de días con disponibilidad para un rango (`GET /public/availability/days?serviceId=&from=&to=`) para deshabilitar días sin horarios como en `components.html` | No bloqueante | Días siempre habilitados + "Ver el día siguiente" |
| P3 | `422 BOOKING_CONTACT_LIMIT` confirma que esos datos tienen turnos futuros; evaluar respuesta genérica o confirmar que el riesgo se acepta (PA-05) | Decisión de producto | Copy que no dice qué dato coincidió |
| P4 | El DNI inválido llega como `errors[{field:'dni', code:'dni length must be between 6 and 9 digits'}]` (texto libre, y recién después de consumir rate limit y validar el slot); preferible `@Pattern` en el request con un `code` estable (`Pattern`) | No bloqueante | Cualquier `code` de `dni` → mismo mensaje; validación local idéntica evita llegar ahí |
| P5 | Sin forma de saber si un POST con timeout se registró (no hay idempotencia); evaluar `Idempotency-Key` | No bloqueante | Mensaje `unknown-outcome` |
| P6 | `practiceName` puede ser `""` y `serviceName` también (`orElse("")`) | Informativo | Se omiten líneas vacías |

## Migration Plan

Sin migración de datos ni cambios de backend. Rollback: revertir el commit del change; vuelven los *placeholder* de C-05. Con backend y datos de ejemplo (`npm run dev` con proxy a `/api`) el flujo se prueba de punta a punta; sin backend, `/reservar` muestra el error de conexión con "Reintentar".

## Open Questions

- ~~OQ-1~~ **Resuelta (Mayra, 2026-11-23)**: la confirmación muestra el email **enmascarado** (`ma•••@gmail.com`, D6). Qué hace el paciente si lo escribió mal sigue sin camino en la SPA (contactar al consultorio).
- OQ-2 (PA-10, legal): texto y enlace del aviso de privacidad; hoy `(TBD)` con versión `PRIVACY_NOTICE_V1` en el servidor.
- ~~OQ-3~~ **Resuelta (Mayra, 2026-11-23)**: el paso se refleja en la URL (`/reservar`, `?paso=2`, `?paso=3`) con `pushState`; el estado vive en memoria dentro de la vista (D3).
- OQ-4 (PA-05): texto definitivo de `BOOKING_CONTACT_LIMIT` y si se muestra el límite (3).
- OQ-5 (PA-21): nivel WCAG objetivo; hoy se aplica la línea base del design system (contraste AA de tokens, foco visible, objetivos de 44 px, errores con ícono + texto).
- OQ-6: los datos de contacto del consultorio para "Reservas no disponibles" y el pie siguen `(TBD)` (design system TBD 8).

## Desviaciones de implementación (registradas en el verify, 2026-11-23)

- **D7 (copy)**: los textos de las alertas siguen los mockups aprobados (título con punto final; `slot-unavailable`: "Elegí otro. Te mostramos los más cercanos; tus datos quedan guardados."); la tabla D7 es orientativa y manda `booking-messages.ts`.
- **D5**: la ayuda del DNI es la del mockup ("Como figura en el documento; podés usar puntos.").
- **D2**: el gateway devuelve uniones por `kind` (`{ kind: 'ok', services }`, `{ kind: 'ok', slots }`) en lugar de `{ ok, ... }`; mismos casos.
- **D3**: las transiciones del flujo son `selectService`, `selectDate`, `selectSlot`, `setForm`, `next`, `back`, `goTo`, `submit`, `resolve`, `urlStepOf`, `flowStepOf`; `RoutesDeps` suma `replaceUrl` y `goBack` (`history.back()` para "Volver").
- **D1**: archivos extra `booking-types.ts`, `booking-ui.ts`, `test-harness.ts` (solo tests, lo escanea `architecture.test.ts` como producción sin hallazgos), `booking-view-details.test.ts` y `booking-integration.test.ts` (integración de 8.2-8.4 en vez de `main.test.ts`).
- **D8**: medidas locales `--layout-booking-day-width/height` (candidatas a token en la Revisión 2); borde del badge y `.check` con `--color-primary-muted`; literales permitidos: bordes 1px/2px, `.hp` de 1px y `64rem`.
- `core/dom/form.ts` suma el ícono `warning`.
- **TDD**: las tareas 8.2-8.4 no se observaron en RED; se hizo una comprobación por mutación. Desviación aceptada.
- **D3 / verify W1 (resuelto)**: el retorno por 409 ahora hace `goBack()` (la entrada previa del paso 3 siempre es `?paso=2`) en vez de `replace`, evitando el historial `[1, 2, 2]`.
- **Pendiente: prueba manual contra backend real (tarea 9.3), a realizar por un integrante con el entorno completo.**

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-23)
- Mockups: openspec/changes/c-24-public-booking-ui/mockups/
- Mockup revision: 1
- Approval: approved (2026-11-23, by Lucas)
- Preview: rendered (2026-11-23, revisión 1, `mockups/previews/<pantalla>-390.png` y `-1440.png`, 14 pantallas / 28 capturas; Chrome headless por CDP con `Emulation.setDeviceMetricsOverride` 390x844 (mobile) y 1440x900, página completa, servidor local `python -m http.server` en 127.0.0.1:8765; sin scroll horizontal de página a 390 px; inspeccionadas visualmente: se corrigió el corte del email en la confirmación y el día vacío fuera de vista en `step2-empty`)
- Revisión menor 2026-11-23: email enmascarado en confirmación por decisión de Mayra; informado a Lucas. (Solo cambia el texto del email en `confirmation.html` y sus capturas `confirmation-390.png`/`-1440.png`.)
- Visual verify: passed (2026-11-23; solo diferencias minor aceptadas, confirmadas por el verify independiente: espaciados de pocos px, `h1` y círculo de éxito de la confirmación más chicos, ícono de información en lugar de sobre; verificación contra backend real pendiente, tarea 9.3; apply: Vite dev + Chrome headless por CDP con `Fetch.enable` simulando `/api/v1/public/*` (servicios, disponibilidad, 201, 409, 429, 422 `BOOKING_CONTACT_LIMIT`, 500, 200 `ACCEPTED`), 14 pantallas a 390x844 y 1440x900 en `mockups/verify/`, comparadas con `mockups/previews/`; sin scroll horizontal a 320 px; sin excepciones en consola. Diferencias menores: casilla del aviso y espaciados verticales unos pocos px distintos, "Ver el día siguiente" sin el chevron del mockup; no hay diferencias mayores ni bloqueantes. Verificación contra backend real pendiente: tarea 9.3.)
- Criterio de aceptación: los HTML de `mockups/` y sus capturas (ver `tasks.md` grupos 6 a 9).
