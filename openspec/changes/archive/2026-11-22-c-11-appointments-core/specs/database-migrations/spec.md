# Spec Delta

## ADDED Requirements

### Requirement: Esquema de turnos creado en V13
La migración `V13__appointment.sql` SHALL crear `appointment` con PK UUID, FK `ON DELETE RESTRICT` a `practice`, `patient` y `service`, instantes `timestamptz`, snapshot de duración positivo, `status`/`origin`/`cancelled_by` con `CHECK`, `notes` de hasta 1000 caracteres, `cancelled_by` y `cancelled_at` presentes si y solo si el turno está `CANCELLED`, columna generada `period tstzrange '[)'` e índices de consulta. V1–V12 MUST NOT modificarse.

#### Scenario: Base migrada hasta V12
- **GIVEN** una base con `V1` a `V12` aplicadas
- **WHEN** arranca la aplicación y Flyway aplica las migraciones pendientes
- **THEN** `V13` queda registrada como exitosa, `appointment` existe con sus columnas, restricciones e índices sobre `(practice_id, starts_at)`, `(patient_id)`, `(service_id)` y `(status, starts_at)`, y no se ejecuta ninguna versión fuera de orden

#### Scenario: Valores de dominio controlados
- **GIVEN** la tabla creada por `V13`
- **WHEN** se intenta insertar un turno con `status`, `origin` o `cancelled_by` fuera de sus valores permitidos, con `duration_min_snapshot` menor o igual a cero, o con `notes` de más de 1000 caracteres
- **THEN** PostgreSQL rechaza la sentencia por la restricción de verificación

#### Scenario: Coherencia de la cancelación
- **GIVEN** la tabla creada por `V13`
- **WHEN** se intenta guardar un turno `CANCELLED` sin `cancelled_by` o sin `cancelled_at`, o un turno no cancelado con `cancelled_by` o con `cancelled_at`
- **THEN** PostgreSQL rechaza la sentencia por la restricción de verificación, de modo que ambos campos están presentes si y solo si el estado es `CANCELLED`

#### Scenario: Rango generado semiabierto
- **GIVEN** un turno con `starts_at = 2026-12-10T13:00:00Z` y `ends_at = 2026-12-10T13:30:00Z`
- **WHEN** se consulta la columna `period`
- **THEN** el valor es `[2026-12-10T13:00:00Z, 2026-12-10T13:30:00Z)`

### Requirement: Integridad del rango y no solapamiento de turnos en la base
La base SHALL rechazar turnos con `ends_at <= starts_at` y, mediante `EXCLUDE USING gist (practice_id WITH =, period WITH &&) WHERE (status <> 'CANCELLED')`, cualquier par de turnos no cancelados del mismo consultorio con rangos solapados. SHALL permitir rangos adyacentes, solapamiento con turnos cancelados y solapamiento entre consultorios distintos.

#### Scenario: Rango inválido
- **WHEN** se intenta insertar un turno con `ends_at` igual o anterior a `starts_at`
- **THEN** PostgreSQL rechaza la sentencia por la restricción de verificación

#### Scenario: Solapamiento de turnos vigentes rechazado
- **GIVEN** un turno `CONFIRMED`, `COMPLETED` o `NO_SHOW` de 10:00 a 10:30
- **WHEN** se inserta otro turno no cancelado de 10:15 a 10:45 en el mismo consultorio
- **THEN** PostgreSQL rechaza la inserción por la restricción de exclusión con SQLState `23P01`

#### Scenario: Adyacencia, cancelados y otro consultorio
- **GIVEN** un turno `CONFIRMED` de 10:00 a 10:30 y un turno `CANCELLED` de 11:00 a 11:30
- **WHEN** se insertan turnos confirmados de 10:30 a 11:00 y de 11:00 a 11:30 en el mismo consultorio y uno de 10:00 a 10:30 en otro consultorio
- **THEN** las tres inserciones se aceptan

#### Scenario: Reactivar el rango de un cancelado ocupado
- **GIVEN** un turno `CANCELLED` de 10:00 a 10:30 y un turno `CONFIRMED` posterior en el mismo rango
- **WHEN** se intenta cambiar el estado del cancelado a `CONFIRMED`
- **THEN** PostgreSQL rechaza la actualización por la restricción de exclusión

### Requirement: Privilegios sin borrado físico en appointment
La migración `V13` SHALL revocar `DELETE` y `TRUNCATE` sobre `appointment` al rol de aplicación, conservando `SELECT`, `INSERT` y `UPDATE`, de modo que un turno solo cambie de estado y nunca se elimine físicamente.

#### Scenario: Borrado físico rechazado
- **GIVEN** la base migrada hasta `V13` con un turno
- **WHEN** el rol de aplicación ejecuta `DELETE` o `TRUNCATE` sobre `appointment`
- **THEN** PostgreSQL rechaza la sentencia por falta de privilegios y el turno permanece

#### Scenario: Alta, lectura y actualización permitidas
- **GIVEN** la base migrada hasta `V13`
- **WHEN** el rol de aplicación ejecuta `INSERT`, `SELECT` y `UPDATE` sobre `appointment`
- **THEN** las sentencias se ejecutan con éxito

#### Scenario: Lock por consultorio disponible para la aplicación
- **GIVEN** la base migrada hasta `V13`
- **WHEN** el rol de aplicación ejecuta `SELECT ... FOR UPDATE` sobre la fila de su consultorio en `practice`
- **THEN** la sentencia se ejecuta con éxito sin GRANT adicional
