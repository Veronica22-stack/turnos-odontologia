# Spec Delta

## MODIFIED Requirements

### Requirement: Los bloqueos no cancelan turnos existentes
Crear, editar o eliminar un bloqueo MUST NOT cancelar, reprogramar ni modificar turnos. La respuesta de consulta, creación y edición SHALL incluir un campo `affectedAppointments` con los turnos `CONFIRMED` cuyo rango intersecta el del bloqueo, expresados solo con identificador e instantes de inicio y fin, ordenados por inicio. Turnos cancelados, completados o ausentes y turnos adyacentes al bloqueo MUST NOT listarse.

#### Scenario: Bloqueo sobre un rango con turnos
- **GIVEN** turnos confirmados dentro del rango que se desea bloquear
- **WHEN** el odontólogo crea o edita el bloqueo
- **THEN** el bloqueo se guarda, los turnos siguen en su estado original y el campo `affectedAppointments` los lista sin modificarlos

#### Scenario: Bloqueo sin turnos en el rango
- **GIVEN** ningún turno confirmado que intersecte el rango
- **WHEN** se crea un bloqueo
- **THEN** la respuesta incluye `affectedAppointments` como lista vacía y la operación es exitosa

#### Scenario: Sin fuente de turnos disponible
- **GIVEN** una composición de la aplicación sin proveedor de turnos registrado
- **WHEN** se crea un bloqueo
- **THEN** la respuesta incluye `affectedAppointments` como lista vacía y la operación es exitosa

#### Scenario: Turnos no confirmados o adyacentes
- **GIVEN** un bloqueo de 09:00 a 12:00, un turno cancelado de 10:00 a 10:30, un turno completado de 09:00 a 09:30 y un turno confirmado de 12:00 a 12:30
- **WHEN** se consulta el bloqueo
- **THEN** `affectedAppointments` está vacío

#### Scenario: Turno que cruza el borde del bloqueo
- **GIVEN** un turno confirmado de 11:45 a 12:15 y un bloqueo de 09:00 a 12:00
- **WHEN** se consulta el bloqueo
- **THEN** `affectedAppointments` lista ese turno con su identificador, inicio y fin

## ADDED Requirements

### Requirement: Serialización de bloqueos con turnos
La creación y la edición de un bloqueo SHALL serializarse con las altas y reprogramaciones de turnos del mismo consultorio, de modo que `affectedAppointments` refleje todos los turnos confirmados en el rango al momento de confirmarse el bloqueo. La eliminación de un bloqueo MUST NOT requerir esa serialización.

#### Scenario: Turno confirmado justo antes del bloqueo
- **GIVEN** un alta de turno de 10:00 a 10:30 y la creación concurrente de un bloqueo de 09:00 a 12:00, donde el turno se confirma primero
- **WHEN** se confirma el bloqueo
- **THEN** su respuesta lista el turno en `affectedAppointments`

#### Scenario: Bloqueo confirmado antes del turno
- **GIVEN** la creación de un bloqueo de 09:00 a 12:00 y un alta concurrente de turno de 10:00 a 10:30, donde el bloqueo se confirma primero
- **WHEN** se procesa el alta de turno
- **THEN** el alta responde `409` con `code = APPOINTMENT_SLOT_BLOCKED`

#### Scenario: Comportamiento de bloqueos sin cambios
- **GIVEN** un bloqueo existente de 09:00 a 11:00
- **WHEN** se crea otro bloqueo solapado de 10:00 a 12:00
- **THEN** el sistema sigue respondiendo `409` con `code = SCHEDULE_BLOCK_OVERLAP`
