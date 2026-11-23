# working-hours Specification

## Purpose
Permite al odontólogo definir y consultar los días y franjas horarias de atención semanal en las que el consultorio presta servicios, impidiendo tramos solapados o inconsistentes.

## Requirements

### Requirement: Consulta de horarios de atención
El sistema SHALL permitir al odontólogo autenticado consultar todos los tramos de atención configurados para el consultorio, ordenados cronológicamente por día de la semana (1 para Lunes hasta 7 para Domingo) y por hora de inicio.

#### Scenario: Consulta de horarios existentes
- **GIVEN** un consultorio con tramos de atención configurados de lunes a viernes
- **WHEN** el odontólogo autenticado solicita los horarios de atención
- **THEN** el sistema retorna la lista de tramos con día de la semana, hora de inicio y hora de fin ordenados adecuadamente

#### Scenario: Consultorio sin tramos configurados
- **GIVEN** un consultorio que aún no tiene tramos de atención registrados
- **WHEN** el odontólogo autenticado solicita los horarios de atención
- **THEN** el sistema retorna una lista vacía con código 200

### Requirement: Configuración y reemplazo de tramos de atención
El sistema SHALL permitir al odontólogo autenticado actualizar de forma integral el conjunto de tramos semanales de atención del consultorio, reemplazando la configuración anterior en una única operación atómica.

#### Scenario: Actualización válida de tramos semanales
- **GIVEN** una solicitud con tramos válidos donde cada tramo tiene hora de inicio anterior a la de fin y no existen solapamientos en un mismo día
- **WHEN** el odontólogo envía la actualización de horarios
- **THEN** el sistema persiste los nuevos tramos, elimina los anteriores y responde exitosamente con los tramos actualizados

#### Scenario: Tramo con hora de fin no posterior a la de inicio
- **GIVEN** una solicitud donde un tramo tiene hora de inicio igual o posterior a la hora de fin
- **WHEN** el odontólogo envía la actualización
- **THEN** el sistema rechaza la solicitud con código 400 y formato ProblemDetail indicando el error de validación

#### Scenario: Solapamiento de tramos dentro del mismo día
- **GIVEN** una solicitud que incluye dos tramos para el mismo día de la semana cuyos intervalos de horario se superponen
- **WHEN** el odontólogo envía la actualización
- **THEN** el sistema rechaza la solicitud con código 400 y formato ProblemDetail indicando que los tramos se solapan

#### Scenario: Múltiples tramos válidos en un mismo día
- **GIVEN** una solicitud que define dos tramos separados para el mismo día (por ejemplo 09:00 a 13:00 y 14:00 a 18:00)
- **WHEN** el odontólogo envía la actualización
- **THEN** el sistema acepta ambos tramos y los persiste correctamente

### Requirement: Tramos semanales como puerto de lectura para el motor
El sistema SHALL ofrecer un contrato de solo lectura, consultable desde `reservas`, que devuelva los tramos de atención vigentes del consultorio como día de la semana (1 = lunes … 7 = domingo) y horas locales de inicio y fin. El contrato SHALL devolver los tramos ordenados por día y hora, SHALL permitir varios tramos por día y MUST NOT modificar la configuración. Los tramos contiguos del mismo día SHALL poder unirse en el cálculo de disponibilidad sin alterar los datos devueltos por el contrato.

#### Scenario: Lectura de varios tramos por día
- **GIVEN** tramos de lunes 09:00–13:00 y 14:00–18:00
- **WHEN** el motor consulta los horarios del lunes
- **THEN** recibe ambos tramos ordenados por hora de inicio

#### Scenario: Sin tramos configurados
- **GIVEN** un consultorio sin horarios de atención
- **WHEN** el motor consulta los tramos de un día
- **THEN** recibe una lista vacía y la disponibilidad de ese día es vacía sin error

#### Scenario: Tramos contiguos
- **GIVEN** tramos de 09:00–12:00 y 12:00–15:00 el mismo día
- **WHEN** se calcula la disponibilidad con una prestación de 30 minutos
- **THEN** se ofrecen slots que cruzan las 12:00 como si fuera un único intervalo continuo
