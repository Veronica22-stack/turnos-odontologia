# appointments Specification

## Purpose
Gestiona el ciclo de vida de los turnos del consultorio (alta manual, consulta, cancelación, reprogramación atómica y registro de resultado) garantizando que ningún turno vigente se superponga con otro turno ni con un bloqueo de agenda.

## Requirements

### Requirement: Alta manual de turno
El odontólogo SHALL poder crear un turno con `POST /api/v1/appointments` indicando `patientId`, `serviceId`, `startsAt`, `overbooking` (opcional, por defecto `false`) y `notes` (opcional, hasta 1000 caracteres). El turno creado SHALL quedar en estado `CONFIRMED`, con origen `INTERNAL`, y la respuesta SHALL ser `201` con el turno y su ubicación. `startsAt` MUST ser posterior al instante actual del servidor.

#### Scenario: Alta exitosa
- **GIVEN** un paciente activo, una prestación activa de 30 minutos y un horario de atención libre de 09:00 a 13:00 hora local
- **WHEN** el odontólogo crea un turno a las 10:00 hora local de un día futuro
- **THEN** el sistema responde `201` con `status = CONFIRMED`, `origin = INTERNAL`, `overbooking = false`, `endsAt` 30 minutos posterior a `startsAt` y la cabecera `Location` del turno

#### Scenario: Inicio en el pasado
- **WHEN** el odontólogo crea un turno cuyo `startsAt` es anterior al instante actual del servidor
- **THEN** el sistema responde `422` con `code = APPOINTMENT_IN_PAST` y no se crea ningún turno

#### Scenario: Inicio igual al instante actual
- **GIVEN** un reloj del servidor fijo
- **WHEN** el odontólogo crea un turno con `startsAt` exactamente igual al instante actual
- **THEN** el sistema responde `422` con `code = APPOINTMENT_IN_PAST`

#### Scenario: Campos obligatorios ausentes o inválidos
- **WHEN** el odontólogo omite `patientId`, `serviceId` o `startsAt`, o envía `notes` de más de 1000 caracteres
- **THEN** el sistema responde `400` con `code = VALIDATION_FAILED` y la lista de campos inválidos, sin devolver los valores enviados

### Requirement: Duración del turno tomada de la prestación
El fin del turno SHALL calcularse como `startsAt` más la duración vigente de la prestación al momento del alta, y esa duración SHALL guardarse como snapshot en el turno. Cambios posteriores en la prestación MUST NOT modificar la duración, el fin ni el estado de turnos ya creados. El cliente MUST NOT poder enviar el fin ni la duración.

#### Scenario: Snapshot al crear
- **GIVEN** una prestación activa de 45 minutos
- **WHEN** se crea un turno a las 10:00 hora local
- **THEN** el turno guarda una duración de 45 minutos y termina a las 10:45 hora local

#### Scenario: Prestación editada después del alta
- **GIVEN** un turno creado con una prestación de 45 minutos
- **WHEN** el odontólogo cambia la duración de la prestación a 60 minutos y luego consulta el turno
- **THEN** el turno conserva 45 minutos de duración y su fin original

#### Scenario: Fin enviado por el cliente
- **WHEN** el odontólogo envía un campo `endsAt` o `durationMin` en el alta
- **THEN** el valor se ignora y el fin se calcula con la duración de la prestación

### Requirement: Prestación existente y activa
Un turno nuevo SHALL referenciar una prestación existente del consultorio y activa. Una prestación inexistente SHALL rechazarse con `404` y `code = SERVICE_NOT_FOUND`; una prestación dada de baja SHALL rechazarse con `422` y `code = SERVICE_INACTIVE`. La visibilidad pública de la prestación MUST NOT impedir su uso en un turno manual.

#### Scenario: Prestación inexistente
- **WHEN** el odontólogo crea un turno con un `serviceId` que no existe en el consultorio
- **THEN** el sistema responde `404` con `code = SERVICE_NOT_FOUND`

#### Scenario: Prestación inactiva
- **GIVEN** una prestación dada de baja
- **WHEN** el odontólogo crea un turno con esa prestación
- **THEN** el sistema responde `422` con `code = SERVICE_INACTIVE` y no se crea el turno

#### Scenario: Prestación activa no visible públicamente
- **GIVEN** una prestación activa marcada como no visible en el catálogo público
- **WHEN** el odontólogo crea un turno manual con esa prestación
- **THEN** el sistema responde `201`

