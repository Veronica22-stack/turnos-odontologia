# Design: C-19 `settings-ui`

## Context

Ver `proposal.md` §Why. Estado relevado del repositorio al redactar este diseño:

- `frontend/src/app/routes.ts` declara `/configuracion` con `layout: 'private'`, `guard: privateGuard` y un `placeholderView('Configuración', …)`. `frontend/src/layouts/private-layout.ts` ya marca la sección `configuracion` en la navegación; no hay que tocar el layout.
- `frontend/src/main.ts` crea un único `HttpClient` con `cookieCsrfTransport()` y un `onUnauthorized` que expira la sesión y navega a `/login?next=…` desde rutas privadas. `createRoutes` recibe `{ session, gateway, navigate, onLogout }`; el gateway nuevo se inyecta por el mismo mecanismo.
- `core/http/client.ts` devuelve `HttpResult<T>`, entiende `application/problem+json` y expone `code`, `errors[{field,code}]`, `errorId` y `retryAfterSeconds`. `core/http/decode.ts` aporta `Decoder<T>` y type guards (`isRecord`, `isString`, `isNumber`, `isBoolean`), sin `any`.
- `core/dom/h.ts` crea nodos con texto siempre como `Text` y rechaza atributos `on*` y URL peligrosas. `core/dom/form.ts` aporta `field()`, `setFieldError()`, `clearFieldErrors()` y `alertBox()`. `architecture.test.ts` prohíbe APIs de HTML dinámico en código de producción.
- `shared/utils/dates.ts` fija `PRACTICE_ZONE = 'America/Argentina/Buenos_Aires'` y expone `parseInstant`, `formatDate`, `formatTime`, `formatDateTime`, `localDateOf`, `today` y `dayBoundsUtc` con reloj inyectable.
- Los contratos de backend **ya existen y se contrastaron contra el código**, no solo contra los specs archivados de C-07/C-08/C-09: `PracticeSettingsController`, `WorkingHoursController`, `ServicesController`, `ScheduleBlocksController` y sus DTO `record` bajo `backend/src/main/java/ar/turnosodontologia/consultorio/`.
- Política visual: `system-and-mockups`, design system Revision 1 aprobado (`design-system/README.md`), tokens en `design-system/tokens.css`, nombre en `brand.js`, componentes de referencia en `design-system/components.html`. El registry `.atl/skill-registry.md` no está presente en este checkout: las convenciones frontend se resolvieron leyendo el código y los mockups aprobados de C-06, sin inventar reglas de un registry ausente.
- Restricciones: reglas duras 9–12 (TS strict sin `any`, sin `innerHTML`, solo tokens, sin dependencias de runtime), 13–14 (commits con ID y sin commit/push), 8 (UTC en BD, presentación local), Strict TDD y governance BAJO.

## Goals / Non-Goals

**Goals:**
- Sustituir el placeholder por cuatro secciones operativas que consuman los endpoints existentes sin cambiar el backend.
- Mantener todo el conocimiento del contrato HTTP en un único módulo de feature, con decodificadores estrictos y mapeo explícito de errores.
- Validar localmente lo que el servidor ya valida, para dar feedback inmediato sin relajar las invariantes del backend.
- Cubrir con Vitest (API mockeada) cada comportamiento observable de los specs, con TDD estricto.
- Respetar el mockup aprobado como criterio de aceptación antes de habilitar el apply.

**Non-Goals:**
- Cambios de backend, nuevas migraciones Flyway o nuevos endpoints.
- Alta manual de turnos, agenda diaria/semanal, pacientes o historia clínica (C-20, C-21, C-22).
- Cálculo de disponibilidad, envío de emails ni link de gestión por token (C-12, C-13, C-14).
- Edición de la zona horaria del consultorio en v1 (ver D7 y Open Questions).
- Política de cancelación, precios, seña ni WhatsApp.
- Diseño visual nuevo: se reutiliza Revision 1 sin introducir tokens propios no aprobados.

## Decisions

### D1 — Estructura del feature `settings/`

