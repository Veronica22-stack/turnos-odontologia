# Design: C-11 `appointments-core`

> **Plan approval:** approved (2026-11-22, by Lucas). Governance CRÍTICO: se habilita el apply sobre este plan; cualquier desvío de D1–D22 requiere nueva aprobación.

## Context

Ver `proposal.md` (Why). Estado actual relevante:

- `agenda` ya tiene `PracticeService` (prestaciones, `durationMin`, `isActive`), `WorkingHours` (tramos por `weekday` en hora local), `ScheduleBlock` + `ScheduleBlockRepository.findOverlapping`, el contrato público `BlockedRanges` y los contratos sin implementar `agenda.domain.AffectedAppointmentsProvider` / `agenda.api.AffectedAppointments`. `ScheduleBlockService` recibe `Optional<AffectedAppointmentsProvider>` y hoy siempre devuelve `affectedAppointments = []`.
- `pacientes.api.PatientLookup.findStatus(UUID)` → `Optional<PatientStatus(id, active)>`, sin PII ni `PATIENT_READ`.
- `shared`: `InstallationPractice` (id y zona), `PracticeTime`, `ZoneConversions`, `InstantRange`, `Clock` bean, excepciones `DomainException` → `ApiExceptionHandler` (`NotFound` 404, `Conflict` 409, `BusinessRule` 422, `Validation` 400). `ApiExceptionHandler.handleDataIntegrity` distingue `schedule_block_no_overlap` por nombre en el mensaje de la causa; el resto → `409 CONFLICT`.
- Migraciones aplicadas hasta `V12__odontogram_entry.sql`; `btree_gist` desde V1; privilegios por defecto `SELECT/INSERT/UPDATE/DELETE` para `${app_role}` (V1); `practice` conserva `UPDATE` para el rol de aplicación (V2 solo revoca `INSERT, DELETE`), por lo que `SELECT ... FOR UPDATE` es posible.
- `ModuleBoundaries.ALLOWED_DEPENDENCIES` no declara ninguna dependencia saliente de `agenda`.
- Tests: `*Test` (`./gradlew test`), `*IT` (`./gradlew integrationTest`) sobre `AbstractIntegrationTest` con PostgreSQL embebido zonky compartido. Hay precedente de concurrencia real con `ExecutorService` en `ClinicalAuditFailureIT`. **Sin Docker ni Testcontainers (DD-10).**

## Goals / Non-Goals

**Goals:**
- Invariante de no solapamiento garantizado por la base (exclusión) y por serialización por consultorio frente a bloqueos.
- Reglas de turno en dominio puro y testeable (sin Spring ni `java.sql`), con `Clock` inyectado.
- Un único camino de escritura (servicio de aplicación) usado por la API interna y por el puerto público de `agenda.api` (que en C-11 expone solo `reschedule`, D16).

**Non-Goals:**
- Cálculo de disponibilidad, outbox de emails, tokens de reserva, UI.
- Optimización de throughput del lock (single-tenant, carga baja).

## Decisions

Las decisiones D1–D13 fueron **aprobadas por el usuario (Lucas)** antes de este propose; D14–D22 son decisiones de diseño derivadas. El **2026-11-22** el usuario aprobó en la revisión del plan: `PATIENT_INACTIVE` → 409 (D4, D19), fusión de tramos contiguos del horario (D5), puerto público reducido a `reschedule` (D16), `GET` de consulta y listado dentro del alcance (D17), y como decisiones (ya no suposiciones) la duración snapshot al reprogramar (D15), los códigos `APPOINTMENT_NOT_STARTED`/`APPOINTMENT_ALREADY_STARTED`/`APPOINTMENT_SLOT_BLOCKED` (D6, D7, D19) y la coherencia de cancelación y el límite de `notes` en base (D9). Cada punto lleva la marca **Aprobado (2026-11-22)**.

### D1 — Migración `V13__appointment.sql` (desvío de `CHANGES.md`)
`CHANGES.md` §C-11 dice `V8`, pero `V8` es `schedule_block` (C-09) y `V12` es `odontogram_entry` (C-17); Flyway corre con `outOfOrder=false`, así que la única versión válida es la siguiente libre: **V13**. Consecuencia: `CHANGES.md` §C-18 también anuncia `V13` para `evolution_entry`; C-18 deberá usar la siguiente versión libre. La actualización de `CHANGES.md` se hace en el archive (ver `tasks.md` § Workflow follow-up), no ahora.

