# Design: C-15 `booking-token-management`

> **Plan approval:** Pendiente de aprobación formal (governance CRÍTICO). El usuario solicitó en esta sesión generar primero los artefactos y luego ejecutar el apply; este es el registro de decisiones para esa revisión.

## UI Mockups

- Design system revision: 1 (`design-system/README.md`, aprobado)
- Decision: no (no UI) — 2026-11-23
- Mockups: n/a. El change es backend puro; la pantalla `/turno/{token}` es C-25.
- Approval: no aplica
- Preview: no aplica
- Visual verify: no aplica

## Context

Estado real verificado en `HEAD 77857f0`:

- C-14 crea `booking_token` en V16 con `token_hash`, `expires_at`, `revoked_at` y un token por correo ligado al mismo `appointment_id` (design C-14 D3).
- `agenda.api.AppointmentOperations` hoy solo expone `reschedule`; C-14 lo amplía con `book`. C-15 debe ampliarlo con `cancel`, delegando en `AppointmentService.cancel` para obtener `cancelled_by = PATIENT` y disparar el `DENTIST_CANCELLATION` de C-13.
- `AppointmentService.book` fija `INTERNAL` y `cancel` fija `DENTIST`; ambos enums ya soportan `PUBLIC`/`PATIENT` y V13 los acepta. C-14 parametriza `origin`; C-15 parametriza `cancelledBy`.
- La reprogramación de C-11 es in-place y conserva el `id`: el token sigue ligado al mismo turno, sin re-vincularlo ni rotarlo.
- `reservas.api.AvailabilityLookup` (C-12) ya aplica granularidad, anticipación, horizonte, horario, bloqueos y `public_visible` con audiencia `PUBLIC`; se reutiliza, no se reimplementa.
- `SecurityConfig:54` habilita `/api/v1/public/**` sin sesión; el CSRF solo se ignora en `POST /api/v1/auth/login`. `CsrfHeaderFilter` expone `X-CSRF-TOKEN`. `SecurityHeadersFilter:24` ya envía `Referrer-Policy: no-referrer`.
- `ModuleBoundaries.ALLOWED_DEPENDENCIES` **no** permite `reservas → auditoria`: hay que habilitarlo (patrón ya aprobado en C-13 D2 para `notificaciones`).
- V3 ya incluye `BOOKING_TOKEN_USE` en el CHECK de acciones y una restricción anti-PII en `details`; `SensitiveDataSanitizer` solo admite `outcome`, `reason_code`, `attempt`, `entity_version`, `field_names`, `duration_ms`, `source`.
- `RATE_LIMIT_PUBLIC_PER_MIN` está declarada pero hoy no es leída por `src/main`; C-15 necesita una instancia dedicada (no el `RateLimiter` de login, que es de 10 intentos).
- `AvailabilityArchitectureTest` congela el grafo actual y `AgendaApiContractsTest` congela los contratos de `agenda.api`: ambos deben actualizarse.

## Goals / Non-Goals

**Goals:** que el link del email permita ver/cancelar/reprogramar solo ese turno; errores indistinguibles para tokens inválidos; atomicidad en reprogramación; auditoría sin PII; reutilización de los núcleos de C-11/C-12/C-13.

**Non-Goals:** UI (C-25), emisión de tokens (C-14), política de cancelación o seña, acceso a historia clínica, cambios en el outbox.

## Decisions

### D1 — Rutas y CSRF
Tres endpoints bajo `/api/v1/public/**` con el token en el path. No se amplía la exención de CSRF: el cliente toma `X-CSRF-TOKEN` de un GET público previo y lo reenvía como cabecera + cookie, igual que en `AvailabilityPublicControllerIT:282-298`. Las respuestas conservan `Referrer-Policy: no-referrer` global.

### D2 — Resolución del token
Se usa la misma función canónica de hash de C-14 (SHA-256 sobre el token en claro, hexadecimal de 64 caracteres) y `MessageDigest.isEqual` sobre los bytes del hash para la comparación en tiempo constante. La consulta filtra por `token_hash`, excluye filas con `revoked_at NOT NULL` y exige `expires_at > now` (reloj inyectado). Cualquier fallo (inexistente, expirado, revocado, formato inválido) se traduce a un único `NotFoundException("INVALID_OR_EXPIRED_TOKEN")` → 404 con cuerpo genérico.

### D3 — Vista acotada y turno pasado
`BookingView` expone solo: referencia del turno, `startsAt`/`endsAt` UTC, nombre de la prestación, nombre del consultorio, estado y dos banderas (`cancellable`, `reschedulable`). Un turno con `startsAt <= now` o no `CONFIRMED` tiene ambas en falso; las mutaciones se rechazan con 422 `BOOKING_MODIFICATION_NOT_ALLOWED`. Nunca se devuelven DNI, email, teléfono, notas ni datos clínicos.

### D4 — Cancelación
`AppointmentOperations.cancel(UUID appointmentId, CancelledBy by)` delega en `AppointmentService.cancel`, parametrizando `cancelledBy` en lugar de fijarlo a `DENTIST`. El caso de uso, en una transacción: resuelve token → cancela → revoca (`revoked_at = now`) **todos** los tokens del turno → auditoría. Los eventos AFTER_COMMIT de C-11/C-13 encolan `CANCELLATION` y `DENTIST_CANCELLATION`. Un fallo de auditoría revierte la operación (fail-closed, coherente con `AuditLoggerService`).

