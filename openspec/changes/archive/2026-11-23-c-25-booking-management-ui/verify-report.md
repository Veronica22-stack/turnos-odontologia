# Verify report: C-25 `booking-management-ui`

Fecha: 2026-11-23 | Rama: dev-mayra (árbol de trabajo con la implementación sin commitear sobre be6ddd8) | Revisor: sub-agente verificador independiente (solo lectura sobre código; solo se creó este informe y se ajustaron artefactos de planificación)

## Veredicto: PASS WITH WARNINGS

No hay issues CRITICAL: el contrato HTTP del frontend coincide con el backend real de C-15 (rutas, métodos, cuerpo `{startsAt}`, campos de `BookingView`, códigos 404/409/422/429/403 y `Retry-After`, CSRF en `DELETE` y `POST`). `openspec validate --strict`, `npm test` (dos corridas), `typecheck` y `build` en verde. tasks.md 29/30; la 8.3 (prueba manual contra backend real) queda como **pendiente manual** por decisión, no como falla.

Conteo: CRITICAL 0 | WARNING 3 | SUGGESTION 6.

## Resultados de ejecución

| Suite | Comando | Resultado |
|-------|---------|-----------|
| OpenSpec | `openspec validate c-25-booking-management-ui --strict` | válido (antes y después de ajustar artefactos) |
| Estado | `openspec status` | spec-driven; 4/4 artefactos completos |
| Tests (corrida 1) | `npm test` | 58 archivos, **869 tests, 0 fallos** (~24 s) |
| Tests (corrida 2) | `npm test` | 58 archivos, **869 tests, 0 fallos**; sin tests inestables |
| Typecheck | `npm run typecheck` | sin errores |
| Build | `npm run build` | OK, 69 módulos; js 110,66 kB (gzip 33,64), css 34,09 kB (gzip 6,41) |
| Dependencias | `git diff 0930140 -- frontend/package.json frontend/package-lock.json` | sin cambios (regla 12) |
| Reglas duras | grep `innerHTML/outerHTML/insertAdjacentHTML/: any/as any` en `src` (no test) | sin resultados; sin `console.*` en `features/manage-booking`; CSS nuevo solo con `var(--…)` (salvo `1px` de subrayado y un `64rem` de media query) |
| C-19 | suite completa (incluye `core/dom/confirm.test.ts` y `features/settings`) | verde; `confirmAction` sin `initialFocus` sigue enfocando "aceptar" |

## 1. Completitud

- tasks.md: 29 `[x]`, 1 `[ ]` (8.3).
- **8.3 Pendiente manual (no es una falla).** Guion para quien tenga backend C-15 + PostgreSQL + `npm run dev` (proxy `/api`):
  1. Reservar con `/reservar` (C-24) y abrir el enlace de gestión del email de desarrollo: ver "Tu turno" con prestación, día/horario de Buenos Aires, consultorio, "Confirmado", y los dos botones.
  2. "Reprogramar": URL `?accion=reprogramar`; elegir otro día y horario; "Confirmar cambio". Esperado: "¡Turno reprogramado!" con el horario de la respuesta y "Antes: …"; URL vuelta a `/turno/{token}`; llega email; el mismo enlace sigue sirviendo; la agenda del profesional muestra el cambio.
  3. Forzar 409: abrir el mismo enlace en dos pestañas, o reservar el mismo horario desde `/reservar`; en la pestaña que pierde, "Confirmar cambio". Esperado: "Ese horario se acaba de ocupar." con "Tu turno sigue el…", "Horarios cercanos" y el turno original intacto en la agenda.
  4. Prestación no pública: desactivar la prestación y tocar "Reprogramar": aviso "No se puede reprogramar…" y "Cancelar turno" sigue disponible. Verificar además que el nombre de la prestación sea único en el catálogo público (resolución por nombre, ver S2).
  5. "Cancelar turno": el diálogo enfoca "No, mantener turno" (comprobar que el anillo de foco se ve); confirmar. Esperado: "Turno cancelado", email de cancelación, horario libre; recargar la página -> "Este enlace no es válido".
  6. Rate limit: más de 30 peticiones por minuto desde la misma IP a `/api/v1/public/bookings/…` -> aviso de espera coherente con `Retry-After`.
  7. Revisar a 320 px de ancho que no haya scroll horizontal y, con devtools, que el token solo aparezca en la ruta de `/api/v1/public/bookings/…`.