### D2 — Exclusión `WHERE (status <> 'CANCELLED')` sobre columna generada
`period tstzrange GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED` (patrón de V8) + `CONSTRAINT appointment_no_overlap EXCLUDE USING gist (practice_id WITH =, period WITH &&) WHERE (status <> 'CANCELLED')`. `COMPLETED`/`NO_SHOW` siguen ocupando su rango histórico (resuelve la Suposición de KB 04). La entidad JPA mapea `period` como no insertable/no actualizable o directamente no lo mapea.
- Alternativa descartada: `WHERE status = 'CONFIRMED'` (permitiría pisar el rango de un turno ya atendido).

### D3 — Lock pesimista por consultorio para turno ↔ bloqueo
La exclusión cubre turno↔turno, pero no turno↔bloqueo (otra tabla). Dentro de la transacción se ejecuta `SELECT id FROM practice WHERE id = :practiceId FOR UPDATE` **antes** de validar y escribir, en: alta y reprogramación de turno, y alta y edición de bloqueo (**modifica `ScheduleBlockService` de C-09**). La eliminación de bloqueo no lo toma (solo libera rango). La validación de bloqueos se hace con `ScheduleBlockRepository.findOverlapping` en la misma transacción. Orden de locks único (siempre `practice` primero) → sin deadlocks entre estos flujos.
- **Desvío aprobado por Lucas 2026-11-22: carrera reprogramar vs cancelar (lost update).** Hibernate reescribe todas las columnas, así que un `reschedule` que cargó el turno antes de que un `cancel` concurrente confirmara podía pisar `status` y des-cancelar el turno. Por eso `cancel`, `complete`, `noShow` y `reschedule` también toman `PracticeLock.lock(practiceId)` **antes de leer el turno** (el `practiceId` sale de `InstallationPractice.id()`, single-tenant), y el turno se carga una sola vez, ya bajo el lock. El orden de locks es siempre `practice` → fila del turno; no se usa `@Version` ni cambio de esquema. Con esto, toda mutación de turno del consultorio queda serializada; las lecturas (`get`, `list`) no toman el lock. Nota: `SELECT ... FOR UPDATE` sobre `practice` también espera a inserciones que referencian la fila (FK `KEY SHARE`), pero como todos los flujos toman `practice` primero no hay deadlock por orden.
- Alternativas: advisory locks (`pg_advisory_xact_lock`, más opaco y requiere clave numérica), tabla común de "ocupación" (refactor grande de C-09), `SERIALIZABLE` (reintentos en toda la app). Se elige el lock de fila por simplicidad y porque single-tenant tiene una sola fila.
- Ubicación: puerto `shared.domain.PracticeLock` (`void lock(UUID practiceId)`) implementado en `shared.infrastructure` con consulta nativa sobre el `EntityManager`; `agenda.application` lo usa como cualquier tipo de `shared`. Requiere transacción activa (`@Transactional` en el servicio; el adaptador usa `Propagation.MANDATORY`).

### D4 — Paciente solo por `patientId`, validado vía `PatientLookup`
El request lleva solo `patientId`. Se valida con `pacientes.api.PatientLookup`: vacío → `NotFoundException("PATIENT_NOT_FOUND")` (404); inactivo → `ConflictException("PATIENT_INACTIVE")` (**409**). **Aprobado (2026-11-22)**: se alinea con C-16 (`historiaclinica/application/ClinicalPatientGuard.requireWritable`, que lanza `ConflictException("PATIENT_INACTIVE")`), para que el mismo código tenga el mismo status HTTP en toda la API: el paciente existe pero su estado impide operar, que es lo que el proyecto ya expresa con 409. No se expone `FindOrCreatePatientByDni`. `ModuleBoundaries.ALLOWED_DEPENDENCIES` suma `AGENDA → Set.of(PACIENTES)` y el spec `module-boundaries` se actualiza. `agenda.application` puede usar `pacientes.api`: la regla solo prohíbe a `application` depender de la `api` **de su propio módulo**, y toda dependencia entre módulos va a `api`.

