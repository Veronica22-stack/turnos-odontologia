# Diseño: C-21 `patients-ui`

## Context

Ver `proposal.md` §Why. Estado relevado el 2026-11-23 sobre el árbol de trabajo actual:

- `frontend/src/app/routes.ts`: `pacientes` (`/pacientes`, layout `private`, título `Pacientes`, guard `privateGuard`) usa `placeholderView('Pacientes', …)`. `RoutesDeps` expone `session`, `gateway`, `agenda`, `settings`, `booking`, `replaceUrl`, `goBack`, `navigate`, `onLogout`, `clock?`. El router fija `document.title = "${route.title} · ${marca}"` y enfoca el `h1` tras cada render; `layouts/private-layout.ts` ya marca «Pacientes» como sección actual (`main.ts` `PRIVATE_SECTIONS`).
- `frontend/src/core/http/client.ts`: `request(method, path, { body, decode })` → `HttpResult<T>` con `problem{status, code, errors, errorId?, retryAfterSeconds?}` / `unauthorized` / `network` / `timeout` / `invalid-response`. `core/http/transport.ts` agrega `X-XSRF-TOKEN` a `POST`, `PUT`, `PATCH` y `DELETE` desde la cookie `XSRF-TOKEN` (`credentials: 'same-origin'`); `main.ts` ya inyecta `cookieCsrfTransport()` y `onUnauthorized`. Sin `console.*` en producción.
- `frontend/src/core/dom/h.ts` construye nodos con `textContent`; `core/dom/confirm.ts` ofrece `confirmAction({ title, text, confirmLabel?, cancelLabel? })` con `<dialog>` nativo, Escape = cancelar y foco devuelto al origen, pero **hoy enfoca el botón de aceptar**.
- `frontend/src/features/agenda/agenda-gateway.ts` ya llama a `GET /patients?q=` y `POST /patients` con `decodePatientSummary` (`agenda-types.ts`): `{ id, firstName, lastName, dni, phone, active }`. Es un consumidor parcial; el detalle y la mutación viven en este change.
- `frontend/src/features/settings/` es el patrón a seguir: gateway que concentra rutas/cuerpos/decodificadores, `*-types.ts` con decodificadores defensivos, `*-messages.ts` como única fuente de textos, vistas por sección y estilos propios con tokens.
- Backend C-10 implementado y archivado; el contrato de D2 se leyó del **código** (`PatientsController.java`, DTOs `api/`, `PatientsService.java`) y de las specs `patients-management`, `api-access-policy`, `error-responses`.
- Design system aprobado: `Revision: 1`, `Approval: approved (2026-11-16, by user)`, política `system-and-mockups` en `.active-orchestrator-state.json`. `components.html` ya incluye tabla de pacientes en desktop, lista apilada en móvil y estados vacíos.
- Existen cambios de usuario sin commitear en el árbol (C-19 archivado, archivos de `features/settings` modificados). Este propose **no los toca**.

Restricciones: reglas duras 6, 8, 9–12, 13–15 (sin PII en logs/URL/título, fechas UTC→Buenos Aires, TS estricto sin `any`, sin `innerHTML`, solo tokens, sin dependencias nuevas, sin commits). Governance **MEDIO** con checkpoints.

## Goals / Non-Goals

**Goals:**
- Reemplazar el placeholder por la vista operativa apoyada en piezas existentes (`HttpClient`, `h()`, `confirmAction`, router, layout, design system), sin reescribirlos.
- Aislar el contrato de C-10 en `features/patients/`, con lógica pura (validación, estado de la vista, decodificación y mensajes) probada sin DOM y vistas probadas con jsdom y `fetch` simulado.
- Mostrar el indicador de RN-PA-03 solo cuando el servidor lo informe, sin inferirlo ni fabricarlo.
- Cumplir el gate **[UI]**: mockups aprobados antes del apply y verificación visual contra ellos después.

