# patients-management Specification

## Purpose
Permite registrar, buscar, consultar y modificar las fichas de los pacientes del consultorio, garantizando la unicidad por DNI y la baja lógica sin pérdida histórica de datos. Expone además a otros módulos una consulta mínima de existencia y estado de un paciente, sin datos personales.

## Requirements

### Requirement: Consulta de existencia y estado de paciente para otros módulos
El módulo de pacientes SHALL ofrecer a otros módulos una consulta por identificador de paciente que devuelve solo si existe en la instalación y si está activo, sin datos personales y sin registrar `PATIENT_READ`.

#### Scenario: Paciente activo
- **GIVEN** un paciente activo de la instalación
- **WHEN** otro módulo consulta su estado
- **THEN** obtiene que existe y está activo, sin nombre, DNI, teléfono ni email

#### Scenario: Paciente dado de baja
- **GIVEN** un paciente dado de baja
- **WHEN** otro módulo consulta su estado
- **THEN** obtiene que existe y está inactivo

#### Scenario: Paciente inexistente
- **WHEN** otro módulo consulta un identificador que no corresponde a ningún paciente de la instalación
- **THEN** obtiene que no existe

### Requirement: Registro de nuevo paciente
El sistema SHALL permitir al odontólogo autenticado registrar un nuevo paciente proporcionando nombre, apellido, DNI, teléfono y correo electrónico. El DNI MUST ser único por consultorio.

#### Scenario: Creación exitosa de paciente
- **GIVEN** datos válidos de un paciente cuyo DNI no existe en el consultorio
- **WHEN** el odontólogo solicita la creación del paciente
- **THEN** el sistema persiste el paciente con estado activo y código 201

#### Scenario: DNI duplicado rechazado
- **GIVEN** un paciente ya registrado con un DNI determinado
- **WHEN** se intenta crear otro paciente con el mismo DNI en el mismo consultorio
- **THEN** el sistema rechaza la solicitud con código 409 y mensaje descriptivo de conflicto

### Requirement: Búsqueda y listado de pacientes
El sistema SHALL permitir al odontólogo buscar pacientes activos por coincidencia parcial en nombre, apellido, DNI o teléfono, devolviendo un resumen ordenado alfabéticamente.

#### Scenario: Búsqueda por coincidencia
- **GIVEN** varios pacientes registrados en el consultorio
- **WHEN** el odontólogo busca por un término que coincide con el apellido o DNI de un paciente
- **THEN** el sistema retorna la lista de pacientes que coinciden con el criterio de búsqueda

#### Scenario: Pacientes dados de baja excluidos de la búsqueda
- **GIVEN** un paciente con baja lógica
- **WHEN** se realiza una búsqueda general de pacientes
- **THEN** el paciente dado de baja no aparece en los resultados habituales

### Requirement: Consulta individual y auditoría de ficha
El sistema SHALL permitir al odontólogo consultar los detalles de un paciente por su identificador único. Toda consulta exitosa MUST registrar un evento de auditoría PATIENT_READ.

#### Scenario: Consulta de ficha existente
- **GIVEN** un paciente registrado en el consultorio
- **WHEN** el odontólogo solicita ver la ficha individual de ese paciente
- **THEN** el sistema responde 200 con los datos del paciente y registra un evento PATIENT_READ en el log de auditoría

#### Scenario: Paciente inexistente
- **WHEN** se solicita un identificador de paciente que no existe
- **THEN** el sistema responde 404 con formato ProblemDetail y no registra acceso indebido

### Requirement: Actualización de datos del paciente
El sistema SHALL permitir al odontólogo modificar los datos de contacto y filiación de un paciente existente sin alterar su identificador único ni generar duplicados de DNI.

#### Scenario: Actualización exitosa
- **GIVEN** un paciente existente
- **WHEN** el odontólogo envía nuevos datos válidos de teléfono, email y dirección
- **THEN** el sistema actualiza la ficha del paciente y responde con los datos actualizados

### Requirement: Baja lógica sin eliminación física
El sistema SHALL ejecutar la baja de un paciente mediante un cambio de estado a inactivo (baja lógica). El sistema MUST NOT eliminar físicamente la fila del paciente de la base de datos.

#### Scenario: Baja lógica exitosa
- **GIVEN** un paciente activo
- **WHEN** el odontólogo solicita dar de baja al paciente
- **THEN** el sistema marca el paciente como inactivo y mantiene el registro persistido en la base de datos

### Requirement: Resolución de paciente por DNI sin sobreescritura
El sistema SHALL proveer un servicio para obtener o crear un paciente a partir de su DNI. Si el paciente ya existe, el sistema MUST asociar la operación sin sobreescribir los datos ya cargados por el odontólogo.

#### Scenario: DNI no existente crea paciente
- **GIVEN** un DNI que no existe en el consultorio
- **WHEN** el servicio de resolución es invocado con datos del paciente
- **THEN** crea y persiste el nuevo paciente con origen correspondiente

#### Scenario: DNI existente no es sobreescrito
- **GIVEN** un paciente existente con datos registrados previamente por el odontólogo
- **WHEN** el servicio de resolución es invocado con el mismo DNI y datos de contacto distintos
- **THEN** devuelve el paciente existente preservando sus datos originales sin sobreescribirlos

### Requirement: Email de contacto del paciente para notificaciones
El módulo de pacientes SHALL ofrecer a otros módulos, por su api, la consulta del email de contacto de un paciente por identificador, sin exponer ningún otro dato personal. Devuelve vacío si el paciente no existe o no tiene email. Es una lectura del sistema para notificar al paciente y MUST NOT registrar `PATIENT_READ` ni loguear el email.

#### Scenario: Paciente con email
- **GIVEN** un paciente con email `ana@ejemplo.com`
- **WHEN** otro módulo consulta su email de contacto
- **THEN** obtiene `ana@ejemplo.com` y ningún otro dato del paciente

#### Scenario: Paciente sin email o inexistente
- **WHEN** otro módulo consulta el email de un paciente sin email registrado, o de un identificador inexistente
- **THEN** la consulta devuelve vacío sin lanzar error

#### Scenario: Paciente dado de baja
- **GIVEN** un paciente inactivo con email
- **WHEN** otro módulo consulta su email de contacto
- **THEN** obtiene el email, porque las notificaciones de sus turnos existentes siguen correspondiendo

#### Scenario: Sin auditoría ni log
- **WHEN** otro módulo consulta el email de contacto de un paciente
- **THEN** no se agrega ninguna fila a `audit_log` y el log no contiene el email
