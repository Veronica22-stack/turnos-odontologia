# Tasks: C-09 `schedule-blocks`

Convención de ejecución (Strict TDD): **cada grupo** se trabaja en el orden `safety net → RED → GREEN → TRIANGULATE → REFACTOR`. La triangulación exige como mínimo un caso feliz y un caso de borde por comportamiento. Los tests se escriben **antes** que el código de producción. La tabla de evidencia TDD se completa durante el apply; este archivo es solo el plan.

## 1. Migración V8 y esquema de bloqueos

- [x] 1.1 Safety net: ejecutar `./gradlew integrationTest --tests MigrationsIT` y confirmar que la suite actual (V1–V7) está verde antes de tocar nada; verificar con el comando en verde
- [x] 1.2 RED: agregar `ScheduleBlockSchemaIT` con aserciones fallidas sobre `V8__schedule_block.sql` (tabla, columnas `starts_at`/`ends_at`/`period`, `CHECK`, `EXCLUDE USING gist`, índice `(practice_id, starts_at)`, `REVOKE TRUNCATE` y DML permitido); verificar que el test falla por migración inexistente
- [x] 1.3 GREEN: crear `backend/src/main/resources/db/migration/V8__schedule_block.sql` sin modificar V1–V7, con columna generada `period tstzrange` y límites `[)`; verificar que `ScheduleBlockSchemaIT` y `MigrationsIT` pasan
- [x] 1.4 TRIANGULATE: ampliar `ScheduleBlockSchemaIT` con, al menos, rango `ends_at <= starts_at` rechazado, solapamiento parcial e idéntico rechazados, bloqueos adyacentes y de otro `practice_id` aceptados, consulta del valor de `period`, `TRUNCATE` denegado y `SELECT/INSERT/UPDATE/DELETE` permitidos para `${app_role}`, y aplicación desde una base migrada hasta V7; verificar todos los casos en verde
- [x] 1.5 REFACTOR: revisar nombres de constraints y comentarios de la migración (documentando por qué V8 y no V6), sin cambiar su checksum una vez aplicada; verificar con `./gradlew integrationTest --tests '*ScheduleBlockSchemaIT'` en verde

## 2. Dominio de agenda: value object y entidad

- [x] 2.1 Safety net: ejecutar `./gradlew test --tests 'ar.turnosodontologia.consultorio.agenda.*'` y confirmar tests unitarios actuales en verde
- [x] 2.2 RED: escribir `TimeIntervalTest` con casos que fallen para rango invertido, duración nula, solapamiento parcial, intervalo contenido e intervalo adyacente; verificar que la suite falla por clase inexistente
- [x] 2.3 GREEN: implementar `TimeInterval` en `agenda.domain` con validación `end > start` e `intersects` semiabierta (`a.start < b.end && b.start < a.end`); verificar `TimeIntervalTest` en verde
- [x] 2.4 TRIANGULATE: escribir `ScheduleBlockTest` con creación válida (UUID v7, `practiceId`, motivo, rango), motivo vacío/solo espacios/más de `500` caracteres, rango inválido y marcas de tiempo tomadas de un `Clock` fijo; verificar al menos dos casos por comportamiento en verde
- [x] 2.5 REFACTOR: extraer mensajes de validación y constantes repetidas sin cambiar el comportamiento; verificar unitarios de `agenda.domain` en verde

## 3. Contratos públicos y adaptador de rangos bloqueados

