# Proposal: C-07 `services-catalog`

## Why

El odontólogo necesita mantener el catálogo de prestaciones que ofrece su consultorio, cada una con su duración real (US-011, RN-AG-05), para que los turnos ocupen el tiempo correcto y para que la reserva pública (C-14) pueda ofrecer al paciente solo las prestaciones habilitadas (RN-AG-09). Hoy no existe la tabla `service` ni ninguna forma de crear, editar, desactivar o publicar prestaciones, por lo que ni la agenda (C-11) ni el motor de disponibilidad (C-12) pueden calcular la duración de un turno.

Governance: **BAJO** (catálogo operativo del consultorio; sin datos personales ni clínicos).

## What Changes

- **Migración V4 (`V4__service.sql`)**:
  - Creación de la tabla `service` (`id` UUID PK, `practice_id` UUID NOT NULL FK a `practice`, `name`, `description` opcional, `duration_min` int NOT NULL con `CHECK (duration_min > 0)`, `active` boolean NOT NULL DEFAULT true, `public_visible` boolean NOT NULL DEFAULT false, `created_at` y `updated_at` timestamptz NOT NULL DEFAULT now()).
  - Índices para el listado de gestión y para el filtro público (`practice_id, active, public_visible`).
  - `REVOKE DELETE, TRUNCATE ON service FROM ${app_role}`: la baja es siempre lógica, nunca física.
  - **Sin columna de precio**: los precios en ARS están fuera de v1 (KB 04 §service, PA-07).
- **CRUD protegido `/api/v1/services` (rol `DENTIST`)**:
  - `GET /api/v1/services`: lista todas las prestaciones del consultorio (activas e inactivas) ordenadas por nombre, para que el odontólogo pueda reactivar las dadas de baja.
  - `GET /api/v1/services/{id}`: consulta una prestación por id, incluida una inactiva.
  - `POST /api/v1/services`: crea una prestación con nombre, descripción opcional, duración y visibilidad pública.
  - `PUT /api/v1/services/{id}`: actualiza nombre, descripción, duración, visibilidad pública y estado activo (permite reactivar).
  - `DELETE /api/v1/services/{id}`: baja lógica (`active = false`); idempotente; nunca `DELETE` físico.
  - Validaciones: `duration_min > 0` y múltiplo de la granularidad vigente del consultorio (`practice.slot_granularity_min`, C-08 / RN-AG-06); nombre obligatorio y acotado.
  - Desactivar una prestación no altera turnos existentes (RN-AG-09): no hay cascadas ni mutaciones sobre turnos.
- **Catálogo público `GET /api/v1/public/services`**:
  - Solo prestaciones `active = true` y `public_visible = true`.
  - DTO público mínimo: `id`, `name`, `description`, `durationMin` (sin banderas internas, sin datos del consultorio, sin timestamps).
  - `Cache-Control: public, max-age=60` (cache corto) y rate limit por IP reutilizando la infraestructura de C-04 (`RateLimiter`), con `429 RATE_LIMITED` + `Retry-After`.
- **Seguridad y errores**:
  - Endpoints de gestión bajo la política deny-by-default existente: 401 sin sesión, 403 sin rol `DENTIST`, CSRF obligatorio en `POST`, `PUT` y `DELETE`.
  - Errores en `application/problem+json` vía `ApiExceptionHandler`: `VALIDATION_FAILED` (400), `SERVICE_NOT_FOUND` (404), `DURATION_NOT_MULTIPLE_OF_GRANULARITY` (422), `RATE_LIMITED` (429).

## Capabilities

### New Capabilities
- `services-catalog`: gestión de prestaciones del consultorio (alta, consulta, edición, baja lógica y reactivación) con validación de duración contra la granularidad configurada y semántica explícita de `active` / `public_visible`.
- `public-services`: catálogo público de solo lectura que expone únicamente las prestaciones activas y visibles, con campos públicos, cache corto y rate limit por IP.

### Modified Capabilities
- `database-migrations`: documenta la migración V4 que crea la tabla `service`, sus restricciones, índices y la revocación de borrado físico.
- `api-access-policy`: incorpora `/api/v1/services/**` como rutas protegidas y `/api/v1/public/services` como ruta pública declarada.

## Impact

- **Base de datos**: nueva migración `V4__service.sql`. `V4` está libre pero es anterior a migraciones ya aplicadas (`V5`, `V7`, `V11`); las bases locales ya migradas deben recrearse o migrarse con `out-of-order` (ver design.md, D8).
- **Backend**: módulo `agenda` (dueño de las prestaciones según KB 08):
  - `agenda.domain`: entidad `Service`, `ServiceRepository`, validación de duración contra granularidad.
  - `agenda.application`: casos de uso de gestión y de catálogo público.
  - `agenda.api`: DTOs `record` de request/response (gestión y público).
  - `agenda.infrastructure`: `ServicesController`, `PublicServicesController`, repositorio Spring Data JPA.
  - `shared`: reutilización de `RateLimiter`, `InstallationPractice` y `PracticeRepository`; sin cambios de contrato.
- **Tests**: unitarios de dominio y aplicación (Strict TDD), integración con PostgreSQL embebido zonky (`ServiceSchemaIT`, `ServiceRepositoryIT`, `ServicesEndpointsIT`, `PublicServicesIT`) y verificación de fronteras ArchUnit.
- **Dependencias posteriores**: habilita C-11 (`appointments-core`, duración del turno), C-12 (`availability-engine`, slots por duración), C-14 (`public-booking-api`, catálogo en la reserva) y C-19 (`settings-ui`, pantalla de prestaciones).