```
frontend/src/features/settings/
├── settings-gateway.ts      # único lugar que conoce rutas, cuerpos, estados y códigos del backend
├── settings-types.ts        # DTOs del frontend (readonly) + decodificadores con isRecord/isString/...
├── settings-messages.ts     # única fuente de copy en español (errores, avisos, éxitos)
├── validation.ts            # funciones puras: tramos, prestaciones, parámetros, bloqueos
├── range-overlap.ts         # solapamiento semiabierto [inicio, fin) reutilizable
├── settings-view.ts         # vista /configuracion: secciones, estados y orquestación
├── practice-section.ts      # datos del consultorio + parámetros de reserva + copiar link
├── working-hours-section.ts # grilla semanal de tramos
├── services-section.ts      # alta/edición/baja/reactivación de prestaciones
├── blocks-section.ts        # alta/edición/eliminación de bloqueos + afectados
└── *.test.ts                # colocated, con API simulada
frontend/src/styles/settings.css  # reglas de las secciones, solo variables de tokens.css
```
- **Por qué `features/settings/`**: KB 08 §Estructura de directorios asigna `settings/` (horarios, prestaciones, bloqueos) dentro de `features/`, junto a `auth/`. La decisión de C-06 D1 (gateway separado de la vista y funciones puras separadas del DOM) se replica porque ya está probada en este repo.
- **Alternativa descartada**: un único archivo `settings-view.ts` con todo el contrato HTTP y la validación mezclados. Dificulta los tests puros y repite el error que C-06 evitó con `auth-gateway.ts`/`validation.ts`.
- **Estado**: cada sección mantiene su propio `{ loading, error, data, draft }`. Guardar una sección no recarga ni descarta el borrador de las demás (spec `settings-ui`). No se introduce una librería de estado: el patrón pub/sub de `core/state` no es necesario para cuatro secciones independientes.

### D2 — Contratos HTTP consumidos (verificados en el código)

| Método y ruta | Cuerpo / respuesta (JSON) | Éxito | Errores que la UI debe manejar |
|---|---|---|---|
| `GET` `/practice/settings` | → `{ id, name, timezone, contactEmail, contactPhone, address, slotGranularityMin, minNoticeMin, maxAdvanceDays, reminderHoursBefore }` | 200 | 401 vencida, error de red |
| `PUT` `/practice/settings` | mismo objeto sin `id` → objeto actualizado | 200 | 400 `VALIDATION_FAILED` con `errors[{field,code}]`, 403 `CSRF_REQUIRED` |
| `GET` `/working-hours` | → `[{ weekday, startTime, endTime }]` (`weekday` 1=Lunes…7=Domingo; horas `HH:mm:ss`) | 200 (lista vacía válida) | 401, red |
| `PUT` `/working-hours` | `{ workingHours: [{ weekday, startTime, endTime }] }` → lista guardada (reemplazo integral) | 200 | 400 `VALIDATION_FAILED` (fin ≤ inicio, solape) |
| `GET` `/services` | → `[{ id, name, description, durationMin, active, publicVisible }]`, **incluye inactivas** | 200 | 401, red |
| `POST` `/services` | `{ name, description?, durationMin, publicVisible }` → objeto creado | 201 | 400 `VALIDATION_FAILED`, 422 `DURATION_NOT_MULTIPLE_OF_GRANULARITY` |
| `PUT` `/services/{id}` | `{ name, description?, durationMin, publicVisible, active }` → objeto actualizado | 200 | 404 `SERVICE_NOT_FOUND`, 400, 422 |
| `DELETE` `/services/{id}` | — | 204 (baja lógica idempotente) | 404 `SERVICE_NOT_FOUND` |
| `GET` `/schedule-blocks?from=&to=` | → `[{ id, startsAt, endsAt, reason, createdAt, updatedAt, affectedAppointments: [{ appointmentId, startsAt, endsAt }] }]` | 200 (lista vacía válida) | 400 `INVALID_RANGE` si el filtro es inválido |
| `POST`/`PUT` `/schedule-blocks[/{id}]` | `{ startsAt, endsAt, reason }` → objeto con `affectedAppointments` | 201 / 200 | 400 `INVALID_RANGE` / `VALIDATION_FAILED`, 409 `SCHEDULE_BLOCK_OVERLAP`, 404 `SCHEDULE_BLOCK_NOT_FOUND` |
| `DELETE` `/schedule-blocks/{id}` | — | 204 | 404 `SCHEDULE_BLOCK_NOT_FOUND` |

- Todas las rutas van bajo `/api/v1` (lo agrega `HttpClient`) y requieren sesión + CSRF; el transporte ya envía `X-XSRF-TOKEN`.
- `startsAt`, `endsAt`, `createdAt` y `updatedAt` son instantes ISO-8601 con zona. El cliente **nunca** envía `createdAt`/`updatedAt`; si vienen en una respuesta se decodifican pero no se reenvían.
- Los decodificadores rechazan respuestas con campos faltantes o de tipo incorrecto devolviendo `undefined` → `invalid-response`. Ningún decodificador usa aserciones de tipo inseguras ni `any`.
- **Alternativa descartada**: reutilizar directamente los tipos del backend. No existen tipos compartidos publicados y acoplar el frontend a DTOs Java bloquearía el contrato; se prefiere una copia tipada y validada en la frontera.