- [x] 3.1 Safety net: ejecutar `./gradlew test --tests ModuleBoundariesRulesTest` y confirmar la verificación de arquitectura actual en verde
- [x] 3.2 RED: escribir `BlockedRangesTest` (mock) y `AffectedAppointmentsContractTest` con fallos esperados para solapamiento parcial, rango contenido, adyacencia, sin bloqueos, intervalo inválido, sin proveedor de turnos y con proveedor simulado; verificar que fallan por contratos inexistentes
- [x] 3.3 GREEN: declarar en `agenda.api` los contratos `BlockedRanges`/`AffectedAppointments` y sus `record` con UUID e `Instant`, e implementar el adaptador en `agenda.infrastructure` con la consulta `starts_at < :to AND ends_at > :from`; verificar tests unitarios en verde
- [x] 3.4 TRIANGULATE: agregar `BlockedRangesIT` con PostgreSQL embebido zonky y datos sembrados (bloqueo que intersecta, bloqueo contenido, bloqueo adyacente, consultorio distinto y sin bloqueos); verificar todos los casos en verde
- [x] 3.5 REFACTOR: comprobar que ninguna firma de `agenda.api` expone JPA, `Pageable` ni tipos de `infrastructure`, y ejecutar `./gradlew test --tests ModuleBoundariesRulesTest` en verde

## 4. Casos de uso de bloqueos

- [x] 4.1 Safety net: ejecutar `./gradlew test --tests 'ar.turnosodontologia.consultorio.agenda.application.*'` y confirmar casos de uso existentes en verde. **Desvío:** no había tests unitarios en `agenda.application` (`No tests found`); se usó `--tests 'ar.turnosodontologia.consultorio.agenda.*'` en verde como safety net equivalente
- [x] 4.2 RED: escribir `ScheduleBlockServiceTest` con mocks que fallen para crear bloqueo válido, listar con y sin filtro, obtener detalle, editar, eliminar, detectar solapamiento (`ConflictException`), no encontrar bloqueo (`NotFoundException`) y devolver `affectedAppointments` vacío sin proveedor; verificar que falla por servicio inexistente
- [x] 4.3 GREEN: implementar `ScheduleBlockService` en `agenda.application` con inyección por constructor de repositorio, `InstallationPractice`, `Clock` y proveedor opcional de turnos, y transacciones de lectura/escritura; verificar `ScheduleBlockServiceTest` en verde. **Desvío técnico:** el diseño D4 proponía `Optional<agenda.api.AffectedAppointments>`, pero `ModuleBoundaries.applicationIndependent` prohíbe que `application` dependa de la `api` de su propio módulo; se usa `Optional<agenda.domain.AffectedAppointmentsProvider>` (mismo contrato JDK) y `agenda.api.AffectedAppointments` sigue publicado para otros módulos
- [x] 4.4 TRIANGULATE: sumar casos de borde al servicio: filtro con `from >= to`, rango invertido, edición que no se confunda consigo misma, detalle/eliminación de bloqueo ajeno al consultorio y proveedor de turnos simulado que devuelve afectados sin modificarlos; verificar al menos dos casos por comportamiento en verde
- [x] 4.5 REFACTOR: simplificar el mapeo entidad→resultado y eliminar duplicación de validación de rango sin tocar la API; verificar unitarios de `agenda.application` en verde

## 5. API REST, DTOs, seguridad y errores

- [x] 5.1 Safety net: ejecutar `./gradlew integrationTest --tests 'ar.turnosodontologia.consultorio.agenda.*IT'` y confirmar endpoints actuales en verde
- [x] 5.2 RED: escribir `ScheduleBlocksEndpointsIT` (MockMvc + zonky) con casos que fallen para `GET` (200, orden y filtro), `POST` (201 + `Location`), `GET /{id}` (200), `PUT /{id}` (200), `DELETE /{id}` (204), `400 INVALID_RANGE`, `400 VALIDATION_FAILED`, `404 SCHEDULE_BLOCK_NOT_FOUND` y `409 SCHEDULE_BLOCK_OVERLAP`; verificar que falla por endpoints inexistentes
- [x] 5.3 GREEN: crear los DTOs `record` con Bean Validation en `agenda.api`, el `ScheduleBlocksController` en `agenda.infrastructure` con `@PreAuthorize("hasRole('DENTIST')")` y la traducción de excepciones al formato `ProblemDetail` existente; verificar `ScheduleBlocksEndpointsIT` en verde
- [x] 5.4 TRIANGULATE: ampliar `ScheduleBlocksEndpointsIT` con: 401 anónimo en `GET`/`POST`, 403 con rol insuficiente, 403 `CSRF_REQUIRED` en `POST`/`PUT`/`DELETE` sin `X-XSRF-TOKEN`, `GET` sin CSRF en 200, solapamiento por carrera que devuelve 409 sin texto de PostgreSQL, y motivo con datos personales ausente en respuesta y logs; verificar todos los casos en verde. **Aclaración:** la respuesta de éxito incluye el motivo (dato del bloque); la prohibición aplica a respuestas de error y logs, que es lo que se verifica (el test inicial se corrigió en ese sentido)
- [x] 5.5 REFACTOR: revisar que las entidades JPA no se exponen en la API y que los DTOs no incluyen `createdAt`/`updatedAt` editables; verificar `ScheduleBlocksEndpointsIT` y `./gradlew test --tests '*Validation*'` en verde

