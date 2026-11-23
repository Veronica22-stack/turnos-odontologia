# Tasks: C-12 `availability-engine`

> El grupo 1 arma la red de seguridad. Los grupos 2 a 7 aplican, cada uno, el ciclo **Safety Net → RED → GREEN → TRIANGULATE (≥2 casos por comportamiento) → REFACTOR**, y cierran con un Gate. El grupo 8 es solo verificación de integración (no introduce tests nuevos).
>
> Skills a cargar antes de ejecutar: `springboot-tdd`, `springboot-patterns`, `springboot-security`, `api-design-principles`. Comandos de ejemplo: `backend/gradlew ...` ejecutados desde `backend/`.

## 1. Safety Net y congelación de contratos

- [x] 1.1 Safety Net: ejecutar `backend/gradlew test` y `backend/gradlew compileJava` sin cambios y verificar que la suite baseline está verde antes de escribir cualquier test nuevo.
- [x] 1.2 Safety Net: congelar en `AgendaApiContractsTest` las firmas `AffectedAppointments.findWithin(UUID, Instant, Instant)` y `BlockedRanges.findOverlapping(UUID, Instant, Instant)` y la aserción existente `AppointmentOperations == containsExactly("reschedule")`, y verificar que el test pasa sin extender esos contratos.
- [x] 1.3 Safety Net + Gate 1: agregar `AvailabilityArchitectureTest` que verifique `reservas.domain` sin dependencias de `api`/Spring/JPA, `reservas.application` sin `reservas.api` ni `reservas.infrastructure`, y `agenda → reservas` prohibido; verificar que pasa hoy y que una dependencia prohibida sintética la hace fallar.

## 2. Dominio puro: intervalos, tramos y granularidad

- [x] 2.1 Safety Net: reejecutar los tests de los grupos 1.1–1.3 y verificar que siguen verdes antes de escribir el primer test de dominio.
- [x] 2.2 RED (`AvailabilityCalculatorTest`): escribir tests que fallen por compilación para `Interval`/`AvailabilityCalculator` con varios tramos, bloqueo parcial, turno adyacente, borde final exacto, granularidad de 20 con duración de 30 y duraciones 15/30/60 sobre el mismo tramo; verificar el fallo esperado.
- [x] 2.3 GREEN: implementar `Interval`, la fusión de rangos ocupados y el cálculo de inicios `[start, start + duration)` dentro de cada tramo libre; verificar que `AvailabilityCalculatorTest` pasa.
- [x] 2.4 TRIANGULATE: agregar al menos dos casos adicionales por comportamiento (bloqueo contiguo a turno, solapamiento total, tramo sin hueco suficiente, duración mayor al paso de granularidad) y verificar que los tests pasan y fallan si `isBefore` se reemplaza por `!isAfter`.
- [x] 2.5 REFACTOR + Gate 2: extraer `subtractOccupied` y `enumerateStarts` sin cambiar el comportamiento, ejecutar `backend/gradlew test --tests '*AvailabilityCalculatorTest'` y verificar Gate 2 en verde.

## 3. Dominio puro: zona horaria, reloj y ventana de reserva

- [x] 3.1 Safety Net: reejecutar el grupo 2 y verificar que sigue verde antes de introducir `Clock`/`ZoneId`.
- [x] 3.2 RED (`AvailabilityWindowTest`): escribir tests que fallen por compilación para reloj fijo `2026-03-10T12:00:00Z`, día local BA `2026-03-10` → `[03:00Z, 03:00Z del día siguiente)`, hora local inexistente/ambigua en zona con DST, `minNoticeMin = 120`, `maxAdvanceDays = 60` y audiencia interna sin filtros; verificar el fallo esperado.
- [x] 3.3 GREEN: implementar la conversión de día local a UTC, el round-trip que descarta horas inexistentes, el offset más temprano para horas ambiguas y los filtros de audiencia pública; verificar que `AvailabilityWindowTest` pasa.
- [x] 3.4 TRIANGULATE: agregar al menos dos casos por comportamiento (día local distinto del UTC con turno a `02:00Z` del día siguiente, salto DST en ambos sentidos, inicio exacto en `minNoticeMin` y un día dentro/fuera del horizonte) y verificar que pasan.
- [x] 3.5 REFACTOR + Gate 3: concentrar la conversión en un único tipo de dominio, ejecutar `backend/gradlew test --tests '*AvailabilityWindowTest'` y verificar Gate 3 en verde.

