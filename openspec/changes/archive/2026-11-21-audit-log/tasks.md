# Tasks: C-03 `audit-log`

> Governance **CRÍTICO**: no empezar el apply hasta que el líder apruebe `proposal.md`, `design.md` y las specs.
> Strict TDD por comportamiento: RED (test nuevo, ejecutado y fallido) → GREEN (código mínimo) → TRIANGULATE (segundo caso distinto) → REFACTOR (tests verdes tras cada paso). No escribir código de producción antes de ver fallar su test.
> Comandos (desde `backend/`, terminal **no** elevada): `./gradlew test --tests '<Clase>'` (unitarios/ArchUnit), `./gradlew integrationTest --tests '<Clase>IT'` (zonky), `./gradlew check` (todo). Sin Docker (DD-10).
> Regla de migraciones: `V3__audit_log.sql` se escribe completa en la tarea 4.3. No ejecutar `bootRun` contra la base local hasta terminar el grupo 4; si se hizo, recrear la base (README de C-01) en vez de editar una migración aplicada (regla dura 3).
> Los grupos 1, 2 y 3 no dependen de BD y pueden ejecutarse en paralelo con el 4.

## 1. Aislamiento de `auditoria` (spec `module-boundaries`)

- [x] 1.1 RED: agregar fixtures `archfixture/violacion_auditoria/auditoria/{api,application,domain,infrastructure}/` (cada uno usa un tipo de un módulo de negocio distinto) y `archfixture/violacion_auditoria_transitiva/auditoria/application/` (dependencia indirecta vía `shared`), más casos en `ModuleBoundariesRulesTest` que exigen que las reglas nuevas fallen nombrando origen y destino; verificar que `./gradlew test --tests '*ModuleBoundariesRulesTest'` falla en esos casos
- [x] 1.2 GREEN: implementar `ModuleBoundaries.auditoriaIsolatedFromBusinessModules(root)` (dependencias directas y transitivas, diseño D5) y `auditEventsExposedOnlyThroughApi(root)`; verificar que los casos nuevos pasan y que `ProductionArchitectureTest` sigue verde
- [x] 1.3 TRIANGULATE: agregar un caso positivo `archfixture/ok_auditoria/auditoria/infrastructure/` que solo depende de `shared` y de su propio módulo, y reutilizar `archfixture/ok/auditoria/api/`; verificar que ambas reglas pasan con los fixtures positivos y fallan con los negativos (el test de la regla comprueba los dos lados)
- [x] 1.4 REFACTOR: extraer el recorrido transitivo a un helper dentro de `ModuleBoundaries` sin cambiar mensajes de error; verificar `./gradlew test --tests '*ModuleBoundaries*'` en verde

## 2. Evento de auditoría y sanitización de datos sensibles (spec `audit-log`)

- [x] 2.1 RED: `auditoria/api/AuditEventTest` (unitario) — construir un evento con `HC_READ`, usuario, paciente, entidad, IP y `userAgent`; exige inmutabilidad del record, que `practiceId` y `occurredAt` **no** sean campos públicos del evento, que un IP inválido se normalice a nulo y un `userAgent` mayor a 512 caracteres sea rechazado; verificar que falla porque la clase no existe
- [x] 2.2 GREEN: `auditoria.api.AuditAction`, `AuditEvent` (record con fábrica `of`, `toString()` redactado) según diseño D2; verificar que `AuditEventTest` y `ProductionArchitectureTest` pasan
- [x] 2.3 TRIANGULATE: segundo caso con evento anónimo (`BOOKING_TOKEN_USE`, sin usuario) y otro con `ip = null`; verificar que ambos pasan y que `toString()` no contiene el contenido de `details`
- [x] 2.4 RED: `auditoria/domain/SensitiveDataSanitizerTest` — `details` con clave `clinical_text` y texto de evolución, y con `dni`, `email`, `phone`, `full_name` → exige que no quede ninguna de esas claves ni valores; verificar que falla
- [x] 2.5 GREEN: `auditoria.domain.SensitiveDataSanitizer` con lista de permitidos, lista negra de claves y redacción por valor (diseño D3), sin Spring; verificar que el test pasa
- [x] 2.6 TRIANGULATE: casos anidados (profundidad 2), email/DNI/token bajo una clave permitida, más de N entradas y valor no escalar → todo redactado o descartado; verificar que pasan
- [x] 2.7 REFACTOR: unificar la normalización de claves y los patrones de redacción en una sola tabla inmutable; verificar `./gradlew test --tests '*SensitiveDataSanitizerTest' --tests '*AuditEventTest'` en verde con cobertura ≥ 80 % de ambas clases

