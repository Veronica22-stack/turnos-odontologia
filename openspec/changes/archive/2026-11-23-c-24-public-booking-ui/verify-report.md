# Verify report: C-24 `public-booking-ui`

Fecha: 2026-11-23 | Rama: dev-mayra (commit 154d511, árbol limpio) | Revisor: sub-agente verificador independiente (solo lectura sobre código; solo se creó este informe y, al final, se ajustaron artefactos de planificación)

## Veredicto: PASS WITH WARNINGS

No hay issues CRITICAL: el contrato HTTP del frontend coincide con el backend real de C-07/C-12/C-14 (rutas, cuerpo exacto, códigos, `Retry-After`, CSRF en el POST, honeypot). `openspec validate --strict`, `npm test` (dos corridas, sin tests inestables), `npm run typecheck` y `npm run build` terminan en verde. tasks.md está 38/39: la única tarea abierta es la 9.3 (prueba manual contra el backend real), que **queda como pendiente manual por decisión explícita** (el equipo de la desarrolladora no puede levantar el stack completo), no como falla. Los warnings son una posible mala experiencia del botón "Volver" tras un 409 (código), la divergencia entre `design.md` y lo construido, y el estado `Visual verify`.

Conteo: CRITICAL 0 | WARNING 4 | SUGGESTION 6.

## Resultados de ejecución (corridos por el verificador)

| Suite | Comando | Resultado |
|-------|---------|-----------|
| OpenSpec | `openspec validate c-24-public-booking-ui --strict` | `Change 'c-24-public-booking-ui' is valid` |
| Estado | `openspec status --change c-24-public-booking-ui` | schema `spec-driven`; 4/4 artefactos completos (proposal, specs, design, tasks); specs `public-booking-ui`, `spa-routing`, `local-time-display` |
| Tests (corrida 1) | `npm test` (desde `frontend/`, ~124 s con carga) | 49 archivos, **689 tests, 0 fallos** |
| Tests (corrida 2) | `npm test` (~69 s) | 49 archivos, **689 tests, 0 fallos**; **sin tests inestables** (flaky) detectados |
| Typecheck | `npm run typecheck` | sin errores |
| Build | `npm run build` | OK, 60 módulos; `index.js` 92,24 kB (gzip 29,02 kB), `css` 32,68 kB (gzip 6,22 kB), logo con hash |
| Dependencias | `git diff dbe1567 -- frontend/package.json frontend/package-lock.json` | **sin cambios**: cero dependencias nuevas (regla 12) |
| Backend | `git diff dbe1567 --stat -- .` (desde `frontend/`) | solo 39 archivos de `frontend/`; el change no toca `backend/`, `knowledge-base/` ni `design-system/` |
| Cobertura | no ejecutada | sin `@vitest/coverage-*` instalado (igual que C-05/C-06); se juzga por escenarios |

## 1. Completitud

