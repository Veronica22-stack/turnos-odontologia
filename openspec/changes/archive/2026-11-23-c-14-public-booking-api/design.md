# Design: C-14 `public-booking-api`

> **Plan approval:** Pendiente de aprobación formal (governance CRÍTICO). El usuario solicitó en esta sesión generar primero los artefactos y luego ejecutar el apply; este es el registro de decisiones para esa revisión.

## UI Mockups

- Design system revision: 1 (`design-system/README.md`, aprobado)
- Decision: no (no UI) — 2026-11-23
- Mockups: n/a. El change es backend puro; la UI pública es C-24.
- Approval: no aplica
- Preview: no aplica
- Visual verify: no aplica

## Context

Estado real verificado en `HEAD 77857f0` (rama `dev-gabriel` alineada con `master`, árbol limpio):

- Existen `service` (V4), `working_hours`/parámetros de reserva (V5), `patient` (V7), `schedule_block` (V8), `appointment` (V13) y `notification` (V14); migraciones actuales: V1–V5, V7, V8, V11–V15, con `outOfOrder=false`.
- `SecurityConfig:54` ya habilita `/api/v1/public/**` sin sesión; `CsrfHeaderFilter` emite `X-CSRF-TOKEN` en cada respuesta y **solo** `POST /api/v1/auth/login` está exento de CSRF (`SecurityConfig:45-46`). `SecurityHeadersFilter` ya envía `Referrer-Policy: no-referrer`.
- Contratos disponibles: `agenda.api.AppointmentOperations.reschedule`, eventos `AppointmentConfirmed/Cancelled/Rescheduled`, `AppointmentNotificationLookup`; `pacientes.api.PatientLookup/PatientContactLookup`, `FindOrCreatePatientByDni` en `pacientes.domain`; `reservas.api.AvailabilityLookup`; `notificaciones.api.ManagementLinkProvider` (SPI sin implementación); `shared.api.RateLimiter`, `PublicReadRateLimiter`, `PracticeBookingSettingsLookup`, `PracticeLock`.
- **No existe** `booking_token`, ni generador/hasher de tokens, ni puerto público de find-or-create o conteo por contacto, ni alta pública de turno.
- `ModuleBoundaries.ALLOWED_DEPENDENCIES` permite `reservas → agenda, pacientes, notificaciones` (no `auditoria`; eso es C-15). `AgendaApiContractsTest` congela `AppointmentOperations` a **solo** `reschedule`: cambiarlo exige actualizar ese test.
- `AppointmentFixtures.cleanAll()` limpia `notification → appointment → …`: agregar `booking_token` con FK `ON DELETE RESTRICT` exige extender ese helper.
- `AvailabilityPublicControllerIT` muestra el patrón de endpoint público: GET anónimo sin CSRF; mutaciones requieren tomar `X-CSRF-TOKEN` de una respuesta previa y reenviar cabecera + cookie.
- Reglas duras aplicables: inyección por constructor, DTOs record, sin entidades JPA en la API, Flyway-only, deny-by-default, sin PII en logs, tokens solo hasheados, fechas UTC.

## Goals / Non-Goals

**Goals:** reserva anónima completa y segura; token de alta entropía guardado solo hasheado y utilizable por C-15; anti-abuse mínimo; cero PII en logs/respuestas; reutilización del núcleo de turnos y del outbox existente.

**Non-Goals:** UI (C-24), gestión por token (C-15), CAPTCHA/verificación por email (PA-05), texto legal del aviso (PA-10), proveedor SMTP (PA-09), cambiar la política de CSRF global.

## Decisions

### D1 — Migración `V16__booking_token.sql`
Siguiente versión libre: V15 es la última aplicada; V10 está vacía pero agregarla ahora rompería Flyway con `outOfOrder=false`. La primera tarea del apply re-chequea el directorio de migraciones y, si aparece una versión mayor, renumera y actualiza proposal/design/specs/tasks. Esquema:

```
booking_token(
  id uuid PK,
  appointment_id uuid NOT NULL FK appointment ON DELETE RESTRICT,
  token_hash text NOT NULL UNIQUE CHECK (char_length(token_hash) = 64),
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  privacy_notice_version text NOT NULL,
  privacy_accepted_at timestamptz NOT NULL)
INDEX booking_token_appointment_idx ON (appointment_id)
REVOKE DELETE, TRUNCATE ON booking_token FROM ${app_role}
```
`token_hash` es SHA-256 en hexadecimal (64 caracteres). Se permiten **varios tokens por turno** porque C-13 emite uno nuevo en cada correo (D5). `revoked_at` es nullable y no se compara con `expires_at`: un token puede revocarse antes o después de su vencimiento.

