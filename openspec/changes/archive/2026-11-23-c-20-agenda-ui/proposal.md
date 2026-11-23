# Proposal: C-20 `agenda-ui`

## Why

`/agenda` es un *placeholder* en la SPA actual: el odontólogo ingresa pero no ve ni gestiona su agenda. Ésta es la primera vista del profesional sobre los turnos, y las C-11/C-12 ya entregaron el contrato interno de turnos y disponibilidad, así que el cambio es de presentación: **vista diaria y semanal mobile-first (US-020)** con turnos, bloqueos, horarios de atención y estado; **alta manual de turno (paciente existente o nuevo, prestación, slot desde disponibilidad interna), cancelar, reprogramar, marcar `COMPLETED`/`NO_SHOW` (US-021, US-022)**; mensajes claros ante solapamiento/409; indicación de sobreturno. Governance: **MEDIO**. Change **[UI]**: requiere el mockup HTML aprobado (design system Revision 1, política `system-and-mockups`) antes del apply: vista día, vista semana, formulario de turno y estados de error.

## What Changes

- **Vista diaria `/agenda` (US-020)**: selector de día (previo/siguiente); grilla de horas del día a partir de los tramos de atención (`GET /api/v1/working-hours`); turnos `CONFIRMED` como badges de estado con indicador de sobreturno (turno contenido en su rango); rangos bloqueados (rayado + candado, sin motivo); huecos libres clickeables; línea del "ahora"; estado vacío con CTA.
- **Vista semanal**: mismos componentes organizados por semana.
- **Alta de turno (US-021)**: formulario: selección de paciente activo existente (`GET /api/v1/patients?q=`) o creación nueva (`POST /api/v1/patients`); selección de prestación (`GET /api/v1/services`, duración snapshot); selección de slot desde `GET /api/v1/availability?serviceId&date` (sin filtros de anticipación/horizonte, según spec de C-12); validación local de campos; envío `POST /api/v1/appointments` (`201`) o error amigable sin instantes ni datos del turno existente.
- **Gestión de turno (US-022)**: desde el detalle del turno: `POST /api/v1/appointments/{id}/cancel`, `POST .../{id}/reschedule`, `POST .../{id}/complete`, `POST .../{id}/no-show`; los cambios se reflejan en la agenda (turno reprogramado en su nuevo rango; turno `COMPLETED`/`NO_SHOW` siguiendo ocupando su rango).
- **Mensajes de solapamiento y conflictos**: `409 APPOINTMENT_SLOT_TAKEN` → "Ese horario ya está ocupado, eligí otro". `409 APPOINTMENT_SLOT_BLOCKED` → "El horario está bloqueado". `422 OUTSIDE_WORKING_HOURS` → mensaje enfocado en el horario de atención. `422 APPOINTMENT_IN_PAST` → "Turnos solo en el futuro". `409 PATIENT_INACTIVE`/`404` → mensaje genérico (mismo criterio de genericidad de C-06).
- **Validación táctil/foco**: entradas a 16 px / 48 px de alto, foco visible, enter/tab nav.

Fuera de alcance: notificaciones por email (C-13); listado paginado completo (vista futura); modo oscuro.

## Capabilities

### New Capabilities

- `agenda`: vista diaria y semanal del profesional sobre turnos (read/list, alta/cancelar/reprogramar/resultado, sobreturno) — frontend (`frontend/src/features/agenda/`).

### Modified Capabilities

> None: la agenda diaria/semanal no tiene spec en `openspec/specs/` aún; los contratos de turnos efectivos quedan en `appointments` y `availability` sin cambio de comportamiento.

## Impact

- **Código** (`frontend/`):
  - `src/app/routes.ts`: `/agenda` reemplaza el *placeholder* (layout `private` ya existe).
  - `src/features/agenda/` (nuevo): `agenda-view.ts` (día/semana), `agenda-gateway.ts` (`listAppointments`, `createAppointment`, `cancel/reschedule/complete/noShow` sobre `HttpClient`), `appointment-types.ts`, `appointment-form.ts`, tests en el mismo estilo de C-06 (`features/auth/`).
  - `src/styles/`: solo tokens de `design-system/tokens.css` (regla dura 11; sin `innerHTML` de datos, regla 10).
- **Dependencias**: ninguna nueva (regla dura 12).
- **Backend**: sin cambios; contrato confirmado contra master: `openspec/specs/appointments/spec.md`, `openspec/specs/availability/spec.md` (C-11/C-12 implementados; verificación real en `backend/.../agenda/`).
- **Design system**: consume Revision 1 aprobada (componentes de `components.html`: agudeza día, formularios, alertas, diálogos).
- **Tests (Vitest + jsdom, `fetch` simulado)**: render de agenda diaria/semana, flujos de alta y cancelación, manejo de 409 y mensajes genéricos.
- **C-26**: incluye verificación visual contra mockups aprobados de todas las pantallas [UI].

## Verificación de dependencias

C-06, C-10, C-11, C-12 están archivados y merged en master. Tras el fast-forward a `77857f0` (master) están disponibles en el working tree: `openspec/changes/archive/2026-11-21-c-06-login-ui`, `openspec/changes/archive/2026-11-22-c-10-patients-management`, `openspec/changes/archive/2026-11-22-c-11-appointments-core` y `openspec/changes/archive/2026-11-23-c-12-availability-engine`, junto con los specs en `openspec/specs/` (`appointments`, `availability`, `booking-settings`). PRs verificados en `git log`: C-11 → #15 (`7d787eb`), C-12 → #18 (`77857f0`).
