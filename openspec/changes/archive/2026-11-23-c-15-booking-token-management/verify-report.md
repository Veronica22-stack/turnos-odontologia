# Verify: C-15 `booking-token-management`

**Fecha**: 2026-11-23 · **Rama**: `dev-gabriel` (HEAD `77857f0`) · **Sin commit ni push**.

## Resultado

`./gradlew check` → **BUILD SUCCESSFUL** (exit 0): **787 tests unitarios + 542 IT, 0 fallos, 0 errores**, incluidos `ProductionArchitectureTest`, `ModuleBoundariesRulesTest`, `AvailabilityArchitectureTest`, `AgendaApiContractsTest` y `BookingAuditBoundaryTest`. Cifras verificadas de forma independiente tras las correcciones.

Ambos changes validan con `openspec validate <change> --strict`.

## Cambios implementados

- `GET /api/v1/public/bookings/{token}`, `DELETE .../{token}` y `POST .../{token}/reschedule`, públicos con CSRF vigente (sin exenciones nuevas) y `Referrer-Policy: no-referrer` global.
- Resolución por hash con `MessageDigest.isEqual`, validación de expiración/revocación y error genérico único `INVALID_OR_EXPIRED_TOKEN` (404).
- Cancelación vía `AppointmentOperations.cancel(..., CancelledByView.PATIENT)`, revocación de todos los tokens del turno y eventos que disparan `CANCELLATION` + `DENTIST_CANCELLATION` en C-13.
- Reprogramación atómica validada con `AvailabilityLookup` (audiencia `PUBLIC`), `overbooking=false`, conflicto traducido a 409 `SLOT_TAKEN`, conservación del `appointment_id` y del token, con ajuste de `expires_at`.
- Auditoría `BOOKING_TOKEN_USE` con `user_id = null` y `details` con `outcome` **y** `reason_code` (`TOKEN_VIEW`/`TOKEN_CANCEL`/`TOKEN_RESCHEDULE`), ambas claves permitidas por `SensitiveDataSanitizer`.
- Grafo de arquitectura ampliado con `reservas → auditoria` (solo `auditoria.api`) en `ModuleBoundaries` y KB 08.
- Rate limit anti-enumeración con `RATE_LIMIT_PUBLIC_PER_MIN`, clave `remoteAddr` hasheada, nunca `X-Forwarded-For`.

## Pruebas nuevas

- `reservas/application/BookingManagementUseCaseTest` (7): vista acotada y auditoría con `reason_code`, fallo de auditoría (fail-closed), cancelación con `PATIENT`, conflicto al reprogramar, reprogramación correcta con token conservado, errores genéricos y turno pasado solo lectura.
- `reservas/infrastructure/BookingManagementEndpointsIT` (10): GET/DELETE/POST con token, CSRF en ambos métodos, error indistinto, token expirado y revocado, revocación, reprogramación, conflicto 409, turno pasado 422, `reason_code` en `audit_log.details` y rate limit 429 con `Retry-After`.

## Adaptaciones necesarias en tests existentes

- `AgendaApiContractsTest`: el contrato `AppointmentOperations` dejó de tener un único método (`reschedule`); ahora declara `book`, `reschedule` y `cancel`, sin relajar las aserciones anti-PII de eventos/vistas.
- `NotificationManagementLinkIT`: el doble de `ManagementLinkProvider` se marcó `@Primary` para seguir siendo el emisor elegido ante la aparición del bean real de C-14.
- `NotificationDispatcherIT`: el email de confirmación ahora incluye `/turno/{token}` (antes no había proveedor de enlaces); se actualizó la aserción del fallback.

## Desvíos documentados

1. **Sin rotación del token al reprogramar** (RN-RS-06 lo dejaba abierto): se conserva el token y el `appointment_id` de C-11; se ajusta su `expires_at`.
2. **Auditoría fail-closed**: un fallo de `audit_log` rechaza la operación, coherente con el resto del sistema. `view` es de lectura-escritura porque audita cada consulta.
3. **Vista del turno**: expone `bookingId` (UUID opaco), instantes UTC, prestación, consultorio, estado y banderas; nunca datos de contacto.
4. **Correcciones tras verificación independiente**: `reason_code` agregado a `audit_log.details`; agregados IT de token expirado, reprogramación sin CSRF y 429 por enumeración; el test antes llamado «expirado» realmente ejercitaba revocación, ahora hay un caso de vencimiento real.
5. **Trazabilidad de `tasks.md`**: se corrigieron las tareas que nombraban `BookingTokenResolverTest`, `BookingAuditTest`, `TokenLookupRateLimiterTest`, fixtures y otros archivos inexistentes; los comportamientos se atribuyen a los tests reales. La matriz completa está en `docs/qa/matriz-de-tests.md`.

## Verificación manual pendiente

Ninguna para este change (backend puro, sin UI). El flujo completo se cubre con IT sobre PostgreSQL embebido.
