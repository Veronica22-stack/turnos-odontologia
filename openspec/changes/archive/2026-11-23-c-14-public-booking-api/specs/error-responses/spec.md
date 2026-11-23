# Spec Delta

## ADDED Requirements

### Requirement: Public booking error codes
Los errores del endpoint de reserva pública SHALL usar `application/problem+json` con códigos estables: `SLOT_TAKEN` (409), `RATE_LIMITED` (429 con `Retry-After`), `VALIDATION_FAILED` (400 con `errors[]`), códigos de regla de negocio (422) y un código genérico para honeypot. El cuerpo NO SHALL incluir mensajes de la base de datos, IP, DNI, email ni teléfono.

#### Scenario: Slot ocupado
- **GIVEN** un slot tomado por otra reserva
- **WHEN** se envía la reserva
- **THEN** la respuesta es 409 con `code = SLOT_TAKEN` y `status = 409`, sin el mensaje de PostgreSQL

#### Scenario: Honeypot sin filtrar información
- **GIVEN** una solicitud con el honeypot completo
- **WHEN** se procesa
- **THEN** la respuesta es genérica y no permite distinguir si el turno o el paciente existían

#### Scenario: Error de validación
- **GIVEN** un email con formato inválido y la aceptación del aviso ausente
- **WHEN** se envía la reserva
- **THEN** la respuesta es 400 `VALIDATION_FAILED` con entradas en `errors[]` para ambos campos y sin repetir los valores enviados