### D5 — Sobreturno explícito persistido
Campo `overbooking boolean NOT NULL DEFAULT false` en request y tabla. Sin flag: el rango local `[inicio, fin)` debe quedar contenido en un **intervalo continuo de atención** del día local (`weekday` ISO 1–7 de `starts_at` en la zona del consultorio, D22) → si no, `BusinessRuleException("OUTSIDE_WORKING_HOURS")` (422). Con flag: no se mira el horario; turnos y bloqueos siempre se validan.
- **Fusión de tramos contiguos — Aprobado (2026-11-22)**: los tramos de `working_hours` del mismo `weekday` se ordenan por inicio y los que se tocan (`fin == inicio del siguiente`) se fusionan en un único intervalo continuo. Ej.: con 09:00–12:00 y 12:00–15:00, un turno 11:30–12:30 es válido sin sobreturno; con hueco (09:00–12:00 y 13:00–15:00), un turno 11:30–12:30 → 422 `OUTSIDE_WORKING_HOURS`. Los bordes exactos caben (inicio local `>=` inicio del intervalo y fin local `<=` fin del intervalo). C-08 ya impide tramos superpuestos del mismo día, así que solo hace falta fusionar los que se tocan.
- Reemplaza la suposición anterior de "un único tramo".
- Un turno que cruza la medianoche local nunca cabe (los tramos son de un solo `weekday`); solo se admite como sobreturno.
- Implementación: función pura `WorkingHoursFit` en `agenda.domain` (sin Spring), con TDD.

### D6 — Máquina de estados
`CONFIRMED → CANCELLED` (siempre; `cancelled_by`, `cancelled_at = clock`), `CONFIRMED → COMPLETED | NO_SHOW` solo si `starts_at <= now` (si no, 422 `APPOINTMENT_NOT_STARTED`, código propio **aprobado 2026-11-22**). `CANCELLED`, `COMPLETED`, `NO_SHOW` terminales → `ConflictException("INVALID_STATUS_TRANSITION")` (409). Implementada en la entidad de dominio `Appointment` con `Clock`.

### D7 — Reglas del turno manual y reprogramación
Alta y reprogramación: `starts_at > now` (si no, 422 `APPOINTMENT_IN_PAST`); sin `min_notice_min`, `max_advance_days` ni alineación a granularidad. Prestación del consultorio existente (404 `SERVICE_NOT_FOUND`) y activa (422 `SERVICE_INACTIVE`); `public_visible` no importa. `ends_at = starts_at + duration_min_snapshot`. Reprogramar: solo `CONFIRMED` (si no, 409 `INVALID_STATUS_TRANSITION`) con `starts_at > now` (si no, 422 `APPOINTMENT_ALREADY_STARTED`, código propio **aprobado 2026-11-22**); atómico (D14). Reutilizable por C-15 vía D16.

### D8 — Sin `audit_log` para turnos
No se agregan acciones `APPOINTMENT_*`. Logs con solo IDs (turno, paciente, prestación, consultorio); nunca `notes`.

### D9 — Nombres y restricciones de columnas (KB 04)
`origin text CHECK (origin IN ('PUBLIC','INTERNAL'))` (no `source`, para no confundir con `patient.source`); `status text CHECK (...)`; `cancelled_by text NULL CHECK (cancelled_by IN ('PATIENT','DENTIST'))`; coherencia de cancelación **aprobada (2026-11-22)**: `cancelled_by` y `cancelled_at` presentes si y solo si `status = 'CANCELLED'` (ambos presentes en `CANCELLED`, ambos nulos en cualquier otro estado), p. ej. `CHECK ((status = 'CANCELLED' AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL) OR (status <> 'CANCELLED' AND cancelled_by IS NULL AND cancelled_at IS NULL))`; `notes text NULL CHECK (char_length(notes) <= 1000)` (límite **aprobado 2026-11-22**, también `@Size(max=1000)` en el DTO); `duration_min_snapshot int CHECK (> 0)`; `CHECK (ends_at > starts_at)`; `created_at`/`updated_at timestamptz NOT NULL DEFAULT now()`. FKs `practice_id`, `patient_id`, `service_id` `ON DELETE RESTRICT`. Índices: `(practice_id, starts_at)`, `(patient_id)`, `(service_id)` (FK), `(status, starts_at)` (job de recordatorios de C-13). **Recomendado y adoptado**: `REVOKE DELETE, TRUNCATE ON appointment FROM ${app_role}` (sin borrado físico, como `service` y `patient`).

