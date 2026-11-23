# Proposal: C-14 `public-booking-api`

## Why

C-07 a C-13 ya dejaron listas las prestaciones públicas, los horarios, los bloqueos, el motor de disponibilidad, los turnos y el outbox de emails, pero un paciente anónimo todavía no puede reservar: falta `POST /api/v1/public/bookings` y el token de gestión con el que C-15 permitirá ver, cancelar y reprogramar (US-030, Flujo 3, RN-RS-01..08). Es el último change del camino crítico antes de la gestión por token y de las UIs públicas (C-24/C-25). Governance: **CRÍTICO** (endpoint público, tokens, datos personales); el plan se revisa antes del apply.

## What Changes

- **`POST /api/v1/public/bookings`**: sin autenticación, con CSRF double-submit vigente (no se abre exención), rate limit por IP, honeypot, validación de formato, de anticipación/horizonte/granularidad y de visibilidad pública de la prestación. Respuesta genérica ante honeypot; 429 + `Retry-After` ante rate limit; 409 `SLOT_TAKEN` ante slot ocupado.
- **Migración `V16__booking_token.sql`** (siguiente versión libre sobre `master` al 2026-11-23; V1–V15 quedan intactas y Flyway corre con `outOfOrder=false`): `booking_token(id, appointment_id, token_hash UNIQUE, expires_at, revoked_at, created_at, privacy_notice_version, privacy_accepted_at)` con `REVOKE DELETE, TRUNCATE` al rol de aplicación. Se guarda **solo el hash SHA-256**; el token en claro (CSPRNG ≥256 bits, Base64URL) viaja únicamente en el email. Varios tokens por turno: C-13 emite uno nuevo en cada email mediante la SPI.
- **Transacción de reserva**: reutilizar `FindOrCreatePatientByDni` (RN-PA-03, sin sobreescritura) y la alta de turno de `agenda` con `origin = PUBLIC` a través de una extensión de `agenda.api.AppointmentOperations` que delega en `AppointmentService.book` (mismo lock por consultorio, validaciones, traducción de errores y eventos). El encolado de `CONFIRMATION`/`REMINDER` lo hacen los listeners de C-13 (AFTER_COMMIT), según lo ya aprobado en C-13 D3.
- **Contratos entre módulos**: ampliar `agenda.api.AppointmentOperations` con el alta pública y una consulta de conteo de turnos futuros activos por contacto (vive en `agenda`, donde está `appointment`; `pacientes → agenda` está prohibido por el grafo); exponer en `pacientes.api` la búsqueda/creación por DNI sin sobreescritura; implementar `notificaciones.api.ManagementLinkProvider` en `reservas` para el link `/turno/{token}` (resuelve la pregunta abierta de C-13 D11).
- **Anti-abuso (PA-05, provisorio)**: límite de turnos futuros activos por contacto (constante de dominio, valor inicial 3), honeypot y rate limit con la variable existente `RATE_LIMIT_PUBLIC_PER_MIN`. Las medidas definitivas siguen pendientes de PA-05.
- **Aviso de privacidad**: campo obligatorio de aceptación en la reserva y registro de la versión del aviso en `booking_token` (texto TBD, PA-10).
- **Seguridad y privacidad**: ningún token en claro, DNI, email ni teléfono en logs; auditoría sin PII; `Referrer-Policy: no-referrer` ya la aplica el filtro global.
- **Tests (TDD)**: reserva correcta, carrera de slots (409), honeypot, rate limit, límite por contacto, no sobreescritura de paciente existente, hash/expiración del token, SPI de link y verificación de arquitectura.

### Fuera de alcance

- `GET`/`DELETE`/`POST .../reschedule` por token, revocación y auditoría `BOOKING_TOKEN_USE` (C-15).
- UI de reserva y confirmación (C-24), proveedor SMTP definitivo (PA-09/PA-27), CAPTCHA o verificación por email (PA-05), texto legal del aviso (PA-10).
- Actualización de `CHANGES.md` (se hace al archivar).

## Capabilities

### New Capabilities
- `public-booking`: reserva anónima de turnos: validaciones de entrada, mecanismos anti-abuso, aceptación del aviso de privacidad, transacción de alta de paciente/turno/token y contrato de respuesta, incluidos los códigos de error públicos.

### Modified Capabilities
- `database-migrations`: esquema `booking_token` en `V16` y privilegios sin borrado físico.
- `appointments`: alta pública de turno (`origin = PUBLIC`) expuesta en `agenda.api.AppointmentOperations`, sin segundo camino de escritura.
- `patients-management`: operación de find-or-create por DNI expuesta en `pacientes.api`, sin sobreescritura ni exposición de datos personales.
- `module-boundaries`: nuevas dependencias de `reservas` hacia `agenda.api` y `pacientes.api` (alta/cancelación y pacientes) y extensión de esas `api`; sin ciclos.
- `api-access-policy`: `POST /api/v1/public/bookings` como endpoint público con CSRF y `Referrer-Policy: no-referrer`.
- `runtime-configuration`: uso de `RATE_LIMIT_PUBLIC_PER_MIN` para el endpoint de reserva y de `BOOKING_TOKEN_TTL_DAYS` para la vigencia del token.
- `error-responses`: códigos estables `SLOT_TAKEN`, `BOOKING_RATE_LIMITED`/`RATE_LIMITED` y respuestas genéricas del endpoint público.

## Impact

- **Base de datos**: nueva `V16__booking_token.sql`; V1–V15 intactas. FK a `appointment` con `ON DELETE RESTRICT`, por lo que los helpers de limpieza de IT que vacían `appointment` deberán limpiar antes `booking_token`.
- **Backend**: código nuevo en `reservas.{api,application,domain,infrastructure}`; extensiones en `agenda.api`/`agenda.infrastructure` y `pacientes.api`/`pacientes.infrastructure`; adaptador de `ManagementLinkProvider`; `ModuleBoundaries.ALLOWED_DEPENDENCIES` ya permite `reservas → agenda, pacientes, notificaciones` (sin cambios de grafo en este change).
- **Dependencias**: ninguna nueva. Se reutilizan `SecureRandom` (JDK), `MessageDigest` (JDK), `RateLimiter`, `AuditLogger` (vía C-15) y los contratos de C-11/C-12/C-13.
- **Configuración**: sin variables nuevas; se usan las ya declaradas en `.env.example`/KB 08. El número de migración se re-chequea en la primera tarea del apply.
- **Contratos para otros changes**: C-15 consume los tokens emitidos aquí para `GET`/`DELETE`/reschedule y registra `BOOKING_TOKEN_USE`; C-24 consume la respuesta de confirmación.
