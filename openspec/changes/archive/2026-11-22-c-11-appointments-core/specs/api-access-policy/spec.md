# Spec Delta

## ADDED Requirements

### Requirement: Protección de endpoints de turnos
Todos los endpoints bajo `/api/v1/appointments/**` SHALL exigir sesión autenticada con rol `DENTIST` y MUST NOT declararse públicos. Sin sesión SHALL responderse `401` con `code = UNAUTHENTICATED`; con otro rol, `403` con `code = FORBIDDEN`. Las mutaciones (`POST`) SHALL exigir token CSRF válido (`403` `CSRF_REQUIRED` si falta); las consultas (`GET`) MUST NOT exigirlo.

#### Scenario: Consulta anónima de turnos
- **WHEN** un cliente sin sesión solicita `GET /api/v1/appointments` o `GET /api/v1/appointments/{id}`
- **THEN** el sistema responde `401` y no revela si el turno existe

#### Scenario: Mutación anónima o con rol insuficiente
- **GIVEN** un cliente sin sesión y luego uno autenticado sin rol `DENTIST`
- **WHEN** envían `POST /api/v1/appointments` o una acción sobre un turno
- **THEN** el primero recibe `401` y el segundo `403`, sin crear ni modificar turnos

#### Scenario: Mutación sin token CSRF
- **GIVEN** una sesión autenticada de odontólogo
- **WHEN** envía `POST /api/v1/appointments`, `.../cancel`, `.../reschedule`, `.../complete` o `.../no-show` sin cabecera `X-XSRF-TOKEN` válida
- **THEN** el sistema responde `403` con `code = CSRF_REQUIRED` y el turno no cambia

#### Scenario: Consulta autenticada sin token CSRF
- **GIVEN** una sesión autenticada de odontólogo
- **WHEN** solicita `GET /api/v1/appointments?from=...&to=...` sin cabecera CSRF
- **THEN** el sistema responde `200`