## 3. Servicio de aplicación y puerto público (spec `audit-log`)

- [x] 3.1 RED: `auditoria/application/AuditLoggerServiceTest` (unitario con *fake* del repositorio, `Clock.fixed(2026-03-11T02:30:00Z)` y `InstallationPractice` de prueba) — al registrar un evento exige que el registro resultante tenga `practice_id` de la instalación y `occurred_at` exactamente ese instante UTC, y que `details` llegue sanitizado; verificar que falla
- [x] 3.2 GREEN: `auditoria.application.AuditLoggerService` (inyección por constructor) y el puerto `auditoria.api.AuditLogger`; verificar que el test pasa y que `ProductionArchitectureTest` sigue verde (sin `api`/`infrastructure` en `application`)
- [x] 3.3 TRIANGULATE: segundo caso con otro `practiceId`/reloj y un evento con `user_id` nulo; verificar que pasa
- [x] 3.4 RED: caso de fallo — el *fake* del repositorio lanza una excepción con un email en el mensaje; exige que se propague un error con código `AUDIT_WRITE_FAILED`, sin el email ni el mensaje original, y que el caso de uso no haya entregado el dato clínico; verificar que falla
- [x] 3.5 GREEN + REFACTOR: endurecer `AuditLoggerService` (fail-closed, diseño D4) y su log de error con `errorId` y solo nombre de clase; verificar `./gradlew test --tests '*AuditLoggerServiceTest'` en verde y que la salida capturada (`OutputCaptureExtension`) no contiene el email

## 4. Esquema V3 y privilegios de la base (specs `database-migrations`, `audit-log`)

- [x] 4.1 RED: `database/AuditLogSchemaIT` — tras arrancar exige `V3` exitosa en `flyway_schema_history` y la tabla `audit_log` con todas las columnas y tipos del diseño D1 (`bigint`, `uuid`, `text`, `inet`, `timestamptz`, `jsonb`), índices, `CHECK` de acciones y `CHECK` de `details`; verificar con `./gradlew integrationTest --tests '*AuditLogSchemaIT'` que falla porque `V3` no existe
- [x] 4.2 RED: `database/AuditLogPrivilegesIT` (SQL directo con `EmbeddedPostgresSupport.Database.appConnection()`, cada caso en transacción revertida) — `INSERT` y `SELECT` exitosos; `UPDATE`, `DELETE` y `TRUNCATE` rechazados por permisos; la fila original sigue intacta; además, inserción con acción desconocida, `details` no objeto y `details` con clave `email` rechazadas por `CHECK`; verificar que falla
- [x] 4.3 GREEN: escribir `backend/src/main/resources/db/migration/V3__audit_log.sql` completo según diseño D1 (columnas, `CHECK`, índices, `REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM ${app_role}` y cabecera de comment); verificar que ambos IT y los IT de C-01/C-02 (`MigrationsIT`, `RolePrivilegesIT`, `MigrationValidationIT`, `PracticeSchemaIT`) pasan con `./gradlew integrationTest`
- [x] 4.4 TRIANGULATE: nuevo caso en `AuditLogSchemaIT` que verifica que `patient_id` es nullable y **no** tiene FK (la tabla `patient` aún no existe, decisión D1) y que los privilegios de `practice`/`app_user` no cambiaron; verificar que pasa
- [x] 4.5 REFACTOR: revisar el DDL con el skill `postgresql-table-design` (tipos, FK indexada, `timestamptz`, `jsonb`) y ajustar solo si no rompe los IT; verificar `./gradlew integrationTest --tests '*AuditLog*IT'` en verde

