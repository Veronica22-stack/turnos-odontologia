# Spec Delta

## ADDED Requirements

### Requirement: Esquema de bloqueos creado en V8
La migración `V8__schedule_block.sql` SHALL crear la tabla `schedule_block` con: `id` UUID como clave primaria; `practice_id` UUID obligatorio que referencia `practice` con `ON DELETE RESTRICT`; `starts_at` y `ends_at` de tipo `timestamptz`; `reason` de texto obligatorio con `CHECK (char_length(btrim(reason)) BETWEEN 1 AND 500)`; `created_at` y `updated_at` de tipo `timestamptz` con valor por defecto del servidor; y una columna generada `period` de tipo `tstzrange` construida desde `starts_at` y `ends_at` con límites semiabiertos `[)`. La columna generada SHALL tener una restricción `EXCLUDE USING gist (practice_id WITH =, period WITH &&)` y la tabla SHALL tener un índice sobre `(practice_id, starts_at)` para las consultas por rango. Las migraciones `V1` a `V7` MUST NOT modificarse ni renombrarse, y no SHALL crearse una migración con versión menor que la última ya aplicada.

#### Scenario: Base migrada hasta V7
- **GIVEN** una base con `V1` a `V7` aplicadas, incluida `V7__patient.sql`
- **WHEN** arranca la aplicación y Flyway aplica las migraciones pendientes
- **THEN** `V8` queda registrada como exitosa, la tabla `schedule_block` existe con la columna `period` y sus restricciones, y no se intenta ejecutar ninguna versión fuera de orden

#### Scenario: Rango generado consistente
- **GIVEN** una fila de `schedule_block` con `starts_at = 2026-12-10T12:00:00Z` y `ends_at = 2026-12-10T14:00:00Z`
- **WHEN** se consulta la columna `period`
- **THEN** el valor es el rango semiabierto `[2026-12-10T12:00:00Z, 2026-12-10T14:00:00Z)`, sin incluir el extremo superior

#### Scenario: Extensión ya disponible
- **GIVEN** una base migrada hasta `V8`
- **WHEN** se consulta `pg_extension` y las restricciones de `schedule_block`
- **THEN** `btree_gist` figura instalada desde `V1` y existe la restricción de exclusión sobre `(practice_id, period)`

### Requirement: Integridad del rango y de la no superposición en la base
La base de datos SHALL rechazar cualquier fila de `schedule_block` con `ends_at <= starts_at` y cualquier par de filas del mismo `practice_id` cuyos rangos `period` se solapen. SHALL permitirse rangos adyacentes y rangos de consultorios distintos aunque se solapen entre sí.

#### Scenario: Rango inválido rechazado
- **GIVEN** la tabla creada por `V8`
- **WHEN** se intenta insertar un bloqueo con `ends_at` igual o anterior a `starts_at`
- **THEN** PostgreSQL rechaza la sentencia por la restricción de verificación

#### Scenario: Solapamiento rechazado por la restricción de exclusión
- **GIVEN** un bloqueo persistido de 09:00 a 11:00 para un consultorio
- **WHEN** se intenta insertar otro bloqueo de 10:00 a 12:00 para el mismo consultorio
- **THEN** PostgreSQL rechaza la inserción por la restricción de exclusión

#### Scenario: Adyacencia y aislamiento por consultorio
- **GIVEN** un bloqueo persistido de 09:00 a 11:00 para un consultorio
- **WHEN** se insertan un bloqueo de 11:00 a 12:00 para ese consultorio y otro de 10:00 a 12:00 para un consultorio distinto
- **THEN** ambas inserciones se aceptan

### Requirement: Privilegios del rol de aplicación sobre bloqueos
El rol de aplicación `${app_role}` SHALL poder ejecutar `SELECT`, `INSERT`, `UPDATE` y `DELETE` sobre `schedule_block` y MUST NOT poder ejecutar `TRUNCATE`. `UPDATE` y `DELETE` SHALL conservarse porque los bloqueos son configuración editable de la agenda, a diferencia de las tablas inmutables de historia clínica y auditoría.

#### Scenario: Gestión de bloqueos desde la aplicación
- **GIVEN** la base migrada hasta `V8`
- **WHEN** el rol de aplicación ejecuta `SELECT`, `INSERT`, `UPDATE` y `DELETE` sobre `schedule_block`
- **THEN** todas las sentencias se ejecutan con éxito sin GRANT adicional en la migración

#### Scenario: TRUNCATE denegado
- **GIVEN** la base migrada hasta `V8` con bloqueos persistidos
- **WHEN** el rol de aplicación intenta `TRUNCATE schedule_block`
- **THEN** PostgreSQL rechaza la sentencia por falta de privilegios y los bloqueos permanecen