**Non-Goals:**
- Alta manual de pacientes desde esta vista: C-10 ya expone `POST /patients` y C-20 la usa desde el alta de turno. La vista no ofrecerá un botón «Nuevo paciente» no funcional; si el mockup de referencia lo muestra, se marca `(TBD)` y queda fuera del alcance aprobado.
- Endpoints, esquema, migraciones ni auditoría del backend: C-10 está archivado.
- Historia clínica, odontograma ni evoluciones (C-22).
- Borrado físico, cambio de DNI, exportación/importación ni multi-tenant.
- Modificar el change activo `c-25-booking-management-ui`.

## Decisions

### D1 — Estructura
```
frontend/src/features/patients/
├── patients-types.ts      # DTOs, decodificadores y uniones de resultado (solo tipos + parseo)
├── patients-gateway.ts    # search/get/update/deactivate: contrato D2
├── patients-messages.ts   # textos propios y clasificación de fallos (D4)
├── patients-state.ts      # máquina de estados de listado/ficha/edición — pura, sin DOM
├── patients-validation.ts # validación local del formulario — pura (D5)
├── patient-row.ts         # tabla desktop / lista móvil (D6)
├── patient-card.ts        # ficha, insignias e indicador RN-PA-03 (D3/D6)
├── patient-form.ts        # formulario de edición (D5/D6)
├── patients-view.ts       # /pacientes: listado, búsqueda, ficha, baja
src/core/dom/confirm.ts     # + opción aditiva `initialFocus?: 'accept' | 'cancel'` (default 'accept')
src/styles/patients.css     # solo tokens del design system (D8)
src/app/routes.ts           # pacientes → createPatientsView; RoutesDeps.patients
src/main.ts                 # createPatientsGateway({ client })
```
Carpeta propia, misma forma que `features/settings/`. Dependencia en un solo sentido: `patients` → `core/*` y `layouts/*`; reutiliza `confirmAction`, `h()` y, si existe, la opción `initialFocus` de C-25 (ver Riesgos).

### D2 — Contrato confirmado con C-10 (contrastado con el código)

| Endpoint | Petición | Éxito | Errores reales |
|----------|----------|-------|----------------|
| `GET /api/v1/patients` (sesión `DENTIST`, sin CSRF) | `?q=<término>` opcional; el servidor hace `trim` y filtra `active = true` | **200** `PatientSummaryResponse[]` | 401 `UNAUTHENTICATED`, 403 `FORBIDDEN` (globales); red/timeout/`invalid-response` |
| `GET /api/v1/patients/{id}` (ídem) | — | **200** `PatientResponse` + evento `PATIENT_READ` auditado en el servidor | **404** `PATIENT_NOT_FOUND`; 401/403; red/timeout/ilegible |
| `PUT /api/v1/patients/{id}` (CSRF) | `{ firstName, lastName, phone, email?, birthDate?, address? }` | **200** `PatientResponse` + `PATIENT_UPDATE` auditado | **400** `VALIDATION_FAILED` con `errors[{field, code}]`; **404** `PATIENT_NOT_FOUND`; **403** `CSRF_REQUIRED`; 401/403; red/timeout/ilegible |
| `DELETE /api/v1/patients/{id}` (CSRF) | — | **204** sin cuerpo (baja lógica: `active = false`, fila conservada) | **404** `PATIENT_NOT_FOUND`; **403** `CSRF_REQUIRED`; 401/403; red/timeout |

- `PatientSummaryResponse` = `{ id: uuid, firstName, lastName, dni, phone, active }`. **Sin email ni dirección**.
- `PatientResponse` = `{ id, firstName, lastName, dni, phone, email, birthDate, address, source, active, createdAt, updatedAt }`. `birthDate` es `LocalDate` (`yyyy-MM-dd`) o `null`; `address`/`email` pueden ser `null`/`""`; `source` ∈ `MANUAL | PUBLIC_BOOKING`; `createdAt`/`updatedAt` son `Instant` ISO UTC. **No expone marcador de revisión ni datos clínicos**.
- El `q` viaja codificado con `URLSearchParams`; el id como segmento con `encodeURIComponent`. `PUT` y `DELETE` obtienen la cabecera CSRF del transporte existente; la cookie `XSRF-TOKEN` ya está disponible porque las consultas `GET` de la vista preceden a las mutaciones.
- Ningún DTO incluye DNI en la petición de edición: `UpdatePatientRequest` no lo acepta y la UI lo muestra solo como texto de solo lectura.
- Errores de validación del servidor: los `field` se mapean a los nombres del formulario (`firstName`, `lastName`, `phone`, `email`, `birthDate`, `address`); campos desconocidos se ignoran sin mostrar el valor enviado.