## 6. Verificación integral y cierre

- [x] 6.1 Ejecutar `./gradlew test --tests ModuleBoundariesRulesTest` con los nuevos escenarios de contratos en `agenda.api` y grafo `agenda` → sin `reservas`; verificar en verde. **Evidencia:** `BUILD SUCCESSFUL`; 27 tests / 0 failures / 0 errors (`TEST-ar.turnosodontologia.consultorio.architecture.ModuleBoundariesRulesTest.xml`)
- [x] 6.2 Ejecutar `./gradlew check` (unitarios, `*IT` con zonky, ArchUnit y cobertura si el proyecto la habilita); verificar suite completa en verde. **Evidencia:** `.\gradlew check jacocoTestReport --console=plain` → `BUILD SUCCESSFUL`; 270 tests unitarios + 150 integration tests, 0 failures/errors/skips
- [x] 6.3 Ejecutar `openspec validate c-09-schedule-blocks --strict` y `openspec status --change c-09-schedule-blocks --json`; verificar validación estricta sin errores y los cuatro artefactos en `done`. **Evidencia:** `Change 'c-09-schedule-blocks' is valid`; `isPlanningComplete: true` y `proposal`, `specs`, `design`, `tasks` en `done`
- [x] 6.4 Registrar en el reporte del apply la tabla de evidencia TDD (safety net, RED, GREEN, TRIANGULATE y REFACTOR por grupo) y el follow-up pendiente de `CHANGES.md` (V6 → V8 en C-09 y renumeración de la V8 de C-11 a V9+); verificar que el reporte incluye ambos puntos sin haber editado `CHANGES.md` en este change. **Evidencia:** sección «Reporte del apply» abajo; `CHANGES.md` no se modificó

---

## Reporte del apply

### Evidencia TDD por grupo

