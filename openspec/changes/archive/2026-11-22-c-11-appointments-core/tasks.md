# Tasks: C-11 `appointments-core`

> **Strict TDD**: en cada grupo, la tarea de test (RED) precede a su implementación (GREEN). RED = el test se escribe y se **ejecuta fallando** por la razón esperada; GREEN = mínimo código para pasarlo; luego triangular (≥2 casos por comportamiento: feliz + borde/error) y refactorizar con la suite verde. Unitarios `*Test` con `./gradlew test`; integración `*IT` con `./gradlew integrationTest` (PostgreSQL embebido zonky; **sin Docker/Testcontainers**). Specs = criterios de aceptación; decisiones = `design.md` D1–D22.

## 1. Safety net

- [x] 1.1 Correr `./gradlew test integrationTest` en `dev-lucas` antes de tocar código y registrar el baseline ("N tests verdes") en el resumen del apply; si algo falla, detenerse y reportarlo como falla preexistente (no corregir)
  - Desvío aprobado: Fix preexistente aprobado por Lucas 2026-11-22: ScheduleBlockSchemaIT dejaba fija la lista de versiones sin V4 (merge paralelo C-07/C-09). Baseline tras el fix: 465 unit + 272 IT verdes. Nota: `OdontogramEntryTest.toStringNoExponeNotasMotivoPiezaNiEstado` es flaky (~10% por corrida: el UUID aleatorio del toString puede contener "36"); falló una vez y pasó al reintentar; no se tocó.
- [x] 1.2 Registrar en particular el estado de `ScheduleBlockServiceTest`, `ScheduleBlocksEndpointsIT`, `BlockedRangesIT`, `BlockedRangesTest`, `AffectedAppointmentsContractTest`, `ApiExceptionHandlerTest`, `ModuleBoundariesRulesTest` y `ProductionArchitectureTest` (archivos que este change modifica o extiende); verificar que todos están verdes

## 2. Migración V13 (D1, D2, D9)

- [x] 2.1 RED: crear `database/AppointmentSchemaIT` (columnas y tipos, FKs `ON DELETE RESTRICT`, índices `(practice_id, starts_at)`, `(patient_id)`, `(service_id)`, `(status, starts_at)`, `period` generado `[)`, CHECKs de `status`/`origin`/`cancelled_by`/`duration_min_snapshot`/`notes` ≤ 1000 (1001 rechazado, 1000 aceptado)/coherencia de cancelación (`cancelled_by` y `cancelled_at` presentes si y solo si `CANCELLED`: cancelado sin alguno de los dos rechazado, no cancelado con cualquiera de los dos rechazado), `ends_at > starts_at`) y verificar que falla porque la tabla no existe
- [x] 2.2 RED: en el mismo IT, casos de la exclusión: solapamiento entre `CONFIRMED`/`COMPLETED`/`NO_SHOW` rechazado con SQLState `23P01`; adyacencia, cancelado y otro consultorio aceptados; reactivar un cancelado sobre rango ocupado rechazado; verificar que fallan
- [x] 2.3 RED: crear `database/AppointmentPrivilegesIT` (`DELETE`/`TRUNCATE` denegados al rol de aplicación, `INSERT`/`SELECT`/`UPDATE` permitidos, `SELECT ... FOR UPDATE` sobre `practice` permitido) y verificar que falla
- [x] 2.4 GREEN: escribir `V13__appointment.sql` (comentario de cabecera explicando por qué V13 y no V8, sin tocar V1–V12) y verificar que 2.1–2.3 y `MigrationsIT`/`MigrationValidationIT` pasan con `./gradlew integrationTest`

## 3. Dominio del turno (D2, D5, D6, D7, D15)

