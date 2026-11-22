# Spec Delta

## Purpose

Permite al odontólogo gestionar los rangos en que su agenda no está disponible —por vacaciones, trámites u otros motivos— impidiendo que se reserven mientras conserva los turnos ya confirmados.

## ADDED Requirements

### Requirement: Gestión de bloqueos de agenda
El sistema SHALL permitir al odontólogo autenticado listar, crear, consultar, editar y eliminar bloqueos del consultorio a través de `/api/v1/schedule-blocks`. Cada bloqueo SHALL exponer identificador, inicio, fin, motivo, fecha de creación y fecha de actualización. El listado SHALL ordenarse por inicio ascendente y SHALL aceptar un filtro opcional por rango de fechas; la creación responde `201` con `Location`, la edición `200`, la consulta de detalle `200` y la eliminación `204`. Todas las operaciones SHALL actuar únicamente sobre el consultorio de la sesión.

#### Scenario: Crear y consultar un bloqueo
- **GIVEN** una sesión autenticada de odontólogo y un rango futuro con motivo no vacío
- **WHEN** envía `POST /api/v1/schedule-blocks` con ese rango y motivo
- **THEN** recibe `201`, el bloqueo queda persistido con marcas de tiempo generadas por el servidor y `GET /api/v1/schedule-blocks/{id}` lo devuelve con los mismos datos

#### Scenario: Listar bloqueos filtrados por rango
- **GIVEN** bloqueos en semanas distintas del consultorio
- **WHEN** se solicita `GET /api/v1/schedule-blocks?from=<inicio>&to=<fin>`
- **THEN** se devuelven únicamente los bloqueos cuyo intervalo intersecta el rango pedido, ordenados por inicio

#### Scenario: Bloqueo inexistente
- **WHEN** se solicita el detalle, la edición o la eliminación de un identificador inexistente en el consultorio
- **THEN** el sistema responde `404` con formato `application/problem+json` y `code = SCHEDULE_BLOCK_NOT_FOUND`

#### Scenario: Editar y eliminar un bloqueo
- **GIVEN** un bloqueo existente del consultorio
- **WHEN** se envía `PUT /api/v1/schedule-blocks/{id}` con un rango y motivo válidos y luego `DELETE /api/v1/schedule-blocks/{id}`
- **THEN** la edición responde `200` con el bloqueo actualizado, la fecha de actualización cambia, la eliminación responde `204` y una consulta posterior devuelve `404`

### Requirement: Validez del rango y del motivo
Todo bloqueo SHALL tener un instante de inicio y un instante de fin expresados en UTC, con fin estrictamente posterior al inicio, y un motivo no vacío de hasta 500 caracteres. Los valores de inicio y fin SHALL rechazarse si no son instantes ISO-8601 válidos. El servidor SHALL generar las marcas de tiempo de creación y actualización; los valores enviados por el cliente para esos campos MUST ser ignorados o rechazados.

#### Scenario: Rango invertido o de duración nula
- **WHEN** se crea o edita un bloqueo con fin igual o anterior al inicio
- **THEN** el sistema responde `400` con `code = INVALID_RANGE` y el bloqueo no se persiste ni se modifica

#### Scenario: Motivo ausente o demasiado largo
- **WHEN** se crea un bloqueo con motivo vacío, solo espacios o mayor a 500 caracteres
- **THEN** el sistema responde `400` con `code = VALIDATION_FAILED` indicando el campo inválido sin devolver el valor rechazado

#### Scenario: Filtro de fechas inválido
- **WHEN** se consulta el listado con `from` o `to` que no son instantes válidos, o con `from` posterior o igual a `to`
- **THEN** el sistema responde `400` con `code = INVALID_RANGE` sin exponer el valor recibido

### Requirement: No solapamiento entre bloqueos
Dos bloqueos del mismo consultorio MUST NOT tener intervalos semiabiertos `[inicio, fin)` que se intersecten. Un bloqueo que comience exactamente cuando otro termina SHALL considerarse adyacente y por lo tanto válido. La edición de un bloqueo SHALL ignorar el propio bloqueo al comprobar solapamientos. Un intento de solapamiento SHALL rechazarse con `409` y `code = SCHEDULE_BLOCK_OVERLAP`, incluso cuando dos solicitudes concurrentes llegan a la base de datos al mismo tiempo.

#### Scenario: Solapamiento parcial o total
- **GIVEN** un bloqueo existente de 09:00 a 11:00 en el consultorio
- **WHEN** se crea otro de 10:00 a 12:00, o uno contenido de 09:30 a 10:30, o uno idéntico
- **THEN** cada intento responde `409` con `code = SCHEDULE_BLOCK_OVERLAP` y no se crea el bloqueo

#### Scenario: Bloqueos adyacentes
- **GIVEN** un bloqueo existente de 09:00 a 11:00 en el consultorio
- **WHEN** se crea un bloqueo de 11:00 a 12:00
- **THEN** el sistema lo acepta con `201` porque los intervalos semiabiertos no se intersectan

#### Scenario: Edición sin falso conflicto consigo mismo
- **GIVEN** un bloqueo existente de 09:00 a 11:00 y ningún otro bloqueo en ese rango
- **WHEN** se edita su motivo y su rango a 09:30 a 11:00
- **THEN** la edición se acepta con `200`

#### Scenario: Carrera entre dos creaciones
- **GIVEN** dos solicitudes simultáneas con rangos que se solapan para el mismo consultorio
- **WHEN** ambas intentan crear el bloqueo
- **THEN** una responde `201` y la otra `409` con `code = SCHEDULE_BLOCK_OVERLAP`, sin que queden dos bloqueos solapados

### Requirement: Los bloqueos no cancelan turnos existentes
Crear, editar o eliminar un bloqueo MUST NOT cancelar, reprogramar ni modificar turnos confirmados. La respuesta de creación y edición SHALL incluir un campo `affectedAppointments` con los turnos comprendidos en el rango, expresados solo con identificador e instantes de inicio y fin. Mientras no exista una fuente de turnos en el sistema, ese campo SHALL devolverse vacío; la consulta efectiva se habilita cuando el módulo de turnos esté disponible.

#### Scenario: Bloqueo sobre un rango con turnos
- **GIVEN** turnos confirmados dentro del rango que se desea bloquear
- **WHEN** el odontólogo crea o edita el bloqueo
- **THEN** el bloqueo se guarda, los turnos siguen en su estado original y el campo `affectedAppointments` los lista sin modificarlos

#### Scenario: Sin fuente de turnos disponible
- **GIVEN** una instalación todavía sin persistencia de turnos
- **WHEN** se crea un bloqueo
- **THEN** la respuesta incluye `affectedAppointments` como lista vacía y la operación es exitosa

### Requirement: Trazabilidad sin exposición de datos
El sistema SHALL registrar en el bloqueo quién y cuándo lo creó y modificó mediante identificadores y marcas de tiempo del servidor, y MUST NOT incluir el motivo ni datos personales en los logs de aplicación; en los logs SHALL figurar solo identificadores de bloqueo y de consultorio.

#### Scenario: Registro de auditoría en logs
- **WHEN** se crea un bloqueo cuyo motivo contiene datos de una persona
- **THEN** el log solo contiene el identificador del bloqueo y del consultorio, y ni el motivo ni esos datos aparecen en el log ni en la respuesta de error