### D3 — Validación local y precedencia

- `validation.ts` expone funciones puras (sin DOM, sin HTTP) para: `validatePracticeSettings` (rangos y conjuntos de D3 de C-08), `validateWorkingHours` (fin > inicio y no solape dentro del mismo día, intervalos semiabiertos), `validateService` (nombre no vacío, duración entera > 0 y `durationMin % slotGranularityMin === 0`, longitudes 120/500) y `validateBlock` (instantes con zona, fin > inicio, motivo 1–500).
- La granularidad **vigente** se obtiene de `GET /practice/settings`; si cambia en el servidor entre la carga y el guardado, manda el error 422 del backend y se muestra en la sección de prestaciones (escenario «Conflicto del servidor»). El cliente no intenta autocorregir la duración.
- Errores locales se vinculan al campo con `setFieldError()` (`aria-invalid`, `aria-describedby`, ícono + texto). Errores de negocio/servidor se muestran con `alertBox()` dentro de la sección; se usa `title`/`detail` **nunca** como texto directo, solo `code` → copy propio de `settings-messages.ts`.
- Mapa de códigos: `VALIDATION_FAILED` → errores por `field` cuando están disponibles, si no, aviso de la sección; `DURATION_NOT_MULTIPLE_OF_GRANULARITY` → aviso en prestaciones; `INVALID_RANGE`/`SCHEDULE_BLOCK_OVERLAP` → aviso en bloqueos; `SERVICE_NOT_FOUND`/`SCHEDULE_BLOCK_NOT_FOUND` → aviso de «registro ya no existe» y recarga de la lista; `CSRF_REQUIRED` → «La página quedó desactualizada. Recargala…»; `network`/`timeout`/`invalid-response` → aviso genérico de conexión.
- **Alternativa descartada**: confiar solo en la validación nativa de `<form>` (`required`, `min`, `max`). Produce mensajes del navegador no controlables y no cubre solapamientos ni múltiplos; C-06 ya optó por `novalidate` + validación propia.

### D4 — Horarios y conversión de horas

- La grilla semanal usa siete grupos (Lunes…Domingo, `weekday` 1…7) con filas de tramo variables. Cada tramo se edita como `HH:mm` local del consultorio y se envía como `HH:mm:ss`; no se convierten a UTC porque el backend los interpreta como `LocalTime` de la zona del consultorio.
- Los instantes de bloqueo se editan con `datetime-local` en hora del consultorio y se convierten a ISO-8601 con zona mediante `dayBoundsUtc()` / `Date` antes de enviar; al mostrar se usan `formatDate`/`formatTime`/`formatDateTime` de `dates.ts`. Un instante sin zona se rechaza en el decoder local con el mismo criterio de `local-time-display`.
- **Por qué `datetime-local` y no texto libre**: el backend exige ISO con zona y la zona del navegador puede diferir; el control restringe el formato y la conversión explícita evita guardar la hora equivocada en horario de verano.

### D5 — Turnos afectados

- En bloqueos, el aviso usa `affectedAppointments` de la respuesta de `POST`/`PUT`: muestra la cantidad y el detalle con `formatDateTime` de cada `startsAt`/`endsAt`, sin `appointmentId` visible (es interno) y sin datos de paciente (el DTO no los trae). Hasta que C-11 cablee `AffectedAppointments`, la lista llegará vacía: se muestra entonces el texto «No hay turnos confirmados dentro del rango según la información disponible», no un número inventado.
- En horarios, `PUT /working-hours` devuelve solo la lista de tramos: **no hay campo de afectados**. Por eso se muestra una advertencia informativa antes de guardar («Los turnos confirmados no se cancelan automáticamente; revisalos en la agenda»), sin afirmar que se consultaron turnos.
- **Alternativa descartada**: llamar a `/schedule-blocks` u otros endpoints para inferir turnos afectados. Sería una inferencia falsa y acoplaría la UI a datos que el backend no ofrece.

### D6 — Copia del link público

- El botón compone `${window.location.origin}/reservar` con `new URL('/reservar', origin).toString()`. No se usa `APP_BASE_URL`: esa variable es de configuración del servidor, no está expuesta a la SPA, y el enlace debe apuntar al mismo despliegue que atiende la aplicación.
- Si `navigator.clipboard?.writeText` está disponible, se usa; el éxito se anuncia con `role="status"`. Ante rechazo o ausencia, se revela un `<input readonly>` con la URL (seleccionable y copiable) y un aviso. Nunca se usa `document.execCommand` ni se escriben URL o datos en logs.
- **Alternativa descartada**: pedir el link a un endpoint nuevo. No existe y agregaría backend a un change de UI.