### D10 — Traducción de la violación de exclusión
Camino amigable: pre-validación en aplicación (bajo el lock) con `findOverlappingActive(practiceId, from, to, excludeId)` → `ConflictException("APPOINTMENT_SLOT_TAKEN")`. Camino de carrera residual: el servicio hace `saveAndFlush` y la violación `23P01` sobre `appointment_no_overlap` llega como `DataIntegrityViolationException`; `ApiExceptionHandler.handleDataIntegrity` suma la detección por nombre de restricción (igual que `schedule_block_no_overlap`) → 409 `APPOINTMENT_SLOT_TAKEN`. Para el puerto de `agenda.api` (llamado sin pasar por el controller), el servicio de aplicación también traduce la excepción a `ConflictException("APPOINTMENT_SLOT_TAKEN")` capturándola tras el `flush`, de modo que ambos caminos den el mismo código. Nunca se expone nombre de constraint ni SQL.

### D11 — Eventos de aplicación
Records en `agenda.api`: `AppointmentConfirmed(appointmentId, practiceId, patientId, serviceId, startsAt, endsAt, origin)`, `AppointmentCancelled(appointmentId, practiceId, patientId, startsAt, endsAt, cancelledBy)`, `AppointmentRescheduled(appointmentId, practiceId, patientId, previousStartsAt, previousEndsAt, startsAt, endsAt)`. Publicados con `ApplicationEventPublisher` **dentro** de la transacción, después del `flush` exitoso. Consumidores (C-13) deben usar `@TransactionalEventListener(phase = AFTER_COMMIT)` para que un fallo de email nunca revierta (RN-NO-05) y para no ver eventos de transacciones revertidas. C-11 solo publica y lo prueba (unit con publisher mockeado; IT con un listener `AFTER_COMMIT` de test).
- Como `agenda.application` no puede depender de `agenda.api`, los eventos se construyen en un adaptador (`agenda.infrastructure`) que implementa un puerto de dominio `AppointmentEventsPublisher` con tipos de dominio; el adaptador mapea a los records de `agenda.api` y publica. Mismo patrón que `AffectedAppointmentsProvider`.
- `COMPLETED`/`NO_SHOW` no publican eventos (no generan notificaciones).

### D12 — Implementación de los contratos de C-09
Un adaptador en `agenda.infrastructure` implementa `agenda.domain.AffectedAppointmentsProvider` y `agenda.api.AffectedAppointments` sobre el repositorio de turnos: turnos `CONFIRMED` del consultorio cuyo `period && [from, to)`, ordenados por `starts_at`. Al registrarse el bean, el `Optional` de `ScheduleBlockService` deja de estar vacío.

### D13 — Tests de concurrencia reales
`AppointmentConcurrencyIT` extiende `AbstractIntegrationTest` **sin** `@Transactional`: dos hilos con `CountDownLatch` de arranque llaman al servicio de aplicación (o a MockMvc con sesión) sobre el mismo slot → exactamente un éxito y un `APPOINTMENT_SLOT_TAKEN`, una sola fila vigente. Segundo caso: turno vs. bloqueo sobre rangos que se intersectan → nunca "turno dentro del bloqueo sin estar en `affectedAppointments`". Limpieza explícita de filas en `@AfterEach` (sin `@DirtiesContext`).

### D14 — Reprogramación in-place
Se actualiza la misma fila (mismo `id`, sigue `CONFIRMED`; cambia `starts_at`, `ends_at`, `overbooking`, `updated_at`). En PostgreSQL el `UPDATE` no choca con la versión anterior de la propia fila en la exclusión, así que mover el turno a un rango que se solapa con su rango anterior funciona (spec "Nuevo rango solapado con el propio rango anterior"). La pre-validación excluye el propio `id`. Si falla (pre-validación o `23P01`), la transacción se revierte y la fila original queda intacta (RN-RS-09). Mantener el `id` evita que C-15 tenga que re-vincular el `booking_token`.
- Alternativa descartada: cancelar y crear un turno nuevo (rompe el vínculo con el token y duplica eventos).

