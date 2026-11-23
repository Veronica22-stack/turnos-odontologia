# Tasks: C-24 `public-booking-ui`

> Change **[UI]**: NO empezar el apply hasta que los mockups de `mockups/` estén aprobados (`Approval: approved` en `design.md` §UI Mockups y en `mockups/README.md`). Los mockups aprobados (`mockups/*.html` y sus capturas en `mockups/previews/`) son criterio de aceptación de los grupos 6 a 9.
> Governance **MEDIO**: implementar con checkpoints; reportar al usuario las decisiones no obvias y cualquier desvío de `design.md` (en especial del contrato D2 y de las preguntas abiertas OQ-1..OQ-6).
> **C-14 está implementado y su contrato está confirmado contra el código (design D2)**: los tests usan un `fetch` simulado con ese contrato real (`firstName`/`lastName`, `website`, `privacyAccepted`, 201 sin token, 200 `ACCEPTED` del honeypot, `SLOT_TAKEN`, `BOOKING_*`, `RATE_LIMITED` + `Retry-After`, CSRF en el POST). No tocar `backend/`.
> Strict TDD por tarea: RED (test que falla, ejecutado) → GREEN (mínimo código) → TRIANGULATE (segundo caso distinto) → REFACTOR (tests verdes tras cada paso). No escribir código de producción antes de ver fallar su test.
> Comandos (desde `frontend/`): `npx vitest run <ruta>` (puntual), `npm test`, `npm run typecheck`, `npm run build`.
> Reglas duras: TS estricto sin `any` (9), sin `innerHTML` (10), solo tokens (11), **ninguna dependencia nueva** (12); nunca escribir nombre, DNI, teléfono ni email en consola/URL/título/storage (6); instantes UTC mostrados en Buenos Aires (8). En tests usar solo datos ficticios (`Ana Pérez`, DNI `30456789`, `ana@ejemplo.com`, `11 2345 6789`) y reloj fijo.

## 0. Red de seguridad

- [x] 0.1 Ejecutar `npm test` y `npm run typecheck` desde `frontend/` y anotar la línea base (suite de C-05/C-06/C-19 verde); si algo falla, detenerse y reportarlo como falla preexistente

## 1. Fechas locales (spec `local-time-display`)

- [x] 1.1 RED: `src/shared/utils/dates.test.ts` — `addLocalDays('2026-11-30', 2)` → `'2026-12-02'`; `addLocalDays('2026-12-31', 1)` → `'2027-01-01'`; `dayLabels('2026-11-24')` → `{ short: 'mar', day: '24', medium: 'mar 24/11', long: 'martes 24 de noviembre' }` (sin coma); `dayLabels('2026-02-30')` lanza "Fecha invalida"; resultado igual con `process.env.TZ = 'Asia/Tokyo'`; verificar que falla
- [x] 1.2 GREEN + REFACTOR: `addLocalDays` y `dayLabels` en `src/shared/utils/dates.ts` (aritmética sobre `Date.UTC`, `Intl` con `timeZone: 'UTC'` sobre la fecha calendario); suite de `dates.test.ts` de C-05 verde
- [x] 1.3 REFACTOR: mover `src/features/auth/retry-after.ts` (+ test) a `src/shared/utils/retry-after.ts` y actualizar imports de auth; `npx vitest run src/features/auth src/shared` verde sin cambios de comportamiento

## 2. Funciones puras de reserva (spec `public-booking-ui`)

