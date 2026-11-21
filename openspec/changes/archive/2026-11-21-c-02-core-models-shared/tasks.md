# Tasks: C-02 `core-models-shared`

> Governance **CRÍTICO**: no empezar el apply hasta que el líder apruebe `proposal.md`, `design.md` y las specs.
> Strict TDD por tarea: RED (test que falla, ejecutado) → GREEN (mínimo código) → TRIANGULATE (segundo caso distinto) → REFACTOR (tests verdes tras cada paso). Las tareas "RED" y "GREEN" van en orden; no escribir código de producción antes de ver fallar su test.
> Comandos (desde `backend/`, en terminal **no** elevada): `./gradlew test --tests '<Clase>'` (unitario puntual), `./gradlew test`, `./gradlew integrationTest --tests '<Clase>IT'`, `./gradlew check`. Sin Docker (DD-10): los `*IT` usan PostgreSQL embebido zonky vía `AbstractIntegrationTest`.
> Regla de migraciones: `V2__core_models.sql` se escribe completa en la tarea 4.3. No ejecutar `bootRun` contra la base local hasta terminar el grupo 4; si se hizo, recrear la base local (C-01 README) en vez de editar una migración aplicada (regla dura 3).
> Implementan Lucas y Elian: los grupos 2 y 3 no dependen de BD y se pueden hacer en paralelo con el 4.

## 1. Dependencias y configuración (sin comportamiento)

- [x] 1.1 Agregar a `backend/build.gradle.kts` `implementation("org.springframework.boot:spring-boot-starter-validation")` y `implementation("org.bouncycastle:bcprov-jdk18on")` (si el BOM de Boot no gestiona su versión, fijar la última estable de Maven Central y anotarla en el resumen del apply); verificar con `./gradlew dependencies --configuration runtimeClasspath` que ambas resuelven y que `./gradlew test` sigue verde
- [x] 1.2 Confirmar con context7 / fuentes oficiales, para las versiones resueltas: (a) el estilo de `@UuidGenerator` que produce UUID versión 7 en Hibernate 7, (b) el artefacto y paquete de `@WebMvcTest` en Spring Boot 4; registrar ambas respuestas en el resumen del apply (si contradicen `design.md` D1/D10, detenerse y avisar al líder)
- [x] 1.3 Verificar que `.env.example` contiene `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD` con marcador (`change-me`, nunca un valor real) y actualizar su comentario para indicar que las consume C-02 y que solo se requieren en el primer arranque; verificar que `ConfigurationHygieneTest` pasa
  - Completada a mano por Lucas (2026-11-21): el archivo esta bloqueado por permisos para los agentes. Comentario actualizado; `ConfigurationHygieneTest` pasa despues del cambio.

## 2. Fronteras de módulos: Spring prohibido en `domain` (spec `module-boundaries`)

- [x] 2.1 RED: agregar fixtures `archfixture/violacion_dominio_spring/pacientes/domain/` (clase de dominio que usa un tipo de `org.springframework.stereotype` o `org.springframework.transaction`) y `archfixture/ok_dominio_jpa/pacientes/domain/` (entidad con `@jakarta.persistence.Entity` y `@org.hibernate.annotations.UuidGenerator`), y casos en `ModuleBoundariesRulesTest`: la regla de dominio falla con la primera nombrando origen y destino, y pasa con la segunda; verificar que `./gradlew test --tests '*ModuleBoundariesRulesTest'` falla en el caso Spring
- [x] 2.2 GREEN: en `ModuleBoundaries` reemplazar los prohibidos de `domain` por `org.springframework.` + `java.sql.` (design D5); verificar que `ModuleBoundariesRulesTest` (casos viejos y nuevos) y `ProductionArchitectureTest` pasan

## 3. Reloj y conversiones de zona horaria (spec `time-and-zones`)

- [x] 3.1 RED: `shared/domain/ZoneConversionsTest` — `toLocal`: `2026-03-10T12:00:00Z` → `2026-03-10T09:00` y `2026-03-11T02:00:00Z` → `2026-03-10T23:00` en `America/Argentina/Buenos_Aires`; verificar que falla (clase inexistente)
- [x] 3.2 GREEN: `shared.domain.ZoneConversions.toLocal(Instant, ZoneId)`; verificar que `ZoneConversionsTest` pasa
- [x] 3.3 RED: casos de `toInstant`: `2026-03-10T09:00` BA → `2026-03-10T12:00:00Z`; hueco `2026-03-08T02:30` en `America/New_York` → `NonexistentLocalTimeException`; ambigua `2026-11-01T01:30` NY → `2026-11-01T05:30:00Z`; verificar que fallan
- [x] 3.4 GREEN: `toInstant(LocalDateTime, ZoneId)` y `shared.domain.NonexistentLocalTimeException` (design D9); verificar que los casos pasan
- [x] 3.5 RED: caso de `dayBounds(2026-03-10, BA)` = `[2026-03-10T03:00:00Z, 2026-03-11T03:00:00Z)` y un segundo caso de día con cambio de hora en NY (23 h); verificar que fallan
- [x] 3.6 GREEN + REFACTOR: `dayBounds` devolviendo un record `InstantRange(start, end)` (semiabierto, valida `start < end`); verificar que `ZoneConversionsTest` completo pasa
- [x] 3.7 RED→GREEN: `shared/infrastructure/ClockConfigurationTest` (unitario, sin contexto) que exige que el `Clock` publicado esté en `ZoneOffset.UTC`; luego crear `ClockConfiguration` con `@Bean Clock clock()`; verificar que el test pasa

