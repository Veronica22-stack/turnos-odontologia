# Proposal: C-13 `email-notifications-outbox`

## Why

C-11 ya publica `AppointmentConfirmed`, `AppointmentCancelled` y `AppointmentRescheduled`, pero nadie los escucha: el paciente no recibe comprobante ni recordatorio (US-040, US-041) y el módulo `notificaciones` está vacío. Sin este change no se cumplen RN-NO-01..06 y C-14/C-15 (reserva pública y gestión por token) no tienen por dónde avisar al paciente. Governance: **ALTO** (sistema de notificaciones, datos personales de contacto): el plan se revisa y aprueba antes de cualquier apply.

## What Changes

- **Migración `V14__notification.sql`** (número a re-chequear contra `master` en el apply: C-12, C-14 y C-19 pueden reclamar V14; Flyway corre con `outOfOrder=false`). Tabla `notification` (outbox) con `practice_id`, `appointment_id` (FK `RESTRICT`), `type` (`CONFIRMATION`, `REMINDER`, `RESCHEDULE`, `CANCELLATION`, `DENTIST_CANCELLATION`), `channel` (`EMAIL`), `status` (`PENDING`, `SENT`, `FAILED`, `DISCARDED`), `scheduled_for`, `sent_at`, `attempts`, `last_error` (solo un código, nunca el mensaje del proveedor), `UNIQUE (appointment_id, type)`, índice parcial de pendientes y `REVOKE DELETE, TRUNCATE` al rol de aplicación.
- **Encolado por eventos** en `notificaciones`: listeners `@TransactionalEventListener(AFTER_COMMIT)` que, en una transacción nueva, encolan confirmación inmediata, recordatorio (`starts_at − practice.reminder_hours_before`), email de reprogramación + recálculo del recordatorio, y email de cancelación + descarte de pendientes (RN-NO-04). Un error al encolar o enviar **nunca** revierte ni hace fallar la operación del turno (RN-NO-05).
- **Despacho**: scheduler (cada 5 min por defecto, configurable) + disparo inmediato asíncrono tras encolar. Cada notificación se toma con `SELECT ... FOR UPDATE SKIP LOCKED` en su propia transacción; idempotencia por estado y por `UNIQUE (appointment_id, type)`; reintentos con backoff exponencial (5, 10, 20, 40 min; tope 1 h) hasta 5 intentos y luego `FAILED` con alerta en el log. Antes de enviar se revalida el turno (un recordatorio de un turno cancelado o ya iniciado se descarta).
- **Puerto `EmailSender`** con dos adaptadores: `log` (desarrollo, DD-10: registra solo IDs, tipo y plantilla; **no** destinatario ni cuerpo) y `smtp` (Jakarta Mail vía `spring-boot-starter-mail`, timeouts acotados, clasificación de fallos transitorios/permanentes). Proveedor definitivo TBD (PA-09). Selección por `MAIL_PROVIDER`.
- **Plantillas de texto plano en español** sin datos clínicos ni datos personales del paciente (sin nombre, DNI, teléfono): fecha y hora en la zona del consultorio, prestación, consultorio (nombre, dirección, teléfono de contacto) y link de gestión `APP_BASE_URL/turno/{token}` **solo si** hay un proveedor de tokens (llega en C-14); mientras no exista, el email indica contactar al consultorio (RN-NO-06).
- **Aviso al odontólogo** (Suposición, PA-14) cuando el **paciente** cancela (`cancelledBy = PATIENT`, que recién ocurre con C-15), enviado a `practice.contact_email`.
- **Contratos nuevos de solo lectura** para resolver datos al momento del envío: `agenda.api.AppointmentNotificationLookup` (estado, instantes y nombre de la prestación) y `pacientes.api.PatientContactLookup` (solo el email). Extensión opcional `notificaciones.api.ManagementLinkProvider` para C-14.
- **Fronteras de módulos**: se habilita `notificaciones → agenda`, `pacientes` (solo por sus `api`). Sin ciclos.
- **Configuración**: `MAIL_PROVIDER` (obligatoria; `log` por defecto solo en el perfil `dev`), `MAIL_HOST/PORT/USER/PASSWORD/FROM` (obligatorias con `smtp`), `NOTIFICATION_POLL_INTERVAL`, `NOTIFICATION_MAX_ATTEMPTS`, `NOTIFICATION_DISPATCH_ENABLED`. La anticipación del recordatorio sale de `practice.reminder_hours_before` (C-08, editable): **se elimina `REMINDER_HOURS_BEFORE`** de `.env.example` y de KB 08.
- **Tests (plan)**: TDD estricto. Idempotencia, backoff, cancelación anula recordatorio, reprogramación recalcula, caída del proveedor (la reserva queda y se reintenta), concurrencia real de dos despachadores con `SKIP LOCKED`, ausencia de PII en logs y en `last_error`.

