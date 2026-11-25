## MODIFIED Requirements

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
