# Spec Delta

## ADDED Requirements

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