- [x] 3.1 RED: `agenda/domain/AppointmentTest` — creación (`CONFIRMED`, `ends_at = starts_at + snapshot`, `starts_at > now` estricto con `Clock.fixed`, snapshot > 0); verificar que falla por clases inexistentes
- [x] 3.2 GREEN: `Appointment`, `AppointmentStatus`, `AppointmentOrigin`, `CancelledBy` (entidad JPA sin mapear o con `period` no insertable/actualizable, UUIDv7) y verificar 3.1 verde
- [x] 3.3 RED: transiciones con `@ParameterizedTest` — cancelar `CONFIRMED` (futuro y pasado) con `cancelledBy`/`cancelledAt`; `COMPLETED`/`NO_SHOW` con `starts_at <= now` (incluido igual), antes de inicio → `APPOINTMENT_NOT_STARTED`; toda acción sobre terminales → `INVALID_STATUS_TRANSITION`; verificar que falla
- [x] 3.4 GREEN: implementar las transiciones y verificar 3.3 verde
- [x] 3.5 RED: reprogramación en dominio — conserva id y snapshot (aunque la prestación esté inactiva o haya cambiado de duración: el dominio no relee la prestación), nuevo `ends_at = nuevo inicio + snapshot`, `overbooking` actualizado; no `CONFIRMED` → `INVALID_STATUS_TRANSITION`; ya iniciado → `APPOINTMENT_ALREADY_STARTED`; nuevo inicio `<= now` → `APPOINTMENT_IN_PAST`; verificar que falla
- [x] 3.6 GREEN: implementar `reschedule` y verificar 3.5 verde
- [x] 3.7 RED: `agenda/domain/WorkingHoursFitTest` (D5, D22) — tramo único: cabe con borde inicial y final exactos, excede el fin, empieza antes del inicio; día sin tramos; cruce de medianoche local; conversión UTC→`America/Argentina/Buenos_Aires` (12:00Z ok, 11:30Z fuera); verificar que falla por clase inexistente
- [x] 3.8 GREEN: función pura `WorkingHoursFit` (sin Spring) mínima para 3.7 y verificar verde
- [x] 3.9 RED: en `WorkingHoursFitTest`, fusión de tramos contiguos — 09:00–12:00 + 12:00–15:00 con turno 11:30–12:30 cabe; con hueco 09:00–12:00 + 13:00–15:00 el mismo turno no cabe; bordes exactos del intervalo fusionado (09:00–09:30 y 14:30–15:00 caben, 14:45–15:15 y 08:45–09:15 no); tramos recibidos desordenados se fusionan igual; tres tramos encadenados; tramos de otro `weekday` no se fusionan; verificar que fallan los casos de fusión
- [x] 3.10 GREEN: ordenar los tramos del día por inicio y fusionar los que se tocan (`fin == inicio del siguiente`) antes de evaluar contención; verificar 3.7 y 3.9 verdes; refactor con la suite de dominio verde

## 4. Lock por consultorio (D3)

- [x] 4.1 RED: `shared/infrastructure/PracticeLockIT` — `lock(practiceId)` dentro de transacción bloquea a una segunda transacción hasta el commit (dos hilos + latch, aserción por orden de finalización con timeout acotado); llamado sin transacción falla; verificar que falla por puerto inexistente
- [x] 4.2 GREEN: `shared.domain.PracticeLock` + `shared.infrastructure.JpaPracticeLock` (`SELECT id FROM practice WHERE id = :id FOR UPDATE`, `Propagation.MANDATORY`) y verificar 4.1 verde y `ProductionArchitectureTest` verde

## 5. Persistencia y contratos de C-09 (D12, D20)

- [x] 5.1 RED: `agenda/infrastructure/AppointmentRepositoryIT` — `findOverlappingActive(practiceId, from, to, excludeId)` (no cancelados, semiabierto, excluye id), `findConfirmedWithin` (solo `CONFIRMED`, ordenado, filtra consultorio), `findInRange` (todos los estados que intersectan); verificar que falla
- [x] 5.2 GREEN: `AppointmentRepository` (dominio) + `SpringDataAppointmentRepository` y verificar 5.1 verde
- [x] 5.3 RED: extender `AffectedAppointmentsContractTest`/`BlockedRangesIT` (o nuevo `AffectedAppointmentsIT`) con los escenarios de `blocked-ranges` "Con proveedor de turnos" y "Turnos excluidos"; verificar que falla
- [x] 5.4 GREEN: adaptador en `agenda.infrastructure` que implementa `AffectedAppointmentsProvider` y `AffectedAppointments`; verificar 5.3 verde y que el escenario "Sin proveedor de turnos" sigue verde en `ScheduleBlockServiceTest`