- [x] 2.1 RED: `src/features/booking/validation.test.ts` — vacío → errores en `firstName`, `lastName`, `dni`, `phone`, `email`, `privacyAccepted` en ese orden; `'  Ana '` válido y recortado; 101 caracteres → "Usá como máximo 100 caracteres."; DNI `30.456.789` válido, `12345` y `1234567890` → "Ingresá un DNI de 6 a 9 números.", `abc` → mismo mensaje; teléfono `'12-34'` → "Ingresá un teléfono con código de área.", 51 caracteres → error; email `ana@` y `ana@ejemplo` → "Ingresá un email válido.", 256 caracteres → error; aviso sin marcar → "Tenés que aceptar el aviso de privacidad para reservar."; longitudes en unidades UTF-16
- [x] 2.2 GREEN + REFACTOR: `validateBookingForm()` y `normalizeDni()` en `validation.ts` (design D5); verificar que pasa
- [x] 2.3 RED→GREEN: `src/features/booking/slots.test.ts` + `slots.ts` — `groupSlots` con `12:00Z`, `12:30Z`, `15:45Z`, `16:00Z`, `18:00Z` del 2026-11-24 → Mañana `09:00, 09:30, 12:45`, Tarde `13:00, 15:00` (borde 13:00 local); vacío → ambos vacíos; `suggestNearest(slots, '…T13:30:00Z', 3)` con 10:00, 11:00, 11:30, 15:00 locales → 10:00, 11:00, 11:30 en orden cronológico; con 2 slots → 2; excluye el instante rechazado si sigue en la lista; empate → el más temprano
- [x] 2.4 RED→GREEN: `src/features/booking/booking-flow.test.ts` + `booking-flow.ts` — estado inicial `service`; `chooseService` → `schedule` con `date = today(clock)`; `chooseSlot` + continuar → `details`; `back` desde `details` conserva servicio, día, slot y datos; cambiar de prestación descarta día y slot; cambiar de día descarta slot; `resolve(slot-unavailable)` → `schedule` con `notice: 'slot-taken'`, slot vacío, mismo día, datos conservados; `submit` en `submitting` no cambia el estado
- [x] 2.4b RED→GREEN: `src/features/booking/mask-email.test.ts` + `mask-email.ts` — `maskEmail('maria@gmail.com')` → `ma•••@gmail.com`; `ana@ejemplo.com` → `an•••@ejemplo.com`; parte local de 2 (`jo@x.com`) → `j•••@x.com`; de 1 (`a@x.com`) → `a•••@x.com`; el dominio se conserva completo; el resultado nunca contiene la parte local completa cuando tiene ≥ 3 caracteres
- [x] 2.4c RED→GREEN: `src/features/booking/booking-step-url.test.ts` + `booking-step-url.ts` — `parseStep(new URLSearchParams('paso=2'))` → 2, `paso=3` → 3, sin parámetro → 1, `paso=1` → 1, `paso=0`, `paso=9`, `paso=abc`, `paso=2x` → 1; `stepPath(1)` → `/reservar`, `stepPath(2)` → `/reservar?paso=2`, `stepPath(3)` → `/reservar?paso=3`
- [x] 2.5 RED→GREEN: `src/features/booking/booking-messages.test.ts` + `booking-messages.ts` — texto por resultado de design D7 (`slot-unavailable`, `rate-limited` con 40 s → "en 1 minuto" y sin segundos → "en unos minutos", `contact-limit`, `service-gone`, `no-services`, `stale-page`, `unknown-outcome`, `connection`); mapa de campo/código del servidor (`email`+`Email`, `firstName`+`NotBlank`, `privacyAccepted`+`AssertTrue`, `dni` + texto libre → mensaje de DNI); un `problem` con `title`/`detail` nunca se refleja

## 3. Gateway de reserva (contrato confirmado con C-14, design D2)