### D3 — Indicador de datos para revisar (RN-PA-03)
El backend C-10 **no serializa hoy** ningún flag de revisión: `FindOrCreatePatientByDni` preserva la ficha existente, pero el dato divergente no quedó expuesto en `PatientResponse`. Decisión:
- El gateway decodifica un campo **opcional** `reviewRequired?: boolean` (nombre candidato; si el backend elige otro, se ajusta solo el decodificador). `true` → la ficha muestra una insignia con ícono + texto «Datos para revisar» y el texto «Los datos informados en una reserva no reemplazaron los de esta ficha. Revisalos antes de guardar cambios.».
- Ausente o `false` → **no se muestra nada**; en particular, `source === 'PUBLIC_BOOKING'` **no** habilita el indicador (rn-RN-PA-02 no implica revisión pendiente).
- Tras una edición correcta, el indicador se recalcula **solo** con el valor devuelto por el servidor; la UI nunca lo guarda en `localStorage` ni lo deriva de la petición enviada.
- Pedido al backend (P1, no bloqueante): exponer `reviewRequired` en `PatientResponse` y definir cuándo se limpia. Workaround actual: el indicador simplemente no aparece; no se inventa un dato.

### D4 — Clasificación de fallos y mensajes
| Llamada | Resultado expuesto a la vista |
|---------|-------------------------------|
| `search(q)` | `ok{patients}` · `error{connection}` (red, timeout, `invalid-response`, 5xx, otro `problem`) · `forbidden` (403) · `unauthorized` (lo gestiona `main.ts`) |
| `get(id)` | `ok{patient}` · `not-found` (cualquier 404) · `connection` · `forbidden` |
| `update(id, input)` | `ok{patient}` · `invalid{errors}` (400 `VALIDATION_FAILED`) · `not-found` · `stale-page` (403 `CSRF_REQUIRED`) · `connection` |
| `deactivate(id)` | `ok` · `not-found` · `stale-page` · `connection` |

`patients-messages.ts` es la única fuente de textos (voseo, como C-19/C-24/C-25); **nunca** se usa `title`/`detail` del `problem`. `stale-page` → «La página quedó desactualizada. Recargala e intentá de nuevo. No se hizo ningún cambio.»; `connection` → «No pudimos cargar…/guardar… Revisá tu conexión.» + «Reintentar»; `not-found` → «No encontramos este paciente» + «Volver al listado». La baja con resultado de red/timeout usa un texto que **no afirma** que se completó.

### D5 — Validación y edición
- Local, antes de cualquier petición: `firstName`, `lastName` y `phone` obligatorios y no vacíos; `email` con formato simple si no está vacío; `birthDate` vacío o `yyyy-MM-dd` con fecha calendario válida; `address` ≤ 255. Límites de tamaño acordes a los `@Size` del DTO (`100`, `50`, `255`). Regla pura en `patients-validation.ts`, sin DOM, con `FieldErrors` por nombre de campo.
- El formulario se precarga desde `PatientResponse`; el DNI y el origen se muestran como texto de solo lectura, nunca como `input` editable.
- Un segundo envío mientras hay una petición en curso se ignora; el botón queda `aria-busy` y deshabilitado.
- Los 400 con `errors[]` se convierten en errores por campo; si el servidor devuelve un campo desconocido, se muestra un aviso genérico sin repetir valores.

