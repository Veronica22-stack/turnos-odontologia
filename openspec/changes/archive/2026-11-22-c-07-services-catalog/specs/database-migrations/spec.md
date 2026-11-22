# Spec Delta

## ADDED Requirements

### Requirement: Esquema de prestaciones creado en V4
La migración `V4` SHALL crear la tabla `service` con clave primaria UUID, `practice_id` foráneo a `practice`, nombre obligatorio, descripción opcional, duración en minutos con restricción de valor positivo, marca de baja lógica `active` y marca `public_visible`, ambas no nulas, y timestamps de alta y modificación. Las migraciones ya aplicadas MUST NOT modificarse.

#### Scenario: Base migrada hasta V4
- **GIVEN** una base de datos con las migraciones previas aplicadas
- **WHEN** arranca la aplicación y ejecuta Flyway
- **THEN** la migración `V4` queda registrada exitosamente y la tabla `service` existe con sus restricciones e índices

#### Scenario: Duración no positiva rechazada por la base
- **GIVEN** la base migrada hasta `V4`
- **WHEN** se intenta insertar o actualizar una prestación con duración cero o negativa
- **THEN** PostgreSQL rechaza la sentencia por violación de restricción check

#### Scenario: Valores por defecto de las marcas
- **GIVEN** la base migrada hasta `V4`
- **WHEN** se inserta una prestación sin indicar las marcas de estado y visibilidad
- **THEN** la prestación queda activa y no visible públicamente

### Requirement: Privilegios sin borrado físico en service
La migración `V4` SHALL revocar `DELETE` y `TRUNCATE` sobre `service` al rol de aplicación, conservando `SELECT`, `INSERT` y `UPDATE`, de modo que la baja de una prestación solo pueda hacerse por cambio de estado.

#### Scenario: Borrado físico rechazado
- **GIVEN** la base migrada hasta `V4` con una fila en `service`
- **WHEN** el rol de aplicación intenta ejecutar `DELETE` o `TRUNCATE` sobre `service`
- **THEN** PostgreSQL rechaza la sentencia por falta de privilegios y la fila permanece intacta

#### Scenario: Alta, lectura y actualización permitidas
- **GIVEN** la base migrada hasta `V4`
- **WHEN** el rol de aplicación ejecuta `INSERT`, `SELECT` y `UPDATE` sobre `service`
- **THEN** las tres sentencias se ejecutan correctamente