## 2. Fidelidad del contrato con C-15 (código real)

| Aspecto | Backend (BookingManagementController / UseCase / SecurityConfig) | Frontend (`manage-gateway.ts`) | Resultado |
|---|---|---|---|
| Rutas y métodos | `GET/DELETE /api/v1/public/bookings/{token}`, `POST …/{token}/reschedule` | idénticas, token con `encodeURIComponent` | OK |
| Cuerpo reprogramar | `RescheduleBookingRequest(@NotNull Instant startsAt)` | `{ startsAt }` con el instante del slot sin reformatear | OK |
| `BookingView` | `bookingId, serviceName, practiceName, startsAt, endsAt, status, cancellable, reschedulable` (sin `serviceId`) | decodifica los 8 campos, descarta `bookingId`, `serviceId` opcional; `status`/instantes/banderas inválidos -> `connection` | OK |
| 404 `INVALID_OR_EXPIRED_TOKEN` | en las tres operaciones (también si no hay prestación) | -> `invalid` (cualquier código 404) | OK |
| 422 `BOOKING_MODIFICATION_NOT_ALLOWED` | `requireModifiable` en DELETE y POST | -> `not-modifiable` + nueva consulta | OK |
| 422 `APPOINTMENT_IN_PAST`, 409 `SLOT_TAKEN` | POST; el 409 también cubre horario no ofrecido/bloqueado/fuera de horario | ambos -> `slot-unavailable` (el original se conserva: el backend no modifica antes de fallar) | OK |
| 429 | `RATE_LIMITED` + `Retry-After` | `rate-limited{retryAfterSeconds}`, sin reintento automático | OK |
| 403 `CSRF_REQUIRED` | `/api/v1/public/**` es `permitAll` pero CSRF sigue activo para `DELETE`/`POST` (solo se ignora `/auth/login`) | cabecera `X-XSRF-TOKEN` desde el transporte de cookie; 403 `CSRF_REQUIRED` -> `stale-page`; 5xx/red/ilegible en acciones -> `unknown-outcome` | OK |
| Cancelación 200 | devuelve la vista con `CANCELLED` y revoca los tokens | "Turno cancelado"; recarga -> 404 -> enlace no válido (anticipado en el copy) | OK |
| Reprogramación 200 | devuelve la vista nueva y extiende la expiración del mismo token | "¡Turno reprogramado!" con datos de la respuesta; copy "el enlace sigue sirviendo" | OK |
| 400 `VALIDATION_FAILED` | `@Valid` | -> `unknown-outcome` (tarea 2.4) | OK |

Sin discrepancias. La limitación P1 (el backend no devuelve `serviceId`) se resuelve en el frontend por nombre (D5), ver S2.

## 3. Matriz de escenarios