### D7 — Datos del consultorio y zona horaria

- El formulario muestra `name`, `contactEmail`, `contactPhone` y `address` editables, y los cuatro parámetros de reserva con ayuda y límites visibles. `timezone` se muestra como dato de solo lectura y se reenvía con el valor obtenido de la API para no alterarlo: `shared/utils/dates.ts` tiene `PRACTICE_ZONE` fijo y no existe validación de zonas en el cliente.
- Esto cumple el Flujo 2 para v1 (la zona ya queda fijada en la instalación) y deja marcado `(TBD)` el pedido de hacerla editable, que requeriría decisiones de backend y de utilidades de fecha. No bloquea este change.

### D8 — Render, estilos y responsive

- Todas las vistas se construyen con `h()`/`svg()`; los textos de API y del usuario se insertan como nodos de texto. El diálogo de confirmación de baja/eliminación usa `<dialog>` nativo, con foco devuelto al control que lo abrió.
- `styles/settings.css` importa/usa **solo variables** de `design-system/tokens.css` para color, tipografía, espaciado, radios, sombras, foco y movimiento; se permite el `1px` de borde como en C-05/C-06. Se reutilizan `.field`, `.input`, `.btn`, `.alert-*`, `.card`, `.empty` y `.badge-*` de `components.html`.
- Desktop (≥ 1024 px): barra lateral de secciones + contenido a la derecha, tablas para prestaciones y bloqueos. Mobile (390 px): secciones apiladas en tarjetas, acciones a ancho completo, tablas convertidas en listas, sin scroll horizontal; el botón de copiar link queda visible sin desplazamiento excesivo.
- Estados `aria-busy` en botones durante el guardado, alertas `role="alert"` para errores y `role="status"` para éxitos. Foco al primer campo inválido y al `h1` al navegar (ya lo hace el router).

### D9 — Estrategia de tests (Strict TDD)

| Archivo | Cubre |
|---|---|
| `range-overlap.test.ts`, `validation.test.ts` | solapes (incluye adyacentes), múltiplos, rangos y límites numéricos; feliz, error de negocio y borde |
| `settings-gateway.test.ts` | rutas/cuerpos exactos, decodificadores, 204, 422/409/400/404/CSRF, 401, red/timeout |
| `settings-messages.test.ts` | copy por código, sin `title`/`detail` del servidor |
| `practice-section.test.ts` | guardado, errores por campo, copia con portapapeles y fallback |
| `working-hours-section.test.ts` | grilla, bloqueo de solape local, aviso de turnos confirmados |
| `services-section.test.ts` | alta/edición/baja/reactivación, estados vacío/error, multiples |
| `blocks-section.test.ts` | validación de rango, afectados, solape 409, confirmación de borrado |
| `settings-view.test.ts` | carga independiente por sección, reintento, render seguro con payload hostil |
| `routes.test.ts` / `main.test.ts` (casos nuevos) | `/configuracion` con sesión, sin sesión y con cambio pendiente |

