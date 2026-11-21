# database-migrations Specification

## Purpose
Garantiza que el esquema de PostgreSQL evolucione solo mediante migraciones versionadas, aplicadas por un rol distinto del que usa la aplicación en tiempo de ejecución, para que la inmutabilidad de datos clínicos y de auditoría pueda imponerse desde la base.

## Requirements

### Requirement: Migraciones versionadas al arrancar
El sistema SHALL aplicar al arrancar, en orden, todas las migraciones versionadas pendientes y registrar cada una en el historial de migraciones. El esquema MUST NOT generarse ni alterarse automáticamente a partir del modelo de la aplicación.

#### Scenario: Base vacía
- **GIVEN** una base PostgreSQL vacía con los roles de migración y de aplicación creados
- **WHEN** la aplicación arranca
- **THEN** la migración `V1` queda registrada como exitosa en el historial y la aplicación queda disponible

#### Scenario: Base ya migrada
- **GIVEN** una base con `V1` ya aplicada
- **WHEN** la aplicación arranca de nuevo
- **THEN** no se vuelve a ejecutar ninguna migración y el arranque es exitoso

### Requirement: Migración fallida o alterada detiene el arranque
El sistema SHALL abortar el arranque si una migración falla o si el contenido de una migración ya aplicada fue modificado.

#### Scenario: Checksum distinto
- **GIVEN** una base con `V1` aplicada
- **WHEN** el archivo de `V1` fue editado y la aplicación arranca
- **THEN** el arranque falla con un error de validación de migraciones y la aplicación no atiende solicitudes

### Requirement: Separación de roles de base de datos
Las migraciones SHALL ejecutarse con el rol de migración (con permisos DDL) y la aplicación SHALL operar con un rol distinto que no puede crear, alterar ni eliminar objetos del esquema.

#### Scenario: El rol de aplicación no tiene DDL
- **WHEN** se ejecuta `CREATE TABLE x (id int)` conectado con el rol de aplicación
- **THEN** PostgreSQL rechaza la sentencia por falta de permisos

#### Scenario: El rol de aplicación tiene DML sobre tablas nuevas
- **GIVEN** una tabla creada por una migración posterior a `V1`
- **WHEN** el rol de aplicación ejecuta `SELECT` e `INSERT` sobre ella
- **THEN** ambas sentencias se ejecutan con éxito sin GRANT adicional en esa migración

### Requirement: Revocación por tabla preparada
El esquema SHALL permitir que una migración posterior revoque `UPDATE` y `DELETE` al rol de aplicación sobre una tabla concreta, de modo que esas operaciones fallen en la base.

#### Scenario: Tabla inmutable de prueba
- **GIVEN** una tabla de prueba creada por el rol de migración con `UPDATE` y `DELETE` revocados al rol de aplicación
- **WHEN** el rol de aplicación intenta `UPDATE` o `DELETE` sobre ella
- **THEN** PostgreSQL rechaza ambas sentencias, mientras que `INSERT` y `SELECT` siguen permitidos

### Requirement: Extensiones requeridas
La migración `V1` SHALL habilitar la extensión `btree_gist`, necesaria para las restricciones de exclusión de turnos.

#### Scenario: btree_gist disponible
- **WHEN** se consulta `pg_extension` luego de aplicar `V1`
- **THEN** `btree_gist` figura como instalada