### Fuera de alcance

- Tokens de gestión y página `/turno/{token}` (C-14/C-15); el link queda preparado detrás de `ManagementLinkProvider`.
- Elección del proveedor transaccional definitivo, SPF/DKIM y dominio (PA-09, PA-16); WhatsApp (v2).
- UI para ver el estado de las notificaciones o reenviar; reenvío manual.
- Recalcular recordatorios pendientes cuando el odontólogo cambia `reminder_hours_before` (aplica a turnos nuevos o reprogramados).
- Reconciliación de turnos cuyo encolado se perdió por una caída entre el commit y el listener (ver design D3).
- Retención/purga de la tabla `notification`; emails HTML.
- Actualización de `CHANGES.md` (se hace en el archive).

## Capabilities

### New Capabilities
- `appointment-notifications`: outbox de notificaciones por email de turnos: qué se encola ante cada evento, cuándo se envía, revalidación al enviar, idempotencia, reintentos con backoff, aislamiento de fallos respecto del turno, contenido de las plantillas sin datos clínicos, link de gestión opcional, aviso al odontólogo, adaptadores de envío (log/SMTP) y privacidad en logs.

### Modified Capabilities
- `database-migrations`: esquema `notification` en `V14`, valores controlados, unicidad por turno y tipo, y privilegios sin borrado físico.
- `module-boundaries`: grafo permitido con `notificaciones → agenda`, `pacientes`.
- `appointments`: consulta de un turno para notificaciones expuesta en `agenda.api`.
- `patients-management`: consulta del email de contacto del paciente expuesta en `pacientes.api`.
- `runtime-configuration`: configuración de email obligatoria y validada al arrancar; secretos SMTP solo por entorno.

## Impact

- **Base de datos**: nueva `V14__notification.sql` (re-chequear número); V1–V13 intactas. La FK `notification.appointment_id → appointment` obliga a ajustar los helpers de limpieza de los tests que vacían `appointment`.
- **Backend**: código nuevo en `notificaciones.{api,application,domain,infrastructure}`; nuevos puertos de lectura en `agenda.api` y `pacientes.api` con adaptadores en sus `infrastructure`; `@EnableScheduling`/`@EnableAsync` acotados al módulo; `ModuleBoundaries.ALLOWED_DEPENDENCIES` suma `NOTIFICACIONES → AGENDA, PACIENTES`.
- **Dependencias**: **nueva** `spring-boot-starter-mail` (requiere aprobación). Sin dependencias nuevas de test.
- **Configuración**: `.env.example`, `application.yml`, `application-dev.yml`, `RequiredConfiguration` y propiedades de test (`EmbeddedPostgresSupport`); KB 08 §Variables de entorno y §Reglas de dependencia.
- **Contratos para otros changes**: C-14 implementa `ManagementLinkProvider` (link en los emails); C-15 produce `cancelledBy = PATIENT` (dispara el aviso al odontólogo); C-27 define el proveedor SMTP real.