## 5. Persistencia del evento y no filtración (spec `audit-log`)

- [x] 5.1 RED: `auditoria/infrastructure/AuditLogPersistenceIT` (contexto completo sobre zonky, rol `app`) — registrar un evento válido mediante `AuditLogger` y releerlo como `migrator`: comprueba `action`, `user_id`, `patient_id`, `entity_type`, `entity_id`, `ip` como `inet`, `occurred_at` UTC y `details` sanitizado; segundo caso con `user_id` nulo (`BOOKING_TOKEN_USE`); verificar que falla porque el adaptador no existe
- [x] 5.2 GREEN: `auditoria.infrastructure.JdbcAuditLogger` con `JdbcTemplate` y `INSERT ... RETURNING id` (diseño D4), parámetros ligados y `PGobject` para `inet`/`jsonb`; verificar que `AuditLogPersistenceIT` pasa y que `ProductionArchitectureTest` sigue verde
- [x] 5.3 TRIANGULATE: caso de fallo de inserción (restricción de base) — exige que `log(...)` propague `AUDIT_WRITE_FAILED` y no deje fila; verificar que pasa
- [x] 5.4 RED: `auditoria/infrastructure/AuditLogLeakageIT` con `OutputCaptureExtension` — pasar DNI `30123456`, email `dr@ejemplo.com`, teléfono, nombre, texto clínico y un token Base64URL en `details` y en la excepción de un repositorio *fake*; exige que ningún valor aparezca en la fila ni en la salida capturada; verificar que falla
- [x] 5.5 GREEN: conectar el sanitizador con el adaptador y el log de errores redactado; verificar que `AuditLogLeakageIT` pasa
- [x] 5.6 REFACTOR: revisar que no haya `@Autowired` en campos, ni entidades JPA para `audit_log`, ni `Instant.now()` (reglas duras 1, 2 y 8); verificar `./gradlew test` y `./gradlew integrationTest --tests '*AuditLog*IT'` en verde

## 6. Verificación integral y cierre de planificación

- [x] 6.1 Ejecutar `./gradlew check` desde `backend/` (unitarios, ArchUnit, IT con zonky y JaCoCo) y verificar todo en verde; revisar en `build/reports/jacoco` que las clases nuevas de `auditoria` tienen cobertura de líneas ≥ 80 %
- [x] 6.2 Revisión de reglas duras sobre el diff: sin `@Autowired` en campos (1), sin entidades expuestas en API (2), sin edición de `V1`/`V2` (3), sin nombres/DNI/email/teléfono/datos clínicos en logs (6), `timestamptz`/`Instant` y UTC (8), sin dependencias nuevas (12), sin commits (14), sin credenciales versionadas (15); verificar con `git diff` y `grep -rn "30123456\\|dr@ejemplo.com" backend/src/main`
- [x] 6.3 Verificar con `openspec validate audit-log` que las tres specs (nueva y dos delta) y los artefactos del change son coherentes; corregir cualquier error de formato antes de avisar al líder

## Workflow follow-up

- Verificar el change con `openspec-verify` contra specs, design y tasks.
- Commit con conventional commits `feat(C-03): ...` **solo** cuando el usuario lo pida (regla dura 14).
- Archivar con `/opsx:archive audit-log` tras la verificación; C-04, C-10, C-16 y C-23 deben consumir `AuditLogger` en sus propios changes, y C-10 debe agregar la FK de `patient_id`.