## 4. Puertos de lectura y adaptadores delgados

- [x] 4.1 Safety Net: reejecutar los grupos 2 y 3 y verificar que el dominio sigue intacto antes de tocar `api`.
- [x] 4.2 RED (`WorkingHoursLookupContractTest`, `ServiceAvailabilityLookupContractTest`, `PracticeBookingSettingsLookupContractTest`): escribir tests que fallen por compilación para los tres puertos y aserciones de arquitectura que prohíben exportar `agenda.application` o entidades JPA; verificar el fallo esperado.
- [x] 4.3 GREEN: crear las interfaces y records propios en `agenda.api`/`shared.api` y los adaptadores en `infrastructure` que mapean `WorkingHoursDto`, repositorio de prestaciones y `PracticeSettingsData`; verificar que los tres tests de contrato pasan.
- [x] 4.4 TRIANGULATE: agregar al menos dos casos por puerto (sin horarios y con tramos múltiples; catálogo vacío, prestación inactiva e identificadores ajenos; settings con defaults y con valores personalizados) y verificar que pasan.
- [x] 4.5 REFACTOR + Gate 4: eliminar cualquier import de `application` desde los puertos, ejecutar los tests de contrato y `AvailabilityArchitectureTest`, y verificar Gate 4 en verde.

## 5. Caso de uso en `reservas.application` y fachada en `reservas.api`

- [x] 5.1 Safety Net: reejecutar el grupo 4 y verificar que los puertos y la arquitectura siguen verdes antes de orquestar el caso de uso.
- [x] 5.2 RED (`AvailabilityUseCaseTest`, `AvailabilityApiContractTest`): escribir tests que fallen por compilación para el caso de uso con `audience` interna/pública, `NO_ACTIVE_SERVICES` cuando no hay prestaciones activas, `SERVICE_NOT_FOUND`/`SERVICE_INACTIVE`, y un contrato `AvailabilityLookup` sin PII ni tipos de framework; verificar el fallo esperado.
- [x] 5.3 GREEN: implementar el caso de uso en `reservas.application` y la fachada `AvailabilityLookup`/`AvailabilityQuery`/`AvailabilityResult`/`AvailableSlot`/`AvailabilityStatus` en `reservas.api`; verificar que ambos tests pasan.
- [x] 5.4 TRIANGULATE: agregar al menos dos casos por comportamiento (404 vs 422, slots vacíos sin tramos, resultado sin `patientId`/`appointmentId`/notas, consulta sin mutaciones en repositorios) y verificar que pasan.
- [x] 5.5 REFACTOR + Gate 5: confirmar que `reservas.application` NO importa `reservas.api` ni `reservas.infrastructure`, ejecutar los tests del grupo y verificar Gate 5 en verde.

## 6. Endpoint interno y seguridad

- [x] 6.1 Safety Net: reejecutar el grupo 5 y verificar que el caso de uso sigue verde antes de exponer HTTP.
- [x] 6.2 RED (`AvailabilityInternalControllerIT`): escribir tests MockMvc que fallen por compilación para `GET /api/v1/availability` con sesión `DENTIST` sin CSRF → 200, anónimo → 401 `UNAUTHENTICATED`, rol distinto → 403 `FORBIDDEN` y parámetros inválidos → 400 `VALIDATION_FAILED`; verificar el fallo esperado.
- [x] 6.3 GREEN: agregar el controller y el mapeo de errores vía `ProblemDetails.of`, reutilizando la configuración HTTP existente; verificar que `AvailabilityInternalControllerIT` pasa.
- [x] 6.4 TRIANGULATE: agregar al menos dos casos por comportamiento (UUID mal formado, fecha `2026-02-30`, `POST` rechazado, vista interna sin `minNoticeMin`/`maxAdvanceDays`) y verificar que pasan.
- [x] 6.5 REFACTOR + Gate 6: comprobar que no se agregaron reglas a `SecurityConfig`, ejecutar `backend/gradlew test --tests '*AvailabilityInternalControllerIT'` y verificar Gate 6 en verde.

## 7. Endpoint público, catálogo vacío y límite de tasa

