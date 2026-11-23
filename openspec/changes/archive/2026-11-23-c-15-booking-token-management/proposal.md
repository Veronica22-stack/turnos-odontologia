# Proposal: C-15 `booking-token-management`

## Why

C-14 creará el turno y el `booking_token`, y C-13 pondrá el link `/turno/{token}` en el email, pero el paciente todavía no tiene forma de usar ese link: faltan las operaciones autogestionadas de ver, cancelar y reprogramar (US-031, US-032, Flujo 4, RN-RS-04..09). Sin este change el token no sirve, el consultorio sigue recibiendo llamadas y la cancelación nunca registra `cancelledBy = PATIENT` (lo que en C-13 deja sin disparador el aviso al odontólogo). Governance: **CRÍTICO** (endpoint público, tokens, datos personales); el plan se revisa antes del apply.

## What Changes

- **Tres endpoints públicos** con el token en la URL y CSRF double-submit vigente (sin exención): `GET /api/v1/public/bookings/{token}` (solo lectura), `DELETE /api/v1/public/bookings/{token}` (cancelar) y `POST /api/v1/public/bookings/{token}/reschedule` (reprogramar).
- **Búsqueda por hash en tiempo constante** (`MessageDigest.isEqual`) sobre `booking_token.token_hash`, validando expiración y revocación; mensaje único y genérico para token inválido, expirado o revocado (sin revelar si el turno existió); rate limit anti-enumeración por IP; `Referrer-Policy: no-referrer` ya la garantiza el filtro global.
- **Vista acotada del turno**: solo identificadores opacos, instantes UTC y nombre de la prestación y del consultorio; nunca DNI, email, teléfono, notas ni datos clínicos. Un turno ya pasado es de solo lectura.
- **Cancelación**: extender `agenda.api.AppointmentOperations` con `cancel(appointmentId, CancelledBy.PATIENT)` delegando en `AppointmentService.cancel` (misma máquina de estados y eventos de C-11), revocar todos los tokens del turno y publicar el evento que C-13 usa para `CANCELLATION` y `DENTIST_CANCELLATION`.
- **Reprogramación atómica**: validar el nuevo slot con `AvailabilityLookup` (audiencia pública: granularidad, anticipación mínima, horizonte, horario y `public_visible`) y delegar en `AppointmentOperations.reschedule`; si el slot se ocupó, 409 `SLOT_TAKEN` y el turno original queda intacto (RN-RS-09). Se mantiene el mismo `appointment_id` y los mismos tokens (RN-RS-06); no se rota el token. C-13 recalcula el recordatorio y envía la nueva confirmación.
- **Auditoría `BOOKING_TOKEN_USE`** en cada uso del token con `details` cerrado (`outcome`, `reason_code`), sin token, DNI ni contacto; para ello se habilita la arista `reservas → auditoria` en `ModuleBoundaries.ALLOWED_DEPENDENCIES` (con el mismo patrón aprobado en C-13 D2) y se actualizan KB 08, la spec `module-boundaries` y sus tests de regla/fixtures.
- **Contrato de errores**: 404 genérico para token inválido/expirado/revocado, 409 `SLOT_TAKEN` para conflicto de slot, 429 + `Retry-After` para enumeración, 422 para modificación de un turno pasado; todos en `application/problem+json`.
- **Tests (TDD)**: cancelar, reprogramar con éxito y con conflicto, token expirado/revocado, sin acceso a datos ajenos, turno pasado solo lectura, rate limit, ausencia de PII en respuestas/logs/auditoría y verificación de arquitectura.

### Fuera de alcance

- Emisión de tokens (C-14), UI `/turno/{token}` (C-25), polling/estado de notificaciones, política de cancelación o seña (v1 sin ella), change de contraseña o datos del paciente.
- Actualización de `CHANGES.md` (se hace al archivar).

## Capabilities

### New Capabilities
- `booking-token-management`: resolución segura del token de gestión, vista acotada del turno, cancelación y reprogramación autogestionadas, revocación/expiración, errores genéricos, rate limit anti-enumeración y auditoría del uso del token.

### Modified Capabilities
- `appointments`: cancelación por el paciente (`CancelledBy.PATIENT`) expuesta en `agenda.api.AppointmentOperations`, reutilizando la máquina de estados y los eventos de C-11.
- `module-boundaries`: se habilita `reservas → auditoria` (solo `auditoria.api`) junto con las reglas, fixtures y tests que la verifican.
- `audit-log`: `BOOKING_TOKEN_USE` pasa a ser emitido por `reservas` en cada uso del token, con `details` sin PII.
- `api-access-policy`: tres nuevos endpoints bajo `/api/v1/public/**`, sin autenticación, con CSRF y cabeceras de seguridad existentes.
- `error-responses`: `code` estable y genérico para token inválido/expirado/revocado, `SLOT_TAKEN` y `RATE_LIMITED` con `Retry-After`.
- `runtime-configuration`: rate limit anti-enumeración con la variable existente `RATE_LIMIT_PUBLIC_PER_MIN`.

## Impact

- **Base de datos**: ninguna migración nueva; se usan las columnas de `V16__booking_token.sql` creada en C-14 (`token_hash`, `expires_at`, `revoked_at`). Solo se actualizan filas (`revoked_at`).
- **Backend**: código nuevo en `reservas.{api,application,domain,infrastructure}`; extensión de `agenda.api.AppointmentOperations` y su adaptador; `ModuleBoundaries.ALLOWED_DEPENDENCIES` y sus tests/fixtures; `EmbeddedPostgresSupport` si hace falta un limitador dedicado.
- **Dependencias**: ninguna nueva. Reutiliza `AvailabilityLookup`, `AppointmentOperations`, los eventos de C-11/C-13, `RateLimiter` y `AuditLogger`.
- **Configuración**: sin variables nuevas. El plazo de vigencia del token proviene de `BOOKING_TOKEN_TTL_DAYS` (C-14).
- **Contratos para otros changes**: C-25 consume estos endpoints; C-13 recibe los eventos de cancelación/reprogramación para los emails y el aviso al odontólogo.
