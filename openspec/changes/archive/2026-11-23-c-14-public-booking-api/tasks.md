# Tasks: C-14 `public-booking-api`

> **Strict TDD**: cada grupo sigue Safety Net → RED (el test se ejecuta y falla por la razón esperada) → GREEN (mínimo código) → TRIANGULATE (≥2 casos por comportamiento) → REFACTOR con la suite verde. Unitarios `*Test` con `./gradlew test`; integración `*IT` con `./gradlew integrationTest` (PostgreSQL embebido zonky; **sin Docker/Testcontainers**, DD-10). Specs = criterios de aceptación; decisiones = `design.md` D1–D11. Frontend: TS estricto, sin `any`, sin `innerHTML`, solo tokens (sin cambios en este change).
>
> Skills a cargar antes del apply: `springboot-tdd`, `spring-boot-test-patterns`, `java-junit`, `postgresql-table-design`, `springboot-security`. Comandos desde `backend/`: `./gradlew test`, `./gradlew integrationTest`, `./gradlew check`. **No hacer commits ni push.**

## 1. Safety net, número de migración y contratos congelados

- [x] 1.1 Safety Net: ejecutar `./gradlew test integrationTest` y registrar el baseline (N unit + M IT verdes). Si algo falla, detenerse y reportarlo como preexistente (no corregir).
- [x] 1.2 Listar `backend/src/main/resources/db/migration`: la última versión aplicada es V15; elegir la siguiente libre (hoy **V16**) y reemplazar "V16" en `proposal.md`, `design.md` D1, `specs/database-migrations/spec.md` y estas tareas si aparece una mayor. Verificar `flyway.out-of-order=false`.
- [x] 1.3 Registrar el estado verde de `ProductionArchitectureTest`, `ModuleBoundariesRulesTest`, `AvailabilityArchitectureTest`, `AgendaApiContractsTest`, `MigrationValidationIT`, `AppointmentSchemaIT`, `NotificationSchemaIT`, `AvailabilityPublicControllerIT`, `ConfigurationHygieneTest` y `AppointmentFixtures` (archivos que este change extiende).
- [x] 1.4 Confirmar que `reservas.application` no importa `reservas.api` y que el dominio de `reservas` sigue sin dependencias de Spring/JDBC; verificar con `./gradlew test --tests '*ProductionArchitectureTest'`.

## 2. Migración `V16__booking_token.sql` (D1)

- [x] 2.1 RED: crear `database/BookingTokenSchemaIT` — columnas y tipos exactos (`id`, `appointment_id`, `token_hash` text(64) UNIQUE, `expires_at`, `revoked_at`, `created_at`, `privacy_notice_version`, `privacy_accepted_at`), FK a `appointment` con `ON DELETE RESTRICT`, CHECK de longitud de hash, índice por `appointment_id` y ausencia de columnas con token en claro; verificar que falla porque la tabla no existe.
- [x] 2.2 RED: en el mismo IT, hash duplicado rechazado con SQLState `23505` y varios hashes distintos para un mismo turno aceptados; verificar que falla.
- [x] 2.3 RED: crear `database/BookingTokenPrivilegesIT` — `INSERT`/`SELECT`/`UPDATE` permitidos al rol de aplicación; `DELETE`/`TRUNCATE` denegados y fila sobrevive; verificar que falla.
- [x] 2.4 GREEN: escribir `V16__booking_token.sql` (comentario de cabecera: por qué V16, desvíos respecto de KB 04, V1–V15 intactas) y verificar 2.1–2.3, `MigrationsIT` y `MigrationValidationIT` verdes.
- [x] 2.5 RED: agregar a `BookingTokenSchemaIT` una migración escalonada estilo `AppointmentSchemaIT:236-263` (`Flyway.target("15")` → `"16"`, sin versiones fuera de orden); verificar que falla.
- [x] 2.6 GREEN: implementar el soporte de fixture/test necesario y verificar 2.5 verde.
- [x] 2.7 Actualizar `support/AppointmentFixtures.cleanAll()` para borrar `booking_token` **antes** que `appointment` (FK RESTRICT); verificar con `./gradlew integrationTest` que los IT de turnos y notificaciones siguen verdes.

## 3. Dominio puro del token y validaciones (D2, D3, D6)

> Ajuste de trazabilidad tras la verificación independiente: no se crearon clases `BookingTokenGeneratorTest`, `BookingRequestValidatorTest`, `ContactBookingLimitTest`, `AppointmentOperationsContractTest`, `PublicBookingOperationsIT`, `PatientBookingOperationsIT`, `ActiveBookingCountIT`, `BookingTokenRepositoryIT` ni `PublicBookingArchitectureTest`. Sus comportamientos se cubren con los tests reales que se nombran en cada tarea.