### D15 — Duración snapshot conservada al reprogramar
**Aprobado (2026-11-22).** Reprogramar no cambia la prestación ni relee su duración: `ends_at = nuevo starts_at + duration_min_snapshot`. La prestación puede estar inactiva al reprogramar y la reprogramación igual procede (RN-AG-09: desactivar no afecta turnos existentes).

### D16 — Puerto público de operaciones en `agenda.api`
**Aprobado (2026-11-22): puerto mínimo.** Interfaz `agenda.api.AppointmentOperations` con **solo** `reschedule(UUID appointmentId, Instant newStartsAt, boolean overbooking)`, devolviendo un record `AppointmentView` sin PII (consumidor previsto: C-15, según `CHANGES.md` §C-15 "Reprogramación atómica reutilizando C-11"). Implementado por un adaptador en `agenda.infrastructure` que delega en el servicio de aplicación (el mismo que usa el controller), así se comparten reglas, lock, traducción de errores y eventos. Alta (`book`) y cancelación (`cancel`) **no** se publican en `agenda.api`: quedan como operaciones internas de `AppointmentService` usadas por `AppointmentsController`.
- Las reglas propias de la reserva pública que **no dependen de datos concurrentes** (`min_notice_min`, `max_advance_days`, granularidad, `public_visible`) quedan en C-14/C-15, que las validan antes de llamar; las que sí dependen (solapamiento, bloqueos, horario) se validan aquí bajo el lock. Para operaciones públicas se pasa siempre `overbooking = false`.
- **Extensión futura (C-14)**: cuando C-14 necesite crear turnos públicos, extenderá `AppointmentOperations` con una operación de alta (`origin = PUBLIC`) que delegue en el mismo `AppointmentService.book`, **reutilizando el lock por consultorio (D3), el orden de validaciones (D18), la traducción de errores (D10) y los eventos (D11)**; no se crea otro camino de escritura sobre `appointment`.

### D17 — Endpoints
| Método | Ruta | Respuesta |
|---|---|---|
| `POST` | `/api/v1/appointments` | `201` + `Location` |
| `GET` | `/api/v1/appointments/{id}` | `200` |
| `GET` | `/api/v1/appointments?from=&to=` | `200` lista (todos los estados que intersectan `[from,to)`, por `starts_at`) |
| `POST` | `/api/v1/appointments/{id}/cancel` | `200` |
| `POST` | `/api/v1/appointments/{id}/reschedule` | `200` |
| `POST` | `/api/v1/appointments/{id}/complete` | `200` |
| `POST` | `/api/v1/appointments/{id}/no-show` | `200` |

Todos con `@PreAuthorize("hasRole('DENTIST')")`, CSRF en `POST`. DTOs `record` con Bean Validation: `CreateAppointmentRequest(@NotNull UUID patientId, @NotNull UUID serviceId, @NotNull Instant startsAt, Boolean overbooking, @Size(max=1000) String notes)`, `RescheduleAppointmentRequest(@NotNull Instant startsAt, Boolean overbooking)`, `AppointmentResponse(id, patientId, serviceId, startsAt, endsAt, durationMin, status, origin, overbooking, cancelledBy, cancelledAt, notes, createdAt, updatedAt)`. Filtro inválido → `IllegalArgumentException` → 400 `INVALID_RANGE` (patrón de `ScheduleBlockService.validateFilter`). **Ajuste post-verify aprobado por Lucas 2026-11-22 (W1, S4):** el listado exige además una amplitud máxima de **93 días** (`to - from <= 93 días`; exactamente 93 se acepta, 93 días + 1 ns se rechaza) con la constante nombrada `AppointmentService.MAX_LIST_RANGE`; si se excede responde el mismo `400` `INVALID_RANGE`. El error ya no depende de que una `IllegalArgumentException` caiga en el handler genérico: el servicio lanza la excepción de dominio explícita `shared.domain.InvalidRangeException` (código `INVALID_RANGE`, HTTP 400 por el mapeo por defecto de `DomainException`). Los escenarios existentes (`from`/`to` ausentes, `to <= from`, instante mal formado) siguen devolviendo `400 INVALID_RANGE`. Los `GET` (consulta por id y listado por rango) están **dentro del alcance aprobado (2026-11-22)**: los necesita C-20 y sirven para verificar el resultado de las mutaciones.