### D2 — Generación y verificación del token
`SecureRandom` del JDK, 32 bytes (256 bits) → Base64URL sin padding (43 caracteres). Hash SHA-256 en hexadecimal. La comparación en búsqueda usa `MessageDigest.isEqual` sobre los bytes del hash (tiempo constante), nunca `String.equals`. El token en claro solo existe en memoria durante el armado del link; no se loguea, no se guarda y no se devuelve en la respuesta de reserva.

### D3 — Política de tokens (resuelve la pregunta abierta de C-13 D11)
**Un token por correo, todos ligados al mismo `appointment_id`; sin rotación al reprogramar (C-15).** C-14 crea el primero al reservar; la SPI `ManagementLinkProvider` crea uno nuevo cuando C-13 envía confirmación/recordatorio/reprogramación, porque el claro no se puede recuperar desde el hash. `expires_at = appointment.ends_at + BOOKING_TOKEN_TTL_DAYS` (default 1 día), coherente con la definición de la variable en KB 08 ("vigencia posterior al turno") y con RN-RS-06 ("expira al pasar el turno", con un día de gracia). Se usa el fin y no el inicio para que la vigencia sea posterior al turno, tal como describe KB 08. Así un recordatorio emitido días antes sigue teniendo un link válido. Al cancelar, C-15 revoca **todos** los tokens del turno.

### D4 — Request/Response del endpoint
Record de entrada en `reservas.api`: `serviceId` (UUID), `startsAt` (Instant), `firstName`, `lastName`, `dni`, `phone`, `email`, `website` (honeypot, `@Size(max=...)`) y `privacyAccepted` (Boolean, debe ser `true`). Se rechaza cualquier campo de duración/fin. Salida: `CreateBookingResponse(bookingReference, serviceName, startsAt, endsAt, status, practiceName)` con instantes UTC; sin token, sin id interno del paciente, sin DNI/email/teléfono. El id del turno se devuelve como referencia opaca; C-15 no lo necesita (resuelve por token).

### D5 — Transacción y límites de módulo
El caso de uso en `reservas.application` orquesta, en una sola transacción: validaciones no concurrentes → `AvailabilityLookup` (audiencia `PUBLIC`, misma prestación/fecha y slot dentro de la lista) → rate limit y honeypot (antes de la transacción) → `pacientes.api` find-or-create → `agenda.api.AppointmentOperations.book` (nuevo método, `origin=PUBLIC`, `overbooking=false`, delega en `AppointmentService.book`) → inserción de `booking_token` con hash, expiración y consentimiento. El encolado de notificaciones no entra en la transacción: ya lo hace C-13 con listeners AFTER_COMMIT y transacción nueva (decisión aprobada en C-13 D3), con la ventana de pérdida de milisegundos allí aceptada.

### D6 — Anti-abuso
Rate limit de escrituras con una instancia dedicada que lee `RATE_LIMIT_PUBLIC_PER_MIN` (clave = `remoteAddr`, hasheada por `InMemoryRateLimiter`; nunca `X-Forwarded-For`). Honeypot: si no está vacío, respuesta 200 genérica sin efectos (ni turno, ni paciente, ni token, ni auditoría). Límite de turnos futuros activos por DNI/email/teléfono: constante de dominio `MAX_ACTIVE_FUTURE_BOOKINGS_PER_CONTACT = 3`, provisional mientras PA-05 siga abierta. Se reutiliza `min_notice_min`/`max_advance_days`/`slot_granularity_min` de `PracticeBookingSettingsLookup`.

### D7 — CSRF
No se amplía `ignoringRequestMatchers`. El cliente obtiene `X-CSRF-TOKEN` de cualquier respuesta GET pública (por ejemplo `GET /api/v1/public/services`) y lo reenvía como cabecera + cookie en el POST. Los IT siguen el patrón de `AvailabilityPublicControllerIT:282-298`.