## 6. Servicio de aplicación de turnos (D4, D5, D7, D10, D11, D14, D16, D18)

- [x] 6.1 RED: `agenda/application/AppointmentServiceTest` (Mockito, `Clock.fixed`) — alta feliz; `PATIENT_NOT_FOUND` (`NotFoundException`) / `PATIENT_INACTIVE` (`ConflictException`, 409 como C-16) vía `PatientLookup`; `SERVICE_NOT_FOUND`/`SERVICE_INACTIVE` (prestación no pública aceptada); `APPOINTMENT_IN_PAST`; orden D18 (validaciones 1–3 no toman el lock); verificar que falla
- [x] 6.2 GREEN: `AppointmentService.book` mínimo y verificar 6.1 verde
- [x] 6.3 RED: horario/sobreturno/bloqueos/turnos — `OUTSIDE_WORKING_HOURS` sin flag (incluido turno que cruza un hueco entre tramos), turno que atraviesa tramos contiguos aceptado (el servicio pasa todos los tramos del día local a `WorkingHoursFit`), sobreturno fuera de horario aceptado, `APPOINTMENT_SLOT_BLOCKED` (también con sobreturno; adyacente aceptado), `APPOINTMENT_SLOT_TAKEN` (también con sobreturno), sin aplicar `min_notice_min`/`max_advance_days`/granularidad; lock tomado antes de las validaciones 5–7; verificar que falla
- [x] 6.4 GREEN: implementar validaciones 4–8 de D18 y verificar 6.3 verde
- [x] 6.5 RED: `DataIntegrityViolationException` de `appointment_no_overlap` tras `saveAndFlush` se traduce a `ConflictException("APPOINTMENT_SLOT_TAKEN")`; otra violación se propaga sin traducir; verificar que falla
- [x] 6.6 GREEN: traducción en el servicio y verificar 6.5 verde
- [x] 6.7 RED: cancelar, completar, no-show, reprogramar (in-place, excluye su propio id, rango solapado con el anterior aceptado, conflicto deja el original intacto, prestación ahora inactiva no impide reprogramar y no se relee su duración) y `APPOINTMENT_NOT_FOUND` para id de otro consultorio; verificar que falla
- [x] 6.8 GREEN: implementar las operaciones y verificar 6.7 verde
- [x] 6.9 RED: eventos — `AppointmentEventsPublisher` invocado con confirmado/cancelado (con actor)/reprogramado (instantes previos y nuevos); no invocado ante rechazo ni al completar/no-show; verificar que falla
- [x] 6.10 GREEN: puerto de dominio `AppointmentEventsPublisher`, adaptador `SpringAppointmentEventsPublisher` que mapea a los records de `agenda.api` (`AppointmentConfirmed/Cancelled/Rescheduled`, Javadoc indicando `@TransactionalEventListener(AFTER_COMMIT)`) y verificar 6.9 verde
- [x] 6.11 RED: `AppointmentOperationsAdapterTest` (D16) — `reschedule` delega en `AppointmentService` y devuelve el `AppointmentView` equivalente; conflicto (`APPOINTMENT_SLOT_TAKEN`), bloqueo (`APPOINTMENT_SLOT_BLOCKED`) y fuera de horario (`OUTSIDE_WORKING_HOURS`) se propagan con el mismo código que el servicio; verificar que falla
- [x] 6.12 GREEN: `agenda.api.AppointmentOperations` con **solo** `reschedule(UUID, Instant, boolean)`, `AppointmentView` y adaptador `AppointmentOperationsAdapter` en `agenda.infrastructure`; alta y cancelación quedan solo en `AppointmentService` (usadas por el controller); verificar 6.11 verde

## 7. Fronteras de módulos y contratos (D4, D11, D16)

