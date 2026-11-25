# agenda Specification

## Purpose
Vista del odontólogo sobre su agenda: vistas diaria y semanal con turnos, bloqueos y horarios de atención; alta, cancelar, reprogramar y marcar turnos internos; mensajes amigables ante conflictos y sobreturno explícito.

## Requirements

### Requirement: Agenda diaria del profesional
El sistema SHALL mostrar los turnos `CONFIRMED` de un día local como badge de estado dentro de su rango contenido, en la cuadrícula de horas a partir de los tramos de atención del día.

#### Scenario: Turno confirmado en agenda
- **WHEN** la vista muestra la agenda de un día que tiene un turno `CONFIRMED`
- **THEN** se muestra un badge de estado dentro del rango contenido — `CONFIRMED` → «Confirmado», `COMPLETED` → «Atendido», `NO_SHOW` → «Ausente», `CANCELLED` → «Cancelado» (clases `.badge-confirmado/.badge-atendido/.badge-ausente/.badge-cancelado` del design system) — y el turno ocupa su rango contenido. La palabra "reservado" no se muestra en v1: no existe estado `RESERVED` en el modelo (TBD en `design-system/README.md:53`); los bloques se muestran rayados con candado (`.block`).

### Requirement: Agenda semanal del profesional
El sistema SHALL mostrar la agenda del resto de los días de la semana con la misma construcción que la diaria.

#### Scenario: Semana con varios días
- **WHEN** la vista muestra la agenda de una semana
- **THEN** se organizan los días de la semana y la vista acepta navegar al día anterior/siguiente

### Requirement: Bloqueos y huecos en la agenda
El sistema SHALL mostrar los rangos bloqueados (rayado + candado, sin motivo ni instantes expuestos) y ofrecer huecos libres clickeables para cargar un turno.

#### Scenario: Bloqueo mostrado
- **WHEN** hay un rango bloqueado en el día
- **THEN** se muestra rayado con icono de candado y no se ofrece turno dentro del rango

#### Scenario: Hueco libre clickeable
- **WHEN** el rango está libre
- **THEN** se muestra clickeable y abre el formulario de turno

### Requirement: Turnos de resultado que siguen ocupando
Un turno `COMPLETED` o `NO_SHOW` SHALL aparecer en la agenda con su badge (atendido/ausente) y seguir ocupando su rango.

#### Scenario: Turno terminal
- **WHEN** la agenda muestra un turno `COMPLETED` o `NO_SHOW`
- **THEN** aparece con su estado correspondiente y su rango no queda libre

### Requirement: Estado vacío
El sistema SHALL mostrar un estado vacío con CTA para cargar un turno cuando el día no tiene turnos.

#### Scenario: Día sin turnos
- **WHEN** la agenda del día está vacía
- **THEN** se muestra un estado vacío con botón "Nuevo turno"

### Requirement: Alta de turno
El sistema SHALL permitir al odontólogo cargar un turno interno: selección de paciente activo existente o creación de nuevo, selección de prestación con duración snapshot y selección de slot desde la disponibilidad interna. El formulario de alta SHALL mostrarse en un diálogo que, cerrado, MUST NOT quedar visible ni permanecer en el documento, y abrirlo de nuevo SHALL reemplazar cualquier formulario anterior.

#### Scenario: Paciente existente
- **WHEN** el odontólogo carga un turno seleccionando un paciente activo
- **THEN** se envía `POST /api/v1/appointments` con `patientId`, `serviceId`, `startsAt` y la agenda muestra el turno con el fin devuelto por el servidor

#### Scenario: Paciente nuevo
- **WHEN** no hay coincidencias activas en la búsqueda
- **THEN** el formulario permite dar de alta un nuevo paciente (`POST /api/v1/patients`) antes de cargar el turno

#### Scenario: Sobreturno explícito
- **WHEN** el odontólogo marca la opción de sobreturno y envía el turno
- **THEN** el turno se crea fuera del horario de atención con indicador de sobreturno en la agenda

#### Scenario: Formulario cerrado no visible
- **GIVEN** el formulario «Nuevo turno» abierto en la agenda
- **WHEN** se cierra el diálogo y se navega a otra vista
- **THEN** el formulario ya no está en el documento y ningún diálogo cerrado se muestra en la página

#### Scenario: Reapertura del formulario
- **GIVEN** el formulario «Nuevo turno» abierto
- **WHEN** se vuelve a abrir el alta de turno
- **THEN** el documento contiene un único formulario de alta

### Requirement: Slots desde disponibilidad interna
El sistema SHALL mostrar los slots libres de un día desde `GET /api/v1/availability?serviceId&date` y SHALL crear el turno en el slot elegido.

#### Scenario: Slots ofrecidos
- **WHEN** se elige la prestación y la fecha, el sistema consulta la disponibilidad interna
- **THEN** se ofrecen los slots libres de esa prestación para elegir

### Requirement: Gestión de turno (cancelar, reprogramar, resultado)
El sistema SHALL permitir, desde la agenda, cancelar (`POST /api/v1/appointments/{id}/cancel`), reprogramar a un slot libre (`POST /{id}/reschedule`) y marcar `COMPLETED`/`NO_SHOW` (`POST /{id}/complete`, `POST /{id}/no-show`).

#### Scenario: Cancelar
- **WHEN** el odontólogo activa cancelar un turno
- **THEN** se confirma la acción y el turno aparece cancelado en su rango

#### Scenario: Reprogramar
- **WHEN** el odontólogo elige un slot libre y reprograma
- **THEN** el turno se muestra en el nuevo rango

#### Scenario: Marcar resultado
- **WHEN** el odontólogo marca un turno que ya empezó como atendido o ausente
- **THEN** el turno muestra su nuevo estado en la agenda

### Requirement: Mensajes de conflictos amigables
El sistema SHALL mostrar mensajes sin instantes ni datos del turno existente ante los conflictos antes documentados.

#### Scenario: Solapamiento (409 APPOINTMENT_SLOT_TAKEN)
- **WHEN** al enviar el turno el servidor responde con `409 APPOINTMENT_SLOT_TAKEN`
- **THEN** se muestra un mensaje genérico sin horarios ni datos del turno ocupante

#### Scenario: Bloqueado (409 APPOINTMENT_SLOT_BLOCKED)
- **WHEN** el servidor responde `409 APPOINTMENT_SLOT_BLOCKED`
- **THEN** se muestra que ese horario está bloqueado

#### Scenario: Fuera de horario (422 OUTSIDE_WORKING_HOURS)
- **WHEN** el servidor responde `422 OUTSIDE_WORKING_HOURS`
- **THEN** el mensaje refiere al horario de atención del consultorio

#### Scenario: Error de conexión
- **WHEN** no hay conexión al crear o gestionar el turno
- **THEN** se muestra un aviso genérico de conexión y la agenda conserva su estado
