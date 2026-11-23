# Spec Delta

## ADDED Requirements

### Requirement: Códigos de error del dominio de disponibilidad
Los errores de las consultas de disponibilidad SHALL usar `application/problem+json` con título genérico y los códigos estables: `400 VALIDATION_FAILED` para parámetros ausentes o mal formados, `404 SERVICE_NOT_FOUND` para un `serviceId` ajeno al consultorio, `422 SERVICE_INACTIVE` para una prestación dada de baja, `429 RATE_LIMITED` para el límite de tasa con cabecera `Retry-After`, `401 UNAUTHENTICATED` y `403 FORBIDDEN` para la ruta interna. El cuerpo MUST NOT incluir valores enviados, dirección de origen, nombres de clase ni datos de la prestación.

#### Scenario: Parámetros inválidos
- **WHEN** se solicita disponibilidad con fecha mal formada o `serviceId` ausente
- **THEN** se responde `400` con `code = VALIDATION_FAILED`, sin devolver el valor rechazado

#### Scenario: Prestación no reservable
- **GIVEN** un identificador inexistente y luego uno de una prestación inactiva
- **WHEN** se consulta la disponibilidad para cada uno
- **THEN** se responde `404 SERVICE_NOT_FOUND` y `422 SERVICE_INACTIVE` respectivamente, sin exponer la prestación

#### Scenario: Límite de tasa sin datos de la clave
- **GIVEN** un origen que superó el límite
- **WHEN** consulta la disponibilidad pública
- **THEN** se responde `429` con `code = RATE_LIMITED`, `Retry-After` y un cuerpo sin la IP ni ningún identificador del cliente
