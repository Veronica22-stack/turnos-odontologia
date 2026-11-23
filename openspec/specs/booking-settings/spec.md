# booking-settings Specification

## Purpose
Permite consultar y ajustar los parámetros que gobiernan la reserva de turnos, la granularidad temporal de los slots y los datos de contacto y zona horaria del consultorio.

## Requirements

### Requirement: Consulta de configuración y parámetros de reserva
El sistema SHALL permitir al odontólogo autenticado consultar los datos generales del consultorio y sus parámetros de reserva, incluyendo granularidad de slots, anticipación mínima, horizonte máximo y ventana de recordatorio.

#### Scenario: Consulta exitosa de parámetros
- **GIVEN** un consultorio con parámetros de reserva configurados
- **WHEN** el odontólogo autenticado solicita la configuración del consultorio
- **THEN** el sistema responde con 200 y los campos de identificación, nombre, zona horaria, datos de contacto y parámetros de reserva

### Requirement: Actualización de configuración y parámetros de reserva
El sistema SHALL permitir al odontólogo autenticado modificar el nombre, datos de contacto y parámetros operativos de reserva del consultorio, validando que todos los valores respeten los límites y formatos permitidos.

#### Scenario: Actualización válida de configuración
- **GIVEN** nuevos valores válidos para nombre, contacto, granularidad de 15 minutos, anticipación mínima de 120 minutos, horizonte de 60 días y recordatorio de 24 horas
- **WHEN** el odontólogo envía la actualización de configuración
- **THEN** el sistema actualiza la fila del consultorio y retorna 200 con los valores actualizados

#### Scenario: Granularidad no permitida
- **GIVEN** una solicitud con granularidad de slot de 25 minutos (no perteneciente al conjunto permitido de 10, 15, 20, 30 o 60 minutos)
- **WHEN** el odontólogo intenta guardar la configuración
- **THEN** el sistema rechaza la solicitud con código 400 y formato ProblemDetail

#### Scenario: Anticipación mínima negativa
- **GIVEN** una solicitud con anticipación mínima menor a cero
- **WHEN** el odontólogo intenta guardar la configuración
- **THEN** el sistema rechaza la solicitud con código 400 y formato ProblemDetail

#### Scenario: Horizonte máximo fuera de rango
- **GIVEN** una solicitud con horizonte máximo de días menor a 1 o mayor a 365
- **WHEN** el odontólogo intenta guardar la configuración
- **THEN** el sistema rechaza la solicitud con código 400 y formato ProblemDetail

#### Scenario: Ventana de recordatorio fuera de rango
- **GIVEN** una solicitud con horas de recordatorio menor a 1 o mayor a 168
- **WHEN** el odontólogo intenta guardar la configuración
- **THEN** el sistema rechaza la solicitud con código 400 y formato ProblemDetail

### Requirement: Parámetros de reserva como puerto de lectura para el motor
El sistema SHALL ofrecer un contrato de solo lectura, consultable desde `reservas`, que exponga únicamente `timezone`, `slotGranularityMin`, `minNoticeMin` y `maxAdvanceDays` del consultorio. El contrato MUST NOT exponer nombre, email, teléfono, dirección ni identificador del consultorio, y SHALL devolver los valores vigentes en cada consulta para que el motor no use constantes copiadas.

#### Scenario: Lectura de los cuatro parámetros
- **GIVEN** un consultorio con granularidad 15, anticipación 120, horizonte 60 y zona `America/Argentina/Buenos_Aires`
- **WHEN** el motor consulta los parámetros de reserva
- **THEN** recibe exactamente esos cuatro valores y ningún dato de contacto

#### Scenario: Parámetros actualizados
- **GIVEN** una actualización previa de la granularidad a 30 minutos
- **WHEN** se calcula la disponibilidad
- **THEN** los inicios se generan en pasos de 30 minutos sin reiniciar la aplicación

#### Scenario: Granularidad inválida en la configuración
- **GIVEN** un valor de granularidad no positivo
- **WHEN** se intenta calcular la disponibilidad
- **THEN** la solicitud se rechaza con un error de configuración y no genera slots arbitrarios
