# Spec Delta

## ADDED Requirements

### Requirement: Protección de los endpoints de bloqueos de agenda
Todos los endpoints bajo `/api/v1/schedule-blocks/**` SHALL exigir una sesión autenticada con rol `DENTIST` y MUST NOT declararse como públicos. Las solicitudes sin sesión SHALL rechazarse con `401` y `code = UNAUTHENTICATED`, y una sesión con rol distinto SHALL rechazarse con `403` y `code = FORBIDDEN`. Las operaciones de mutación (`POST`, `PUT`, `DELETE`) SHALL exigir la cabecera y cookie de validación CSRF (`X-XSRF-TOKEN`); su ausencia o invalidez SHALL responderse con `403` y `code = CSRF_REQUIRED`. Las consultas (`GET`) MUST NOT exigir token CSRF.

#### Scenario: Consulta anónima de bloqueos
- **WHEN** un cliente sin sesión solicita `GET /api/v1/schedule-blocks` o `GET /api/v1/schedule-blocks/{id}`
- **THEN** el sistema responde `401` con `application/problem+json` y no revela si el bloqueo existe

#### Scenario: Mutación anónima o con rol insuficiente
- **GIVEN** un cliente sin sesión y luego uno autenticado sin rol `DENTIST`
- **WHEN** envían `POST`, `PUT` o `DELETE` sobre `/api/v1/schedule-blocks`
- **THEN** el primero recibe `401` y el segundo `403`, sin modificar ningún bloqueo

#### Scenario: Mutación autenticada sin token CSRF
- **GIVEN** una sesión autenticada de odontólogo
- **WHEN** envía `POST`, `PUT` o `DELETE` a `/api/v1/schedule-blocks` sin la cabecera `X-XSRF-TOKEN` válida
- **THEN** el sistema rechaza cada operación con `403` y `code = CSRF_REQUIRED`

#### Scenario: Consulta autenticada sin token CSRF
- **GIVEN** una sesión autenticada de odontólogo
- **WHEN** solicita `GET /api/v1/schedule-blocks` sin cabecera CSRF
- **THEN** el sistema responde `200` con el listado correspondiente
