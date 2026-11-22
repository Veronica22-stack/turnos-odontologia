# Spec Delta

## ADDED Requirements

### Requirement: Esquema de auditoría creado en V3
La migración `V3` SHALL crear la tabla `audit_log` con: `id` entero de 64 bits generado por la base como clave primaria; `practice_id` obligatorio que referencia `practice`; `user_id` opcional que referencia `app_user`; `action`, `entity_type` y `entity_id`; `patient_id` opcional; `ip` de tipo de red; `user_agent`; `occurred_at` de tipo `timestamptz` con valor por defecto del servidor; y `details` de tipo `jsonb` restringido a objetos. La columna `patient_id` SHALL ser un identificador sin restricción de clave foránea mientras la tabla de pacientes no exista; la restricción SHALL agregarse en una migración posterior. Las migraciones `V1` y `V2` MUST NOT modificarse.

#### Scenario: Base migrada hasta V3
- **GIVEN** una base con `V1` y `V2` aplicadas
- **WHEN** arranca la aplicación
- **THEN** `V3` queda registrada como exitosa y la tabla `audit_log` expone todas las columnas indicadas con sus tipos

#### Scenario: Valores inválidos rechazados por la base
- **GIVEN** la tabla creada por `V3`
- **WHEN** se intenta insertar una acción fuera del catálogo, un `details` que no es un objeto JSON, o un `details` con claves de datos personales o clínicos prohibidas
- **THEN** PostgreSQL rechaza cada inserción por una restricción de la tabla

#### Scenario: Paciente aún sin tabla
- **GIVEN** una base migrada hasta `V3`, anterior al change que crea los pacientes
- **WHEN** se consultan las restricciones de `audit_log`
- **THEN** `patient_id` acepta valores nulos o identificadores sin exigir una tabla `patient` inexistente

### Requirement: Privilegios de solo escritura y lectura sobre la auditoría
La migración `V3` SHALL revocar `UPDATE`, `DELETE` y `TRUNCATE` sobre `audit_log` al rol de aplicación, conservando únicamente `INSERT` y `SELECT`. El rol de migración SHALL mantener los privilegios de administración del esquema.

#### Scenario: La aplicación registra y consulta
- **GIVEN** la base migrada hasta `V3`
- **WHEN** el rol de aplicación ejecuta `INSERT` y luego `SELECT` sobre `audit_log`
- **THEN** ambas sentencias se ejecutan con éxito y la fila insertada es visible para la consulta

#### Scenario: La aplicación no puede alterar el rastro
- **GIVEN** la base migrada hasta `V3` con al menos una fila en `audit_log`
- **WHEN** el rol de aplicación intenta `UPDATE`, `DELETE` y `TRUNCATE` sobre esa tabla
- **THEN** PostgreSQL rechaza las tres sentencias por falta de privilegios y la fila permanece intacta

#### Scenario: Otras tablas conservan sus privilegios
- **GIVEN** la base migrada hasta `V3`
- **WHEN** se comprueban los privilegios del rol de aplicación sobre tablas creadas antes de `V3`
- **THEN** no se han modificado por la nueva migración