| Requirement / Scenario | Test(s) que lo asertan |
|---|---|
| Turno confirmado futuro | `manage-view.test.ts` "con 200 muestra prestación, día, horario…" |
| Cargando | "mientras consulta muestra «Cargando tu turno…» con role=status y ninguna acción" |
| Consultorio sin nombre | "con practiceName vacío omite la línea…" |
| Banderas del servidor mandan | "las banderas del servidor mandan…" y "solo una bandera…" |
| Token vencido o revocado | "404 -> mensaje genérico con enlace a /reservar" (+ gateway 404 con cualquier código) |
| Token con forma imposible | "(símbolos)" asserta solo que no hay petición (el router muestra "Página no encontrada", ver W1); "(corto)" asserta el mensaje genérico y que no hay petición |
| Turno pasado / Ausente no se revela / Cancelado por el consultorio | "banderas en falso…", "NO_SHOW…", "CANCELLED…" |
| Desistir de cancelar | "No, mantener turno…" y "Escape tampoco…" |
| Cancelación confirmada / Doble toque | "200 -> «Turno cancelado» enfocado…" y "confirmar envía un DELETE; pendiente…segundo clic ignorado" |
| Volver sin cambios | `reschedule-step.test.ts` "«Volver» retrocede…" |
| Reprogramación correcta | "200 -> replaceUrl…", "confirmar envía un único POST…" y gateway (cuerpo exacto, CSRF) |
| Sin horario elegido | "«Confirmar cambio» empieza con aria-disabled y tocarlo no envía nada" |
| Horario ocupado en carrera | "409 -> mismo día, aviso con el turno vigente, disponibilidad … de nuevo, «Horarios cercanos» y sin preselección" |
| Turno que empezó mientras tanto (422) | "422 -> aviso, nueva consulta y el turno en solo lectura" (+ variante de reprogramar) |
| Demasiados intentos al abrir | "429 con Retry-After 40…" |
| Timeout al reprogramar | "timeout -> «No pudimos confirmar el cambio de horario.» y «Ver estado actual»…" |
| Error de conexión al abrir | "error de red -> … «Reintentar» repite la consulta" |
| Prestación ya no pública | `it.each` "service-gone / no-services" y "0/2 coincidencias / catálogo con error" |
| Sin rastros del token | `manage-integration.test.ts` "sin rastros del token…" y "un 404 tampoco…" |
| Foco al cambiar de pantalla | asserts de `document.activeElement` en el `h1` |
| spa-routing: título fijo / token fuera de títulos | `manage-integration.test.ts` "el título es siempre…" + `routes.test.ts` |
| spa-routing: Atrás desde la reprogramación | `reschedule-step.test.ts` y `manage-integration.test.ts` |
| spa-routing: recarga en reprogramación de turno pasado / acción desconocida | "carga directa … sin reschedulable normaliza la URL…" (sin nueva entrada de historial) y "accion desconocida…" |

Los tests aseveran comportamiento observable; no se hallaron tautologías. Las mutaciones de 7.2-7.3 declaradas por el implementador no fueron reejecutadas (solo lectura).

## 4. Desvíos del implementador: juicio

| Desvío | Juicio |
|---|---|
| Test `/turno/corto` además de `abc$%25` | Justificado: el router no enlaza símbolos (`PARAM_VALUE`), así que el escenario literal nunca llega a la vista. Cubre la rama de `isPlausibleToken`. Escenario y diseño corregidos (W1). |
| "Volver" usa `goBack` solo si la vista hizo el push; si no, `navigate` con `replace` | Aceptable y más correcto que `goBack` incondicional (evita salir del sitio tras carga directa). La rama `replace` no tiene test propio (S1). |
| Resolución por `serviceId` o nombre (D5) | Correcta ante el contrato real. Nombre exacto y único; ante duda, "no disponible". Riesgo residual en S2. |
| Foco al `h1` tras cada resultado | Cumple la spec de accesibilidad. |
| Copy del resumen del selector según el mockup | Coincide con la captura aprobada. |
| Ícono info en avisos de email (mockup: sobre) | Diferencia menor (S4). |
| Insignias con `--color-border`/`--color-text-secondary` | Tokens existentes; `--color-border` es "decorativo" (S5). Aceptable. |
| RED por módulo y no por comportamiento (2.1-2.4, 4-6) | Debilita la trazabilidad de Strict TDD; tests resultantes específicos (S6). |
| Mutaciones 7.2-7.3 | Plausible; no reejecutado. |

## 5. Issues

### CRITICAL
Ninguno.

