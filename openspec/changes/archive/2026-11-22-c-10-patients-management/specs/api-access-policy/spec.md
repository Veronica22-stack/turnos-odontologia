# Spec Delta

## ADDED Requirements

### Requirement: Protección de endpoints de gestión de pacientes
Todos los endpoints bajo `/api/v1/patients/**` SHALL exigir una sesión autenticada con rol `DENTIST`. Toda solicitud no autenticada MUST ser rechazada con código 401, y las operaciones de mutación (POST, PUT, DELETE) MUST requerir la cabecera y cookie de validación CSRF (`X-XSRF-TOKEN`).

#### Scenario: Petición no autenticada a endpoints de pacientes
- **WHEN** un cliente no autenticado envía cualquier solicitud a `/api/v1/patients` o `/api/v1/patients/{id}`
- **THEN** el sistema responde con 401 y formato ProblemDetail

#### Scenario: Mutación de paciente sin CSRF
- **GIVEN** una sesión autenticada con rol `DENTIST`
- **WHEN** envía un POST, PUT o DELETE a `/api/v1/patients` sin la cabecera `X-XSRF-TOKEN`
- **THEN** el sistema rechaza la operación con código 403 CSRF_REQUIRED
