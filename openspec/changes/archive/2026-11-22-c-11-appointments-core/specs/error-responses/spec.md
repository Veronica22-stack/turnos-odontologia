# Spec Delta

## ADDED Requirements

### Requirement: Códigos de error del dominio de turnos
Los errores de `/api/v1/appointments` SHALL usar `application/problem+json` con título genérico y códigos estables: `400` `VALIDATION_FAILED`/`INVALID_RANGE`; `404` `APPOINTMENT_NOT_FOUND`/`PATIENT_NOT_FOUND`/`SERVICE_NOT_FOUND`; `409` `APPOINTMENT_SLOT_TAKEN`/`APPOINTMENT_SLOT_BLOCKED`/`INVALID_STATUS_TRANSITION`/`PATIENT_INACTIVE`; `422` `APPOINTMENT_IN_PAST`/`OUTSIDE_WORKING_HOURS`/`SERVICE_INACTIVE`/`APPOINTMENT_NOT_STARTED`/`APPOINTMENT_ALREADY_STARTED`.

#### Scenario: Conflicto con turno existente
- **WHEN** un alta o reprogramación choca con un turno vigente
- **THEN** el sistema responde `409` con `code = APPOINTMENT_SLOT_TAKEN`, sin identificador, paciente ni instantes del turno existente

#### Scenario: Paciente dado de baja
- **WHEN** un alta de turno referencia un paciente dado de baja
- **THEN** el sistema responde `409` con `code = PATIENT_INACTIVE`, el mismo status que devuelve la historia clínica para ese código, sin datos personales del paciente

#### Scenario: Regla de negocio incumplida
- **WHEN** un alta usa una prestación inactiva, un inicio pasado o un horario fuera de atención sin sobreturno
- **THEN** el sistema responde `422` con el código correspondiente y sin valores enviados por el cliente

#### Scenario: Listado con rango excedido o campo de reprogramación ausente
- **WHEN** el listado de turnos pide más de 93 días, o una reprogramación omite el nuevo inicio
- **THEN** el sistema responde `400` con `code = INVALID_RANGE` en el primer caso y `VALIDATION_FAILED` en el segundo, sin valores enviados por el cliente ni error interno

#### Scenario: Error sin datos sensibles
- **GIVEN** una solicitud rechazada cuyas notas contienen datos personales
- **WHEN** se inspecciona la respuesta
- **THEN** el cuerpo no contiene las notas, el DNI, el nombre ni nombres de restricciones de la base

### Requirement: Conflicto de exclusión de turnos traducido
Cuando la restricción de exclusión de turnos rechace una operación (SQLState `23P01` sobre `appointment_no_overlap`), el sistema SHALL responder `409` con `code = APPOINTMENT_SLOT_TAKEN`, sin mensaje de PostgreSQL, nombre de restricción, SQL ni nombres de clase. Otras violaciones de integridad MUST conservar su traducción actual.

#### Scenario: Violación de exclusión por carrera
- **GIVEN** dos altas concurrentes del mismo rango donde la base rechaza la segunda por la restricción de exclusión
- **WHEN** se procesa esa falla
- **THEN** la respuesta es `409` con `code = APPOINTMENT_SLOT_TAKEN` y el log contiene solo identificadores y el tipo de excepción

#### Scenario: Exclusión de bloqueos sin cambios
- **WHEN** la base rechaza un bloqueo por `schedule_block_no_overlap`
- **THEN** la respuesta sigue siendo `409` con `code = SCHEDULE_BLOCK_OVERLAP`

#### Scenario: Otra violación de integridad
- **WHEN** la base rechaza una operación por una restricción distinta de las de exclusión conocidas
- **THEN** la respuesta sigue siendo `409` con `code = CONFLICT`