- [x] 3.1 RED: `src/features/booking/booking-gateway.test.ts` (fetch falso) — `listServices()` → `GET /api/v1/public/services`; 200 con `[{id,name,description:null,durationMin:30}]` → `{ ok, services }`; elemento sin `id` o `durationMin` no entero → `connection`; 429 con `Retry-After: 30` → `rate-limited{30}`; red → `connection`
- [x] 3.2 GREEN: `listServices` en `booking-gateway.ts`; verificar que pasa
- [x] 3.3 RED→GREEN: `availability(serviceId, '2026-11-24')` → `GET /api/v1/public/availability?serviceId=<uuid>&date=2026-11-24` (query codificada); 200 `OK` → `{ ok, slots }` con `startsAt`/`endsAt` validados con `parseInstant` (instante sin zona → `connection`); `NO_ACTIVE_SERVICES` → `no-services`; 404 `SERVICE_NOT_FOUND` y 422 `SERVICE_INACTIVE` → `service-gone`; 429 → `rate-limited`; 400 `VALIDATION_FAILED` → `connection`; timeout → `connection`
- [x] 3.4 RED→GREEN: `book(input)` → `POST /api/v1/public/bookings` con cabecera `X-XSRF-TOKEN` (cookie presente en el test) y cuerpo JSON exacto `{ serviceId, startsAt, firstName, lastName, dni, phone, email, website, privacyAccepted: true }` con `startsAt` idéntico al del slot y campos recortados; 201 → `confirmed{serviceName, startsAt, endsAt, practiceName}` (sin `bookingId` en el resultado); 200 `{status:'ACCEPTED', …null}` → `accepted`; 409 `SLOT_TAKEN` y 422 `BOOKING_TOO_SOON`/`BOOKING_BEYOND_HORIZON`/`APPOINTMENT_IN_PAST` → `slot-unavailable`; 422 `BOOKING_CONTACT_LIMIT` → `contact-limit`; 404 `SERVICE_NOT_FOUND`/422 `SERVICE_INACTIVE` → `service-gone`; 400 `VALIDATION_FAILED` con `errors[{field:'email',code:'Email'}]` → `fields`; 400 sin campos conocidos y `MALFORMED_REQUEST` → `unknown-outcome`; 403 `CSRF_REQUIRED` → `stale-page`; 429 → `rate-limited{retryAfterSeconds}`; 500, red y timeout → `unknown-outcome`; 201 sin `startsAt` → `unknown-outcome`

## 4. Paso 1 y estado "reservas no disponibles" (acepta contra `mockups/step1-service.html`, `unavailable.html`)

- [x] 4.1 RED: `src/features/booking/booking-view.test.ts` (jsdom, gateway y `navigate` falsos, reloj fijo) — al montar: estado de carga `role="status"`; con 2 prestaciones → "Paso 1 de 3", `h1` "¿Qué turno necesitás?", `fieldset` con `legend` y 2 radios con nombre, "30 min" y descripción; continuar sin elegir → "Elegí una prestación." y sin llamada a disponibilidad
- [x] 4.2 GREEN: `booking-view.ts` + `service-step.ts` con `h()`; verificar que pasa
- [x] 4.3 RED→GREEN: catálogo `[]` → `.empty` "Reservas online no disponibles" sin stepper; disponibilidad `NO_ACTIVE_SERVICES` → mismo estado; catálogo con 429 → aviso de espera; red → aviso de conexión con "Reintentar" que repite la carga

## 5. Paso 2: día y horario (acepta contra `mockups/step2-*.html`)