- [x] 7.1 Safety Net: reejecutar el grupo 6 y verificar que la ruta interna sigue protegida antes de habilitar la pública.
- [x] 7.2 RED (`AvailabilityPublicControllerIT`): escribir tests MockMvc que fallen por compilación para `GET /api/v1/public/availability` anónimo → 200 sin PII, catálogo vacío → 200 `NO_ACTIVE_SERVICES`, `404 SERVICE_NOT_FOUND`, `422 SERVICE_INACTIVE`, `400 VALIDATION_FAILED`, `429 RATE_LIMITED` con `Retry-After`, y `POST`/`PUT`/`DELETE` rechazados; verificar el fallo esperado.
- [x] 7.3 GREEN: agregar el controller público, la comprobación previa de `hasPublicServices` y el reutilizado `PublicReadRateLimiter` con clave `getRemoteAddr()`; verificar que `AvailabilityPublicControllerIT` pasa.
- [x] 7.4 TRIANGULATE: agregar al menos dos casos por comportamiento (catálogo no vacío con `serviceId` ajeno → 404 y no `NO_ACTIVE_SERVICES`, IP distinta dentro del límite, `Retry-After >= 1`, cuerpo sin IP ni clave del limitador) y verificar que pasan.
- [x] 7.5 REFACTOR + Gate 7: comprobar que no se lee `X-Forwarded-For`, ejecutar `backend/gradlew test --tests '*AvailabilityPublicControllerIT'` y verificar Gate 7 en verde.

## 8. Verificación de integración

- [x] 8.1 Ejecutar `backend/gradlew check` completo y verificar que todos los tests de los grupos 1–7 pasan juntos, sin fallos de arquitectura ni de contratos.
- [x] 8.2 Revisar `git diff --stat` y verificar que no se modificaron `CHANGES.md`, `openspec/specs/**`, entidades JPA, migraciones Flyway ni dependencias de runtime; `frontend/**` permanece intacto.
- [x] 8.3 Ejecutar `openspec validate c-12-availability-engine --strict` y verificar que los nueve deltas siguen siendo `ADDED Requirements` y que el planning queda listo para `/opsx:apply c-12-availability-engine`.

## Tabla de evidencia Strict TDD

