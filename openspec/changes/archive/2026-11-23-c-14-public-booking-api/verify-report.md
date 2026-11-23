# Verify: C-14 `public-booking-api`

**Fecha**: 2026-11-23 · **Rama**: `dev-gabriel` (HEAD `77857f0`) · **Sin commit ni push**.

## Resultado

`./gradlew check` → **BUILD SUCCESSFUL** (exit 0): **787 tests unitarios + 542 IT, 0 fallos, 0 errores**, incluidos los tests de arquitectura. Cifras verificadas de forma independiente tras las correcciones.

## Cambios implementados

- `V16__booking_token.sql`: tabla `booking_token` con hash SHA-256 de 64 hex, expiración, revocación, versión/aceptación del aviso, índice por turno y `REVOKE DELETE/TRUNCATE`. V1–V15 intactas; `outOfOrder=false`.
- Dominio `reservas`: `BookingToken` (JPA), `BookingTokenGenerator` (CSPRNG 256 bits Base64URL), `TokenHasher` (SHA-256 + `MessageDigest.isEqual`), `BookingTokenRepository` y adaptador JPA.
- Contratos: `agenda.api.AppointmentOperations.book`, `agenda.api.ActiveBookingsLookup`, `agenda.api.PublicServiceNameLookup`, `agenda.api.AppointmentServiceLookup`, `pacientes.api.PatientBookingOperations`. Todos los adaptadores delegan en los servicios existentes; `AgendaApiContractsTest` se actualizó al nuevo contrato.
- `POST /api/v1/public/bookings` con validación Bean Validation, honeypot genérico, rate limit con `RATE_LIMIT_PUBLIC_PER_MIN` (clave `remoteAddr` hasheada), límite por contacto, alta de turno `origin=PUBLIC` y token hasheado en la misma transacción.
- `ManagementLinkProvider` implementado en `reservas`: emite un token nuevo por correo, vigente hasta `endsAt + BOOKING_TOKEN_TTL_DAYS`; resuelve la pregunta abierta de C-13 D11.
- `AppointmentFixtures.cleanAll()` limpia `booking_token` antes de `appointment`.

## Pruebas nuevas

- `reservas/domain/BookingTokenCryptoTest` (5): entropía, hash, comparación en tiempo constante, expiración/revocación.
- `reservas/application/PublicBookingUseCaseTest` (6): reserva correcta, slot no ofrecido (409), anticipación/horizonte (422), límite por contacto y rate limit.
- `database/BookingTokenSchemaIT` (4) y `database/BookingTokenPrivilegesIT` (2): esquema, unicidad del hash, migración escalonada V15→V16 y privilegios.
- `reservas/infrastructure/PublicBookingsEndpointsIT` (9): reserva anónima con CSRF, sin CSRF (403), honeypot, slot ocupado (409 `SLOT_TAKEN`), validaciones, anticipación (422), rate limit (429 + `Retry-After`) y ausencia de PII en logs.

## Desvíos documentados

1. **Migración V16 en lugar de V10**: la documentación del roadmap quedó desactualizada (V15 es la última aplicada; `outOfOrder=false`). Igual criterio que C-11/C-13.
2. **Comunicación application↔api**: por la regla `applicationIndependent`, los casos de uso definen sus propios DTO/puertos (`PublicBookingCommand`, `PublicBookingResult`, `PublicSlotLookup`); los controllers hacen el mapeo.
3. **Conteo por contacto en `agenda.api`**: `pacientes → agenda` está prohibido; `appointment` vive en `agenda`, así que el conteo se expone desde allí y `pacientes.api` solo aporta find-or-create e IDs opacos.
4. **Encolado de notificaciones**: sigue siendo AFTER_COMMIT (C-13 D3), no dentro de la transacción de reserva como decía el roadmap original.
5. **Aviso de privacidad**: texto TBD (PA-10); se registra la versión estable `PRIVACY_NOTICE_V1` y el instante de aceptación.
6. **Correcciones tras verificación independiente**: anticipación mínima y horizonte ahora responden 422 con códigos `BOOKING_TOO_SOON`/`BOOKING_BEYOND_HORIZON` (antes caían en 409 `SLOT_TAKEN`); se eliminó la propiedad no declarada `app.public-booking.rate-limit.window-seconds`; la documentación de `expires_at` se alineó al código (`endsAt + TTL`, coherente con KB 08).
7. **Trazabilidad de `tasks.md`**: se corrigieron las tareas que nombraban archivos de test inexistentes; los comportamientos se atribuyen a los tests reales. Los dos escenarios que faltaban (turnos cancelados en el conteo por contacto y TTL inválido al arrancar) se cubrieron con `ActiveBookingsLookupIT` y `PublicBookingUseCaseTest.configuracionDeTtlNoPositivaFallaAlArrancar`; ver `docs/qa/matriz-de-tests.md`.

## Verificación manual pendiente

Ninguna para este change (backend puro, sin UI). Las reglas duras de PII/tokens se verifican con tests automáticos (body, logs y `audit_log.details`).