### Requirement: Paciente existente y activo
Un turno nuevo SHALL referenciar un paciente existente de la instalación y activo, identificado solo por `patientId`; el alta de turno MUST NOT crear ni modificar pacientes. Un paciente inexistente SHALL rechazarse con `404` y `code = PATIENT_NOT_FOUND`; un paciente dado de baja SHALL rechazarse con `409` y `code = PATIENT_INACTIVE`, con el mismo status que usa la historia clínica para ese código.

#### Scenario: Paciente activo
- **GIVEN** un paciente activo creado previamente con el alta de pacientes
- **WHEN** el odontólogo crea un turno con su `patientId`
- **THEN** el sistema responde `201` y el turno referencia a ese paciente sin modificar sus datos

#### Scenario: Paciente inexistente
- **WHEN** el odontólogo crea un turno con un `patientId` que no existe en la instalación
- **THEN** el sistema responde `404` con `code = PATIENT_NOT_FOUND` y no se crea ningún paciente ni turno

#### Scenario: Paciente dado de baja
- **GIVEN** un paciente dado de baja
- **WHEN** el odontólogo crea un turno para ese paciente
- **THEN** el sistema responde `409` con `code = PATIENT_INACTIVE` y no se crea ningún turno

### Requirement: No solapamiento entre turnos
Dos turnos del mismo consultorio cuyo estado no sea `CANCELLED` MUST NOT tener intervalos semiabiertos `[inicio, fin)` que se intersecten; los turnos `COMPLETED` y `NO_SHOW` siguen ocupando su rango. Un turno que empieza cuando otro termina SHALL aceptarse. Un intento de solapamiento SHALL rechazarse con `409` y `code = APPOINTMENT_SLOT_TAKEN`, aun con `overbooking = true`.

#### Scenario: Turno adyacente
- **GIVEN** un turno confirmado de 10:00 a 10:30
- **WHEN** se crea otro turno de 10:30 a 11:00
- **THEN** el sistema responde `201`

#### Scenario: Solapamiento parcial, contenido o idéntico
- **GIVEN** un turno confirmado de 10:00 a 10:30
- **WHEN** se crea un turno de 10:15 a 10:45, uno de 10:00 a 10:30 o uno de 09:45 a 11:00
- **THEN** cada intento responde `409` con `code = APPOINTMENT_SLOT_TAKEN` y no revela datos del turno existente

#### Scenario: Sobreturno superpuesto
- **GIVEN** un turno confirmado de 10:00 a 10:30
- **WHEN** se crea un turno con `overbooking = true` de 10:15 a 10:45
- **THEN** el sistema responde `409` con `code = APPOINTMENT_SLOT_TAKEN`

#### Scenario: Turno cancelado libera el rango
- **GIVEN** un turno de 10:00 a 10:30 cancelado
- **WHEN** se crea otro turno de 10:00 a 10:30
- **THEN** el sistema responde `201`

#### Scenario: Turnos completados o ausentes siguen ocupando
- **GIVEN** un turno de 10:00 a 10:30 en estado `COMPLETED` o `NO_SHOW`
- **WHEN** se intenta ocupar ese mismo rango por cualquier vía que llegue a la base
- **THEN** la operación se rechaza por solapamiento

### Requirement: Carrera entre altas simultáneas
Cuando dos solicitudes concurrentes intenten ocupar rangos que se intersectan en el mismo consultorio, exactamente una SHALL confirmarse y la otra SHALL responder `409` con `code = APPOINTMENT_SLOT_TAKEN`. El sistema MUST NOT persistir dos turnos vigentes superpuestos en ninguna intercalación de transacciones.

#### Scenario: Dos altas simultáneas del mismo slot
- **GIVEN** un slot libre de 10:00 a 10:30 y dos solicitudes que lo piden al mismo tiempo en transacciones reales
- **WHEN** ambas se ejecutan concurrentemente
- **THEN** una responde `201`, la otra `409` con `code = APPOINTMENT_SLOT_TAKEN`, y la base contiene un solo turno vigente en ese rango

#### Scenario: Altas simultáneas parcialmente superpuestas
- **GIVEN** dos solicitudes concurrentes de 10:00 a 10:30 y de 10:15 a 10:45
- **WHEN** ambas se ejecutan concurrentemente
- **THEN** exactamente una se confirma y la otra responde `409`

