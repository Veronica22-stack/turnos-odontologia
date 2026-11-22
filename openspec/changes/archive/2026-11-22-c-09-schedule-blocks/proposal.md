# Proposal: C-09 `schedule-blocks`

## Why

El odontólogo necesita bloquear rangos de su agenda (vacaciones, trámites, feriados) para que esos horarios no puedan reservarse ni en forma pública ni manualmente (US-012, RN-AG-02). Sin este change, el motor de disponibilidad (C-12) y los turnos manuales (C-11) no tienen una fuente de verdad consultable de horarios no reservables, y la base no puede impedir bloqueos duplicados. Governance: **MEDIO** (reglas de negocio de agenda y contrato entre módulos); se implementa por pasos con decisiones visibles en el apply.

## What Changes

- **Migración `V8__schedule_block.sql`** (no `V6`; ver más abajo): tabla `schedule_block` con `id` UUID PK, `practice_id` FK a `practice ON DELETE RESTRICT`, `starts_at`/`ends_at` `timestamptz`, `reason` text, `created_at`/`updated_at` `timestamptz`, `CHECK (ends_at > starts_at)` y rango `period tstzrange` generado con límites `[)`. El rango se construye desde columnas JPA normales (sin dependencias nuevas) y lleva `EXCLUDE USING gist (practice_id WITH =, period WITH &&)` para rechazar solapamientos entre bloqueos. Permisos: DML por defecto para `${app_role}` con `REVOKE TRUNCATE`; se conservan `UPDATE`/`DELETE` porque un bloqueo es configuración editable, no dato clínico ni registro append-only.
- **Numeración de la migración**: `CHANGES.md` §C-09 sigue indicando `V6`, pero `V7__patient.sql` (C-10) ya existe y está aplicada en la secuencia. Crear una `V6` nueva dejaría la migración fuera de orden (`outOfOrder` no habilitado) y rompería el arranque de cualquier base migrada hasta V7; está prohibido editar o renombrar V7. Se usa por lo tanto **`V8`**, manteniendo el orden monotónico. La actualización de `CHANGES.md` (y la renumeración consecuente de la migración de `appointment` prevista para C-11, hoy anunciada como V8) queda **fuera de alcance**: se propone como follow-up de documentación/roadmap, sin editarlo en este change.
- **Dominio y contratos públicos (`agenda`)**: entidad `ScheduleBlock` y value object `TimeInterval` (`Instant` + validación de rango) en `agenda.domain`; repositorio en `agenda.infrastructure`. En `agenda.api`, el puerto de consulta `BlockedRanges` (rangos que solapan un intervalo para un `practice_id`, con tipos del dominio/JDK, sin JPA ni filtros de infraestructura) y el puerto `AffectedAppointments` para informar turnos existentes alcanzados por un bloqueo. C-09 declara ambos contratos; la consulta real de turnos y su cableado se resuelven en C-11.
- **Casos de uso y API REST**: listar (filtro opcional por rango de fechas), crear, ver detalle, editar y eliminar bloqueo en `/api/v1/schedule-blocks`. DTOs `record` con Bean Validation; nunca entidades JPA en la API. Los errores usan `application/problem+json` vía `@ControllerAdvice`, con `400`, `401`, `403`, `404` y `409` (solapamiento con otro bloqueo, sin revelar nombres de constraints ni fechas ajenas).
- **Seguridad y arquitectura**: endpoints privados con sesión `DENTIST` y CSRF (`X-XSRF-TOKEN`) en mutaciones; `Clock` inyectable de `shared` para `created_at`/`updated_at`; `agenda` no depende de `reservas` ni de otros módulos de negocio. Los contratos consumibles por C-11 y C-12 viven en `agenda.api`; no se agregan dependencias de runtime (el mapeo del rango usa SQL del driver ya presente).
- **Tests (plan, no implementación acá)**: unitarios de rango/solapamiento y DTOs; integración `*IT` con PostgreSQL embebido zonky para V8, constraints/gist/permisos y endpoints; verificación final `./gradlew check` y `openspec validate c-09-schedule-blocks --strict`.

## Capabilities

### New Capabilities
- `schedule-blocks`: CRUD de bloqueos de agenda, validación de rango, regla de no solapamiento entre bloqueos y aviso de turnos afectados sin cancelarlos (US-012, RN-AG-02, RN-AG-08).
- `blocked-ranges`: contrato público para consultar qué rangos bloqueados solapan un intervalo dado de un consultorio, consumible por disponibilidad (C-12) y turnos manuales (C-11).

### Modified Capabilities
- `database-migrations`: documenta la migración `V8` de `schedule_block`, su rango generado, la restricción de exclusión y los permisos del rol de aplicación.
- `api-access-policy`: incorpora `/api/v1/schedule-blocks` a la política deny-by-default con rol `DENTIST` y CSRF en mutaciones.
- `error-responses`: define los códigos de error del dominio de bloqueos, en particular el `409` por solapamiento, sin exponer datos ni detalles de PostgreSQL.
- `module-boundaries`: fija que `BlockedRanges` y `AffectedAppointments` se publican en `agenda.api` y que `agenda` no depende de `reservas` ni de infraestructura ajena.

## Impact

- **Base de datos**: nueva migración `V8__schedule_block.sql`; sin cambios a V1–V7; `btree_gist` ya habilitado en V1.
- **Backend**: nuevos tipos en `ar.turnosodontologia.consultorio.agenda.{api,application,domain,infrastructure}`; controlador siguiendo el patrón de `WorkingHoursController` (inyección por constructor, `@PreAuthorize`, DTOs `record`).
- **Contratos para otros changes**: C-11 debe implementar `AffectedAppointments` y sumar la validación contra bloqueos; C-12 debe consultar `BlockedRanges` al calcular disponibilidad. Ninguno de esos changes se implementa acá.
- **Roadmap**: `CHANGES.md` queda desactualizado (`V6` para C-09; `V8` para C-11). Requiere follow-up explícito fuera de este propose.
- **Dependencias**: ninguna nueva librería de runtime; se reutilizan PostgreSQL, Flyway, Spring Data/JPA, zonky (test) y el `Clock` de `shared`.