- `fetch` simulado por tabla `método + ruta → Response` (patrón C-06). Sin backend, sin emails reales y sin datos clínicos.
- Cada grupo se escribe RED → GREEN → TRIANGULATE → REFACTOR con al menos un caso feliz, uno de error de negocio y uno de borde; ver `tasks.md`.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-22)
- Approval: approved (2026-11-22, by user)
- Mockups: `openspec/changes/c-19-settings-ui/mockups/`
- Viewports requeridos: 390×844 (mobile) y 1440×900 (desktop), con capturas renderizadas en `mockups/previews/` y revisión visual antes de la aprobación.
- Preview: rendered (2026-11-22, 36 capturas en `openspec/changes/c-19-settings-ui/mockups/previews/`: 18 estados × 390×844 y 1440×900; revisión automatizada de estado, variantes, diálogos, sin scroll horizontal, 16 px/48 px en inputs, foco, logo provisional y sin errores de consola). Capturas de verificación de la app (2026-11-23) en `openspec/changes/c-19-settings-ui/mockups/verify/`: `mockup-loaded-{390,1440}.png` (mockup de referencia), `app-final-{390,1440}.png`, `app-empty-all-{390,1440}.png`, `app-initial-empty-{390,1440}.png` y estados de error/éxito (`app-services-load-error-1440.png`, `app-session-expired-1440.png`, `app-practice-server-400-1440.png`, `app-hours-overlap-1440.png`, `app-hours-inverted-1440.png`, `app-service-duration-1440.png`, `app-block-overlap-409-1440.png`, `app-block-affected-1440.png`, `app-clipboard-fallback-1440.png`, `app-xss-localtime-1440.png`).
- Visual verify: passed (2026-11-23). Comparación en navegador contra la revisión 1 aprobada en 390×844 y 1440×900: cuatro secciones, navegación con «Configuración» actual, sin scroll horizontal, estados vacíos/error/éxito, mensajes por campo, diálogos de confirmación, portapapeles y render seguro coinciden. Diferencias detectadas durante la verificación y corregidas antes de completar: línea duplicada en `frontend/src/app/routes.ts` (rompía tests/typecheck/build), decodificador de instantes con nanosegundos del backend (POST 201 se mostraba como error de conexión), copy de validación de horarios/prestación y conflicto 409, y copy de estado vacío/advertencia de horarios. Sin diferencias residuales. El caso con `affectedAppointments` no vacío se verificó con payload simulado porque el backend aún no expone turnos afectados (ver Risks).
- Pantallas/estados a cubrir: `/configuracion` con datos cargados (las cuatro secciones); estados vacíos de horarios, prestaciones y bloqueos; error de carga por sección con «Reintentar»; validación local de tramos superpuestos e invertidos; formulario de prestación con duración no múltiplo; baja/reactivación de prestación; bloqueo con turnos afectados; error 409 de solape de bloqueo; confirmación de eliminación; guardado con éxito; sesión vencida; portapapeles disponible y fallback del link público.
- **NO ejecutar apply hasta aprobación explícita del mockup por el usuario.** La aprobación se registra acá y en `mockups/README.md` antes de iniciar la implementación.

## Risks / Trade-offs

- **[Riesgo] Cambio de granularidad deja prestaciones con duración no múltiplo** → la UI obtiene la granularidad vigente al cargar y muestra el 422 del servidor en la sección de prestaciones; no autocorrige ni oculta el error.
- **[Riesgo] `affectedAppointments` vacío hasta C-11** → se usa copy honesto («según la información disponible») y no se inventan turnos; el campo ya está en el DTO, por lo que no habrá que cambiar el contrato cuando se cablee.
- **[Riesgo] El endpoint de horarios no informa turnos afectados** → advertencia informativa antes de guardar, sin afirmar una consulta que no existe (D5).
- **[Riesgo] Cache de 60 s del catálogo público** → una prestación recién despublicada puede seguir visible breve tiempo en `/reservar`; se documenta en la ayuda de la sección y no se modifica el backend (decisión de C-07).
- **[Riesgo] Motivo del bloqueo con datos personales** → el formulario advierte que no se ingresen datos de pacientes; el frontend nunca escribe el motivo en `console` ni en URLs, y los errores del servidor no lo devuelven (C-09 D5).
- **[Riesgo] Portapapeles bloqueado por permisos o `file://`** → fallback con campo de solo lectura y aviso (D6).
- **[Riesgo] `CSRF_REQUIRED` por página abierta mucho tiempo** → copy específico que pide recargar; no se reintentan mutaciones automáticamente.
- **[Trade-off] Zona horaria no editable** → se preserva el valor del servidor; `PRACTICE_ZONE` sigue siendo la única fuente de presentación en v1 (D7).

## Migration Plan

Sin migración de base de datos: el change es solo frontend y consume migraciones ya aplicadas (V4, V5, V8). Implementación: crear el feature y sus tests, sustituir el `placeholderView('Configuración', …)` por la vista real en `routes.ts` e inyectar el gateway desde `main.ts`. Rollback: revertir esos dos puntos y quitar los archivos nuevos; vuelve el placeholder sin tocar datos. Verificación local: `npm test`, `npm run typecheck` y revisión visual contra mockups aprobados con el backend corriendo; sin backend, cada sección debe mostrar su estado de error con reintento.

## Open Questions

- **(TBD) Zona horaria editable**: confirmar si el odontólogo debe cambiarla desde la UI. Requiere decidir validación de zonas y hacer que `dates.ts` deje de depender de una constante; no bloquea v1 (D7).
- **(TBD) URL base del link público**: si el despliegue sirve la SPA bajo un dominio distinto del configurado en `APP_BASE_URL`, confirmar si debe mostrarse ese valor. Hoy se usa el origen de la página (D6).
- **(TBD) Texto del aviso de horarios**: validar con el usuario si prefiere un listado de turnos afectados; el endpoint actual no lo provee (D5).
