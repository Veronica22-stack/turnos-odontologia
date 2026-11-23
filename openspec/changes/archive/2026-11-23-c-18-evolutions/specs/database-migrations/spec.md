# Spec Delta: database-migrations

## ADDED Requirements

### Requirement: Esquema de evoluciones creado en V15
La migración `V15` SHALL crear `evolution_entry` con historia clínica, descripción, turno asociado opcional, entrada corregida opcional, motivo, autor e instante, con claves foráneas a `clinical_record`, `appointment` y `app_user` y la restricción de descripción no vacía de hasta 5000 caracteres.

#### Scenario: Base migrada hasta V15
- **GIVEN** una base con las migraciones previas aplicadas
- **WHEN** arranca la aplicación y ejecuta Flyway
- **THEN** `V15` queda registrada y existe `evolution_entry` con sus restricciones

#### Scenario: Descripción vacía o excedida
- **WHEN** se inserta una evolución con `description` sin ningun caracter no blanco (vacia, solo espacios, saltos de linea o tabs) o de 5001 caracteres
- **THEN** PostgreSQL rechaza la inserción

#### Scenario: Turno inexistente
- **WHEN** se inserta una evolución con un `appointment_id` que no corresponde a ningún turno
- **THEN** PostgreSQL rechaza la inserción por clave foránea

#### Scenario: Evolución sin turno
- **WHEN** se inserta una evolución con `appointment_id` nulo
- **THEN** la inserción se acepta

### Requirement: Integridad de correcciones de evoluciones en la base
`evolution_entry` SHALL aplicar la misma convención de corrección que las demás tablas de entradas clínicas: referencia y motivo ambos presentes o ambos ausentes, motivo no vacío de hasta 500 caracteres, entrada corregida de la misma historia clínica y a lo sumo una corrección por entrada.

#### Scenario: Corrección cruzada entre historias clínicas
- **WHEN** se inserta una evolución que corrige una evolución de otra historia clínica
- **THEN** PostgreSQL rechaza la inserción por clave foránea

#### Scenario: Doble corrección
- **GIVEN** una evolución ya corregida
- **WHEN** se inserta otra evolución que la corrige
- **THEN** PostgreSQL rechaza la inserción por unicidad

#### Scenario: Motivo sin referencia
- **WHEN** se inserta una evolución con motivo de corrección pero sin entrada corregida
- **THEN** PostgreSQL rechaza la inserción

### Requirement: Evoluciones sin mutación para el rol de aplicación
La migración `V15` SHALL revocar `UPDATE`, `DELETE` y `TRUNCATE` sobre `evolution_entry` al rol de aplicación, conservando `SELECT` e `INSERT`.

#### Scenario: Mutación rechazada
- **GIVEN** la base migrada hasta `V15` con una evolución
- **WHEN** el rol de aplicación ejecuta `UPDATE`, `DELETE` o `TRUNCATE` sobre `evolution_entry`
- **THEN** PostgreSQL rechaza cada sentencia por falta de privilegios

#### Scenario: Alta y lectura permitidas
- **GIVEN** la base migrada hasta `V15`
- **WHEN** el rol de aplicación ejecuta `INSERT` y `SELECT` sobre `evolution_entry`
- **THEN** ambas sentencias se ejecutan correctamente