- [x] 3.1 RED/GREEN: `reservas/domain/BookingTokenCryptoTest` — token Base64URL de 43 caracteres/256 bits, generaciones distintas, hash SHA-256 de 64 hex y comparación en tiempo constante (`MessageDigest.isEqual`); implementar `BookingToken`, `BookingTokenGenerator` y `TokenHasher` en `reservas.domain` (JDK puro) y verificar la suite verde.
- [x] 3.2 RED/GREEN: expiración y revocación del token (`BookingToken.isUsable`, `revoke`, `extendExpiration`) con reloj fijo en `BookingTokenCryptoTest`.
- [x] 3.3 RED/GREEN: validación de formato y consentimiento en `reservas.api.CreateBookingRequest` (`@NotBlank`, `@Email`, `@NotNull @AssertTrue`), verificada en `PublicBookingsEndpointsIT.validacionIncompletaDevuelve400` y `sinAceptarAvisoDePrivacidadDevuelve400`.
- [x] 3.4 RED/GREEN: anticipación mínima y horizonte máximo en `PublicBookingUseCase.requireOfferedSlot` (`BOOKING_TOO_SOON` / `BOOKING_BEYOND_HORIZON`, 422), verificados en `PublicBookingUseCaseTest` y `PublicBookingsEndpointsIT.inicioDentroDeLaAnticipacionMinimaDevuelve422`.
- [x] 3.5 RED/GREEN: límite de 3 turnos futuros activos por contacto (`MAX_ACTIVE_FUTURE_BOOKINGS_PER_CONTACT`), verificado en `PublicBookingUseCaseTest.limitePorContactoRechazaLaReserva`.

## 4. Puertos y contratos entre módulos (D5, D8)

> Ajuste de trazabilidad: no se crearon `AppointmentOperationsContractTest`, `PublicBookingOperationsIT`, `PatientBookingOperationsIT` ni `ActiveBookingCountIT`. Los contratos se verifican con `AgendaApiContractsTest`, `PublicBookingsEndpointsIT`, `PublicBookingUseCaseTest` y los tests de C-10.

- [x] 4.1 RED/GREEN: actualizar `AgendaApiContractsTest` para exigir `book`/`reschedule`/`cancel` en `agenda.api.AppointmentOperations` sin relajar las aserciones anti-PII de eventos y vistas.
- [x] 4.2 GREEN: ampliar `agenda.api.AppointmentOperations` con `book(...)`, parametrizar `AppointmentService.book` con `origin` y adaptar en `agenda.infrastructure`; verificar `AgendaApiContractsTest` y `ProductionArchitectureTest` verdes.
- [x] 4.3 RED/GREEN: alta pública verificada en `PublicBookingsEndpointsIT.reservaAnonimaConCsrfCreaTurnoYTokenSoloHasheado` (turno `CONFIRMED`, `origin=PUBLIC`) y traducción de conflictos en `slotOcupadoDevuelve409SinCrearOtroTurno`.
- [x] 4.4 RED/GREEN: `pacientes.api.PatientBookingOperations` + adaptador que delega en `FindOrCreatePatientByDni` (RN-PA-03); la no sobreescritura está verificada por `FindOrCreatePatientByDniTest` de C-10.
- [x] 4.5 RED/GREEN: `agenda.api.ActiveBookingsLookup` + adaptador con conteo de `CONFIRMED` futuros; límite verificado en `PublicBookingUseCaseTest.limitePorContactoRechazaLaReserva`. El conteo NO va en `pacientes.api` porque `pacientes → agenda` está prohibido.

## 5. Persistencia y caso de uso de reserva (D3, D5, D6)

- [x] 5.1 RED/GREEN: persistencia de tokens verificada en `BookingTokenSchemaIT` (hash único, varios tokens por turno, esquema) y en `PublicBookingsEndpointsIT` (hash de 64 hex persistido, sin token en claro). No se creó `BookingTokenRepositoryIT`; su cobertura queda en esos IT.
- [x] 5.2 GREEN: puerto en `reservas.domain`, entidad JPA (UUIDv7, sin Spring/JDBC en dominio) y adaptador con Spring Data en `reservas.infrastructure`; verificar `ProductionArchitectureTest` verde.
- [x] 5.3 RED: `reservas/application/PublicBookingUseCaseTest` con fakes — flujo completo (validar → disponibilidad → paciente → turno → token), honeypot sin efectos, rate limit rechazado, slot tomado → 409 `SLOT_TAKEN`, límite por contacto, DNI existente sin sobreescritura, consentimiento registrado; verificar que falla.
- [x] 5.4 GREEN: caso de uso en `reservas.application` (sin importar `reservas.api`) y verificar 5.3 verde.
- [x] 5.5 TRIANGULATE + REFACTOR: agregar variantes de borde (frontera de anticipación/horizonte, granularidad, paciente inactivo, prestación no pública) con la suite verde.

## 6. Enlace de gestión en emails (D3)

> Ajuste de trazabilidad: no se creó `ManagementLinkProviderAdapterTest/IT`; el SPI se verifica con los IT de C-13 y con `PublicBookingsEndpointsIT`.