- tasks.md: **38 `[x]`, 1 `[ ]`** (9.3). Coincide con lo esperado.
- **9.3 Pendiente manual (no es una falla)**: la PC de la desarrolladora no puede correr el stack completo (backend + PostgreSQL). Guion exacto para quien tenga el entorno completo (backend de C-14 con al menos una prestación pública, sus días y horarios, y `npm run dev` con el proxy a `/api`):
  1. **Reserva de punta a punta**: abrir `/reservar`, elegir una prestación, un día y un horario, completar nombre, apellido, DNI (p. ej. `30456789`), teléfono y email propios, aceptar el aviso y confirmar. Esperado: URL `/reservar/confirmado`, "¡Turno reservado!" con prestación, día y horario correctos en hora de Buenos Aires y el email **enmascarado**; llega el email con el enlace de gestión; el turno aparece en la agenda del profesional. Atrás en el navegador no reenvía la reserva (verificar que no se crea un segundo turno).
  2. **Forzar 409 con dos pestañas**: abrir `/reservar` en dos pestañas, elegir en ambas la misma prestación y el mismo horario; en la pestaña A completar y confirmar; en la pestaña B completar y confirmar después. Esperado en B: vuelve al paso 2 del mismo día con "Ese horario se acaba de ocupar.", recarga los horarios, muestra "Horarios cercanos" sin el horario tomado, conserva los datos; elegir un cercano y continuar lleva al paso 3 con los datos intactos. Probar además el botón "Volver" tras el 409 (ver W1).
  3. **Backend apagado**: con el backend detenido, abrir `/reservar` -> "No pudimos cargar las prestaciones." con "Reintentar"; levantar el backend y tocar "Reintentar" carga el catálogo. Variante: apagar el backend con el paso 2 abierto y cambiar de día -> "No pudimos cargar los horarios." con "Reintentar"; apagarlo justo al confirmar -> "No pudimos confirmar tu reserva." con los datos conservados (revisar el email antes de reintentar).
  4. Opcional: 429 repitiendo consultas de disponibilidad (límite de lectura 120/min por IP) y comprobar el aviso con la espera derivada de `Retry-After`; con una consola del navegador abierta, verificar que no aparecen datos personales.
  Registrar el resultado en el resumen del apply o en este informe.
- Cada grupo de tareas tiene código y test: fechas (`addLocalDays`, `dayLabels`), `retry-after` movido a `shared/utils`, `validation`, `slots`, `booking-flow`, `mask-email`, `booking-step-url`, `booking-messages`, `booking-gateway`, vistas (`booking-view`, `service-step`, `schedule-step`, `details-step`, `confirmation-view`), rutas e integración (`booking-integration.test.ts`) y `styles.test.ts`.

## 2. Matriz de cobertura de escenarios

Leyenda: OK = test que asserta el comportamiento. PARCIAL = indirecto. FALTA = sin test. (VISUAL = solo verificable en captura.)