## 4. Esquema V2 (specs `practice-installation`, `user-accounts`, `database-migrations`)

- [x] 4.1 RED: `database/PracticeSchemaIT` — tras arrancar: exactamente una fila en `practice` con `timezone = 'America/Argentina/Buenos_Aires'` y contactos `NULL`; `V1` y `V2` exitosas en `flyway_schema_history`; como `migrator`, un segundo `INSERT` en `practice` es rechazado (unicidad); `UPDATE` con `name = ''` o `timezone = ''` rechazado; como `app`, `INSERT` y `DELETE` en `practice` rechazados por permisos y `SELECT`/`UPDATE` permitidos (revertir el `UPDATE` en la transacción); verificar con `./gradlew integrationTest --tests '*PracticeSchemaIT'` que falla porque `V2` no existe
- [x] 4.2 RED: `database/AppUserSchemaIT` (SQL directo con el rol `app`, cada caso en su transacción revertida) — inserta una fila válida; rechaza `practice_id` inexistente (FK), `role = 'SECRETARY'`, email con mayúsculas o espacios (`CHECK`), email duplicado con otras mayúsculas (índice `lower(email)`), `failed_attempts = -1`; `DELETE` rechazado por permisos y `UPDATE` permitido; verificar que falla
- [x] 4.3 GREEN: escribir `backend/src/main/resources/db/migration/V2__core_models.sql` completa según design D6/D7 (tablas, `CHECK`, índice de fila única, índice único `lower(email)`, índice de la FK, seed de `practice` con el UUID literal, `REVOKE` con `${app_role}`) y un comentario de cabecera; verificar que `PracticeSchemaIT`, `AppUserSchemaIT` y los IT de C-01 (`MigrationsIT`, `RolePrivilegesIT`, etc.) pasan con `./gradlew integrationTest`

## 5. Consultorio en la aplicación (spec `practice-installation`, `time-and-zones`)

- [x] 5.1 RED: `shared/application/InstallationPracticeTest` (unitario, `PracticeRepository` *fake*): con una fila devuelve su `id` y `ZoneId`; sin filas lanza un error que dice que falta el consultorio; con zona `Mars/Olympus` lanza un error de zona inválida sin otros datos; con dos filas lanza error; la segunda llamada no vuelve a consultar el repositorio (caché); verificar que falla
- [x] 5.2 GREEN: `shared.domain.Practice` (entidad JPA, sin Spring, `@UuidGenerator` v7, getters; sin setters públicos innecesarios), `shared.domain.PracticeRepository` (`List<Practice> findAll()`), `shared.application.InstallationPractice`; verificar que `InstallationPracticeTest` y `ProductionArchitectureTest` pasan
- [x] 5.3 RED→GREEN: `shared/infrastructure/PracticeRepositoryIT` — el contexto arranca con `ddl-auto=validate` y `InstallationPractice.id()` devuelve el UUID sembrado por V2 con zona BA; implementar `SpringDataPracticeRepository extends JpaRepository<Practice, UUID>, PracticeRepository` (ajustar el mapeo `text` si `validate` falla, design §Risks); verificar que pasa
- [x] 5.4 RED→GREEN: `shared/application/PracticeTimeTest` con `Clock.fixed(2026-03-11T02:30:00Z)` y BA: `now()` = ese instante, `today()` = `2026-03-10`; segundo caso con `2026-03-10T12:00:00Z` → `today()` = `2026-03-10`; implementar `PracticeTime`; verificar que pasa

## 6. Cuentas de usuario y hash de contraseñas (spec `user-accounts`)