### D5 — Reprogramación
En una transacción: resuelve token → exige bandera `reschedulable` → consulta `AvailabilityLookup` con `AvailabilityAudience.PUBLIC` para la fecha local del nuevo inicio y verifica que el par `(startsAt, endsAt)` esté en la lista → llama a `AppointmentOperations.reschedule(id, newStartsAt, false)` → auditoría. El servidor calcula el fin con la duración snapshot del turno; no se acepta `endsAt` del cliente. Si el slot se ocupó, `ConflictException("APPOINTMENT_SLOT_TAKEN")` se traduce a 409 `SLOT_TAKEN` y la transacción revierte, conservando el original (RN-RS-09). El token **no se rota** (misma fila sigue vigente; C-11 conserva el `appointment_id`). Se actualiza el `expires_at` del token para que siga cubriendo el nuevo inicio más `BOOKING_TOKEN_TTL_DAYS`.

### D6 — Grafo de módulos
Habilitar `reservas → auditoria` en `ModuleBoundaries.ALLOWED_DEPENDENCIES`, solo hacia `auditoria.api`. Actualizar en la misma tarea: KB 08 §Reglas de dependencia, `openspec/specs/module-boundaries/spec.md` (vía sync al archivar), `ModuleBoundariesRulesTest` (nuevo fixture `ok_reservas_auditoria` y negativos), `AvailabilityArchitectureTest` y `ProductionArchitectureTest`. Auditoría sigue sin poder alcanzar módulos de negocio.

### D7 — Rate limit anti-enumeración
Instancia dedicada (no el bean `RateLimiter` de login) que lee `RATE_LIMIT_PUBLIC_PER_MIN`, con clave `remoteAddr` hasheada por `InMemoryRateLimiter`, igual que `PublicReadRateLimiter`. Se aplica a los tres endpoints. 429 `RATE_LIMITED` con `Retry-After` calculado con `RateLimiter.retryAfterSeconds`. Nunca se usa `X-Forwarded-For` como clave.

### D8 — Auditoría
`AuditEvent.of(BOOKING_TOKEN_USE, null, patientId, "booking_token", appointmentId.toString(), ip, userAgent, Map.of("outcome", ..., "reason_code", ...))`. El `ip` pasa por el validador de `AuditEvent`; los `details` solo usan claves permitidas. Sin token en claro en ningún `details`, log ni excepción.

### D9 — Errores
Traducción centralizada en la frontera pública: token inválido → 404 `INVALID_OR_EXPIRED_TOKEN`; slot ocupado/bloqueado → 409 `SLOT_TAKEN`; turno pasado → 422 `BOOKING_MODIFICATION_NOT_ALLOWED`; enumeration → 429 `RATE_LIMITED` + `Retry-After`; CSRF → 403 `CSRF_REQUIRED`. `ProblemDetails` ya fija títulos genéricos y `ApiExceptionHandler` no incluye `detail` dinámico.

### D10 — Orden de pruebas (TDD estricto)
1) Safety net y revisión de grafo/contratos congelados; 2) RED/GREEN del repositorio de tokens (búsqueda por hash, expiración, revocación); 3) dominio de resolución y vista; 4) ampliación de `agenda.api` (cancel) y su test contractual; 5) grafo `reservas → auditoria` con fixtures; 6) caso de uso con fakes (cancel, reprogramar OK/conflicto, expirado/revocado, turno pasado, auditoría); 7) rate limit; 8) endpoints IT con CSRF/headers/PII; 9) arquitectura; 10) `./gradlew check` completo.

## Risks / Trade-offs

- [Token sin rotación tras reprogramar] → simplifica el vínculo y respeta RN-RS-06; un token filtrado sigue válido hasta la expiración, se mitiga con revocación al cancelar.
- [Varios tokens por turno] → la cancelación revoca todos en una sola actualización; se prueba explícitamente.
- [Cambio de grafo de arquitectura] → afecta `AvailabilityArchitectureTest`, fixtures y KB; se actualizan en la misma tarea y se verifica `./gradlew test`.
- [Auditoría fail-closed] → un fallo de `audit_log` rechaza la operación del paciente; es el comportamiento ya adoptado por el resto del sistema.
- [CSRF sin exención] → el SPA debe hacer un GET previo; se documenta para C-25.
- [`AgendaApiContractsTest` congela `AppointmentOperations`] → se actualiza el contrato en la tarea correspondiente sin relajar las aserciones anti-PII.

## Migration Plan

1. Sin migración nueva: se usan las columnas de `V16__booking_token.sql` (C-14).
2. Desplegar después de C-14; si un token no tiene `expires_at`, se rechaza con el error genérico (no se inventa un valor por defecto).
3. Rollback: revertir código; las revocaciones ya escritas en `booking_token` no se deshacen (son auditables y no destructivas).

## Open Questions

- PA-05: política definitiva anti-abuso y valores de límite.
- Confirmar con negocio si el token debe rotarse tras reprogramar; hoy se mantiene por decisión D5 y por conservación del `appointment_id` en C-11.

## Follow-ups

- C-25 consume estos tres endpoints y respeta CSRF/`Referrer-Policy`.
- C-13 envía `CANCELLATION`, `DENTIST_CANCELLATION` y `RESCHEDULE` a partir de los eventos disparados aquí.