### D18 — Orden de validaciones en el servicio
1) inicio futuro (y estado para reprogramar) → 2) paciente (solo alta) → 3) prestación (solo alta) → 4) **lock** → 5) horario de atención (si `!overbooking`) → 6) bloqueos → 7) turnos → 8) `saveAndFlush` → 9) evento. Las validaciones 1–3 no dependen de datos concurrentes y fallan antes de tomar el lock. **Ajuste (desvío aprobado 2026-11-22, D3):** en `reschedule`, `cancel`, `complete` y `noShow` el lock se toma antes de cargar el turno; en `reschedule` solo la validación sin datos concurrentes (nuevo inicio futuro) puede ir antes del lock, mientras que la carga del turno, el control de pertenencia (404), el estado y `validateReschedulable` van **después** del lock.

### D19 — Códigos de error
404: `APPOINTMENT_NOT_FOUND`, `PATIENT_NOT_FOUND`, `SERVICE_NOT_FOUND`. 409: `APPOINTMENT_SLOT_TAKEN`, `APPOINTMENT_SLOT_BLOCKED`, `INVALID_STATUS_TRANSITION`, `PATIENT_INACTIVE`. 422: `APPOINTMENT_IN_PAST`, `OUTSIDE_WORKING_HOURS`, `SERVICE_INACTIVE`, `APPOINTMENT_NOT_STARTED`, `APPOINTMENT_ALREADY_STARTED`. Se reutilizan los códigos existentes `PATIENT_NOT_FOUND`, `SERVICE_NOT_FOUND` y `PATIENT_INACTIVE` (este con el mismo 409 que C-16, D4).
- **Aprobado (2026-11-22)**: `APPOINTMENT_NOT_STARTED` y `APPOINTMENT_ALREADY_STARTED` (422) y `APPOINTMENT_SLOT_BLOCKED` (409) son códigos propios, separados de `APPOINTMENT_SLOT_TAKEN`.

- **Ajuste post-verify aprobado por Lucas 2026-11-22 (W1, S4):** `400 INVALID_RANGE` también para un listado de más de 93 días; `400 VALIDATION_FAILED` (campo `newStartsAt` o `appointmentId`) para una reprogramación con nuevo inicio o identificador ausentes, tanto por el REST (`@NotNull`) como por el puerto `AppointmentOperations` y el servicio (antes terminaba en `NullPointerException` → 500).
### D20 — Ownership por consultorio
Toda lectura filtra por `InstallationPractice.id()`; un turno de otro consultorio se trata como inexistente (404), igual que `ScheduleBlockService.requireOwned`.

### D21 — Paquetes
- `agenda.domain`: `Appointment` (entidad, transiciones), `AppointmentStatus`, `AppointmentOrigin`, `CancelledBy`, `AppointmentRepository`, `WorkingHoursFit` (función pura: fusiona tramos contiguos del día y decide si el rango cabe, D5), `AppointmentEventsPublisher` (puerto).
- `agenda.application`: `AppointmentService` (alta, cancelación, reprogramación y resultado; alta y cancelación solo las usa el controller), `AppointmentResult`, comandos.
- `agenda.api`: DTOs, `AppointmentOperations` (solo `reschedule`), `AppointmentView`, eventos, enums públicos.
- `agenda.infrastructure`: `AppointmentsController`, `SpringDataAppointmentRepository`, `AppointmentAffectedAdapter` (D12), `SpringAppointmentEventsPublisher` (D11), `AppointmentOperationsAdapter` (D16, solo `reschedule`).
- `shared.domain.PracticeLock` + `shared.infrastructure.JpaPracticeLock` (D3).