#### Scenario: Altas simultáneas de slots adyacentes
- **GIVEN** dos solicitudes concurrentes de 10:00 a 10:30 y de 10:30 a 11:00
- **WHEN** ambas se ejecutan concurrentemente
- **THEN** ambas responden `201`

### Requirement: No solapamiento con bloqueos de agenda
Un turno nuevo o reprogramado MUST NOT intersectar un bloqueo de agenda del consultorio, ni siquiera como sobreturno. El intento SHALL rechazarse con `409` y `code = APPOINTMENT_SLOT_BLOCKED`, sin revelar el motivo ni los instantes del bloqueo. Un turno adyacente a un bloqueo SHALL aceptarse.

#### Scenario: Turno dentro de un bloqueo
- **GIVEN** un bloqueo de 09:00 a 11:00
- **WHEN** se crea un turno de 10:30 a 11:00
- **THEN** el sistema responde `409` con `code = APPOINTMENT_SLOT_BLOCKED` y la respuesta no incluye el motivo del bloqueo

#### Scenario: Sobreturno dentro de un bloqueo
- **GIVEN** un bloqueo de 18:00 a 20:00 fuera del horario de atención
- **WHEN** se crea un turno con `overbooking = true` de 18:30 a 19:00
- **THEN** el sistema responde `409` con `code = APPOINTMENT_SLOT_BLOCKED`

#### Scenario: Turno adyacente a un bloqueo
- **GIVEN** un bloqueo de 09:00 a 11:00
- **WHEN** se crea un turno de 11:00 a 11:30 dentro del horario de atención
- **THEN** el sistema responde `201`

### Requirement: Horario de atención y sobreturno explícito
Sin `overbooking`, el rango del turno, en la zona del consultorio, SHALL quedar contenido en un intervalo continuo de atención de su día local; si no, SHALL rechazarse con `422` y `code = OUTSIDE_WORKING_HOURS`. Los tramos del mismo día que se tocan SHALL fusionarse en un único intervalo; los separados por un hueco MUST NOT unirse. Con `overbooking = true` no se exige horario y el valor SHALL persistirse y devolverse.

#### Scenario: Turno dentro del tramo
- **GIVEN** un tramo de atención de 09:00 a 13:00 hora local el día del turno
- **WHEN** se crea un turno de 12:30 a 13:00 sin sobreturno
- **THEN** el sistema responde `201` con `overbooking = false`

#### Scenario: Turno que excede el fin del tramo
- **GIVEN** un tramo de atención de 09:00 a 13:00 hora local
- **WHEN** se crea un turno de 12:45 a 13:15 sin sobreturno
- **THEN** el sistema responde `422` con `code = OUTSIDE_WORKING_HOURS`

#### Scenario: Turno que atraviesa tramos contiguos
- **GIVEN** tramos de atención de 09:00 a 12:00 y de 12:00 a 15:00 hora local el mismo día
- **WHEN** se crea un turno de 11:30 a 12:30 sin sobreturno
- **THEN** el sistema responde `201` con `overbooking = false`

#### Scenario: Turno que atraviesa un hueco entre tramos
- **GIVEN** tramos de atención de 09:00 a 12:00 y de 13:00 a 15:00 hora local el mismo día
- **WHEN** se crea un turno de 11:30 a 12:30 sin sobreturno
- **THEN** el sistema responde `422` con `code = OUTSIDE_WORKING_HOURS` y no se crea el turno

#### Scenario: Bordes exactos del intervalo fusionado
- **GIVEN** tramos de atención de 09:00 a 12:00 y de 12:00 a 15:00 hora local el mismo día y una prestación de 30 minutos
- **WHEN** se crean sin sobreturno un turno de 09:00 a 09:30 y otro de 14:30 a 15:00
- **THEN** ambos responden `201`, y un turno de 14:45 a 15:15 o de 08:45 a 09:15 responde `422` con `code = OUTSIDE_WORKING_HOURS`

#### Scenario: Turno que cruza la medianoche local
- **GIVEN** un tramo de atención de 20:00 a 23:59 hora local
- **WHEN** se crea un turno sin sobreturno de 23:45 a 00:15 hora local
- **THEN** el sistema responde `422` con `code = OUTSIDE_WORKING_HOURS`

