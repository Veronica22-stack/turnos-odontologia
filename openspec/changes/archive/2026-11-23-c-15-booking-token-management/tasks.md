# Tasks: C-15 `booking-token-management`

> **Strict TDD**: Safety Net → RED (fallo esperado) → GREEN → TRIANGULATE (≥2 casos) → REFACTOR. Unitarios `*Test` con `./gradlew test`; IT con `./gradlew integrationTest` (zonky, sin Docker, DD-10). Specs = criterios de aceptación; decisiones = `design.md` D1–D10. Skills: `springboot-tdd`, `springboot-security`, `spring-boot-test-patterns`, `java-junit`, `postgresql-table-design`. **No hacer commits ni push.**

## 1. Safety Net y contratos previos

- [x] 1.1 Safety Net: `./gradlew test integrationTest` en el estado post-C-14; registrar baseline. Si algo falla, detenerse y reportar como preexistente.
- [x] 1.2 Verificar que `V16__booking_token.sql`, `ManagementLinkProvider` y `AppointmentOperations.book` (C-14) están presentes y verdes; si falta algo, detenerse (C-15 no reemplaza a C-14).
- [x] 1.3 Registrar el estado de `ProductionArchitectureTest`, `ModuleBoundariesRulesTest`, `AvailabilityArchitectureTest`, `AgendaApiContractsTest`, `AvailabilityApiContractTest` y `AccessPolicyIT` (contratos que este change extiende).

## 2. Resolución segura del token (D2, D3)

> Ajuste de trazabilidad: no se crearon `BookingTokenResolverTest` ni `BookingTokenRepositoryIT`. La resolución vive en `BookingManagementUseCase.resolve` y se prueba con `BookingManagementUseCaseTest`, `BookingTokenCryptoTest` y `BookingManagementEndpointsIT`.

- [x] 2.1 RED/GREEN: error genérico único para inexistente/expirado/revocado (`NotFoundException("INVALID_OR_EXPIRED_TOKEN")`) verificado en `BookingManagementUseCaseTest.tokenInvalidoOExpiradoDaSiempreElMismoErrorGenerico`, `BookingManagementEndpointsIT.tokenInvalidoRevocadoYExpiradoDanElMismoError` y `tokenExpiradoDevuelveElMismo404Generico`.
- [x] 2.2 Comparación en tiempo constante con `MessageDigest.isEqual` en `TokenHasher`, verificada en `BookingTokenCryptoTest.comparacionEnTiempoConstanteAceptaIgualesYRechazaDistintos`.
- [x] 2.3 Consulta por hash y revocación masiva en `BookingTokenRepositoryAdapter` / `SpringDataBookingTokenRepository`, verificadas en `BookingManagementUseCaseTest.cancelarRevocaTodosLosTokensYRegistraPatient` y en `BookingManagementEndpointsIT.cancelarConTokenRegistraPatientYRevocaElToken`.

## 3. Contrato de cancelación en agenda (D4)

> Ajuste de trazabilidad: no se crearon `AppointmentOperationsContractTest` ni `PublicCancellationOperationsIT`; el contrato se congela en `AgendaApiContractsTest` y la cancelación se verifica en `BookingManagementEndpointsIT`.

- [x] 3.1 RED/GREEN: actualizar `AgendaApiContractsTest` para exigir `book`/`reschedule`/`cancel` en `agenda.api.AppointmentOperations`, sin relajar sus aserciones anti-PII.
- [x] 3.2 RED/GREEN: ampliar `AppointmentOperations`, parametrizar `AppointmentService.cancel` con `cancelledBy` y delegar en el adaptador; verificado en `BookingManagementUseCaseTest.cancelarRevocaTodosLosTokensYRegistraPatient` y `BookingManagementEndpointsIT.cancelarConTokenRegistraPatientYRevocaElToken` (`cancelled_by = PATIENT`), junto con `AppointmentEventsIT` y `AppointmentNotificationsIT`.

## 4. Grafo de módulos y auditoría (D6, D8)

> Ajuste de trazabilidad: no se creó el fixture `ok_reservas_auditoria` ni `BookingAuditTest`. La arista se verifica con las reglas de arquitectura sobre producción y la auditoría con `BookingManagementUseCaseTest` + `BookingManagementEndpointsIT`.

- [x] 4.1 RED/GREEN: `ModuleBoundaries.ALLOWED_DEPENDENCIES` suma `RESERVAS → AUDITORIA`; verificado con `ProductionArchitectureTest` (grafo y `auditEventsExposedOnlyThroughApi`) y `AvailabilityArchitectureTest`, sin modificar `ModuleBoundariesRulesTest` (sus escenarios existentes siguen verdes).
- [x] 4.2 Actualizar `knowledge-base/08_arquitectura_propuesta.md` §Reglas de dependencia con la nueva arista y verificar `openspec validate c-15-booking-token-management --strict`.
- [x] 4.3 RED/GREEN: evento `BOOKING_TOKEN_USE` con `user_id = null`, `entity_id = appointmentId` y `details` con `outcome` **y** `reason_code`, sin token en claro, verificado en `BookingManagementUseCaseTest.vistaConTokenValidoEsAcotadaYAudita` y en `BookingManagementEndpointsIT.vistaConTokenValidoEsAcotadaYAudita` (consulta SQL sobre `audit_log.details`).
- [x] 4.4 RED/GREEN: fallo del logger revierte la operación, verificado en `BookingManagementUseCaseTest.falloDeAuditoriaRevierteElUsoDelToken`.