| Tarea | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|---|---|---|---|---|---|---|---|
| 1.1 | `backend/gradlew test` | suite | `baseline verde` | — | — | — | — |
| 1.2 | `AgendaApiContractsTest` | architecture | firmas congeladas + `containsExactly("reschedule")` | — | — | — | — |
| 1.3 | `AvailabilityArchitectureTest` | architecture | reglas actuales verde; dependencia sintética falla | — | — | — | — |
| 2.1 | grupos 1.1–1.3 | suite | reejecución verde | — | — | — | — |
| 2.2 | `AvailabilityCalculatorTest` | domain | — | falla por clase inexistente | — | — | — |
| 2.3 | `AvailabilityCalculatorTest` | domain | — | — | casos base verdes | — | — |
| 2.4 | `AvailabilityCalculatorTest` | domain | — | — | — | ≥2 casos por comportamiento; mutación `isBefore` falla | — |
| 2.5 | `AvailabilityCalculatorTest` | domain | — | — | — | — | Gate 2 verde tras extraer helpers |
| 3.1 | grupos 2.x | suite | dominio verde | — | — | — | — |
| 3.2 | `AvailabilityWindowTest` | domain | — | falla por `Clock`/`ZoneId` ausentes | — | — | — |
| 3.3 | `AvailabilityWindowTest` | domain | — | — | reloj fijo + BA + DST verdes | — | — |
| 3.4 | `AvailabilityWindowTest` | domain | — | — | — | ≥2 casos por comportamiento | — |
| 3.5 | `AvailabilityWindowTest` | domain | — | — | — | — | Gate 3 verde con conversión centralizada |
| 4.1 | grupos 2.x–3.x | suite | dominio intacto | — | — | — | — |
| 4.2 | `WorkingHoursLookupContractTest`, `ServiceAvailabilityLookupContractTest`, `PracticeBookingSettingsLookupContractTest` | api / architecture | — | falla por puertos ausentes | — | — | — |
| 4.3 | mismos tests de contrato | api + infrastructure | — | — | tres puertos verdes, sin tipos de `application` | — | — |
| 4.4 | mismos tests de contrato | api + infrastructure | — | — | — | ≥2 casos por puerto | — |
| 4.5 | mismos tests + `AvailabilityArchitectureTest` | architecture | — | — | — | — | Gate 4 verde sin imports de `application` |
| 5.1 | grupo 4.x | suite | puertos verdes | — | — | — | — |
| 5.2 | `AvailabilityUseCaseTest`, `AvailabilityApiContractTest` | application + api | — | falla por caso de uso/fachada ausentes | — | — | — |
| 5.3 | mismos tests | application + api | — | — | 200 con slots y errores de dominio verdes | — | — |
| 5.4 | mismos tests | application + api | — | — | — | ≥2 casos por comportamiento; sin PII y sin mutaciones | — |
| 5.5 | mismos tests + `AvailabilityArchitectureTest` | application + api | — | — | — | — | Gate 5 verde sin `api`/`infrastructure` en `application` |
| 6.1 | grupo 5.x | suite | `test --tests '*AvailabilityUseCaseTest' --tests '*AvailabilityApiContractTest' --tests '*AvailabilityArchitectureTest'` → BUILD SUCCESSFUL | — | — | — | — |
| 6.2 | `AvailabilityInternalControllerIT` | api (MockMvc) | — | 6/7 tests fallaron: sin controller (404/400/403/405); 401 ya lo daba la seguridad | — | — | — |
| 6.3 | `AvailabilityInternalControllerIT` | api (MockMvc) | — | — | 7/7 verdes tras agregar controller, parser y `expiresAt` en la sesión de prueba | — | — |
| 6.4 | `AvailabilityInternalControllerIT` | api (MockMvc) | — | — | — | 12/12 verdes: +5 casos (serviceId vacío, fecha con formato inválido, PUT/DELETE rechazados, filtros públicos ignorados) | — |
| 6.5 | `AvailabilityInternalControllerIT` + `AvailabilityArchitectureTest` | api (MockMvc) | — | — | — | — | Gate 6 verde; `git status` sin cambios en `SecurityConfig` y arquitectura verde |
| 7.1 | grupo 6.x | suite | `integrationTest` interna + `AvailabilityArchitectureTest` → BUILD SUCCESSFUL | — | — | — | — |
| 7.2 | `AvailabilityPublicControllerIT` | api (MockMvc) | — | 17/17 fallaron: sin controller público (404 en lugar de 200/400/404/422/429/405) | — | — | — |
| 7.3 | `AvailabilityPublicControllerIT` | api + shared infrastructure | — | — | 17/17 verdes: 200 sin PII, `NO_ACTIVE_SERVICES`, 404, 422, 400, 429 + `Retry-After`, 405 | — | — |
| 7.4 | `AvailabilityPublicControllerIT` | api (MockMvc) | — | — | — | 17/17 verdes: ≥2 casos por comportamiento; `Retry-After=1` en el pasado; cuerpo sin IP ni clave; dos IPs permitidas | — |
| 7.5 | `AvailabilityPublicControllerIT` | api (MockMvc) | — | — | — | — | Gate 7 verde; mutación `getRemoteAddr()` → `X-Forwarded-For` hace fallar el test de clave; grep sin coincidencias en `main` |
| 8.1 | `backend/gradlew check jacocoTestReport` | suite | integración completa | — | — | — | BUILD SUCCESSFUL; cobertura línea nueva: `AvailabilityUseCase` 100 %, `AvailabilityCalculator` 98,1 %, `AvailabilityWindow` 92,9 %, `Interval` 85,7 %, `AvailabilityParams` 92,3 %, controllers 100 %, adaptadores 94,4–100 % (todas ≥80 %) |
| 8.2 | `git diff --stat` + `git status --short` | repo | alcance restringido | — | — | — | único tracked modificado: `AgendaApiContractsTest.java` (+20); sin `CHANGES.md`, `openspec/specs/**`, migraciones, entidades JPA, `build.gradle.kts` ni `frontend/**` |
| 8.3 | `openspec validate c-12-availability-engine --strict` | planning | deltas aditivos | — | — | — | `Change 'c-12-availability-engine' is valid`; 9/9 deltas con `## ADDED Requirements` |
