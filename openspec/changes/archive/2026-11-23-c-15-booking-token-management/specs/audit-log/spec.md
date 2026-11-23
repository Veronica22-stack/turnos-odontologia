# Spec Delta

## ADDED Requirements

### Requirement: Booking token use audit event
La acción `BOOKING_TOKEN_USE` SHALL ser emitida por el módulo `reservas` en cada consulta, cancelación o reprogramación con token, con `user_id` nulo y el turno como entidad referenciada. Los `details` SHALL usar únicamente claves del catálogo permitido (`outcome`, `reason_code`) y NO SHALL contener token, DNI, email, teléfono ni notas.

#### Scenario: Registro del uso del token
- **GIVEN** una consulta válida con token de gestión
- **WHEN** se resuelve el turno
- **THEN** se inserta una fila `BOOKING_TOKEN_USE` con `entity_id` igual al identificador del turno y `details` con `outcome` y `reason_code`

#### Scenario: Sin token en claro
- **GIVEN** un token con valor reconocible en claro
- **WHEN** se audita su uso
- **THEN** ni la fila ni los logs contienen ese valor, y la restricción anti-PII de la base acepta la inserción
