# Spec Delta

## ADDED Requirements

### Requirement: Esquema de historia clínica creado en V11
La migración `V11` SHALL crear `clinical_record` (una por paciente, con `practice_id`, instante de apertura y responsable) y `anamnesis_entry` (con historia clínica, versión única por historia clínica, contenido JSON objeto, entrada corregida opcional, motivo, autor e instante), con claves foráneas a `practice`, `patient` y `app_user`.

#### Scenario: Base migrada hasta V11
- **GIVEN** una base con las migraciones previas aplicadas
- **WHEN** arranca la aplicación y ejecuta Flyway
- **THEN** `V11` queda registrada y existen `clinical_record` y `anamnesis_entry` con sus restricciones

#### Scenario: Segunda historia clínica para el mismo paciente
- **GIVEN** la base migrada con una historia clínica para un paciente
- **WHEN** se inserta otra historia clínica para el mismo paciente
- **THEN** PostgreSQL rechaza la inserción por unicidad

#### Scenario: Versión duplicada
- **GIVEN** una anamnesis en versión `1` de una historia clínica
- **WHEN** se inserta otra anamnesis con versión `1` en la misma historia clínica
- **THEN** PostgreSQL rechaza la inserción por unicidad

### Requirement: Integridad de correcciones en la base
Las tablas de entradas clínicas SHALL garantizar en la base que la referencia a la entrada corregida y el motivo estén ambos presentes o ambos ausentes, que el motivo no esté vacío, que la entrada corregida pertenezca a la misma historia clínica y que cada entrada se corrija a lo sumo una vez.

#### Scenario: Motivo sin referencia
- **WHEN** se inserta una anamnesis con motivo de corrección pero sin entrada corregida
- **THEN** PostgreSQL rechaza la inserción

#### Scenario: Corrección cruzada entre historias clínicas
- **WHEN** se inserta una anamnesis que corrige una entrada de otra historia clínica
- **THEN** PostgreSQL rechaza la inserción por clave foránea

#### Scenario: Doble corrección de la misma entrada
- **GIVEN** una entrada ya corregida
- **WHEN** se inserta otra entrada que la corrige
- **THEN** PostgreSQL rechaza la inserción por unicidad

### Requirement: Historia clínica sin mutación para el rol de aplicación
La migración `V11` SHALL revocar `UPDATE`, `DELETE` y `TRUNCATE` sobre `clinical_record` y `anamnesis_entry` al rol de aplicación, conservando `SELECT` e `INSERT`.

#### Scenario: Mutación rechazada
- **GIVEN** la base migrada hasta `V11` con una historia clínica y una anamnesis
- **WHEN** el rol de aplicación ejecuta `UPDATE`, `DELETE` o `TRUNCATE` sobre cualquiera de las dos tablas
- **THEN** PostgreSQL rechaza cada sentencia por falta de privilegios

#### Scenario: Alta y lectura permitidas
- **GIVEN** la base migrada hasta `V11`
- **WHEN** el rol de aplicación ejecuta `INSERT` y `SELECT` sobre ambas tablas
- **THEN** ambas sentencias se ejecutan correctamente