| Grupo | Safety net | RED | GREEN | TRIANGULATE | REFACTOR |
|-------|------------|-----|-------|-------------|----------|
| 1 — Migración V8 y esquema | `integrationTest --tests MigrationsIT` en verde con V1–V7 | `ScheduleBlockSchemaIT` falló por migración `V8` inexistente | `V8__schedule_block.sql` con `period tstzrange GENERATED ALWAYS ... '[)'` → `ScheduleBlockSchemaIT` + `MigrationsIT` verdes | 9 tests: rango invertido, solapamiento parcial/idéntico rechazados, adyacentes y otro `practice_id` aceptados, valor de `period`, `TRUNCATE` denegado, DML permitido, migración desde V7 | Nombres de constraints y comentario «por qué V8 y no V6» revisados; checksum no tocado; `integrationTest --tests '*ScheduleBlockSchemaIT'` en verde |
| 2 — Dominio | `test --tests 'ar.turnosodontologia.consultorio.agenda.*'` en verde | `TimeIntervalTest` falló por clase inexistente | `TimeInterval` con `end > start` e `intersects` semiabierta → 7 tests verdes | `ScheduleBlockTest` (12 tests): UUID v7, `practiceId`, motivo válido/vacío/solo espacios/>500, rango inválido, `Clock` fijo | Mensajes y constantes extraídos; unitarios de `agenda.domain` en verde |
| 3 — Contratos y adaptador | `test --tests ModuleBoundariesRulesTest` en verde | `BlockedRangesTest` + `AffectedAppointmentsContractTest` fallaron por contratos inexistentes | Contratos en `agenda.api`, `BlockedRanges`, `AffectedAppointments`, `BlockedInterval`, `AffectedAppointment` y adaptador con `starts_at < :to AND ends_at > :from` → verdes | `BlockedRangesIT` (6 tests): intersección, contenido, adyacente, otro consultorio, sin bloqueos e intervalo inválido | `AgendaApiContractsTest` (3 tests) + `ModuleBoundariesRulesTest` (27 tests) sin JPA/`Pageable`/`infrastructure` en firmas de `agenda.api` |
| 4 — Casos de uso | Sin tests previos en `agenda.application` (`No tests found`); safety net equivalente `test --tests 'ar.turnosodontologia.consultorio.agenda.*'` en verde | `ScheduleBlockServiceTest` falló por servicio inexistente | `ScheduleBlockService` con inyección por constructor y `@Transactional`; `Optional<agenda.domain.AffectedAppointmentsProvider>` por regla arquitectural → 15 tests verdes | Casos de borde: `from >= to`, rango invertido, edición propia/ajena, detalle/eliminación de otro consultorio, proveedor simulado sin mutar afectados | Mapeo entidad→resultado simplificado y validación de rango centralizada; unitarios verdes |
| 5 — API REST y errores | `integrationTest --tests 'ar.turnosodontologia.consultorio.agenda.*IT'` en verde | `ScheduleBlocksEndpointsIT` falló por endpoints inexistentes | DTOs `record` + `ScheduleBlocksController` con `@PreAuthorize("hasRole('DENTIST')")` y `ProblemDetail` → 14 tests verdes | 401/403/`CSRF_REQUIRED`, `GET` sin CSRF, 409 sin texto de PostgreSQL, motivo ausente en respuestas de error y logs | DTOs sin `createdAt`/`updatedAt` editables, sin entidades JPA expuestas; `ScheduleBlocksEndpointsIT` + `test --tests '*Validation*'` en verde |

Todos los grupos se ejecutaron en orden `safety net → RED → GREEN → TRIANGULATE → REFACTOR`, con al menos un caso feliz y un caso de borde por comportamiento. El motivo del bloqueo aparece solo en la respuesta de éxito; no aparece en respuestas de error ni en logs.

### Verificación final

| Comando | Resultado |
|---------|-----------|
| `.\gradlew test --tests ModuleBoundariesRulesTest --console=plain` | `BUILD SUCCESSFUL` — 27 tests, 0 failures/errors |
| `.\gradlew check jacocoTestReport --console=plain` | `BUILD SUCCESSFUL` — 270 unitarios + 150 integración, 0 failures/errors/skips |
| JaCoCo (`backend/build/reports/jacoco/test/jacocoTestReport.xml`) | Clases ejecutables nuevas ≥80%: `ScheduleBlockService` 100/100/100, `ScheduleBlocksController` 92,3/100/100 (instrucción/rama/línea), `ScheduleBlock` 95,5/87,5/97,2, `TimeInterval` 90/91,7/88,9; records y adaptador 100% |
| `openspec validate c-09-schedule-blocks --strict` | `Change 'c-09-schedule-blocks' is valid` |
| `openspec status --change c-09-schedule-blocks --json` | `isPlanningComplete: true`; artefactos `proposal`, `specs`, `design`, `tasks` en `done` |
| `openspec instructions apply --change c-09-schedule-blocks --json` | `29/29` tareas completas, `remaining: 0` |

### Follow-up pendiente (fuera de este change)

- `CHANGES.md` todavía describe C-09 con la migración `V6`; debe actualizarse a `V8__schedule_block.sql`.
- `CHANGES.md` todavía anuncia `V8` para C-11; debe renumerarse a `V9+` para evitar colisión con la migración ya aplicada.
- **No se editó `CHANGES.md` durante este apply**; el follow-up queda registrado aquí para el change de documentación/roadmap correspondiente.