### D8 — Puertos nuevos y arquitectura
- `agenda.api.AppointmentOperations`: agregar `book(BookAppointmentCommand)` y, más adelante (C-15), `cancel`. Delegan en `AppointmentService`, que debe aceptar `origin` y `cancelledBy` como parámetros en lugar de fijarlos a `INTERNAL`/`DENTIST`. Actualizar `AgendaApiContractsTest` (que hoy exige un único método) y los fixtures de arquitectura que correspondan. Además `agenda.api` expone el conteo de turnos futuros activos por contacto: la tabla `appointment` vive en `agenda` y `pacientes → agenda` está prohibido, así que el conteo **no** puede ir en `pacientes.api`.
- `pacientes.api`: nuevo `PatientBookingOperations` con `findOrCreate(...)`, adaptando `FindOrCreatePatientByDni` (que hoy está en `pacientes.domain` y no es invocable desde `reservas`). Devuelve solo identificadores/banderas, nunca entidades ni `PatientSource`. El adaptador vive en `pacientes.infrastructure`.
- `reservas.infrastructure.ManagementLinkProviderAdapter` implementa la SPI de notificaciones.
- Sin cambios de grafo en C-14: `reservas → agenda, pacientes, notificaciones` ya está permitido. `reservas.application` no importa `reservas.api` (regla `applicationIndependent`).

### D9 — Errores
Traducción en la frontera pública: cualquier `APPOINTMENT_SLOT_TAKEN`/`APPOINTMENT_SLOT_BLOCKED`/violación de exclusión → `ConflictException("SLOT_TAKEN")`; `RateLimitedException` → 429 `RATE_LIMITED` con `Retry-After` en segundos; honeypot → 200 genérico; validación → 400 `VALIDATION_FAILED` con `errors[]`; reglas de anticipación/horizonte/límite por contacto → 422 con códigos estables. Sin mensajes de PostgreSQL ni PII en el cuerpo.

### D10 — Auditoría y logs
C-14 no escribe `audit_log` (la acción `BOOKING_TOKEN_USE` es de C-15). Logs solo con `appointmentId`, `patientId`, `serviceId`, `practiceId` y códigos. El evento `AuditEvent` de C-13 no incluye token (ya validado por sus tests).

### D11 — Orden de pruebas (TDD estricto)
1) Número de migración y baseline verde; 2) RED/GREEN del esquema y privilegios V16 (incluyendo migración escalonada estilo `AppointmentSchemaIT:236-263`); 3) extender `AppointmentFixtures.cleanAll()`; 4) dominio puro (generador/hasher/validador de token y validaciones de request); 5) puertos y adaptadores (contract tests); 6) caso de uso con fakes; 7) SPI de link; 8) endpoint IT con CSRF/rate limit/honeypot/409; 9) arquitectura; 10) suite completa `./gradlew check`.

## Risks / Trade-offs

- [C-13 ya aprobó AFTER_COMMIT: el encolado no es atómico con la reserva] → ventana de milisegundos, aceptada aguas arriba; no se reabre aquí.
- [Varios tokens por turno → más filas y superficie de revocación] → todas se revocan juntas en C-15;necessary para links de recordatorios.
- [Constante de límite por contacto sin validar negocio] → PA-05 sigue abierta; se documenta como suposición y se aísla en una constante.
- [Texto del aviso de privacidad TBD] → se guarda solo la versión `PRIVACY_NOTICE_V1` y el instante; el texto lo define PA-10 sin migración adicional si se cambia solo el contenido.
- [Cambio de `AppointmentOperations` rompe `AgendaApiContractsTest`] → se actualiza el test contractual en la misma tarea; es un contrato intencional del change.
- [FK `booking_token → appointment RESTRICT` rompe limpieza de IT] → extender `AppointmentFixtures.cleanAll()` antes de cualquier IT que cree turnos.

## Migration Plan

1. Traer `master` actualizado y verificar la última versión de migrated; usar la siguiente libre (hoy V16).
2. Desplegar código y migración juntos: Flyway crea la tabla; no hay datos a migrar.
3. Rollback: revertir código; la tabla queda vacía o con filas huérfanas solo si se despliega la migración sin el código (evitar). No se edita V16 una vez aplicada.

## Open Questions

- PA-05: medidas definitivas anti-abuso (CAPTCHA, verificación de email) y valor del límite por contacto.
- PA-10: texto y versión definitiva del aviso de privacidad.
- PA-09: proveedor SMTP (no afecta el token).

## Follow-ups

- C-15 consume `booking_token`, revoca tokens y escribe `auditoria.api`.
- C-24 consume la respuesta de confirmación; C-25 consume los endpoints de gestión.
