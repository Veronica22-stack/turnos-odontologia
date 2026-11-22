# Spec Delta: database-migrations

## ADDED Requirements

### Requirement: Esquema del odontograma creado en V12
La migración `V12` SHALL crear `odontogram_entry` con historia clínica, código FDI, cara opcional, estado, notas opcionales, entrada corregida opcional, motivo, autor e instante, con claves foráneas a `clinical_record` y `app_user` y restricciones de dominio (FDI, catálogo de estados, catálogo de caras, cara solo en `CARIES`/`FILLED`, notas ≤ 500).

#### Scenario: Base migrada hasta V12
- **GIVEN** una base con las migraciones previas aplicadas
- **WHEN** arranca la aplicación y ejecuta Flyway
- **THEN** `V12` queda registrada y existe `odontogram_entry` con sus restricciones

#### Scenario: Código FDI inválido
- **WHEN** se inserta una entrada con `tooth_code` `19` o `56`
- **THEN** PostgreSQL rechaza la inserción

#### Scenario: Estado fuera del catálogo
- **WHEN** se inserta una entrada con `condition` `FRACTURED`
- **THEN** PostgreSQL rechaza la inserción

#### Scenario: Cara en estado de pieza completa
- **WHEN** se inserta una entrada `MISSING` con `surface` `MESIAL`
- **THEN** PostgreSQL rechaza la inserción

### Requirement: Integridad de correcciones del odontograma en la base
`odontogram_entry` SHALL aplicar la misma convención de corrección que las demás tablas de entradas clínicas: referencia y motivo ambos presentes o ambos ausentes, motivo no vacío de hasta 500 caracteres, entrada corregida de la misma historia clínica y a lo sumo una corrección por entrada.

#### Scenario: Corrección cruzada entre historias clínicas
- **WHEN** se inserta una entrada de odontograma que corrige una entrada de otra historia clínica
- **THEN** PostgreSQL rechaza la inserción por clave foránea

#### Scenario: Doble corrección
- **GIVEN** una entrada de odontograma ya corregida
- **WHEN** se inserta otra entrada que la corrige
- **THEN** PostgreSQL rechaza la inserción por unicidad

#### Scenario: Motivo sin referencia
- **WHEN** se inserta una entrada con motivo de corrección pero sin entrada corregida
- **THEN** PostgreSQL rechaza la inserción

### Requirement: Odontograma sin mutación para el rol de aplicación
La migración `V12` SHALL revocar `UPDATE`, `DELETE` y `TRUNCATE` sobre `odontogram_entry` al rol de aplicación, conservando `SELECT` e `INSERT`.

#### Scenario: Mutación rechazada
- **GIVEN** la base migrada hasta `V12` con una entrada de odontograma
- **WHEN** el rol de aplicación ejecuta `UPDATE`, `DELETE` o `TRUNCATE` sobre `odontogram_entry`
- **THEN** PostgreSQL rechaza cada sentencia por falta de privilegios

#### Scenario: Alta y lectura permitidas
- **GIVEN** la base migrada hasta `V12`
- **WHEN** el rol de aplicación ejecuta `INSERT` y `SELECT` sobre `odontogram_entry`
- **THEN** ambas sentencias se ejecutan correctamente