### public-booking-ui
| Escenario | Test | Estado |
|-----------|------|--------|
| Inicio del flujo | `booking-view.test.ts` "muestra el estado de carga y luego las prestaciones..." | OK |
| Volver conserva la elección | `booking-view-details.test.ts` "Volver conserva el horario y los datos" y `booking-view.test.ts` "atras y adelante recorren los pasos..." | OK |
| Elegir prestación / Continuar sin elegir | `booking-view.test.ts` "elegir una prestacion muestra el resumen" y "continuar sin elegir muestra el error en el grupo y no consulta la disponibilidad" | OK |
| Catálogo vacío / Prestaciones dadas de baja | `booking-view.test.ts` "un catalogo vacio..." y "NO_ACTIVE_SERVICES muestra..." (sin `.stepper`) | OK |
| Día local distinto del día UTC / Cambio de día | `booking-view.test.ts` "muestra el paso 2 con foco en el h1, resumen y 14 dias..." (reloj `01:30Z` -> lunes 23, `date=2026-11-23`) y chequeos de `aria-pressed` al cambiar de día | OK |
| Horarios agrupados / Elegir horario | `slots.test.ts` (borde 13:00 local) + `booking-view.test.ts` "agrupa..." y "elegir un horario lo marca, ... resumen con el fin del servidor" | OK |
| Día sin horarios / Falla de conexión | `booking-view.test.ts` "un dia sin horarios ofrece 'Ver el dia siguiente'..." y "un error de conexion... Reintentar repite la misma consulta" | OK |
| Formulario inicial / Volver y regresar conserva datos | `details-step.test.ts` (estructura, precarga) + `booking-view-details.test.ts` "atras y adelante... conservan horario y datos" | OK |
| Envío vacío / DNI con puntos / DNI corto | `validation.test.ts` + `details-step.test.ts` "con el formulario vacio no envia, marca todos los campos y enfoca Nombre" y "un DNI corto marca solo ese campo" | OK |
| Campo no perceptible / Envío normal | `details-step.test.ts` "website esta dentro de .hp oculto a lectores, fuera del tabulado y sin autocompletar" y "ningun elemento alcanzable con tab es el campo trampa"; cuerpo con `website: ''` en `booking-view-details.test.ts` | OK |
| Doble toque / Instante enviado | `booking-view-details.test.ts` "un envio valido hace una unica llamada con el cuerpo exacto y website vacio" (dos `submit`, 1 llamada, `aria-busy`, `readOnly`) + `booking-gateway.test.ts` (cuerpo JSON exacto, `startsAt` sin reformatear) | OK |
| Conflicto con alternativas / sin horarios ese día | `booking-view-details.test.ts` describe "409: horario tomado..." (3 tests: sugerencias, elegir sugerencia conserva datos, día vacío) | OK |
| Reserva limitada / Disponibilidad limitada | `booking-view-details.test.ts` "rate-limited muestra la espera, conserva datos y rehabilita el boton" (40 s -> "en 1 minuto") y `booking-view.test.ts` "un 429 muestra el aviso de espera en lugar de los horarios" (sin `Retry-After` -> "en unos minutos") | OK |
| Error de validación del servidor | `booking-view-details.test.ts` "fields marca el campo..." (`email`/`Email`, foco, datos conservados) + gateway | OK |
| Límite por contacto / Respuesta perdida | `booking-view-details.test.ts` `it.each` (`contact-limit`, `stale-page`, `unknown-outcome`) + `booking-gateway.test.ts` (422 `BOOKING_CONTACT_LIMIT`, timeout -> `unknown-outcome`) | OK |
| Reserva confirmada / Email enmascarado / local corta | `booking-view-details.test.ts` "201 navega a /reservar/confirmado... email enmascarado", `confirmation-view.test.ts`, `mask-email.test.ts` (`ma•••`, `j•••`) | OK |
| Atrás desde la confirmación | `booking-view-details.test.ts` "atras desde la confirmacion no reenvia..." (1 llamada a `book`, flujo vacío) | OK |
| Recarga de la confirmación / Respuesta genérica | `booking-view-details.test.ts` "abrir la confirmacion directo..." y "200 ACCEPTED del honeypot..."; `confirmation-view.test.ts` | OK |
| Sin rastros tras reservar | `booking-integration.test.ts` "sin rastros: consola, URL, titulo y storages..." (espías de las cinco funciones de consola, `location.href`, `document.title`, ambos storages, con 409, 400 y 201) | OK |
| Teclados del celular | `details-step.test.ts` "cada campo tiene etiqueta, autocomplete y teclado adecuados" | OK |
| Uso desde 320 px / acción al pie / táctil | sin test unitario (CSS) | VISUAL (ver sección 6; 320 px verificado en el apply) |

### spa-routing
| Escenario | Test | Estado |
|-----------|------|--------|
| Atrás vuelve al paso anterior / Adelante restaura | `booking-view.test.ts` "atras y adelante recorren los pasos conservando prestacion, dia y horario" + `booking-integration.test.ts` | OK |
| Recarga de un paso sin estado / Valor inválido | `booking-view.test.ts` `it.each(['?paso=2','?paso=3','?paso=9','?paso=abc','?paso=1'])` (normaliza con `replaceState`, sin entrada nueva) y `booking-step-url.test.ts` | OK |
| Sin datos en la URL | `booking-view.test.ts` "avanzar de paso deja la URL con solo el numero de paso" + `booking-integration.test.ts` (snapshots de `location.href`) | OK |
| Confirmación sin datos en la URL / título fijo | `app/routes.test.ts` (título "Reserva confirmada") + `booking-integration.test.ts` sin rastros | OK |

### local-time-display
| Escenario | Test | Estado |
|-----------|------|--------|
| Fin de mes / Fin de año | `dates.test.ts` `addLocalDays` | OK |
| Etiquetas desde otra zona / Fecha inválida | `dates.test.ts` `dayLabels` (con `TZ=Asia/Tokyo`, sin coma, `2026-02-30` lanza) | OK |