- [x] 7.1 RED: en `ModuleBoundariesRulesTest` (fixtures en `archfixture`) agregar los escenarios de `module-boundaries`: `agenda → pacientes.api` pasa, `agenda → pacientes.infrastructure/application/domain` falla nombrando origen y destino, `pacientes → agenda.api` falla, `agenda → reservas.api` sigue fallando; verificar que falla el caso permitido
- [x] 7.2 GREEN: sumar `AGENDA, Set.of(PACIENTES)` a `ModuleBoundaries.ALLOWED_DEPENDENCIES` y verificar 7.1 y `ProductionArchitectureTest` verdes
- [x] 7.3 RED: extender `AgendaApiContractsTest` — `AppointmentOperations` declara únicamente `reschedule` (sin métodos de alta ni cancelación), y `AppointmentOperations`, `AppointmentView` y los tres eventos no usan tipos JPA/Spring Data/`agenda.infrastructure` y solo contienen UUID, `Instant`, `boolean` y enums; en `ModuleBoundariesRulesTest`, un fixture de `reservas` que usa `agenda.application.AppointmentService` falla nombrando origen y destino; verificar que falla
- [x] 7.4 GREEN: ajustar firmas si hace falta y verificar 7.3 verde

## 8. API REST, errores y seguridad (D10, D17, D19)

- [x] 8.1 RED: `ApiExceptionHandlerTest` — `23P01` sobre `appointment_no_overlap` → 409 `APPOINTMENT_SLOT_TAKEN` sin texto de PostgreSQL; `schedule_block_no_overlap` sigue `SCHEDULE_BLOCK_OVERLAP`; otra violación sigue `CONFLICT`; verificar que falla
- [x] 8.2 GREEN: extender `handleDataIntegrity` (nombre de constraint + SQLState) y verificar 8.1 verde
- [x] 8.3 RED: `agenda/infrastructure/AppointmentsEndpointsIT` (MockMvc con sesión DENTIST + CSRF, zonky) — todos los escenarios HTTP de `specs/appointments` (201 + `Location`, 400 `VALIDATION_FAILED`/`INVALID_RANGE`, 404, 409 incluido `PATIENT_INACTIVE`, 422, tramos contiguos aceptados y hueco rechazado, `endsAt` del cliente ignorado, listado por rango con turno que cruza `from`, sin PII del paciente en la respuesta, `DELETE` no borra); verificar que falla
- [x] 8.4 GREEN: DTOs `record` con Bean Validation y `AppointmentsController` con `@PreAuthorize("hasRole('DENTIST')")`; verificar 8.3 verde
- [x] 8.5 RED: `AppointmentsSecurityIT` — 401 anónimo (GET y POST), 403 rol distinto, 403 `CSRF_REQUIRED` en cada `POST`, `GET` sin CSRF 200; verificar que falla (o que pasa ya por deny-by-default y documentar cuál caso es RED real)
- [x] 8.6 GREEN: ajustar seguridad si hace falta y verificar 8.5 verde
- [x] 8.7 RED/GREEN: test de logs (appender de captura) — alta aceptada y rechazada con notas que contienen nombre/DNI/teléfono: el log solo tiene IDs y la respuesta no los incluye

## 9. Bloqueos serializados con turnos (D3, modifica C-09)

- [x] 9.1 RED: `ScheduleBlockServiceTest` — `create` y `update` toman `PracticeLock` antes de validar; `delete` no; `affectedAppointments` con proveedor real solo lista `CONFIRMED` que intersectan (cruce de borde listado, adyacente/cancelado/completado no); verificar que falla
- [x] 9.2 GREEN: inyectar `PracticeLock` en `ScheduleBlockService` y verificar 9.1 verde, más `ScheduleBlocksEndpointsIT` y `BlockedRangesIT` sin regresiones (comparar con el baseline 1.2)

## 10. Concurrencia real (D13)

- [x] 10.1 RED/GREEN: `agenda/application/AppointmentConcurrencyIT` (sin `@Transactional`, dos hilos + `CountDownLatch`, timeout acotado, limpieza en `@AfterEach`) — mismo slot: exactamente un éxito y un `APPOINTMENT_SLOT_TAKEN`, una sola fila vigente; slots parcialmente solapados: uno solo; slots adyacentes: ambos
- [x] 10.2 RED/GREEN: en el mismo IT, turno vs. bloqueo sobre rangos que se intersectan, repetido N veces: nunca queda un turno vigente dentro del bloqueo sin figurar en `affectedAppointments`; rangos disjuntos: ambos se confirman; tras un rechazo, nuevas operaciones no quedan bloqueadas
- [x] 10.3 RED/GREEN: `AppointmentEventsIT` con listener de test `@TransactionalEventListener(AFTER_COMMIT)` — recibe confirmado/cancelado/reprogramado tras commit y nada ante rechazo o rollback