#### Scenario: Día sin atención
- **GIVEN** un día de la semana sin tramos de atención
- **WHEN** se crea un turno ese día sin sobreturno
- **THEN** el sistema responde `422` con `code = OUTSIDE_WORKING_HOURS`

#### Scenario: Sobreturno fuera de horario
- **GIVEN** un tramo de atención de 09:00 a 13:00 y ningún turno ni bloqueo a las 19:00
- **WHEN** se crea un turno con `overbooking = true` de 19:00 a 19:30 hora local
- **THEN** el sistema responde `201` con `overbooking = true`

#### Scenario: Hora local del consultorio
- **GIVEN** un tramo de atención de 09:00 a 13:00 en `America/Argentina/Buenos_Aires`
- **WHEN** se crea un turno con `startsAt = 12:00Z` (09:00 hora local)
- **THEN** el sistema lo acepta, y uno con `startsAt = 11:30Z` (08:30 hora local) se rechaza con `OUTSIDE_WORKING_HOURS`

### Requirement: Parámetros de reserva pública no aplicados a turnos manuales
El alta y la reprogramación manuales MUST NOT aplicar la anticipación mínima, el horizonte máximo de reserva ni la alineación a la granularidad de slots; solo SHALL exigir que el inicio sea futuro.

#### Scenario: Turno con menos anticipación que la mínima pública
- **GIVEN** una anticipación mínima pública de 120 minutos
- **WHEN** el odontólogo crea un turno que empieza dentro de 30 minutos, en horario libre de atención
- **THEN** el sistema responde `201`

#### Scenario: Turno más allá del horizonte público
- **GIVEN** un horizonte máximo de reserva de 60 días
- **WHEN** el odontólogo crea un turno a 90 días, en horario libre de atención
- **THEN** el sistema responde `201`

#### Scenario: Inicio no alineado a la granularidad
- **GIVEN** una granularidad de 15 minutos
- **WHEN** el odontólogo crea un turno a las 10:07 hora local, en horario libre de atención
- **THEN** el sistema responde `201` y el turno empieza a las 10:07

### Requirement: Consulta y listado de turnos
El odontólogo SHALL poder consultar un turno por identificador y listar los turnos de cualquier estado cuyo rango intersecte `[from, to)`, ordenados por inicio, con una amplitud máxima de 93 días entre `from` y `to`. La respuesta SHALL incluir identificadores de paciente y prestación, instantes en UTC, duración, estado, origen, sobreturno, datos de cancelación y notas, sin otros datos personales del paciente.

#### Scenario: Consulta de un turno existente
- **WHEN** el odontólogo consulta `GET /api/v1/appointments/{id}` de un turno del consultorio
- **THEN** el sistema responde `200` con el turno, incluyendo `patientId` y sin nombre, DNI, email ni teléfono del paciente

#### Scenario: Turno inexistente
- **WHEN** el odontólogo consulta, cancela, reprograma o marca un identificador que no corresponde a un turno del consultorio
- **THEN** el sistema responde `404` con `code = APPOINTMENT_NOT_FOUND`

#### Scenario: Listado por rango
- **GIVEN** turnos confirmados, cancelados y completados en una semana
- **WHEN** el odontólogo solicita `GET /api/v1/appointments?from=...&to=...` para esa semana
- **THEN** recibe `200` con todos esos turnos ordenados por inicio, incluido un turno que empieza antes de `from` y termina después

#### Scenario: Rango de listado inválido
- **WHEN** el odontólogo omite `from` o `to`, o envía `to` anterior o igual a `from`
- **THEN** el sistema responde `400` con `code = INVALID_RANGE` sin devolver los valores enviados

#### Scenario: Rango de listado de 93 días aceptado
- **WHEN** el odontólogo solicita el listado con `to` exactamente 93 días posterior a `from`
- **THEN** el sistema responde `200` con los turnos que intersectan el rango

#### Scenario: Rango de listado mayor a 93 días rechazado
- **WHEN** el odontólogo solicita el listado con `to` más de 93 días posterior a `from` (por ejemplo 94 días, o 93 días más un nanosegundo)
- **THEN** el sistema responde `400` con `code = INVALID_RANGE` sin devolver los valores enviados y sin leer turnos

#### Scenario: Rango de listado invertido
- **WHEN** el odontólogo solicita el listado con `to` anterior a `from`
- **THEN** el sistema responde `400` con `code = INVALID_RANGE`