- [x] 6.1 RED: `auth/domain/AppUserTest` (unitario) — `AppUser.newDentist(practiceId, "  Dr.Perez@Ejemplo.COM ", hash, Clock.fixed(2026-03-10T12:00:00Z))` guarda `dr.perez@ejemplo.com`, rol `DENTIST`, `failedAttempts = 0`, `lockedUntil`/`lastLoginAt` vacíos, `createdAt` = reloj, `mustChangePassword = true`; segundo caso con otro email/reloj; `toString()` no contiene el hash; verificar que falla
- [x] 6.2 GREEN: `auth.domain.Role { DENTIST }` y `auth.domain.AppUser` (entidad JPA, `@Enumerated(STRING)`, `@UuidGenerator` v7, `Instant` para timestamps, constructor protegido para JPA, fábrica `newDentist`); verificar que `AppUserTest` y `ProductionArchitectureTest` pasan
- [x] 6.3 RED: `auth/infrastructure/AppUserRepositoryIT` — guardar un `AppUser` y releerlo: id con `version() == 7`, `createdAt` exactamente igual, email normalizado; `existsByPracticeIdAndRole(practiceId, DENTIST)` falso antes y verdadero después; guardar otro con el mismo email en otra capitalización lanza `DataIntegrityViolationException`; limpiar con el rol `migrator` (el rol `app` no puede borrar); verificar que falla
- [x] 6.4 GREEN: `auth.domain.AppUserRepository` (puerto: `save`, `existsByPracticeIdAndRole`, `findByEmail`) y `auth.infrastructure.SpringDataAppUserRepository`; verificar que `AppUserRepositoryIT` pasa
- [x] 6.5 RED: `auth/infrastructure/Argon2PasswordHasherTest` — dos hashes de la misma contraseña empiezan con `$argon2id$`, contienen `m=19456,t=2,p=1`, son distintos y no contienen la contraseña; `matches` verdadero con la original y falso con otra; verificar que falla
- [x] 6.6 GREEN: `auth.domain.PasswordHasher` (puerto), `auth.infrastructure.Argon2PasswordHasher` y `PasswordConfiguration` (bean `PasswordEncoder` Argon2 con parámetros de design D2, reutilizado por el hasher); verificar que el test pasa

## 7. Bootstrap del DENTIST (specs `dentist-bootstrap`, `runtime-configuration`)

- [x] 7.1 RED: `auth/domain/BootstrapCredentialsTest` — acepta email válido y contraseña de 12 caracteres; rechaza contraseña de 8 (mensaje menciona `BOOTSTRAP_ADMIN_PASSWORD`, no la contraseña), email `no-es-un-email` (menciona `BOOTSTRAP_ADMIN_EMAIL`, no el valor) y email de más de 254; `toString()` no contiene email ni contraseña; verificar que falla
- [x] 7.2 GREEN: `auth.domain.BootstrapCredentials` (record con validación y `toString` redactado); verificar que pasa
- [x] 7.3 RED: `auth/application/BootstrapDentistTest` (unitario con *fakes* de `AppUserRepository`, `PasswordHasher`, `InstallationPractice` y proveedor de variables): sin DENTIST y variables válidas → `CREATED` con una cuenta `mustChangePassword = true` y hash del *fake*; con DENTIST existente → `SKIPPED` sin leer variables ni guardar; sin DENTIST y falta `BOOTSTRAP_ADMIN_PASSWORD` (ausente o en blanco) → error que la nombra sin mostrar el email; contraseña corta → error y nada guardado; verificar que falla
- [x] 7.4 GREEN: `auth.application.BootstrapDentist` (transaccional, design D11); verificar que `BootstrapDentistTest` y `ProductionArchitectureTest` pasan
- [x] 7.5 Preparar el soporte de IT: `AbstractIntegrationTest`/`EmbeddedPostgresSupport` publican `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD` generados en el test (no versionados, contraseña aleatoria ≥ 12) y exponen esos valores a los tests; verificar que `./gradlew integrationTest` sigue verde
- [x] 7.6 RED: `auth/infrastructure/BootstrapDentistRunnerIT` (con `OutputCaptureExtension`) — tras el arranque existe exactamente una cuenta `DENTIST` con el email de prueba normalizado, `must_change_password = true` y hash `$argon2id$`; ejecutar el runner otra vez (mismas variables y luego otra contraseña) deja una sola cuenta con el hash sin cambios; la salida capturada contiene el id de la cuenta y no contiene ni el email ni la contraseña de prueba; verificar que falla
- [x] 7.7 GREEN: `auth.infrastructure.BootstrapDentistRunner` (`ApplicationRunner`, lee las variables con `Environment.getProperty`, sin mapearlas en `application.yml`; logs de design D11); verificar que `BootstrapDentistRunnerIT` y todos los IT pasan
- [x] 7.8 Documentar en `README.md` (sección de primer arranque): definir `BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD` en `.env` antes del primer `bootRun`, mínimo 12 caracteres, que la contraseña se debe cambiar en el primer login (C-04) y que conviene quitar las variables después; verificar que los pasos del README coinciden con `.env.example` y con `ConfigurationHygieneTest`
  - Nota (7.5/D11): las credenciales de bootstrap de los IT se inyectan desde `EmbeddedPostgresSupport.Database.properties()` (aleatorias por JVM, no versionadas)