- [x] 5.1 RED: al elegir prestación → "Paso 2 de 3", `h1` "Elegí día y horario" enfocado, resumen "Limpieza · 45 min", tira de 14 días desde el día local del reloj (`2026-11-24T01:30:00Z` → primer día lunes 23), primer día `aria-pressed="true"` con `aria-label` "lunes 23 de noviembre", consulta `date=2026-11-23`; mientras tanto, "Buscando horarios…" `role="status"`
- [x] 5.2 GREEN: `schedule-step.ts` (`createSchedulePicker` exportado, design D4); verificar que pasa
- [x] 5.3 RED→GREEN: slots agrupados Mañana/Tarde con hora local; elegir 09:30 → `aria-pressed`, resumen "mar 24/11 · 09:30 – 10:00" (con `endsAt` del servidor), "Continuar" habilitado; continuar sin horario no avanza; día vacío → "No hay horarios disponibles este día." y "Ver el día siguiente" consulta el día +1 y lo marca; "Más días" agrega 14 días; red → aviso con "Reintentar" que repite la misma consulta; respuesta tardía de un día ya no seleccionado se descarta; 429 → aviso de espera en lugar de la grilla; `service-gone` → vuelve al paso 1 con catálogo recargado y aviso
- [x] 5.4 RED→GREEN: "Volver" → paso 1 con la prestación marcada; foco en el `h1`
- [x] 5.5 RED→GREEN: pasos en la URL (design D3; spec `spa-routing`) — continuar del paso 1 deja `location.search` = `?paso=2` con una entrada de historial nueva (`pushState`), del paso 2 `?paso=3`; `history.back()` (popstate) → paso 2 con prestación, día y horario aún marcados, y otra vez → paso 1 con la prestación marcada; `history.forward()` → paso 2 y 3 con datos del formulario conservados; la URL nunca contiene prestación, horario ni datos personales; cargar `?paso=3` o `?paso=2` sin estado → `replaceState` a `/reservar` (sin entrada nueva) y paso 1; `?paso=9`/`abc` → paso 1 y URL `/reservar`; estado parcial (prestación sin horario) con `?paso=3` → paso 2; navegar a otra ruta (otro elemento reemplaza el contenido) y volver a `/reservar` → paso 1 vacío (el estado se descarta)

## 6. Paso 3: datos, honeypot y envío (acepta contra `mockups/step3-*.html`, `slot-taken.html`, `rate-limited.html`, `contact-limit.html`)

- [x] 6.1 RED: `src/features/booking/details-step.test.ts` — "Paso 3 de 3", `h1` "Tus datos", resumen (prestación, día largo, horario), campos con `label[for]`, `autocomplete` (`given-name`, `family-name`, `tel`, `email`; DNI `off`), DNI `inputmode="numeric"`, teléfono `type="tel"`, email `type="email"`, casilla de aviso con enlace `(TBD)`; honeypot: `#website` dentro de `.hp[aria-hidden="true"]`, `tabindex="-1"`, `autocomplete="off"`, no es el primer ni ningún elemento alcanzable recorriendo los enfocables del formulario
- [x] 6.2 GREEN: `details-step.ts` con `field()`/`setFieldError()` de C-06; verificar que pasa
- [x] 6.3 RED→GREEN: envío vacío → sin llamada, todos los errores, foco en "Nombre"; DNI corto → solo ese error; envío válido → una llamada con el cuerpo de 3.4 y `website: ''`; respuesta pendiente → "Reservando…", `aria-busy="true"`, inputs `readonly`, segundo submit ignorado (1 llamada)
- [x] 6.4 RED→GREEN: respuestas — `fields` → mensaje en el campo y foco; `rate-limited` → alerta "Demasiados intentos seguidos… en 1 minuto." con datos conservados y botón habilitado; `contact-limit` → alerta; `stale-page` → alerta de recarga; `unknown-outcome` (timeout) → "No pudimos confirmar tu reserva…" con datos conservados; `service-gone` → paso 1 con aviso
- [x] 6.5 RED→GREEN: `slot-unavailable` (409) → paso 2 (`replace`: la URL pasa de `?paso=3` a `?paso=2` sin apilar) del mismo día, aviso "Ese horario se acaba de ocupar", disponibilidad recargada, bloque "Horarios cercanos" con hasta 3 sugerencias (design D4), ningún horario marcado; elegir sugerencia + continuar → paso 3 con los datos intactos; 409 con día vacío → aviso + estado vacío con "Ver el día siguiente"
- [x] 6.6 Estilos: `src/styles/booking.css` (design D8) importado desde `main.ts`; `styles.test.ts` verde sin nuevas excepciones de color/espaciado; sin scroll horizontal a 320 px (verificado en el verify visual)

## 7. Confirmación (acepta contra `mockups/confirmation*.html`)