Resumen: 0 FALTA; 0 PARCIAL; 1 escenario solo VISUAL (mobile 320 px / acción pegada al pie). No se detectaron aserciones triviales: los tests de "sin rastros" usan espías reales de consola, URL, título y storages sobre el flujo completo; los tests de honeypot recorren los elementos enfocables; los de doble envío cuentan llamadas al gateway.

## 3. Fidelidad del contrato contra el backend real (solo lectura)

| Aspecto | Backend (código real) | Frontend | Resultado |
|---------|-----------------------|----------|-----------|
| Catálogo | `GET /api/v1/public/services` -> `PublicServiceResponse(id, name, description, durationMin)`; 429 `RATE_LIMITED` | `decodeServices` exige `id`, `name`, `description` string/null, `durationMin` entero; 429 -> `rate-limited`, otro fallo -> `connection` | OK |
| Disponibilidad | `AvailabilityPublicController`: `serviceId`/`date`; `AvailabilityResult(status OK|NO_ACTIVE_SERVICES, slots[{startsAt, endsAt}])`; 404 `SERVICE_NOT_FOUND`, 422 `SERVICE_INACTIVE`, 429 | query codificada; `status` y slots validados con `parseInstant`; `no-services`, `service-gone`, `rate-limited`, `connection` | OK |
| Reserva | `POST /api/v1/public/bookings` con `CreateBookingRequest(serviceId, startsAt, firstName, lastName, dni<=20, phone<=50, email, website<=100, privacyAccepted @AssertTrue)` | cuerpo exacto `{serviceId, startsAt, firstName, lastName, dni, phone, email, website, privacyAccepted: true}`; DNI se envía tal cual escrito (6-9 dígitos tras normalizar, <=20 del servidor) | OK |
| Éxito / honeypot | 201 `CreateBookingResponse(bookingId, serviceName, startsAt, endsAt, status, practiceName)`; honeypot no vacío -> **200** `ACCEPTED` con nulos | `decodeBooking`: `CONFIRMED` -> `confirmed` (ignora `bookingId`, `practiceName` null -> `''`); `ACCEPTED` -> `accepted` | OK |
| Conflicto | `SLOT_TAKEN` (409) incluyendo la traducción de la exclusión de la base (`saveAndTranslate` -> `APPOINTMENT_SLOT_TAKEN` -> `SLOT_TAKEN` dentro de `bookAppointment`); 422 `APPOINTMENT_IN_PAST`, `BOOKING_TOO_SOON`, `BOOKING_BEYOND_HORIZON`, `BOOKING_CONTACT_LIMIT` | 409 `SLOT_TAKEN` y los tres 422 -> `slot-unavailable`; `BOOKING_CONTACT_LIMIT` -> `contact-limit` | OK |
| 400 | `VALIDATION_FAILED` + `errors[{field, code}]` (códigos `NotBlank`, `Size`, `Email`, `AssertTrue`, `NotNull`; el DNI inválido llega con texto libre) | campos conocidos -> `fields`; `dni` con cualquier código -> mismo mensaje; sin campos conocidos o `MALFORMED_REQUEST` -> `unknown-outcome` | OK |
| 429 | `RateLimitedException` -> 429 `RATE_LIMITED` + `Retry-After` en segundos (lectura 120/min por IP; reserva con límite propio) | `retryAfterSeconds` -> `describeWait` ("en 1 minuto", "en unos N minutos", "en unos minutos") | OK |
| CSRF | `/api/v1/public/**` es `permitAll` pero **POST exige CSRF** (solo `/auth/login` está exento); `CsrfHeaderFilter` emite la cookie `XSRF-TOKEN` y `X-CSRF-TOKEN` en cualquier respuesta; 403 `CSRF_REQUIRED` | el POST sale por `cookieCsrfTransport` (cabecera `X-XSRF-TOKEN`, cookie emitida por los GET previos); 403 `CSRF_REQUIRED` -> `stale-page`; test de cabecera en `booking-gateway.test.ts` | OK |
| Honeypot | `website` no vacío -> 200 genérico sin efectos (antes del rate limit y del caso de uso) | campo `website` oculto, siempre enviado (vacío para personas), 200 `ACCEPTED` -> confirmación genérica indistinguible | OK |
| Mensajes | `title`/`detail` del servidor pueden contener texto del framework | `booking-messages.ts` es la única fuente de copy; nunca refleja `title`/`detail` | OK |