- [x] 6.1 RED/GREEN: implementar `notificaciones.api.ManagementLinkProvider` en `reservas.infrastructure` (`BookingManagementLinkProvider`): emite token nuevo hasheado para `CONFIRMATION`/`REMINDER`/`RESCHEDULE`, vacío para cancelaciones, `expires_at = endsAt + BOOKING_TOKEN_TTL_DAYS`.
- [x] 6.2 Verificar con `NotificationDispatcherIT` que el email incluye `/turno/{token}` y con `NotificationManagementLinkIT` (doble `@Primary`) el fallback; `AppointmentNotificationsIT` sigue verde con el proveedor real.
- [x] 6.3 Sin captura de logs en el SPI: la ausencia de token en logs se verifica en `PublicBookingsEndpointsIT.datosPersonalesNoAparecenEnLogs` (`OutputCaptureExtension`) y en los tests de C-13 sobre `last_error`.

## 7. Endpoint público, seguridad y errores (D4, D7, D9)

- [x] 7.1 RED: `reservas/infrastructure/PublicBookingsEndpointsIT` — POST anónimo con CSRF válido (tomado de un GET previo `X-CSRF-TOKEN`) → 201 y cuerpo sin token/DNI/email/teléfono; sin CSRF → 403 `CSRF_REQUIRED`; honeypot → 200 genérico sin efectos; rate limit → 429 + `Retry-After`; slot tomado → 409 `SLOT_TAKEN`; validación → 400 `VALIDATION_FAILED`; verificar que falla.
- [x] 7.2 GREEN: `reservas.api.CreateBookingRequest/CreateBookingResponse` (records con Bean Validation) y `PublicBookingsController`; verificar 7.1 verde.
- [x] 7.3 TRIANGULATE: IP simulada con `request.setRemoteAddr(...)` (nunca `X-Forwarded-For`), sin PII en body ni logs (captura con `ListAppender`), y `Referrer-Policy: no-referrer` presente.
- [x] 7.4 RED/GREEN: cablear explícitamente `RATE_LIMIT_PUBLIC_PER_MIN` (hoy declarada pero no leída por `src/main`) en un limitador dedicado de reservas con clave `remoteAddr` hasheada; verificar 7.1 (429) y `ConfigurationHygieneTest` verdes. NO inyectar el `RateLimiter` de login.
- [x] 7.5 Verificar `AccessPolicyIT` y `AvailabilityPublicControllerIT` siguen verdes (deny-by-default y prefijo público intactos).

## 8. Arquitectura, configuración y cierre

- [x] 8.1 RED/GREEN: no se creó `PublicBookingArchitectureTest`; las reglas se verifican con `ProductionArchitectureTest`, `ModuleBoundariesRulesTest`, `AvailabilityArchitectureTest` y `AgendaApiContractsTest` sobre producción (dominio sin Spring/JDBC, `application` sin `reservas.api`, solo `api` ajena).
- [x] 8.2 El grafo no cambia en C-14; todas las verificaciones de arquitectura pasan con `./gradlew test`.
- [x] 8.3 Verificar `ConfigurationHygieneTest` y `.env.example` intactos (sin variables nuevas; `RATE_LIMIT_PUBLIC_PER_MIN` y `BOOKING_TOKEN_TTL_DAYS` ya declaradas).
- [x] 8.4 RED/GREEN: `openspec validate c-14-public-booking-api --strict` — corregir cualquier delta inválido.
- [x] 8.5 Ejecutar `./gradlew check` completo y registrar el resultado (unitarios + IT + arquitectura). Reportar diferencias o fallos sin ocultarlos.
- [x] 8.6 Actualizar este `tasks.md` marcando cada tarea completada y escribir un resumen del apply (número de migración, contratos nuevos, tests ejecutados). **No archivar el change ni hacer commit/push.**

## Tabla de evidencia Strict TDD

| Tarea | Test File | Capa | RED | GREEN | TRIANGULATE |
|---|---|---|---|---|---|
| 2.1–2.3 | `BookingTokenSchemaIT`, `BookingTokenPrivilegesIT` | database | tabla inexistente | V16 | dos hashes/turno |
| 3.1 | `BookingTokenCryptoTest` | domain | clases inexistentes | generador/hasher | longitud y timing |
| 3.3 | `PublicBookingsEndpointsIT` | api | validación ausente | DTO + handler | email/consentimiento |
| 3.4 | `PublicBookingUseCaseTest`, `PublicBookingsEndpointsIT` | application | código ausente | 422 `BOOKING_TOO_SOON` | horizonte |
| 4.1 | `AgendaApiContractsTest` | api | método ausente | puerto ampliado | sin PII |
| 4.5 | `PublicBookingUseCaseTest`, `FindOrCreatePatientByDniTest` (C-10) | application/domain | puerto inexistente | adaptador | divergencia de datos |
| 5.3 | `PublicBookingUseCaseTest` | application | clase inexistente | caso de uso | anticipación y límite |
| 6.1 | `ManagementLinkProviderAdapterTest` | infrastructure | bean inexistente | SPI | expiración |
| 7.1 | `PublicBookingsEndpointsIT` | infrastructure | endpoint inexistente | controller | CSRF/PII/log |
| 8.1 | `PublicBookingArchitectureTest` | architecture | dependencias ilegales | contratos `api` | — |
