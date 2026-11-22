# Proposal: C-08 `working-hours-booking-settings`

## Why

El odontólogo necesita definir sus días y franjas horarias de atención habituales (US-010, RN-AG-03) y los parámetros operativos de la reserva online (granularidad de slots, anticipación mínima, horizonte máximo y ventana de recordatorio por email, US-013, RN-AG-06, RN-RS-10, PA-19) para que el motor de disponibilidad (C-12) y la reserva pública (C-14) puedan ofrecer turnos válidos que no interfieran con la organización del consultorio. Sin este change no se pueden implementar los bloqueos de agenda (C-09), el motor de disponibilidad ni la pantalla de configuración del profesional (C-19).

Governance: **MEDIO** (reglas de negocio de agenda y parámetros del consultorio).

## What Changes

- **Migración V5 (`V5__working_hours_and_booking_settings.sql`)**:
  - Creación de tabla `working_hours` (`id` UUID PK, `practice_id` UUID NOT NULL FK a `practice`, `weekday` smallint NOT NULL CHECK entre 1 y 7, `start_time` time NOT NULL, `end_time` time NOT NULL CHECK `end_time > start_time`).
  - Extensión de tabla `practice` con columnas de configuración de reserva: `slot_granularity_min` (int NOT NULL DEFAULT 15 CHECK en 10, 15, 20, 30, 60), `min_notice_min` (int NOT NULL DEFAULT 120 CHECK >= 0), `max_advance_days` (int NOT NULL DEFAULT 30 CHECK BETWEEN 1 AND 365), y `reminder_hours_before` (int NOT NULL DEFAULT 24 CHECK BETWEEN 1 AND 168).
  - Privilegios sobre `working_hours`: rol de aplicación (`${app_role}`) recibe SELECT, INSERT, UPDATE, DELETE para permitir configuración completa de tramos por el profesional.
- **Endpoints REST protegidos (rol `DENTIST`)**:
  - `GET /api/v1/working-hours`: lista tramos de atención del consultorio ordenados por `weekday` y `start_time`.
  - `PUT /api/v1/working-hours`: reemplaza el conjunto de tramos semanales del consultorio en una sola transacción. Valida que no haya solapamiento entre tramos de un mismo día (`RN-AG-03`), que `end_time > start_time`, y que los horarios respeten la granularidad configurada. Emite aviso de turnos confirmados existentes afectados (si los hubiera) sin cancelarlos (US-010).
  - `GET /api/v1/practice/settings`: devuelve la configuración operativa y datos generales del consultorio (`id`, `name`, `timezone`, `contactEmail`, `contactPhone`, `address`, `slotGranularityMin`, `minNoticeMin`, `maxAdvanceDays`, `reminderHoursBefore`).
  - `PUT /api/v1/practice/settings`: actualiza la configuración y datos de contacto de `practice` con validaciones estrictas (Bean Validation).
- **Protección y seguridad**:
  - Todos los endpoints requieren autenticación activa con rol `DENTIST`.
  - Operaciones de modificación (`PUT`) exigen validación CSRF (`X-XSRF-TOKEN`).
  - Errores de validación devuelven `400 VALIDATION_FAILED` con formato ProblemDetails estándar.
- **Módulos y arquitectura**:
  - Módulo `agenda`: entidad `WorkingHours`, repositorio `WorkingHoursRepository`, casos de uso de consulta y actualización de horarios, controlador `WorkingHoursController`.
  - Módulo `shared`: actualización de entidad `Practice`, DTOs de configuración, caso de uso y controlador `PracticeSettingsController`.
  - Fronteras ArchUnit verificadas sin dependencias cruzadas indebidas.

## Capabilities

### New Capabilities
- `working-hours`: definición y consulta de tramos semanales de atención habitual del consultorio, con validación de no solapamiento por día y consistencia horaria.
- `booking-settings`: configuración de parámetros operativos de turnos y reservas (`slot_granularity_min`, `min_notice_min`, `max_advance_days`, `reminder_hours_before`) y datos del consultorio.

### Modified Capabilities
- `database-migrations`: documenta la migración V5 para `working_hours` y parámetros de reserva en `practice`.
- `practice-installation`: amplía la entidad y tabla `practice` con los parámetros operativos de reserva y su actualización.
- `api-access-policy`: incorpora los endpoints `/api/v1/working-hours` y `/api/v1/practice/settings` bajo la política deny-by-default (requiere rol `DENTIST` y CSRF).

## Impact

- **Base de datos**: nueva migración `V5__working_hours_and_booking_settings.sql`; `practice` gana 4 columnas con defaults sensatos; nueva tabla `working_hours`.
- **Backend**:
  - Paquete `ar.turnosodontologia.consultorio.agenda`: clases de dominio, aplicación, infraestructura y API para horarios de atención.
  - Paquete `ar.turnosodontologia.consultorio.shared`: campos y métodos en `Practice`, DTOs y controlador de settings.
- **Tests**: tests unitarios para reglas de negocio de tramos y solapamientos, tests de arquitectura ArchUnit, tests de integración con PostgreSQL embebido zonky (`WorkingHoursIT`, `PracticeSettingsIT`).
- **Dependencias posteriores**: habilita C-09 (`schedule-blocks`), C-11 (`appointments-core`), C-12 (`availability-engine`), y C-19 (`settings-ui`).