## 8. Manejo global de errores (spec `error-responses`)

- [x] 8.1 RED: `shared/api/ApiExceptionHandlerTest` (MockMvc standalone, sin `@WebMvcTest` por no agregar `spring-boot-webmvc-test` — design D10 — + `ProblemProbeController` de test en `ar.turnosodontologia.probe`): `NotFoundException("PATIENT_NOT_FOUND")` → 404, `application/problem+json`, `title` genérico, `code = PATIENT_NOT_FOUND`; `ConflictException` → 409; `BusinessRuleException` → 422; verificar que falla
- [x] 8.2 GREEN: `shared.domain.DomainException` (solo `code`) + `NotFoundException`, `ConflictException`, `BusinessRuleException`; `shared.api.ApiExceptionHandler` (`@RestControllerAdvice`, extiende `ResponseEntityExceptionHandler`) y fábrica de `ProblemDetail` (design D10); verificar que los casos pasan
- [x] 8.3 RED→GREEN: casos sin datos sensibles — `DataIntegrityViolationException` con mensaje que contiene `dr@ejemplo.com` y el nombre de una restricción → 409 `CONFLICT` sin ninguno de los dos ni texto de PostgreSQL; JSON mal formado con `"dni":"30123456"` → 400 `MALFORMED_REQUEST` sin el DNI ni el mensaje del parser; implementar los handlers/override de `handleExceptionInternal`; verificar que pasan
- [x] 8.4 RED→GREEN: validación — DTO record de test con `@NotBlank` y `@Size` enviado con un campo vacío y otro largo → 400 `VALIDATION_FAILED` con `errors` = `[{field, code}]` para ambos y sin valores enviados; verificar que pasa
- [x] 8.5 RED→GREEN: protocolo — método no soportado → 405 `METHOD_NOT_ALLOWED`; parámetro numérico con texto → 400 sin el valor; tipo de contenido no soportado → 415; todos `application/problem+json`; verificar que pasan
- [x] 8.6 RED→GREEN: error inesperado — `RuntimeException("falló para dr@ejemplo.com")` → 500 `INTERNAL_ERROR` con `errorId` UUID; con `OutputCaptureExtension`, el log contiene ese `errorId` y `RuntimeException`, y ni la respuesta ni el log contienen el email; verificar que pasa y que `ProductionArchitectureTest` sigue verde
- [x] 8.7 (post-verify W1/W2) RED→GREEN: `ApiExceptionHandler` relanza `AccessDeniedException`/`AuthenticationException` (no 500); tests de 406, estados 4xx/5xx no listados, `DomainException` sin subtipo y de que la validación de parámetros resuelve `HandlerMethodValidationException`; cobertura de `ApiExceptionHandler` >= 80 %

## 9. Verificación integral

- [x] 9.1 Ejecutar `./gradlew check` desde `backend/` (unitarios, ArchUnit, IT con zonky, JaCoCo) y verificar que todo está verde; revisar en `build/reports/jacoco` que las clases nuevas de `shared` y `auth` tienen cobertura de líneas ≥ 80 %
- [x] 9.2 Revisión de reglas duras sobre el diff: sin `@Autowired` en campos (1), sin entidades expuestas en API (2), sin edición de `V1` (3), sin email/contraseña/DNI en logs (6), timestamps `timestamptz`/`Instant` (8), sin credenciales en archivos versionados (15), dependencias solo las de design D12 (12); verificar con `git diff` y `grep -rn "BOOTSTRAP_ADMIN" backend/src/main` (solo en el runner, sin valores)
- [x] 9.3 Arranque manual contra el PostgreSQL local (base recreada si hacía falta): `./gradlew bootRun` con las variables de `.env` crea el DENTIST y un segundo `bootRun` registra "Bootstrap omitido"; `GET /actuator/health` = `UP`; verificar en el log que no aparecen email ni contraseña
  - Ejecutada a mano por Lucas (2026-11-21) contra PostgreSQL 18 local, sobre la base existente con V1 ya aplicada, es decir, como actualización a V2. Sin errores. El primer `bootRun` aplicó V2 y creó el DENTIST; el segundo registró "Bootstrap omitido". `/actuator/health` respondió `UP`, sin email ni contraseña en el log. El resultado lo informó el usuario; el agente no lo observó directamente.

## Workflow follow-up

- Verificar el change con `openspec-verify` contra specs, design y tasks.
- Commit con conventional commits `feat(C-02): ...` solo cuando el usuario lo pida (regla dura 14).
- Archivar con `/opsx:archive c-02-core-models-shared` tras la verificación; C-05 debe invalidar la caché de `InstallationPractice` si permite cambiar la zona, y C-04 debe reutilizar `PasswordHasher` y cablear 401/403 como `ProblemDetail`.
