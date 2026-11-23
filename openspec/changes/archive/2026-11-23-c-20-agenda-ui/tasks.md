# Tasks — C-20 `agenda-ui`

Pendiente: done por OpenSpec cuando se aprueban los mockups. Estilos únicamente de `design-system/tokens.css`.

## 0. Safety net / baseline (Strict TDD)

- [x] 0.1 Ejecutar la suite de tests frontend existente antes del primer RED: `cd frontend && npm test` (comando real del repo verificado en `frontend/package.json`: `vitest run`); registrar "N tests passing". Si hay fallos pre-existentes → STOP: reportar antes de continuar (Strict TDD).

## 1. Tipos y contrato del gateway

- [x] 1.1 `frontend/src/features/agenda/agenda-types.ts` — decodificadores y tipos del contrato (`AppointmentDto`, `AvailableSlotDto`, `AppointmentStatus`, `AppointmentOrigin`, `CreateAppointmentPayload`); verificar: `npm run typecheck`.
- [x] 1.2 `frontend/src/features/agenda/agenda-gateway.ts` — `createAgendaGateway({client})`: `listAppointments(from?, to?)`, `createAppointment(payload)`, `cancelAppointment(id)`, `rescheduleAppointment(id, startsAt, overbooking?)`, `completeAppointment(id)`, `noShowAppointment(id)`, `listServices()`, `searchPatients(q?)`, `createPatient(payload)`, `getWorkingHours()`, `listAvailability(serviceId, date)`; todos devuelven el resultado del HttpClient y genéricos decodificados; verificar: `agenda-gateway.test.ts`.
- [x] 1.3 `frontend/src/features/agenda/agenda-gateway.test.ts` — fetch simulado (máscara C-06): 201 + `Location`, `listAppointments` 200 con turnos decodificados, 409 `APPOINTMENT_SLOT_TAKEN` / 409 `APPOINTMENT_SLOT_BLOCKED` / 422 `OUTSIDE_WORKING_HOURS` / 422 `APPOINTMENT_IN_PAST` / 404 `SERVICE_NOT_FOUND` / 404 `PATIENT_NOT_FOUND` / 409 `PATIENT_INACTIVE` / 422 `APPOINTMENT_NOT_STARTED` / 409 `INVALID_STATUS_TRANSITION` expuestos como `{ok:false, error}` con el `code` sin instantes ni datos del turno; verificar: pasen con `npm test -- agenda-gateway`.
- [x] 1.4 `frontend/src/features/agenda/agenda-messages.ts` — mapa de `ErrorCode` → mensaje genérico (sin datos del turno ocupante, sin `title`/`detail`); verificar: `agenda-messages.test.ts`.

## 2. Vista diaria

- [x] 2.1 `frontend/src/features/agenda/day-grid.ts` — función pura `buildDayGrid({workingHours, appointments, blocks, date})`: grilla de 30 min a partir de los tramos de atención (sin tramos → `hasTurns:false`/`empty`), turnos en su rango contenido (`confirmed`-like) con badge, turnos terminal (`COMPLETED`/`NO_SHOW`/`CANCELLED`) que siguen ocupando su rango, bloqueos rayados, huecos libres clickeables; verificar: `day-grid.test.ts` (turno a cualquier hora dentro del tramo; adjacencia turno-bloqueo no se superpone; día sin tramos → grid vacío; turno fuera de horario marcado como terminal).
- [x] 2.2a RED: escribir `agenda-view.test.ts` — pruebas de la vista diaria (render de la agenda con turno confirmado + bloqueo + hueco libre clickeable, selector de día, leyenda, línea del día actual); verificar: fallan antes de implementar.
- [x] 2.2b GREEN: implementar `frontend/src/features/agenda/agenda-view.ts` — vista diaria: selector de día (`today()` por defecto; ±1), grilla de `day-grid.ts`, leyenda, línea del día actual (placeholder), tooltip sin datos de turnos ocupantes.
- [x] 2.3a RED: escribir pruebas de flujo de detalle en `agenda-view.test.ts` — clic en turno → `<dialog>` nativo con estado; flujo de cancelación OK; reprogramar a slot libre OK; marcar resultado; 409 amigable; verificar: fallan antes de implementar.
- [x] 2.3b GREEN: implementar el flujo de detalle en `agenda-view.ts`: cancelar (`POST /{id}/cancel`), reprogramar a slot libre de `listAvailability` (`POST /{id}/reschedule`), marcar atendido/ausente (`POST /{id}/complete`, `POST /{id}/no-show`, solo si `startsAt <= ahora`); errores → `agenda-messages.ts`.

## 3. Formulario de alta (US-021)

- [x] 3.1 `frontend/src/features/agenda/appointment-form.ts` — `createAppointmentForm({gateway, setTurno})`: campos (paciente select: existente buscando con `searchPatients`, "Nuevo paciente" → campos `POST /api/v1/patients` y dar de alta luego de la búsqueda; prestación select de `listServices` con duración; slot select de `listAvailability` al elegir servicio+fecha; checkbox sobreturno); validación local de campos obligatorios; doble envío bloqueado; envío `POST /api/v1/appointments`; errores → `agenda-messages.ts` (409/422 amigables); verificar: `appointment-form.test.ts` — flujo existente OK (201 + llamada con `patientId` del paciente existente), flujo nuevo (dado de alta y creado), flujo 409 (mensaje genérico), 422 (mensaje del horario).

## 4. Integración

- [x] 4.1 `frontend/src/app/routes.ts` — reemplazar el `placeholderView('Agenda', ...)` por `createAgendaView()` (import desde `features/agenda/agenda-view`); verificar: `npm run build` + `npm test`.

## 5. Verificación global

- [x] 5.1 `npm run typecheck` — `tsc --noEmit` verde.
- [x] 5.2 `npm test` — suite verde total.

## Workflow follow-up

- Archivar el change después del verify de C-20 (convención C-06).
- Verificación visual contra mockups aprobados de C-20 en `mockups/previews/` y `mockups/verify/` (gate C-26).

## 6. Verify visual — correcciones D1/D2/D4 (2026-11-23)

- [x] 6.1 RED: `day-grid.test.ts` — filas de 15 min (`rows`), etiquetas cada 30 min y helpers `gridRowStart`/`gridRowSpan` (30 min → 2 filas, 45 min → 3 filas, turno desalineado, mínimo 1 fila).
- [x] 6.2 RED: `agenda-view.test.ts` — la vista diaria/semanal renderiza 16 filas para 09:00–13:00, turno de 30 min con `--span:2`, bloqueo de 60 min en 4 filas, línea del ahora y huecos libres en filas de 15 min.
- [x] 6.3 GREEN: `day-grid.ts` — `GRID_ROW_MINUTES = 15`, `rows`, `gridRowStart`, `gridRowSpan`; `agenda-view.ts` renderiza filas de 15 min con etiquetas cada 30 min (alineado con `design-system/components.html` §Agenda, Revision 1).
- [x] 6.4 D1/D2 (huecos libres): diferencia aceptada — el mockup es ilustrativo; la spec exige un hueco clickeable por rango libre. D4: falso positivo (nav del mockup = navegación entre pantallas de revisión; el shell real es C-05 / `app-layout`).
- [x] 6.5 `npm run typecheck`, `npm test` (565 verdes), `npm run build`; re-verify visual en `mockups/verify/`.