### D6 — Vistas y estados
- **Listado**: `h1` «Pacientes», buscador con etiqueta accesible y placeholder «Buscar por nombre, DNI o teléfono», tabla desktop (`components.html` `.data`, columnas Paciente/DNI/Teléfono) y lista apilada móvil (`.patient-list`). Cada fila enlaza la ficha sin cambiar la URL. Orden: el devuelto por el servidor (alfabético); la UI no reordena.
- **Ficha**: `h1` con nombre completo, `h2` «Datos de contacto», lista de definiciones con DNI (solo lectura), teléfono, email, fecha de nacimiento, dirección, origen y fechas; insignia «Inactivo» si `active === false`; bloque RN-PA-03 de D3. Acciones «Editar ficha» y «Dar de baja». Fechas e instantes en `America/Argentina/Buenos_Aires` mediante `shared/utils/dates.ts`.
- **Edición**: misma estructura del formulario de `settings/` (inputs con etiqueta, ayuda y error, `.form-actions`), «Guardar cambios» y «Cancelar» (vuelve a la ficha sin petición).
- **Baja**: `confirmAction({ title: '¿Dar de baja a este paciente?', text: 'La ficha quedará inactiva y no aparecerá en las búsquedas. Sus turnos y su historia clínica se conservan. No se puede deshacer.', confirmLabel: 'Sí, dar de baja', cancelLabel: 'No, mantener activo', initialFocus: 'cancel' })`. Tras el éxito: aviso info y «Volver al listado»; la baja nunca muestra un botón «Eliminar definitivamente».
- **Estados vacíos**: «Todavía no hay pacientes» (sin `q`) con texto «Cuando registres un paciente desde la agenda o por una reserva, aparecerá acá.»; «No encontramos pacientes con ese criterio» (con `q`) con sugerencia de buscar por DNI o teléfono. Sin botón «Crear paciente» (ver Non-Goals).

### D7 — Rutas y arranque
- `routes.ts`: `pacientes` → `createPatientsView({ gateway: deps.patients, navigate })`; se elimina `placeholderView` de esa ruta; `RoutesDeps` suma `patients: PatientsGateway` (los tests existentes reciben un gateway falso). Título «Pacientes» y `privateGuard` sin cambios.
- `main.ts`: `createPatientsGateway({ client })`, con el mismo `HttpClient` que los demás features; import de `styles/patients.css`.
- La selección de ficha vive en memoria de la vista: la ruta no lleva `?q=`, ids ni datos personales (spec `spa-routing`). Volver al listado conserva el último término de búsqueda solo mientras la vista siga montada; al salir y volver se descarta.

### D8 — Estilos y accesibilidad
- `styles/patients.css` importa/usa solo variables de `design-system/tokens.css`, con las mismas excepciones documentadas de C-05/C-19/C-24 para bordes `1px`/`2px` y media query `64rem`. Sin colores ni medidas literales nuevas. Sin cambios a `design-system/` (Revision 1 se reutiliza).
- Tabla con `<caption class="sr-only">` y `<th scope="col">`; lista móvil con `aria-label`; estados con `role="status"`/`role="alert"`; foco visible con `--focus-ring`; diálogo con `initialFocus: 'cancel'`; `prefers-reduced-motion` cubierto por tokens.
- Revisión 1 no incluye modo oscuro; nada en este change lo habilita.

### D9 — Estrategia de tests (Vitest + jsdom, `fetch` simulado con D2)
| Archivo | Cubre (spec) |
|---------|--------------|
| `patients-types.test.ts` | Decodificadores de resumen y detalle; `null`/`undefined`, campos malformados, `source` inválido, `reviewRequired` opcional |
| `patients-gateway.test.ts` | Rutas exactas (`?q=` codificado, id codificado), bodies de `PUT`, CSRF en `PUT`/`DELETE`, cada código de D2, `reviewRequired` ausente/presente |
| `patients-validation.test.ts` | Campos obligatorios, email, `yyyy-MM-dd` válido/inválido, longitudes, `errors[]` del servidor |
| `patients-state.test.ts` | Transiciones puras de listado → ficha → edición → baja, doble envío, volver, reintento |
| `patients-messages.test.ts` | Copy por resultado; sin `title`/`detail`; insignias por `active`/`source` |
| `patient-row.test.ts`, `patient-card.test.ts`, `patient-form.test.ts` | Tabla/lista, ficha, indicador RN-PA-03, edición y errores por campo |
| `patients-view.test.ts` | Búsqueda, estados vacíos/carga/error, ficha 404, edición 400/403, baja (confirmar/cancelar/Escape/200/timeout) |
| `app/routes.test.ts`, `patients-integration.test.ts` | Ruta real (no placeholder), título, guard, sin datos personales en URL/título/consola/storage, flujo completo |
| `core/dom/confirm.test.ts` (+casos) | `initialFocus: 'cancel'`; default sin cambios; Escape y foco devuelto |

