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

### Requirement: Privilegios mínimos sobre las tablas core
El rol de aplicación SHALL poder leer y actualizar la fila del consultorio pero MUST NOT insertar ni eliminar consultorios; SHALL poder leer, insertar y actualizar cuentas de usuario pero MUST NOT eliminarlas.

#### Scenario: Consultorio sin alta ni baja desde la aplicación
- **GIVEN** la base migrada hasta `V2`
- **WHEN** el rol de aplicación intenta `INSERT` y luego `DELETE` sobre la tabla del consultorio
- **THEN** PostgreSQL rechaza ambas sentencias por falta de permisos, mientras que `SELECT` y `UPDATE` se ejecutan con éxito

#### Scenario: Cuentas sin borrado desde la aplicación
- **GIVEN** la base migrada hasta `V2` con una cuenta de usuario
- **WHEN** el rol de aplicación intenta `DELETE` sobre la tabla de cuentas
- **THEN** PostgreSQL rechaza la sentencia por falta de permisos, mientras que `SELECT`, `INSERT` y `UPDATE` se ejecutan con éxito

### Requirement: Esquema validado contra el modelo
Al arrancar, la aplicación SHALL verificar que el esquema migrado coincide con el modelo persistente (tablas, columnas y tipos) y MUST negarse a arrancar si no coincide, sin modificar el esquema.

#### Scenario: Esquema coincidente
- **GIVEN** una base vacía
- **WHEN** la aplicación arranca y aplica `V1` y `V2`
- **THEN** el arranque es exitoso y ambas migraciones figuran como exitosas en el historial

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

### Requirement: Esquema de horarios de atención creado en V5
La migración `V5` SHALL crear la tabla `working_hours` con clave primaria UUID, `practice_id` foráneo a `practice`, `weekday` entre 1 y 7, y horas de inicio y fin con restricción `end_time > start_time`.

#### Scenario: Base migrada hasta V5 con tabla de horarios
- **GIVEN** una base de datos con migraciones previas aplicadas
- **WHEN** arranca la aplicación y ejecuta Flyway
- **THEN** la migración `V5` queda registrada exitosamente y la tabla `working_hours` existe con sus restricciones

#### Scenario: Privilegios de gestión de horarios
- **GIVEN** la base migrada hasta `V5`
- **WHEN** el rol de aplicación ejecuta SELECT, INSERT, UPDATE o DELETE sobre `working_hours`
- **THEN** PostgreSQL permite cada operación sin errores de privilegios

#### Scenario: Restricción de horario en base de datos
- **GIVEN** la base migrada hasta `V5`
- **WHEN** se intenta insertar una fila en `working_hours` con `end_time <= start_time` o con `weekday` fuera del rango 1 a 7
- **THEN** PostgreSQL rechaza la sentencia por violación de restricción check

### Requirement: Columnas de parámetros de reserva en practice creadas en V5
La migración `V5` SHALL agregar a la tabla `practice` las columnas `slot_granularity_min`, `min_notice_min`, `max_advance_days` y `reminder_hours_before`, con valores por defecto no nulos y restricciones de validación de rango.

#### Scenario: Columnas de reserva disponibles en practice
- **GIVEN** la base migrada hasta `V5`
- **WHEN** se consulta la tabla `practice`
- **THEN** contiene las columnas de granularidad, anticipación mínima, horizonte máximo y recordatorio con sus defaults y restricciones check

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

### Requirement: Esquema de pacientes creado en V7
La migración `V7` SHALL crear la tabla `patient` con clave primaria UUID, `practice_id` foráneo a `practice`, `first_name`, `last_name`, `dni`, `phone`, `email`, `source`, marca de baja lógica `active` booleana no nula por defecto verdadera, y restricción de unicidad sobre `(practice_id, dni)`.

#### Scenario: Base migrada hasta V7
- **GIVEN** una base de datos con migraciones previas aplicadas
- **WHEN** arranca la aplicación y ejecuta Flyway
- **THEN** la migración `V7` queda registrada exitosamente y la tabla `patient` existe con sus restricciones

#### Scenario: Restricción de unicidad por DNI en consultorio
- **GIVEN** la base migrada hasta `V7` con un paciente registrado
- **WHEN** se intenta insertar otro paciente con el mismo `dni` en el mismo `practice_id`
- **THEN** PostgreSQL rechaza la inserción por violación de la restricción de unicidad

#### Scenario: Privilegios sin borrado físico en patient
- **GIVEN** la base migrada hasta `V7` con una fila en `patient`
- **WHEN** el rol de aplicación intenta ejecutar DELETE sobre `patient`
- **THEN** PostgreSQL rechaza la sentencia por falta de privilegios

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
