# Proposal: C-24 `public-booking-ui`

## Why

C-05 dejó `/reservar` y `/reservar/confirmado` como vistas *placeholder*: hoy un paciente que abre el link público no puede reservar, aunque el backend ya lo permite (C-07 catálogo público, C-12 disponibilidad, **C-14 `POST /api/v1/public/bookings` y C-15 gestión por token, implementados y archivados el 2026-11-23**). US-030 (Épica 3, MVP) es la razón de ser del producto —reservar 24/7 desde el celular sin cuenta ni llamada— y está en el camino crítico (`C-14 → C-24 → C-25`).

Governance: **MEDIO** (lógica de frontend; el endpoint público, CRÍTICO, es C-14). Es un change **[UI]**: requiere los mockups de los pasos 1-3, confirmación, conflicto 409, límite de tasa y "reservas no disponibles" aprobados (design system Revision 1, política `system-and-mockups`) antes del apply.

Dependencias: C-05 (archivado: router, layout público, cliente HTTP, `h()`), C-06 (archivado: `credentialAttempt`, `retryAfterSeconds`, `form.ts` con `field`/`alertBox`/`formIcon`, `forms.css`) y **C-14 implementado**: el contrato de design D2 se contrastó con el código real de C-07/C-12/C-14 (no es supuesto). Los tests usan un `fetch` simulado con ese contrato.

## What Changes

- **`/reservar` (reemplaza el *placeholder*)**: flujo de tres pasos en una sola ruta, mobile-first, con indicador "Paso N de 3":
  1. **Prestación**: lista de `GET /api/v1/public/services` (nombre, duración, descripción) como opciones de radio.
  2. **Día y horario**: tira de días (desde hoy del consultorio) y slots de `GET /api/v1/public/availability?serviceId=&date=` agrupados en Mañana/Tarde, en hora de Buenos Aires; estados cargando, "sin horarios este día" (con acceso al día siguiente) y error de conexión con reintento.
  3. **Tus datos**: resumen del turno elegido, nombre, apellido, DNI, teléfono y email, casilla obligatoria de aceptación del aviso de privacidad (texto `(TBD)`, PA-10) y campo honeypot `website` oculto para personas y lectores de pantalla. Validación local alineada con el servidor; envío con "Reservando…" que bloquea el doble envío.
- **Errores del envío**: 409 `SLOT_TAKEN` (y 422 de ventana `BOOKING_TOO_SOON`/`BOOKING_BEYOND_HORIZON`/`APPOINTMENT_IN_PAST`) → vuelve al paso 2, recarga la disponibilidad de ese día y **sugiere los horarios libres más cercanos** conservando los datos cargados; 429 `RATE_LIMITED` → aviso de espera con `Retry-After`; 422 `BOOKING_CONTACT_LIMIT` → aviso de límite de turnos activos (texto provisional, PA-05); 404 `SERVICE_NOT_FOUND`/422 `SERVICE_INACTIVE` → vuelve al paso 1 con el catálogo recargado; 400 `VALIDATION_FAILED` → errores por campo; red/timeout → aviso que no afirma ni niega la reserva y pide revisar el email antes de reintentar.
- **"Reservas no disponibles"**: catálogo vacío o `status = NO_ACTIVE_SERVICES` muestran un estado vacío sin formulario.
- **`/reservar/confirmado`**: confirmación con prestación, fecha y hora (Buenos Aires), nombre del consultorio y aviso de que el enlace para cancelar o reprogramar llega **solo por email** (`/turno/{token}`, C-25). Los datos viajan en memoria (nunca en URL, título, `localStorage`/`sessionStorage` ni consola); al recargar o en la respuesta genérica del honeypot (200 `ACCEPTED` sin datos) se muestra una confirmación genérica "revisá tu email".
- **Tests (Vitest + jsdom, `fetch` simulado con el contrato real)**: flujo completo, honeypot no visible ni enfocable, validaciones, 409 con sugerencias, 429, sin prestaciones, sin rastros de datos personales.

Fuera de alcance: endpoints y reglas anti-abuso (C-14), gestión por token `/turno/{token}` (C-25), texto legal del aviso de privacidad (PA-10), CAPTCHA o verificación de email (PA-05), nivel WCAG formal (PA-21: se aplica la línea base del design system), selección de día por calendario mensual, modo oscuro, i18n.

## Capabilities

### New Capabilities
- `public-booking-ui`: flujo público de reserva en la SPA: selección de prestación, día y horario, datos del paciente con honeypot y aviso de privacidad, manejo de conflicto/límite de tasa/reservas no disponibles y página de confirmación sin token.

### Modified Capabilities
- `spa-routing`: el requisito "Token de gestión fuera de títulos y registros" se amplía a `/reservar` y `/reservar/confirmado`: los datos personales del paciente y el identificador de la reserva nunca aparecen en la URL, el título ni la consola.
- `local-time-display`: se agrega el cálculo de días locales consecutivos y su etiqueta legible (`mar 24`, "martes 24 de noviembre") en la zona del consultorio, para la tira de días y la confirmación.

## Impact

- **Código** (`frontend/`): `src/features/booking/` (nuevo: gateway con el contrato real, mensajes, validación, agrupado y sugerencia de slots, estado del flujo en memoria, vistas de pasos y confirmación), `src/app/routes.ts` (vistas reales para `/reservar` y `/reservar/confirmado`), `src/shared/utils/dates.ts` (días locales y etiquetas), `src/styles/booking.css` (solo tokens), `src/main.ts` (cablea el gateway).
- **Dependencias**: ninguna nueva (regla dura 12).
- **Backend**: sin cambios. Contrato confirmado con C-07/C-12/C-14 en design D2; pedidos no bloqueantes a C-14 en design §Pedidos a C-14.
- **Design system**: consume Revision 1 sin modificarla (`.service-option`, `.stepper`, `.day-strip`/`.day`, `.slots`/`.slot`, `.sticky-cta`, `.empty`, `.check`, `.meta-list`, alertas y spinner de `components.html`).
- **Changes que dependen de este**: C-25 (`booking-management-ui`) reutiliza el selector de día/horario y el gateway de disponibilidad.