### Requirement: Cancelación de turno
Un turno `CONFIRMED` SHALL poder cancelarse en cualquier momento con `POST /api/v1/appointments/{id}/cancel`, quedando en `CANCELLED` con `cancelledBy = DENTIST` y `cancelledAt` del servidor, y liberando su rango. Cancelar un turno en estado terminal SHALL responder `409` con `code = INVALID_STATUS_TRANSITION`.

#### Scenario: Cancelación de turno futuro
- **GIVEN** un turno confirmado futuro
- **WHEN** el odontólogo lo cancela
- **THEN** el sistema responde `200` con `status = CANCELLED`, `cancelledBy = DENTIST` y `cancelledAt` igual al instante del servidor

#### Scenario: Cancelación de turno ya iniciado
- **GIVEN** un turno confirmado cuyo inicio ya pasó
- **WHEN** el odontólogo lo cancela
- **THEN** el sistema responde `200` con `status = CANCELLED`

#### Scenario: Cancelar un turno terminal
- **GIVEN** un turno en estado `CANCELLED`, `COMPLETED` o `NO_SHOW`
- **WHEN** el odontólogo intenta cancelarlo
- **THEN** el sistema responde `409` con `code = INVALID_STATUS_TRANSITION` y el turno no cambia

### Requirement: Registro de resultado del turno
Un turno `CONFIRMED` cuyo inicio sea anterior o igual al instante actual SHALL poder marcarse `COMPLETED` o `NO_SHOW` con `POST .../{id}/complete` o `POST .../{id}/no-show`. Antes de su inicio SHALL responder `422` con `code = APPOINTMENT_NOT_STARTED`; sobre un estado terminal, `409` con `code = INVALID_STATUS_TRANSITION`.

#### Scenario: Marcar completado o ausente
- **GIVEN** un turno confirmado cuyo inicio ya pasó
- **WHEN** el odontólogo lo marca `COMPLETED`, o en otro turno equivalente `NO_SHOW`
- **THEN** el sistema responde `200` con el nuevo estado y el turno sigue ocupando su rango

#### Scenario: Marcar exactamente al inicio
- **GIVEN** un reloj del servidor fijo igual al inicio de un turno confirmado
- **WHEN** el odontólogo lo marca `COMPLETED`
- **THEN** el sistema responde `200`

#### Scenario: Marcar antes del inicio
- **GIVEN** un turno confirmado futuro
- **WHEN** el odontólogo lo marca `COMPLETED` o `NO_SHOW`
- **THEN** el sistema responde `422` con `code = APPOINTMENT_NOT_STARTED` y el turno sigue `CONFIRMED`

#### Scenario: Marcar un turno terminal
- **GIVEN** un turno `CANCELLED`, `COMPLETED` o `NO_SHOW`
- **WHEN** el odontólogo intenta marcarlo `COMPLETED` o `NO_SHOW`
- **THEN** el sistema responde `409` con `code = INVALID_STATUS_TRANSITION`

### Requirement: Reprogramación atómica
Un turno `CONFIRMED` futuro SHALL poder moverse con `POST .../{id}/reschedule` (`startsAt`, `overbooking`) conservando identificador, paciente, prestación y duración snapshot, con las mismas reglas del alta. La operación SHALL ser atómica: si el nuevo rango no está disponible, el turno original MUST quedar intacto.

#### Scenario: Reprogramación exitosa
- **GIVEN** un turno confirmado futuro de 10:00 a 10:30 y un rango libre de 15:00 a 15:30 en horario de atención
- **WHEN** el odontólogo lo reprograma a las 15:00
- **THEN** el sistema responde `200` con el mismo `id`, `startsAt` 15:00, `endsAt` 15:30 y `status = CONFIRMED`, y el rango de 10:00 queda libre

#### Scenario: Nuevo rango ocupado
- **GIVEN** un turno confirmado futuro de 10:00 a 10:30 y otro turno confirmado de 15:00 a 15:30
- **WHEN** el odontólogo reprograma el primero a las 15:00
- **THEN** el sistema responde `409` con `code = APPOINTMENT_SLOT_TAKEN` y el primer turno conserva 10:00 a 10:30