Datos ficticios (por ejemplo `30.456.789`, `paciente@ejemplo.invalid`); reloj fijo `2026-11-23T13:00:00Z`. Sin credenciales reales.

## Risks / Trade-offs

- **[Sin flag de revisión en el backend]** RN-PA-03 no se puede mostrar hasta que C-10 exponga el marcador → D3 decodifica un campo opcional y nunca lo inventa; P1 queda documentado. No bloquea el apply.
- **[`confirm.ts` compartido con C-25]** Ambos changes planean agregar `initialFocus?: 'accept' | 'cancel'` con default `'accept'` → si C-25 se aplica primero, la tarea de C-21 se reduce a verificar el comportamiento existente; si se aplica C-21 primero, C-25 reutiliza la opción. No se modifica ningún archivo de `c-25-booking-management-ui`.
- **[C-20 ya consume `/patients`]** `decodePatientSummary` de `agenda-types.ts` sigue vigente → no se refactoriza ni se unifica; C-21 crea sus propios decodificadores porque necesita el detalle completo. Posible duplicación aceptada y documentada.
- **[Timeout en `PUT`/`DELETE`]** No hay idempotencia: una edición o baja pudo aplicarse sin respuesta → mensajes que no afirman ni niegan el resultado y, para la edición, «Ver ficha actual» que vuelve a consultar; para la baja, recargar el listado.
- **[Paciente dado de baja con turnos/HC]** La baja es lógica y no cancela turnos ni oculta la HC → el texto de confirmación lo explicita; no se ofrece borrado físico.
- **[Cambios de usuario sin commitear]** El árbol tiene trabajo ajeno en `features/settings/` y C-19 archivado → el apply debe limitarse a los archivos listados en D1 y no incluir los dif preexistentes en el commit (que este change no realiza).
- **[Datos personales en memoria]** La búsqueda y la ficha viven en la closure de la vista → se descartan al navegar fuera de `/pacientes`; nunca en `localStorage`/`sessionStorage`.

## Migration Plan

Sin migración de datos ni cambios de backend. Rollback: revertir los archivos del change; vuelve el `placeholderView` de C-05 en `/pacientes`. Verificación manual sugerida con `npm run dev` y backend con C-10: buscar por nombre/DNI/teléfono, abrir ficha, editar, provocar 400 y 403, dar de baja y confirmar que desaparece del listado sin borrado físico.

## Open Questions

- **P1 (backend, no bloqueante)**: ¿se agrega `reviewRequired` a `PatientResponse` y cuándo se limpia? Mientras tanto el indicador no se muestra (D3).
- **OQ-1 (producto)**: ¿se quiere alta manual de pacientes desde `/pacientes` en una versión posterior? Fuera de C-21 (Non-Goals); los estados vacíos no prometen esa acción.
- **OQ-2 (producto)**: nivel WCAG objetivo; se aplica la línea base del design system (D8).

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-23)
- Mockups: openspec/changes/c-21-patients-ui/mockups/
- Mockup revision: 1
- Approval: approved (2026-11-23, by user)
- Preview: rendered (2026-11-23, `mockups/previews/<estado>-390.png` y `mockups/previews/<estado>-1440.png`; 14 estados × 2 viewports)
- Visual verify: passed (2026-11-23; 40 capturas en `mockups/verify/` a 390×844 y 1440×900; flujo real contra C-10, 400/403/404/review simulados en el navegador; baja lógica confirmada en BD; consola, URL, título, storages y peticiones sin PII; detalle y evidencia TDD en `tasks.md` §«Resumen del apply — verificación manual (10.3)»)
- Criterio de aceptación: los HTML de `mockups/` (listado con resultados, búsqueda sin coincidencias, sin pacientes, ficha, ficha con «Datos para revisar», edición, error por campo, confirmación de baja, baja correcta, estados de error) y sus capturas a 390×844 y 1440×900. Revisión aprobada y registrada: `Approval: approved (2026-11-23, by user)` aquí y en `mockups/README.md`; el verify visual quedó registrado arriba.
