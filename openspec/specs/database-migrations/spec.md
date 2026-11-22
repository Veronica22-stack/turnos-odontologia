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
