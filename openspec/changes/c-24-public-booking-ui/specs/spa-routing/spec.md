# Spec Delta: spa-routing

## MODIFIED Requirements

### Requirement: Token de gestión fuera de títulos y registros
El parámetro `token` de `/turno/{token}` MUST NOT aparecer en el título del documento, en el parámetro `next` ni en mensajes de consola o de error. Las rutas `/reservar` y `/reservar/confirmado` MUST NOT llevar en la URL, el título del documento ni la consola datos personales del paciente (nombre, DNI, teléfono, email) ni el identificador de la reserva.

#### Scenario: Título de gestión de turno
- **WHEN** se navega a `/turno/AbC-123_x`
- **THEN** el título del documento no contiene `AbC-123_x` y no se escribe nada en la consola que lo contenga

#### Scenario: Confirmación de reserva sin datos en la URL
- **GIVEN** una reserva confirmada con DNI `30456789` e identificador de reserva `7b1e…`
- **WHEN** se muestra `/reservar/confirmado`
- **THEN** la URL es exactamente `/reservar/confirmado`, el título es "Reserva confirmada" con la marca y ni la URL, ni el título, ni la consola contienen el DNI, el email ni el identificador
