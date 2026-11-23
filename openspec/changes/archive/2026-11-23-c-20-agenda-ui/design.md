# Design: C-20 `agenda-ui`

## Context

`/agenda` es un *placeholder* en `frontend/src/app/routes.ts` (layout `private`, guard ya existente). El backend de turnos ya existe (master: `backend/src/main/java/ar/turnosodontologia/consultorio/agenda/`): `POST /api/v1/appointments` (201, errores `409 APPOINTMENT_SLOT_TAKEN`, `409 APPOINTMENT_SLOT_BLOCKED`, `422 OUTSIDE_WORKING_HOURS`, `422 APPOINTMENT_IN_PAST`...), `POST /{id}/cancel|complete|no-show|reschedule`, `GET /api/v1/appointments?from&to`, `GET /api/v1/availability` (interna; sin filtros de anticipación/horizonte), `GET /api/v1/working-hours` (`[{weekday, startTime, endTime}]`). `GET /api/v1/patients?q=` y `POST /api/v1/patients` (pacientes existentes/nuevos) pertenecen a C-10, ya archivado. Los contratos están en `openspec/specs/appointments/spec.md` y `openspec/specs/availability/spec.md` (master). Los errores vienen como ProblemDetail con `code` (formato único, regla 4). El frontend tiene `shared/utils/dates.ts` (`today()`, `dayBoundsUtc()`, `formatTime()`, `localDateOf()`, `PRACTICE_ZONE`) y el sistema de diseño Revision 1 en `design-system/tokens.css` + `components.html`.

## Goals / Non-Goals

**Goals:**
- Vista diaria y semanal mobile-first sobre los turnos del día local y la semana.
- Alta de turno interno: paciente (existente o nuevo), prestación con duración snapshot, slot desde disponibilidad interna, sobreturno explícito.
- Gestión del turno desde la agenda: cancelar, reprogramar a un slot libre, marcar `COMPLETED`/`NO_SHOW`.
- Mensajes genéricos ante conflictos (409/422) sin instantes ni datos del turno existente.

**Non-Goals:**
- Notificaciones por email (C-13).
- Listado paginado completo de turnos (view to be added later).
- Modo oscuro, exports, filtros avanzados.

## Decisions

### D1 — Estructura de módulos
```
frontend/src/features/agenda/
├── agenda-gateway.ts      # tipado sobre HttpClient: listAppointments, createAppointment, cancel/reschedule/complete/noShow
├── agenda-types.ts        # AppointmentDto, AvailableSlotDto, AppointmentStatus, ErrorCode → mensaje
├── agenda-view.ts         # vista /agenda: switch diaria/semana, día seleccionado, grilla
├── day-grid.ts            # construcción de la grilla de un día: tramos de atención, turnos, bloqueos, huecos libres
├── appointment-form.ts    # alta: paciente, prestación, slot, sobreturno; envío + errores
└── messages.ts            # mensajes de error genéricos por `code` de problem+json
```
- Patrón por C-06: funciones puros testeables (`day-grid.ts`, `messages.ts`) + vistas con `h()`.

### D2 — Views sin framework, solo tokens
Vistas construidas con `h()` (`core/dom/h`; escapa texto por definición, regla 10) y estilos con `design-system/tokens.css` (regla 11): colores del rol `--status-*`, badg de turnos, `--color-primary`, grilla y bloques reutilizando los componentes de `components.html` (`.agenda`, `.appt`, `.block`, `.free-slot`, `.legend`, formularios, `.alert-*`, `.empty`, diálogos). Sin `innerHTML` de datos (regla 10).

### D3 — Día y semana de la práctica
`agenda-view.ts` guarda el día local seleccionado (`localDateOf(new Date())` + ±1 día via `dayBoundsUtc`/`localDate`) y el selector de semana (semana de la fecha seleccionada). La grilla se construye por `day-grid.ts` a partir de los tramos de atención (`GET /api/v1/working-hours`) que contienen la fecha del día; sin tramos → estado vacío con CTA "Nuevo turno".

### D4 — Turno en la agenda (día)
```
Vista diaria: grilla de 2h con cabecera de horas; turnos de estado mostrados dentro del rango contenido y
ocupando su rango: `CONFIRMED` → badge «Confirmado», `COMPLETED` → badge «Atendido», `NO_SHOW` → badge
«Ausente», `CANCELLED` → badge «Cancelado» (clases `.badge-confirmado/.badge-atendido/.badge-ausente/.badge-cancelado`
del design system; la palabra "reservado" no se muestra en v1 — no hay estado `RESERVED` en el modelo, TBD en
`design-system/README.md:53`); badges con icono + texto; sobrerturno: turno fuera del tramo marcado con badge
de sobrerturno; bloques bloqueados (rayado + candado, sin motivo; `.block` + tokens `--status-bloqueado-*`);
huecos libres clickeables.
```
Badge de "ahora": línea vertical roja (compañera del horizonte temporal); en realizar: placeholder definitivo en apply.

