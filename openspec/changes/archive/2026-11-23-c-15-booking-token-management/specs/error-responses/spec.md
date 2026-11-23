# Spec Delta

## ADDED Requirements

### Requirement: Token management error contract
Los errores de gestión por token SHALL usar `application/problem+json`. Todo token inválido, expirado o revocado SHALL devolver 404 con un único `code` estable y el mismo cuerpo. Los conflictos de slot SHALL devolver 409 `SLOT_TAKEN`, el exceso de intentos 429 `RATE_LIMITED` con `Retry-After` y la modificación de un turno pasado 422 con un código de regla de negocio. Ningún cuerpo SHALL incluir el token, IP, DNI, email ni teléfono.

#### Scenario: Error indistinto para token inválido y revocado
- **GIVEN** un token inexistente y otro revocado
- **WHEN** se consulta cada uno
- **THEN** ambas respuestas tienen el mismo `status` 404, el mismo `code` y el mismo cuerpo

#### Scenario: Conflicto al reprogramar
- **GIVEN** un token válido y un nuevo slot ocupado
- **WHEN** se reprograma
- **THEN** la respuesta es 409 con `code = SLOT_TAKEN` y sin el mensaje de PostgreSQL

#### Scenario: Enumeración excedida
- **GIVEN** un IP que superó el límite de intentos
- **WHEN** prueba otro token
- **THEN** la respuesta es 429 `RATE_LIMITED` con `Retry-After` y sin IP en el cuerpo