#### Scenario: Nuevo rango solapado con el propio rango anterior
- **GIVEN** un turno confirmado futuro de 10:00 a 10:30 sin otros turnos cerca
- **WHEN** el odontólogo lo reprograma a las 10:15
- **THEN** el sistema responde `200` con el turno de 10:15 a 10:45

#### Scenario: Prestación desactivada o editada antes de reprogramar
- **GIVEN** un turno confirmado futuro de 45 minutos cuya prestación luego se dio de baja o pasó a durar 60 minutos
- **WHEN** el odontólogo lo reprograma a un rango libre en horario de atención
- **THEN** el sistema responde `200`, el turno conserva la prestación y 45 minutos de duración, y `endsAt` es el nuevo `startsAt` más 45 minutos

#### Scenario: Nuevo rango bloqueado o fuera de horario
- **GIVEN** un turno confirmado futuro
- **WHEN** el odontólogo lo reprograma dentro de un bloqueo, o fuera de horario sin sobreturno
- **THEN** el sistema responde `409` con `APPOINTMENT_SLOT_BLOCKED` o `422` con `OUTSIDE_WORKING_HOURS`, y el turno no cambia

#### Scenario: Turno no reprogramable
- **GIVEN** un turno `CANCELLED`, `COMPLETED` o `NO_SHOW`, y otro `CONFIRMED` cuyo inicio ya pasó
- **WHEN** el odontólogo intenta reprogramarlos
- **THEN** el primero responde `409` con `INVALID_STATUS_TRANSITION` y el segundo `422` con `APPOINTMENT_ALREADY_STARTED`

#### Scenario: Nuevo inicio en el pasado
- **GIVEN** un turno confirmado futuro
- **WHEN** el odontólogo lo reprograma a un instante anterior o igual al actual
- **THEN** el sistema responde `422` con `code = APPOINTMENT_IN_PAST` y el turno no cambia

### Requirement: Serialización de turnos y bloqueos por consultorio
Altas, reprogramaciones, cancelaciones y registros de resultado de turnos y altas y ediciones de bloqueos del mismo consultorio SHALL serializarse, de modo que ninguna intercalación concurrente deje un turno vigente dentro de un bloqueo que no lo informe en sus turnos afectados ni pierda la actualización de otra operación sobre el mismo turno.

#### Scenario: Turno y bloqueo simultáneos sobre el mismo rango
- **GIVEN** una solicitud de turno de 10:00 a 10:30 y una solicitud de bloqueo de 09:00 a 12:00 ejecutadas concurrentemente en transacciones reales
- **WHEN** ambas terminan
- **THEN** o bien el bloqueo se creó y el turno respondió `409` con `APPOINTMENT_SLOT_BLOCKED`, o bien el turno se creó y la respuesta del bloqueo lo lista en `affectedAppointments`

#### Scenario: Operaciones sobre rangos disjuntos
- **GIVEN** una solicitud de turno de 10:00 a 10:30 y una de bloqueo de 15:00 a 16:00 concurrentes
- **WHEN** ambas terminan
- **THEN** ambas se confirman

#### Scenario: Cancelar y reprogramar el mismo turno en paralelo
- **GIVEN** un turno confirmado futuro y una cancelación y una reprogramación suyas ejecutadas concurrentemente en transacciones reales
- **WHEN** ambas terminan
- **THEN** el resultado es consistente: o bien la cancelación ganó, el turno queda `CANCELLED` con `cancelledBy` y `cancelledAt` y la reprogramación responde `409` con `INVALID_STATUS_TRANSITION`, o bien la reprogramación ganó y la cancelación posterior deja el turno `CANCELLED` en el nuevo rango; nunca queda un turno `CONFIRMED` con `cancelledAt` ni se revierte una cancelación ya confirmada

#### Scenario: Completar y reprogramar el mismo turno en paralelo
- **GIVEN** un turno confirmado cuyo inicio ya pasó y una marca de resultado y una reprogramación suyas concurrentes
- **WHEN** ambas terminan
- **THEN** el turno queda en el estado terminal de la marca de resultado y la reprogramación responde `422` con `APPOINTMENT_ALREADY_STARTED` o `409` con `INVALID_STATUS_TRANSITION`, sin que el estado terminal se pierda

#### Scenario: Error en una operación no bloquea las siguientes
- **GIVEN** un alta de turno rechazada por solapamiento
- **WHEN** inmediatamente después se crea un bloqueo y otro turno en rangos libres
- **THEN** ambas operaciones se confirman sin espera indefinida