No hay discrepancias de contrato. Observación (no discrepancia): un 404 `SERVICE_NOT_FOUND` o 422 `SERVICE_INACTIVE` en el POST no se produce con el backend actual (el caso de uso responde 409 `SLOT_TAKEN` cuando la prestación no ofrece el slot); el mapeo `service-gone` del POST es defensivo y inocuo.

## 4. Reglas duras (CLAUDE.md raíz)

| Regla | Resultado |
|-------|-----------|
| 9 (strict, sin `any`) | `grep` de `: any`, `as any`, `<any>` en `frontend/src`: 0 usos (producción y tests); `tsc --noEmit` verde. OK |
| 10 (sin `innerHTML`) | 0 apariciones de `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `srcdoc`, `eval(`, `new Function` en producción; `architecture.test.ts` lo vigila. OK |
| 11 (solo tokens) | Sin colores literales (`#`, `rgb()`, `hsl()`) en `booking.css`. Literales dimensionales: bordes `1px`/`2px`, `outline-offset: 1px`, `1px` del `.hp` y `64rem` de media queries (mismas excepciones documentadas en C-05 D8 y C-06 D7); medidas locales `--layout-booking-day-width: 3.5rem` y `--layout-booking-day-height: 4rem`, marcadas como candidatas a token. `--color-primary-muted` (borde del badge y `.check`) existe en `design-system/tokens.css`. OK con observación (S1) |
| 12 (sin deps) | `package.json` y lock idénticos a `dbe1567`. OK |
| 6 (sin PII en logs) | 0 `console.*` en `features/booking`, `app` y `main.ts`; test de "sin rastros" con espías de consola. OK |
| Sin almacenamiento | 0 usos de `localStorage`, `sessionStorage`, `indexedDB` en producción; la confirmación vive en una variable de módulo de consumo único. OK |
| Email enmascarado | `maskEmail` (`ma•••@gmail.com`, `j•••@x.com`, dominio completo); `confirmation-store` recibe solo el enmascarado; el test asserta que `maria@gmail.com` no aparece en la página. En `flow.form` el email completo vive en memoria hasta el éxito (`resolve` -> `createFlow()` lo descarta). OK |
| Honeypot | `<div class="hp" aria-hidden="true">` con `input#website` `tabindex="-1"` `autocomplete="off"`, clase `.hp` fuera de pantalla sin `display:none` (D5); presente en el DOM, no perceptible, no alcanzable con Tab. OK |
| Pasos en la URL | `?paso=2|3` con `pushState`; `parseStep` solo reconoce 1/2/3; normalización con `replaceState`; la URL nunca lleva prestación, horario ni datos personales (test de `location.href`). OK |
| Título | rutas con título fijo "Reservar turno" y "Reserva confirmada"; sin datos del paciente. OK |
| 13 / 14 | Commit `feat(C-24): ...` por el implementador; el verificador no hizo commits. OK |

## 5. Desviaciones reportadas por el implementador

| Desviación | Evaluación |
|-----------|------------|
| Copy de alertas alineado a los mockups aprobados (con punto final; "Elegí otro. Te mostramos los más cercanos; tus datos quedan guardados."); tabla D7 desactualizada | **Aceptable**: los mockups aprobados son el criterio de aceptación (tasks.md). Falta actualizar D7 (W2). |
| Texto de ayuda del DNI tomado del mockup ("Como figura en el documento; podés usar puntos.") en vez de D5 | **Aceptable** por la misma razón; registrar en D5 (W2). |
| Gateway devuelve `kind: 'ok'` (`{ kind: 'ok', services }`/`{ kind: 'ok', slots }`) en vez de `{ ok, ... }` de la tabla D2 | **Aceptable**: unión discriminada homogénea por `kind`, mejor para `switch` exhaustivo; mismos casos que D2. Registrar (W2). |
| Funciones de estado del flujo (`selectService`, `selectDate`, `selectSlot`, `setForm`, `next`, `back`, `goTo`, `submit`, `resolve`, `urlStepOf`, `flowStepOf`) en lugar de `chooseService`/`chooseDate`/`chooseSlot`/`back`/`submit`/`resolve` de D3 | **Aceptable**: mismas transiciones, puras y probadas en `booking-flow.test.ts`; los nombres de la tarea 2.4 eran orientativos. Registrar (W2). |
| `history.back()` para "Volver" (tarea 5.4), inyectado como `goBack`/`replaceUrl` en `RoutesDeps` | **Aceptable en principio, pero con un efecto indeseado tras un 409** (ver W1). |
| Ícono `warning` nuevo en `core/dom/form.ts` (`FormIconName`) | **Aceptable**: los avisos de límite/horario tomado del mockup usan triángulo; cambio mínimo y aditivo. Registrar (W2). |
| `test-harness.ts` (importa `vitest`) dentro de `src/features/booking`, tratado como producción por `architecture.test.ts` | **Aceptable**: nadie en producción lo importa (no entra al bundle; build de 60 módulos) y el escaneo lo cubre sin hallazgos; ver S3 para renombrarlo `*.test-util.ts` o moverlo. |
| Medidas locales `--layout-booking-day-width/height` (D8 hablaba de `--booking-day-*`) | **Aceptable**: mismo patrón de C-05/C-06 (`--layout-hairline`); registrar nombre real (W2). |
| Borde del badge y `.check` con `--color-primary-muted` | **Aceptable**: token existente de `design-system/tokens.css`. |
| Archivos extra: `booking-types.ts`, `booking-ui.ts`, `booking-view-details.test.ts`, `booking-integration.test.ts` (en vez de `main.test.ts` para 8.2-8.4) | **Aceptable**: la integración vive en un archivo propio para no inflar `main.test.ts`; los escenarios de 8.2-8.4 están cubiertos. Registrar (W2). |
| 8.2-8.4 no vistos en RED (se hizo una comprobación por mutación) | **Warning W3** (mismo patrón que W4 de C-06): el comportamiento está cubierto y la mutación demuestra que los tests fallan al romper el código, pero el ciclo RED estricto no consta. |

## Issues

### CRITICAL
Ninguno.

### WARNING
- **W1 (código, no corregido)** `frontend/src/features/booking/booking-view.ts` (`onBack: deps.goBack`, líneas ~215 y ~232) + retorno por conflicto con `replace` (línea ~278). Tras un 409 el historial queda `[/reservar, ?paso=2, ?paso=2]` (la entrada del paso 3 se reemplaza por el paso 2). "Volver" en el paso 2 ejecuta `history.back()` y cae en el `?paso=2` anterior: se vuelve a mostrar el paso 2 en lugar del paso 1, y hace falta tocar "Volver" dos veces. Lo mismo ocurre después de elegir una sugerencia y volver desde el paso 3 (`[1, 2, 2, 3]`). Analizado por lectura del flujo de historial; **no reproducido en navegador** (queda en el guion manual 9.3, punto 2). Ningún test cubre "Volver tras un 409". Corrección sugerida (de código, a decidir): que "Volver" navegue explícitamente al paso anterior con `navigate(stepPath(n - 1))` (o `replace`) cuando la entrada previa del historial no es ese paso, o que el retorno por conflicto use `pushState` en vez de `replace`; agregar un test "Volver desde el paso 2 tras un 409 muestra el paso 1".
- **W2 (artefactos, resuelto)** `design.md` no refleja lo construido: copy de alertas de la tabla D7, ayuda del DNI de D5, forma `kind: 'ok'` del gateway (D2), nombres de las funciones de estado y dependencias `replaceUrl`/`goBack` (D3), archivos extra de D1 (`booking-types.ts`, `booking-ui.ts`, `test-harness.ts`, `booking-integration.test.ts`, `booking-view-details.test.ts`), medidas `--layout-booking-day-*` y `--color-primary-muted` (D8) e ícono `warning` en `form.ts`. Corregir con una sección "Desviaciones de implementación" (como en C-05 y C-06).
- **W3 (proceso TDD, resuelto/aceptado)** Las tareas 8.2-8.4 no se observaron en RED (hay comprobación por mutación). Dejar constancia en `design.md` y aceptar la desviación.
- **W4 (artefactos, resuelto)** `design.md` línea `Visual verify: differences (...)` debe quedar en `passed` con las diferencias minor aceptadas y la nota de que falta la verificación contra backend real (9.3). Las `Approval:` no se tocan.

### SUGGESTION
- **S1** Documentar los literales dimensionales (`1px`/`2px`, `64rem`) y las dos medidas locales del día como excepciones permitidas y candidatas a token en la Revisión 2 del design system.
- **S2** Un test de integración para "Volver tras un 409" cuando se corrija W1.
- **S3** Renombrar `test-harness.ts` (p. ej. `booking-test-utils.ts` fuera de `src/features/booking` o excluirlo del escaneo de producción de `architecture.test.ts`) para que el escaneo "de producción" no recorra código solo de tests.
- **S4** Datos descartados al abandonar `/reservar`: el spec dice que los datos del formulario se descartan de la memoria al salir; hoy `flow` (incluido el email completo) queda referenciado en el closure de `createBookingView` hasta la próxima entrada a `/reservar`. No es observable por el usuario (el test confirma que se reinicia vacío), pero se puede limpiar al detectar la navegación a otra ruta.
- **S5** Diferencias visuales menores en la confirmación (ver sección 6): título "¡Turno reservado!" y círculo de éxito algo más chicos que en el mockup y ícono de sobre del aviso reemplazado por el de información; "Volver" con unos pocos px de diferencia de separación. Ajustar en la próxima iteración de estilos si se quiere igualdad exacta.
- **S6** Sigue sin medirse cobertura de líneas (mismo W3 de C-05): se juzga por escenarios.

## 6. Visual verify

- `design.md` registra `Visual verify: differences (2026-11-23, apply: ...)` con 14 pantallas en 390x844 y 1440x900 (28 capturas en `mockups/verify/`), sin scroll horizontal a 320 px y sin excepciones de consola. Pares revisados por mí (imagen del mockup aprobado en `previews/` contra la captura real en `verify/`):
  - `step2-schedule-390` y `-1440`: misma estructura (stepper "Paso 2 de 3", "Volver", `h1` "Elegí día y horario", resumen "Limpieza · 45 min", tira de días con MAR 24 marcado, grupos Mañana/Tarde, 10:30 marcado, resumen "mar 24/11 · 10:30 – 11:15" y "Continuar" al pie). Diferencias: separación vertical de unos 4 px, "Volver" un poco más grande, fondo del área principal más claro a 1440 px, sin la leyenda "Mockup C-24...". Minor.
  - `slot-taken-390`: aviso ámbar "Ese horario se acaba de ocupar." con el mismo texto, bloque "Horarios cercanos" con 09:00, 09:45 y 11:15, sin horario marcado, "Continuar" deshabilitado con "Elegí un horario para continuar". Coincide; diferencia de unos px.
  - `confirmation-390`: tarjeta con Limpieza, badge "Confirmado", Día, Horario y Consultorio, aviso con el email enmascarado (`ma•••@gmail.com` en la captura real; `an•••@ejemplo.com` en el mockup, solo cambia el dato de ejemplo), "Reservar otro turno". Diferencias: `h1` y círculo de éxito más chicos, ícono de información en lugar de sobre, línea "Consultorio" sin marca `(TBD)` (el dato viene del servidor). Minor (S5).
  - `step3-errors-390`: mismos campos, ayudas y errores ("Ingresá un DNI de 6 a 9 números.", "Ingresá un email válido.", "Tenés que aceptar el aviso de privacidad para reservar."), casilla con borde de error; el DNI de ejemplo difiere (`12345` frente a `30.456`) por el caso forzado. Coincide.
- No volví a levantar el navegador ni la aplicación: se compararon capturas existentes (4 pares a 390 px y 1 par a 1440 px; las otras 23 capturas no se inspeccionaron una por una).

## 7. Pendientes manuales (no bloqueantes)

- **Tarea 9.3**: prueba manual contra el backend real de C-14 (guion en la sección 1). Debe hacerla un integrante con el entorno completo; incluye comprobar W1.
- Texto del aviso de privacidad, datos del consultorio y límite por contacto siguen `(TBD)` (PA-10, PA-05, TBD 8 del design system).

## Lo que no pude verificar

- Ejecución contra un backend real o en un navegador (solo lectura de código, ejecución de la suite y comparación de capturas existentes).
- Reproducción en navegador de W1.
- Cobertura de líneas (sin proveedor instalado).
- Evidencia histórica de RED (no hay salida previa a la implementación en el repo).

## Siguiente acción recomendada

1. Decidir W1 con el equipo (un cambio pequeño en `booking-view.ts` más un test) antes de archivar, o aceptarlo explícitamente y cubrirlo en la prueba manual 9.3.
2. Que un integrante con el entorno completo ejecute el guion 9.3.
3. `/opsx:archive c-24-public-booking-ui` (sincroniza `public-booking-ui`, `spa-routing` y `local-time-display` y marca C-24 en `CHANGES.md`). Commit solo cuando el usuario lo pida.

## Resolución de warnings (2026-11-23)

- **W1 resuelto (2026-11-23)**: causa raíz: el retorno por 409 usaba `replace` (historial `[1, 2, 2]`) mientras "Volver" usa `history.back()`. Corrección en `booking-view.ts`: el caso `slot-unavailable` llama a `deps.goBack()` y retrocede a la entrada `?paso=2` existente (el paso 3 solo se alcanza por push desde el 2). Tests en `booking-view-details.test.ts` (describe "409: horario tomado mientras se reservaba"): "Volver en el paso 2 tras un 409 lleva al paso 1 con una sola pulsacion", "tras un 409 y elegir una sugerencia, Volver va al paso 2 y otro Volver al paso 1" y "tras un 409 el paso 2 conserva el aviso y las sugerencias". Vistos en RED antes del cambio; suite completa 692/692, typecheck y build OK. `design.md` D3 y Desviaciones actualizados.
- **S2 hecho**: los tests anteriores cubren "Volver tras un 409".
- **W2 resuelto**: `design.md` tiene la sección "Desviaciones de implementación" (copy D7, ayuda DNI, `kind: 'ok'`, funciones de estado y `replaceUrl`/`goBack`, archivos extra, medidas locales, ícono `warning`).
- **W3 resuelto (aceptado explícitamente)**: constancia en `design.md` de que 8.2-8.4 no se vieron en RED (comprobación por mutación).
- **W4 resuelto**: `Visual verify: passed (2026-11-23; diferencias minor aceptadas ...)` en `design.md`; las líneas `Approval:` no se modificaron.
- **Tarea 9.3**: sigue sin marcar; se agregó la nota "pendiente: prueba manual contra backend real, a realizar por un integrante con el entorno completo".
- S1-S6 quedan como seguimientos.