### WARNING
- **W1. Escenario "Token con forma imposible" no se cumple literalmente**: para `/turno/abc$%25` el router (`match.ts`) no enlaza el parámetro y muestra "Página no encontrada", no "Este enlace no es válido"; el test de símbolos solo asserta que no hay petición. Es seguro pero difiere de la spec. **Corregido en artefactos** (escenario y D3).
- **W2. Verify visual incompleto y sobredeclarado**: `design.md` decía "17 pantallas" (hay 16 pares de capturas), no hay capturas de cargando, cancelando ni reprogramando, y la spec exige 320 px sin scroll horizontal, no verificado, aunque la tarea 6.7 decía "verificado en el verify visual". **Corregido en artefactos**; el chequeo a 320 px queda en el guion manual.
- **W3. Prueba contra backend real pendiente (8.3)**: el contrato se verificó leyendo el código de C-15 y con `fetch` simulado. Ver guion en la sección 1.

### SUGGESTION
- **S1.** Agregar un test de "Volver" tras carga directa de `?accion=reprogramar` (rama `navigate` con `replace`).
- **S2.** La resolución por nombre falla si el catálogo público tiene dos prestaciones con el mismo nombre o si se renombró la prestación del turno. Ideal: pedir a C-15 que agregue `serviceId` a `BookingView` (P1); el frontend ya lo tolera.
- **S3.** Los textos de producción incluyen la marca literal "(TBD · datos)" en "Enlace no válido" y "No se puede reprogramar online" (aprobado en el mockup, OQ-2): resolver con los datos de contacto antes de producción.
- **S4.** Ícono del aviso de email: info en vez de sobre; check del héroe de "reprogramado" sin anillo. Diferencias menores.
- **S5.** `--color-border` es un divisor decorativo; si se usa como borde de la insignia, confirmar contraste no textual en la revisión de accesibilidad de C-26.
- **S6.** Registrar en próximos changes la evidencia RED por comportamiento.

## 6. Verificación visual

Comparadas `mockups/verify/*.png` contra `mockups/previews/*.png`: `detail-390`, `cancel-confirm-390`, `reschedule-conflict-390`, `rescheduled-390` y `reschedule-1440`.
- Coinciden estructura, jerarquía, copy, insignias, avisos y botones. Las capturas de `previews/` incluyen botonera y notas del mockup; las de `verify/` no (esperado).
- Diferencias: la marca "(TBD)" junto al consultorio solo está en el mockup (el dato real viene del servidor); íconos (S4); el diálogo de cancelación en `verify` no muestra el anillo de foco que sí muestra el mockup (comprobar a mano); horarios de ejemplo distintos por datos de fixture; en el 390 de conflicto la barra fija queda a mitad de la captura de página completa (artefacto de captura).
- `report.json`: título "Gestionar turno · La Muela" en todos los estados, sin scroll horizontal a 1440.
- No capturados: cargando, cancelando, reprogramando (transitorios), ni 320 px. No verificado contra backend real.

## 7. Pendiente manual

- 8.3 completa (guion de la sección 1), incluido 320 px y el anillo de foco del diálogo.
- Las líneas `Approval:` no se tocaron; `Visual verify` queda "passed" con las salvedades anotadas.

## Resolución de warnings (2026-11-23)

- **W1**: corregido en artefactos. `specs/booking-management-ui/spec.md` (escenario "Token con forma imposible") y `design.md` D3 describen ahora el comportamiento real (router -> "Página no encontrada"; longitud fuera de rango -> "Este enlace no es válido"). Sin cambios de código.
- **W2**: corregido en artefactos. `design.md` (`Visual verify`: 16 pantallas y estados no capturados) y `tasks.md` 6.7 (320 px no verificado, pendiente manual).
- **W3**: no resoluble sin entorno completo; queda como pendiente manual (8.3 sin marcar).
- Tras los ajustes: `openspec validate c-25-booking-management-ui --strict` -> válido.