### Requirement: Eventos de cambio de turno
Tras crear, cancelar o reprogramar un turno, el sistema SHALL emitir un evento de turno confirmado, cancelado o reprogramado con identificadores e instantes (anteriores y nuevos al reprogramar), sin datos personales ni notas. Un oyente posterior al commit MUST NOT recibirlo si la operación falla o se revierte.

#### Scenario: Evento al confirmar
- **WHEN** se crea un turno con éxito
- **THEN** se emite un evento de turno confirmado con el identificador del turno, del paciente, de la prestación, el inicio, el fin y el origen

#### Scenario: Evento al cancelar y al reprogramar
- **WHEN** se cancela un turno, y luego se reprograma otro
- **THEN** se emite un evento de cancelación con quién canceló, y uno de reprogramación con los instantes anteriores y nuevos

#### Scenario: Sin evento ante rechazo
- **GIVEN** un oyente registrado para después del commit
- **WHEN** un alta o reprogramación se rechaza por solapamiento, bloqueo u horario
- **THEN** el oyente no recibe ningún evento

#### Scenario: Marcar resultado no emite eventos de notificación
- **WHEN** un turno se marca `COMPLETED` o `NO_SHOW`
- **THEN** no se emite evento de confirmación, cancelación ni reprogramación

### Requirement: Operaciones de turno reutilizables por otros módulos
El sistema SHALL ofrecer a otros módulos, por su contrato público, **solo** la reprogramación de un turno, aplicando las mismas reglas, atomicidad, serialización por consultorio, traducción de errores y eventos que la API interna. El alta y la cancelación de turnos MUST NOT publicarse en el contrato público en este change; SHALL quedar disponibles únicamente por la API interna del odontólogo.

#### Scenario: Reprogramación sin nuevo inicio
- **WHEN** se reprograma un turno sin nuevo inicio (ausente en el REST o nulo en el contrato público) o sin identificador de turno en el contrato público
- **THEN** la operación falla con un error de validación (`400` con `code = VALIDATION_FAILED` y el campo `newStartsAt` o `appointmentId`) y no con un error interno `500`; el turno no cambia

#### Scenario: Reprogramación desde otro módulo
- **GIVEN** un turno confirmado futuro y un rango nuevo libre
- **WHEN** otro módulo lo reprograma por el contrato público
- **THEN** el turno se mueve con las mismas reglas, conserva su identificador y duración snapshot, y se emite el evento de reprogramación

#### Scenario: Conflicto al reprogramar desde otro módulo
- **GIVEN** un turno confirmado futuro y un rango nuevo ya ocupado por otro turno vigente
- **WHEN** otro módulo intenta reprogramarlo a ese rango por el contrato público
- **THEN** recibe el mismo error de conflicto `APPOINTMENT_SLOT_TAKEN` que la API interna y el turno original queda intacto

#### Scenario: Reprogramación desde otro módulo fuera de horario o bloqueada
- **GIVEN** un turno confirmado futuro
- **WHEN** otro módulo intenta reprogramarlo sin sobreturno fuera del horario de atención, o dentro de un bloqueo
- **THEN** recibe `OUTSIDE_WORKING_HOURS` o `APPOINTMENT_SLOT_BLOCKED` respectivamente y el turno no cambia

#### Scenario: Contrato público sin alta ni cancelación
- **WHEN** se inspecciona el contrato público de operaciones de turno
- **THEN** expone únicamente la operación de reprogramación, sin operaciones de alta ni de cancelación

### Requirement: Turnos sin borrado y con datos mínimos en logs
Los turnos MUST NOT eliminarse físicamente ni exponerse un endpoint de borrado. Los logs de turnos SHALL contener solo identificadores de turno, paciente, prestación y consultorio, nunca notas, nombre, DNI, email ni teléfono.

#### Scenario: Sin endpoint de borrado
- **WHEN** el odontólogo envía `DELETE /api/v1/appointments/{id}`
- **THEN** el turno no se elimina y la respuesta es un error en formato único

#### Scenario: Notas con datos personales
- **GIVEN** un alta de turno cuyas notas contienen nombre, DNI o teléfono
- **WHEN** el alta se acepta o se rechaza
- **THEN** ninguno de esos valores aparece en el log ni en la respuesta de error