## 5. Caso de uso de gestión (D4, D5, D8, D9)

- [x] 5.1 RED: `reservas/application/BookingManagementUseCaseTest` con fakes — cancelación correcta (revoca todos los tokens), reprogramación correcta (mismo id/token, eventos), conflicto de slot → 409 conservando el original, token expirado/revocado → 404 genérico, turno pasado solo lectura → 422; verificar que falla.
- [x] 5.2 GREEN: caso de uso en `reservas.application` (sin importar `reservas.api` ni `agenda.application`); verificar 5.1 verde.
- [x] 5.3 TRIANGULATE: agregar variantes de borde (token de otro turno, segunda cancelación, recordatorio recomputado vía evento, `expires_at` ajustado tras reprogramar) con la suite verde.
- [x] 5.4 Verificar que `AvailabilityLookup` se usa con `AvailabilityAudience.PUBLIC` y que el nuevo slot debe figurar en la lista devuelta.

## 6. Rate limit anti-enumeración (D7)

> Ajuste de trazabilidad: no se creó `TokenLookupRateLimiterTest/IT`; el rate limit se verifica en `BookingManagementEndpointsIT.enumeracionExcedidaDevuelve429ConRetryAfter`.

- [x] 6.1 RED/GREEN: `PublicBookingRateLimiter` con `RATE_LIMIT_PUBLIC_PER_MIN` y clave `remoteAddr` hasheada (nunca `X-Forwarded-For`), verificado con 429 + `Retry-After` en `BookingManagementEndpointsIT` y en `PublicBookingsEndpointsIT.rateLimitDevuelve429ConRetryAfter`.
- [x] 6.2 Sin variables nuevas: se eliminó la propiedad no declarada `app.public-booking.rate-limit.window-seconds`; `ConfigurationHygieneTest` sigue verde.

## 7. Endpoints públicos, seguridad y privacidad (D1, D9)

- [x] 7.1 RED/GREEN: `reservas/infrastructure/BookingManagementEndpointsIT` — GET anónimo con token válido → 200 acotado; DELETE y POST reschedule con CSRF double-submit → estado/token/eventos correctos; sin CSRF → 403 `CSRF_REQUIRED` (ambos métodos, ver `sinCsrfNoCancela` y `reprogramacionSinCsrfDevuelve403`); token inválido/expirado/revocado → 404 con el mismo `status`/`code`; conflicto → 409 `SLOT_TAKEN`; turno pasado → 422; rate limit → 429 + `Retry-After`.
- [x] 7.2 GREEN: DTOs `record` en `reservas.api` y `BookingManagementController`; verificar 7.1 verde.
- [x] 7.3 TRIANGULATE: `Referrer-Policy: no-referrer`, sin PII en el body ni en `audit_log.details` y sin token en claro en la respuesta. La captura de logs del endpoint de C-14 (`OutputCaptureExtension`) cubre la regla de no logging de PII.
- [x] 7.4 Verificar `AccessPolicyIT`, `AvailabilityPublicControllerIT` y `PublicBookingsEndpointsIT` (C-14) siguen verdes.

## 8. Cierre y verificación

- [x] 8.1 RED/GREEN: `openspec validate c-15-booking-token-management --strict`; corregir deltas inválidos.
- [x] 8.2 Ejecutar `./gradlew check` completo (unitarios + IT + arquitectura) y registrar resultados.
- [x] 8.3 Revisar que no se editaron migraciones aplicadas ni se agregaron credenciales o variables nuevas; verificar `ConfigurationHygieneTest`.
- [x] 8.4 Marcar todas las tareas completadas y escribir el resumen del apply (contratos extendidos, arista de auditoría, tests ejecutados). **No archivar el change ni hacer commit/push.**

## Tabla de evidencia Strict TDD

| Tarea | Test File | Capa | RED | GREEN | TRIANGULATE |
|---|---|---|---|---|---|
| 2.1 | `BookingTokenCryptoTest`, `BookingManagementUseCaseTest` | domain/application | error ausente | resolvedor + token | expirado/revocado |
| 3.1 | `AgendaApiContractsTest` | api | método ausente | contrato ampliado | sin PII |
| 4.1 | `ProductionArchitectureTest` | architecture | arista prohibida | grafo ampliado | aislamiento de auditoría |
| 4.3 | `BookingManagementUseCaseTest`, `BookingManagementEndpointsIT` | application | evento ausente | `AuditLogger` con `reason_code` | fail-closed |
| 6.1 | `BookingManagementEndpointsIT` | infrastructure | 429 ausente | limitador | `Retry-After` |
| 5.1 | `BookingManagementUseCaseTest` | application | clase inexistente | caso de uso | conflicto/pasado |
| 6.1 | `TokenLookupRateLimiterTest` | infrastructure | componente ausente | limitador | clave IP |
| 7.1 | `BookingManagementEndpointsIT` | infrastructure | endpoint inexistente | controller | CSRF/PII/headers |
