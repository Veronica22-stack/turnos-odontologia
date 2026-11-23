# Spec Delta

## ADDED Requirements

### Requirement: Esquema de notificaciones creado en V14
La migración `V14__notification.sql` SHALL crear `notification` con PK UUID, FK `ON DELETE RESTRICT` a `practice` y `appointment`, `type`, `channel` y `status` con `CHECK`, `scheduled_for` y `sent_at` `timestamptz`, `attempts` no negativo, `last_error` acotado a un código corto, `created_at`/`updated_at`, e índice parcial de pendientes por `scheduled_for`. V1–V13 MUST NOT modificarse.

#### Scenario: Base migrada hasta V13
- **GIVEN** una base con `V1` a `V13` aplicadas
- **WHEN** arranca la aplicación y Flyway aplica las migraciones pendientes
- **THEN** `V14` queda registrada como exitosa, `notification` existe con sus columnas, restricciones, el índice parcial sobre `scheduled_for` filtrado por `status = 'PENDING'` y un índice cuya columna líder es `appointment_id`, y no se ejecuta ninguna versión fuera de orden

#### Scenario: Valores de dominio controlados
- **GIVEN** la tabla creada por `V14`
- **WHEN** se intenta insertar una notificación con `type` fuera de `CONFIRMATION`, `REMINDER`, `RESCHEDULE`, `CANCELLATION`, `DENTIST_CANCELLATION`, con `channel` distinto de `EMAIL`, con `status` fuera de `PENDING`, `SENT`, `FAILED`, `DISCARDED`, con `attempts` negativo o con `last_error` de más de 64 caracteres
- **THEN** PostgreSQL rechaza la sentencia por la restricción de verificación

#### Scenario: Coherencia del envío
- **GIVEN** la tabla creada por `V14`
- **WHEN** se intenta guardar una notificación `SENT` sin `sent_at`, o una no `SENT` con `sent_at`
- **THEN** PostgreSQL rechaza la sentencia por la restricción de verificación

### Requirement: Una notificación por turno y tipo en la base
La base SHALL garantizar con `UNIQUE (appointment_id, type)` que no existan dos notificaciones del mismo tipo para el mismo turno.

#### Scenario: Duplicado rechazado
- **GIVEN** una notificación `REMINDER` para un turno
- **WHEN** se inserta otra `REMINDER` para el mismo turno
- **THEN** PostgreSQL rechaza la inserción por violación de unicidad

#### Scenario: Tipos distintos del mismo turno
- **GIVEN** una notificación `CONFIRMATION` para un turno
- **WHEN** se insertan una `REMINDER` y una `CANCELLATION` para el mismo turno
- **THEN** ambas inserciones se aceptan

### Requirement: Privilegios sin borrado físico en notification
La migración `V14` SHALL revocar `DELETE` y `TRUNCATE` sobre `notification` al rol de aplicación, conservando `SELECT`, `INSERT` y `UPDATE` (incluido `SELECT ... FOR UPDATE SKIP LOCKED`), de modo que el historial de notificaciones solo cambie de estado.

#### Scenario: Borrado físico rechazado
- **GIVEN** la base migrada hasta `V14` con una notificación
- **WHEN** el rol de aplicación ejecuta `DELETE` o `TRUNCATE` sobre `notification`
- **THEN** PostgreSQL rechaza la sentencia por falta de privilegios y la notificación permanece

#### Scenario: Toma con bloqueo permitida
- **GIVEN** la base migrada hasta `V14`
- **WHEN** el rol de aplicación ejecuta `INSERT`, `UPDATE` y `SELECT ... FOR UPDATE SKIP LOCKED` sobre `notification`
- **THEN** las sentencias se ejecutan con éxito