### D22 — Zona y día local
El día y la hora local se obtienen con `ZoneConversions`/`PracticeTime` en la zona de `InstallationPractice`. Los instantes se reciben y devuelven en ISO-8601 (`Instant`, UTC) y se guardan en `timestamptz`.

## UI Mockups

- Design system revision: no aplica
- Decision: no (no UI) — 2026-11-22. C-11 es solo backend; la UI de agenda es C-20.
- Mockups: —
- Approval: —
- Preview: —
- Visual verify: —

## Risks / Trade-offs

- [El lock por fila de `practice` serializa todas las altas/bloqueos del consultorio] → Aceptable en single-tenant con carga baja; las transacciones son cortas (validaciones 1–3 fuera del lock). En multi-tenant el lock ya es por `practice_id`.
- [C-14/C-15 podrían olvidar el lock] → C-15 reprograma por `AppointmentOperations.reschedule`, que toma el lock internamente. C-14 deberá extender el puerto con el alta delegando en `AppointmentService.book` (D16), nunca escribir `appointment` por otro camino (ver Follow-up).
- [Detección de la constraint por nombre en el mensaje] → Mismo enfoque probado en C-09; se agrega además el chequeo de SQLState `23P01` y un test de regresión que rompe si cambia el nombre.
- [Eventos publicados dentro de la transacción] → Un listener síncrono no transaccional (`@EventListener`) vería eventos de transacciones luego revertidas; se documenta en `agenda.api` que los consumidores deben usar `AFTER_COMMIT`.
- [Tests de concurrencia intermitentes] → Latch de arranque, timeout acotado, aserción sobre el estado final de la base (no sobre el orden).
- [Hibernate y columna generada `period`] → No mapearla (o `insertable=false, updatable=false`); `ddl-auto=validate` no exige mapear todas las columnas.
- [Cambio en `ScheduleBlockService` (C-09)] → Safety net: correr `ScheduleBlockServiceTest`, `ScheduleBlocksEndpointsIT` y `BlockedRangesIT` antes y después.
- [S1 del verify: paciente y prestación se validan antes del lock (D18), y el horario puede cambiar por C-08 sin tomar el lock] → Una baja de paciente o un cambio de horario concurrentes de C-08/C-10 con un alta pueden no verse; es aceptable en single-tenant. Si se quisiera cerrarlo, C-10/C-08 podrían tomar `PracticeLock` al desactivar o editar. **Ajuste post-verify aprobado por Lucas 2026-11-22 (W1, S4).**

## Migration Plan

1. Agregar `V13__appointment.sql` (no toca V1–V12). Rollback: no hay down-migration en el proyecto; en desarrollo, recrear la base; en producción, una migración correctiva nueva.
2. Desplegar backend con el adaptador de turnos; los bloqueos existentes empiezan a informar turnos afectados.
3. Archive: actualizar `CHANGES.md` §C-11 (V13, decisiones) y avisar el corrimiento de versión de C-18.

## Open Questions

- Ninguna. Las tensiones detectadas en el propose (status HTTP de `PATIENT_INACTIVE`, alcance de los `GET`, tamaño del puerto público, tramos contiguos, snapshot al reprogramar, códigos propios, CHECK de cancelación y límite de `notes`) fueron resueltas por el usuario el 2026-11-22 y están incorporadas en D4, D5, D6, D7, D9, D15, D16, D17 y D19.

## Follow-up para changes posteriores

- **C-14**: extender `agenda.api.AppointmentOperations` con el alta pública delegando en `AppointmentService.book`, reutilizando lock (D3), validaciones (D18), traducción de errores (D10) y eventos (D11).
- **C-15**: reprograma vía `AppointmentOperations.reschedule` (disponible desde C-11). Para la cancelación por el paciente (`DELETE /api/v1/public/bookings/{token}`, `cancelled_by = PATIENT`) deberá extender `AppointmentOperations` con `cancel`, delegando en `AppointmentService.cancel` (misma máquina de estados D6 y eventos D11).
- **Fin de tramo a medianoche**: C-08 modela `end_time` como `LocalTime`, por lo que la API no admite `24:00`; el último fin posible de un tramo es `23:59`. Un turno que termine exactamente a las 00:00 requiere sobreturno. Si C-08 cambia esa representación, revisar `WorkingHoursFit`.