- [x] 7.1 RED: `src/features/booking/confirmation-view.test.ts` — con datos en `confirmation-store` → `h1` "¡Turno reservado!", "Limpieza", "martes 24 de noviembre", "10:30 – 11:15", consultorio si no está vacío, el email **enmascarado** (`ma•••@gmail.com` para `maria@gmail.com`; `j•••@ejemplo.com` para `jo@ejemplo.com`) en el aviso de enlace por email y el email completo ausente de todo el texto de la página, enlace "Reservar otro turno"; la segunda vista (sin datos) → genérica "Revisá tu email" sin datos de turno; `practiceName: ''` omite la línea
- [x] 7.2 GREEN: `confirmation-store.ts` (consumo único; recibe el email ya enmascarado vía `maskEmail`, nunca el completo) y `confirmation-view.ts`; verificar que pasa
- [x] 7.3 RED→GREEN: en `booking-view.test.ts`, `confirmed` → `navigate('/reservar/confirmado', { replace: true })`, datos del formulario descartados y el store sin el email completo; `accepted` (200 honeypot) → misma navegación con confirmación genérica

## 8. Rutas e integración (`routes.test.ts`, `main.test.ts`; specs `public-booking-ui`, `spa-routing`)

- [x] 8.1 RED→GREEN: `src/app/routes.test.ts` — `reservar` y `reserva-confirmada` usan las vistas reales (no `placeholderView`), layout `public`, sin guard, títulos "Reservar turno" y "Reserva confirmada"; `RoutesDeps.booking` cableado en `main.ts`
- [x] 8.2 RED→GREEN: `main.test.ts` con `bootstrap` y `fetch` simulado (`/auth/me` 401, servicios, disponibilidad, POST 201): `/reservar` → paso 1 → 2 → 3 → `/reservar/confirmado` con la confirmación; `history.back()` no reenvía el POST (1 sola llamada); atrás/adelante entre `?paso=2` y `?paso=3` recorren los pasos con el estado conservado
- [x] 8.3 RED→GREEN: sin rastros — espiar `console.*`; tras el flujo completo y tras un 409 y un 400, ni la consola, ni `location.href` (en ningún paso: solo `?paso=N`), ni `document.title`, ni `localStorage`/`sessionStorage` contienen `Ana`, `Pérez`, `30456789`, `ana@ejemplo.com`, `2345 6789` ni el `bookingId`
- [x] 8.4 RED→GREEN: abrir `/reservar/confirmado` directo → confirmación genérica; navegar fuera de `/reservar` y volver → flujo vacío

## 9. Verificación integral

- [x] 9.1 Ejecutar `npm test`, `npm run typecheck` y `npm run build` desde `frontend/`; todo verde y `git diff frontend/package.json` vacío
- [x] 9.2 Revisión de reglas duras sobre el diff: `grep -rnE "innerHTML|outerHTML|insertAdjacentHTML|: any|as any" frontend/src --include=*.ts` sin resultados en producción; sin `console.*` en `features/booking`; sin colores ni medidas literales en `src/styles/booking.css` salvo las documentadas en design D8
- [ ] 9.3 Verificación manual con `npm run dev` (backend de C-14 corriendo con al menos una prestación pública y horarios): reservar de punta a punta; forzar 409 reservando dos veces el mismo slot en dos pestañas; backend apagado → aviso de conexión con "Reintentar" (registrar el resultado en el resumen del apply)

## Workflow follow-up

- Verify: comparar `/reservar` (pasos 1-3 y su URL `?paso=`, cargando, día vacío, enviando, errores de validación, 409 con sugerencias, 429, reservas no disponibles) y `/reservar/confirmado` (con datos y genérica) con los mockups aprobados a 390x844 y 1440x900 (capturas en `mockups/verify/`; estados de error forzados interceptando `/api/v1/public/*`), con consola y red inspeccionadas; registrar `Visual verify` en `design.md`.
- Archive: sincronizar `public-booking-ui`, `spa-routing` y `local-time-display`; marcar C-24 en `CHANGES.md`.