### D5 — Validación cliente táctil
Entradas 16 px / 48 px de alto (`--control-height`/`--control-height-lg`), foco visible (`--focus-ring`), double-submit bloqueado, campos deshabilitados solo al enviar. Validación mínima local: campos requeridos y formato de fechas; el resto la hace el servidor.

### D6 — Errores
`agenda-gateway.ts` expone el `code` del problem+json (401/403/404/409/422/429). `messages.ts` mapea: `APPOINTMENT_SLOT_TAKEN` → "Ese horario ya está ocupado, eligí otro"; `APPOINTMENT_SLOT_BLOCKED` → "El horario está bloqueado"; `OUTSIDE_WORKING_HOURS` → "Ese horario no está dentro del horario de atención"; `PATIENT_INACTIVE`/`SERVICE_NOT_FOUND` → genérico; `APPOINTMENT_IN_PAST` → "Turnos solo se pueden cargar para el futuro". Sin conexión: mensaje genérico de conexión (mismo criterio de C-06). Los mensajes son siempre de texto (nunca `title`/`detail`).

### D7 — Alta de turno (US-021)
`appointment-form.ts`: búsqueda de paciente activo (`GET /api/v1/patients?q=`); si no hay coincidencias, botón de "Nuevo paciente" que abre formulario (`POST /api/v1/patients`, campos: nombre, DNI, teléfono, email) y luego el turno; prestación: `GET /api/v1/services` → select; slot: `GET /api/v1/availability?serviceId&date` → lista clickeable; sobreturno: checkbox. Envío `POST /api/v1/appointments` con `{patientId, serviceId, startsAt, overbooking}`; en 201, actualizar la agenda; en error, `messages.ts`.

### D8 — Gestión del turno (US-022)
Desde el detalle del turno en la agenda: "Cancelar turno" → `POST /api/v1/appointments/{id}/cancel`; "Reprogramar" → selector de slots libres → `POST /{id}/reschedule`; "Marcar como atendido" / "Marcar como ausente" (solo cuando `startsAt <= ahora`) → `POST /{id}/complete` / `POST /{id}/no-show`. Confirmaciones con `<dialog>` nativo (patrón de `components.html`).

### D9 — Cálculo de la grilla y granularidad
Turnos creados en cualquier hora (sin alineación obligatoria a la granularidad; RN-AG-05). La grilla se genera a pasos de **15 min** desde los tramos de atención (design system Revision 1: `design-system/components.html` §Agenda usa franjas de 15 min con etiquetas cada 30 min); `--start`/`--span` se calculan en filas de 15 min, así un turno de 30 min ocupa 2 filas (52 px móvil / 68 px desktop, igual que los mockups). El turno se ubica en su rango devuelto por el servidor.

## Risks / Trade-offs

[Riesgo: complejidad de la grilla en mobile] → Mitigación: scope estrecho: vista diaria simple (12h/día + turnos + bloqueos), sin compresión visual.
[Riesgo: recargar la agenda por rango] → Mitigación: `GET /api/v1/appointments?from&to` del rango local del día/semana (max 93 días); paginación futura (no in scope).
[Riesgo: turnos largos overflowando la grilla en apply] → Mitigación: vista diaria comprimida por estilo de los componentes de `components.html` (truncamiento, sin compresión visual); ajuste visual por verificación de mockup (C-26).

## UI Mockups

> Política `system-and-mockups` (design system Revision 1, aprobada 2026-11-16): mockup HTML aprobado antes del apply.

- **Design system revision:** 1
- **Decision:** yes
- **Mockups:** `openspec/changes/c-20-agenda-ui/mockups/`
- **Approval:** approved (2026-11-23, by user)
- **Preview:** rendered (2026-11-23: `day-390/day-1440`, `week-390/week-1440`, `appointment-form-390/appointment-form-1440`, `errors-390/errors-1440`, `appointment-detail-390/appointment-detail-1440` in `mockups/previews/`; render automático via Playwright MCP, fallback documentado en `mockups/README.md`)
- **Visual verify:** passed (2026-11-23, re-verificación D1/D2/D4: day/week 390/1440 capturados e inspeccionados visualmente en `mockups/verify/*-fixed*.png`; D1/D2 resueltas en geometría — 15 min por fila, turno de 30 min `--span:2` — y huecos libres extra aceptados por diseño; D4 falso positivo, shell C-05 + `app-layout`; diferencias minor restantes documentadas en `mockups/verify/README.md`)

Mockups planeados:
- `mockups/day.html` — agenda diaria: selector de día, grilla de horas (turnos confirmados/bloqueados/huecos libres/ahora), leyenda.
- `mockups/week.html` — agenda semanal.
- `mockups/appointment-form.html` — alta: paciente, prestación, slot, sobreturno, validación local.
- `mockups/errors.html` — estados de error: solapamiento 409, fuera de horario 422, estado vacío.
