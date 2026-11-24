# Proposal: C-25 `booking-management-ui`

## Why

C-05 dejó `/turno/:token` como vista *placeholder*: hoy el enlace "cancelar o reprogramar" que llega en cada email de confirmación (C-13/C-14) abre una pantalla vacía, aunque el backend ya permite ver, cancelar y reprogramar el turno por token (**C-15, implementado y archivado el 2026-11-23**). US-031 y US-032 (Épica 3, MVP) son la otra mitad de la reserva sin cuenta: sin ellas la persona tiene que llamar al consultorio para liberar o mover su turno. Es el último eslabón de la reserva pública en el camino crítico (`C-24 → C-25 → C-26`).

Governance: **MEDIO** (lógica de frontend; los endpoints con token, CRÍTICOS, son C-15). Es un change **[UI]**: requiere los mockups de detalle, confirmación de cancelación, reprogramación y error de token (y sus estados) aprobados (design system Revision 1, política `system-and-mockups`) antes del apply.

Dependencias: C-15 (archivado; contrato contrastado con el código, design D2), C-24 (archivado en esta rama: `createSchedulePicker`, `booking-gateway.availability`, `booking-messages`, `booking-ui`, `booking.css`), C-05/C-06/C-19 (router, layout público, cliente HTTP con CSRF, `h()`, `form.ts`, `confirmAction`).

## What Changes

- **`/turno/{token}` (reemplaza el *placeholder*)**: carga el turno con `GET /api/v1/public/bookings/{token}` y muestra prestación, día, horario (Buenos Aires), consultorio y estado, con las acciones **Reprogramar** y **Cancelar turno** solo si el servidor las habilita (`reschedulable`/`cancellable`).
- **Cancelar**: confirmación previa en un diálogo nativo (reutiliza `confirmAction` de C-19, con el foco inicial en la opción segura) → `DELETE /api/v1/public/bookings/{token}` → pantalla "Turno cancelado" con aviso de email y de que el enlace deja de funcionar.
- **Reprogramar**: `/turno/{token}?accion=reprogramar` (entrada de historial propia) con el selector de día/horario de C-24 para la **misma prestación**, el turno actual siempre visible y "Confirmar cambio" → `POST …/{token}/reschedule { startsAt }` → "¡Turno reprogramado!" con el horario nuevo y el anterior. **409 `SLOT_TAKEN`** → aviso "tu turno sigue el …", disponibilidad recargada y horarios cercanos.
- **Estados**: cargando; **enlace no válido** genérico para 404 `INVALID_OR_EXPIRED_TOKEN` o token con formato imposible (sin llamar a la API); turno **pasado o cancelado** en solo lectura; 422 `BOOKING_MODIFICATION_NOT_ALLOWED` → aviso y estado actual; 429 `RATE_LIMITED` con `Retry-After`; error de conexión con "Reintentar"; resultado desconocido de una mutación (timeout/5xx) sin afirmar ni negar el cambio; página desactualizada (403 `CSRF_REQUIRED`); reprogramación online no disponible (la prestación ya no es pública).
- **Token**: se conserva en la URL (permite recargar y abrir el enlace del email); nunca aparece en el título, la consola, mensajes, `storage`, `next` ni en otra petición que no sea la ruta de la API de C-15 (spec `spa-routing`).
- **Tests (Vitest + jsdom, `fetch` simulado con el contrato real de C-15)**: ver, cancelar (confirmar/desistir), reprogramar OK/conflicto, token inválido, turno pasado, 422, 429, sin rastros del token.

Fuera de alcance: endpoints, rate limit, revocación/expiración del token, emails (C-13/C-15); exponer `serviceId` en `BookingView` (pedido P1 a C-15, con alternativa en el frontend); política de cancelación (RN-RS-07: no hay en v1); verificación visual integral y CSP (C-26); datos de contacto del consultorio (TBD); WCAG formal (PA-21); modo oscuro; i18n.

## Capabilities

### New Capabilities
- `booking-management-ui`: pantalla pública de gestión del turno por enlace con token: ver el turno, cancelarlo con confirmación previa, reprogramarlo eligiendo un nuevo horario de la misma prestación, y los estados de enlace no válido, solo lectura, conflicto, límite de tasa y resultado desconocido.

### Modified Capabilities
- `spa-routing`: el requisito "Token de gestión fuera de títulos y registros" se amplía a todo lo que hace la vista de gestión (mensajes en pantalla, `storage`, peticiones a otras rutas, enlaces salientes) y se agrega la acción de reprogramar como entrada de historial `?accion=reprogramar` sin otros datos en la URL.

## Impact

- **Código** (`frontend/`): `src/features/manage-booking/` (nuevo: gateway con el contrato real de C-15, mensajes, validación de forma del token, estado de la vista, vistas de detalle/reprogramación/resultados), `src/app/routes.ts` (vista real para `gestionar-turno`; `RoutesDeps` suma el gateway de gestión), `src/main.ts` (cablea el gateway), `src/core/dom/confirm.ts` (opción aditiva de foco inicial en "cancelar"; C-19 sin cambios de comportamiento), `src/styles/booking.css` (sección "Gestión de turno", solo tokens).
- **Reutiliza sin cambios**: `features/booking/schedule-step.ts` (`createSchedulePicker`), `booking-gateway.availability`/`listServices`, `booking-ui.ts`, `shared/utils/dates.ts`, `shared/utils/retry-after.ts`.
- **Dependencias**: ninguna nueva (regla dura 12).
- **Backend**: sin cambios. Pedidos a C-15 en design §Pedidos a C-15 (P1 `serviceId` en `BookingView`, recomendado; resto informativos).
- **Design system**: consume Revision 1 sin modificarla (`.confirm-card`, `.meta-list`, `.badge-confirmado`/`-cancelado`/`-neutral`, `.btn-danger`, diálogo de confirmación, selector de horario, alertas, `.empty`).