## 11. Verificación integral

- [x] 11.1 Correr `./gradlew test integrationTest` completo y verificar todo verde contra el baseline 1.1 (sin tests deshabilitados ni debilitados); revisar cobertura JaCoCo del paquete `agenda` (objetivo ≥ 80 %)
- [x] 11.2 Recorrer la matriz de escenarios de los 7 delta specs y verificar que cada escenario (feliz, error de negocio y borde) tiene al menos un test que lo cubre; anotar la trazabilidad en el resumen del apply
- [x] 11.3 Ejecutar `openspec validate c-11-appointments-core --strict` y verificar que pasa

## 12. Lock por consultorio en toda mutación de turno (desvío aprobado por Lucas 2026-11-22, D3/D18)

- [x] 12.1 RED: en `AppointmentServiceTest`, con `InOrder`, `cancel`, `complete`, `noShow` y `reschedule` toman `PracticeLock.lock(practiceId)` antes de `findById`; `get` y `list` no toman el lock; `reschedule` con inicio pasado falla antes del lock; verificar que falla
- [x] 12.2 RED: en `AppointmentConcurrencyIT`, `reschedule` vs `cancel` del mismo turno y `complete` vs `reschedule`, forzando la ventana de forma determinista (el lock del consultorio retenido por el test mientras ambas operaciones cargan) y con threads + latch; verificar que falla
- [x] 12.3 GREEN: mover la carga del turno detrás del lock en las cuatro mutaciones (una sola carga, `practiceId` desde `InstallationPractice.id()`) y verificar 12.1 y 12.2 verdes
- [x] 12.4 Correr `./gradlew test integrationTest` completo y `openspec validate c-11-appointments-core --strict`

## 13. Ajustes post-verify (aprobados por Lucas 2026-11-22: W1 tope de 93 días y S4 nuevo inicio nulo)

- [x] 13.1 RED: en `AppointmentServiceTest`, `list` acepta exactamente 93 días y rechaza 93 días + 1 ns con `InvalidRangeException` (`INVALID_RANGE`) sin leer turnos; `from`/`to` nulos y `to <= from` siguen dando `INVALID_RANGE`; `reschedule` con `newStartsAt` o `id` nulos lanza `ValidationException` (campo `newStartsAt`/`appointmentId`) sin tomar el lock; verificar que falla
- [x] 13.2 RED: en `AppointmentOperationsAdapterTest` (y `ApiExceptionHandlerTest` si hace falta), `reschedule` con `newStartsAt` o `appointmentId` nulos en el puerto lanza `ValidationException` y no `NullPointerException`; la excepción de rango se traduce a 400 `INVALID_RANGE`; verificar que falla
- [x] 13.3 RED: en `AppointmentsEndpointsIT`, listado de 94 días → 400 `INVALID_RANGE` y de exactamente 93 días → 200; reprogramar sin `startsAt` → 400 `VALIDATION_FAILED`; verificar que falla
- [x] 13.4 GREEN: `shared.domain.InvalidRangeException`, constante `AppointmentService.MAX_LIST_RANGE` (93 días), validación explícita en `list`, validación de nulos en `reschedule` (servicio y puerto); verificar 13.1 a 13.3 verdes
- [x] 13.5 Correr `./gradlew test integrationTest --rerun-tasks` completo y `openspec validate c-11-appointments-core --strict`

## Workflow follow-up

- Verify por Gabriel (`openspec-verify`): matriz de escenarios feliz/error/borde contra las specs.
- Archive (`/opsx:archive`): actualizar `CHANGES.md` §[C-11] — estado, migración **V13** (no V8), decisiones D1–D22 resumidas — y anotar en §[C-18] que su migración pasa a la siguiente versión libre (V13 ya usada).
- Registrar en engram el cierre (`opsx/c-11-appointments-core/archive`).
- Nota para C-14 (D16): extender `agenda.api.AppointmentOperations` con el alta pública delegando en `AppointmentService.book`, reutilizando el lock por consultorio (D3), el orden de validaciones (D18), la traducción de errores (D10) y los eventos (D11); no crear otro camino de escritura sobre `appointment`.
