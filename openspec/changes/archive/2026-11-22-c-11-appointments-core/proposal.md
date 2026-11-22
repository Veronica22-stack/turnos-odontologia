# Proposal: C-11 `appointments-core`

## Why

La agenda todavía no tiene turnos: existen prestaciones (C-07), horarios de atención y parámetros de reserva (C-08), bloqueos (C-09) y pacientes (C-10), pero nada que los combine en un turno que ocupe un rango del consultorio. Sin este change no se puede cargar un turno pedido por teléfono (US-021), ni cancelarlo, reprogramarlo o marcar su resultado (US-022), y quedan sin implementar los contratos que C-09 dejó pendientes (`AffectedAppointments`) y los que consumen C-12 (disponibilidad), C-13 (notificaciones), C-14/C-15 (reserva pública) y C-18 (evoluciones). Es el núcleo del dominio y está en el camino crítico. Governance: **CRÍTICO** (invariante de no solapamiento, modelo core): el plan se revisa antes de cualquier apply.

## What Changes

- **Migración `V13__appointment.sql`** (no `V8`: `V8` es `schedule_block` de C-09 y `V12` es el odontograma de C-17; Flyway corre con `outOfOrder=false`). Tabla `appointment` con `practice_id`, `patient_id`, `service_id`, `starts_at`/`ends_at` `timestamptz`, `duration_min_snapshot`, `status` (`CONFIRMED|CANCELLED|COMPLETED|NO_SHOW`), `origin` (`PUBLIC|INTERNAL`), `overbooking`, `cancelled_by` (`PATIENT|DENTIST`, nulo), `cancelled_at`, `notes`, `created_at`/`updated_at`; columna generada `period tstzrange '[)'` y `EXCLUDE USING gist (practice_id WITH =, period WITH &&) WHERE (status <> 'CANCELLED')`. Índices de KB 04 y `REVOKE DELETE, TRUNCATE` al rol de aplicación (sin borrado físico).
- **Endpoints internos `/api/v1/appointments`** (rol `DENTIST`, CSRF en mutaciones): crear turno manual con paciente existente y sobreturno explícito (`overbooking`), consultar uno (`GET /{id}`), listar por rango (`GET ?from=&to=`), cancelar, reprogramar, marcar `COMPLETED` / `NO_SHOW`. Los dos `GET` son alcance aprobado (2026-11-22).
- **Reglas de negocio**: no solapamiento con turnos (validación previa + constraint de base traducida a `409 APPOINTMENT_SLOT_TAKEN`) y con bloqueos (`409 APPOINTMENT_SLOT_BLOCKED`); horario de atención obligatorio salvo sobreturno, con tramos contiguos del mismo día fusionados en un intervalo continuo (`422 OUTSIDE_WORKING_HOURS`); inicio en el futuro; prestación existente y activa; paciente existente (`404 PATIENT_NOT_FOUND`) y activo (`409 PATIENT_INACTIVE`, alineado con C-16); `ends_at = starts_at + duración de la prestación` con snapshot; máquina de estados con terminales (`409 INVALID_STATUS_TRANSITION`).
- **Serialización turno ↔ bloqueo**: lock pesimista por consultorio (`SELECT ... FOR UPDATE` sobre la fila de `practice`) al crear/reprogramar turnos **y** al crear/editar bloqueos. Esto **modifica `ScheduleBlockService` de C-09**.
- **Reprogramación atómica** (RN-RS-09) reutilizable por C-15 mediante el puerto `agenda.api.AppointmentOperations`, que en C-11 expone **solo** `reschedule`; alta y cancelación quedan como operaciones internas de la aplicación usadas por el controller (C-14 extenderá el puerto con el alta cuando lo necesite).
- **Eventos de aplicación** `AppointmentConfirmed`, `AppointmentCancelled`, `AppointmentRescheduled` (records en `agenda.api`, publicados con `ApplicationEventPublisher`); C-11 solo publica.
- **Cierre de C-09**: implementación de `agenda.domain.AffectedAppointmentsProvider` y `agenda.api.AffectedAppointments` (turnos `CONFIRMED` en un rango); `affectedAppointments` deja de ser siempre vacío.
- **Fronteras de módulos**: se habilita la dependencia `agenda → pacientes` (solo `pacientes.api.PatientLookup`).
- **Tests (plan)**: TDD estricto; unitarios de dominio y servicio, `*IT` sobre zonky para esquema, privilegios, endpoints y concurrencia real (dos inserts simultáneos; turno vs. bloqueo).

### Fuera de alcance

- UI (agenda C-20), motor de disponibilidad (C-12), notificaciones/outbox y listeners (C-13), reserva pública y tokens (C-14/C-15).
- Alta de paciente dentro del turno (`FindOrCreatePatientByDni` no se expone); la UI crea el paciente antes con `POST /api/v1/patients`.
- `min_notice_min`, `max_advance_days` y alineación a granularidad para turnos manuales.
- Auditoría (`audit_log`) de turnos; cambio de prestación al reprogramar.
- Exponer alta o cancelación de turnos en `agenda.api` (el puerto público solo reprograma; C-14 lo extenderá).
- Actualización de `CHANGES.md` (se hace en el archive).

## Capabilities

### New Capabilities
- `appointments`: ciclo de vida del turno (alta manual, consulta, listado, cancelación, reprogramación atómica, marcado de resultado), reglas de no solapamiento con turnos y bloqueos, horario de atención y sobreturno, snapshot de duración, puerto de reprogramación reutilizable por reservas y eventos de aplicación.

### Modified Capabilities
- `database-migrations`: esquema `appointment` en `V13`, integridad de rango/exclusión en base y privilegios sin borrado físico.
- `module-boundaries`: grafo permitido con `agenda → pacientes`; contratos públicos de turnos en `agenda.api`.
- `error-responses`: códigos de error del dominio de turnos y traducción de la violación de exclusión de `appointment`.
- `api-access-policy`: protección de `/api/v1/appointments/**`.
- `schedule-blocks`: `affectedAppointments` efectivo y serialización con turnos por consultorio.
- `blocked-ranges`: contrato de turnos afectados con proveedor real.

## Impact

- **Base de datos**: nueva `V13__appointment.sql`; V1–V12 intactas. El `V13` que `CHANGES.md` asigna hoy a C-18 (`evolution_entry`) deberá pasar a la siguiente versión libre.
- **Backend**: nuevos tipos en `agenda.{api,application,domain,infrastructure}`; puerto de lock de consultorio en `shared`; cambios en `ScheduleBlockService` (lock) y `ApiExceptionHandler` (traducción de `appointment_no_overlap`); `ModuleBoundaries.ALLOWED_DEPENDENCIES` suma `AGENDA → PACIENTES`.
- **Contratos para otros changes**: C-12 lee turnos `CONFIRMED`; C-13 escucha los eventos con `@TransactionalEventListener(AFTER_COMMIT)`; C-15 reprograma con `AppointmentOperations.reschedule` (toma el lock internamente); C-14 extenderá ese puerto con el alta reutilizando el mismo lock y validaciones; C-18 referencia `appointment.id`.
- **Dependencias**: ninguna librería nueva.
