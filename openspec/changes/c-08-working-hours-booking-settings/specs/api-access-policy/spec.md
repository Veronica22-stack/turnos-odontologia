# Spec Delta

## ADDED Requirements

### Requirement: Protección de endpoints de horarios y configuración de consultorio
Los endpoints `/api/v1/working-hours` y `/api/v1/practice/settings` SHALL exigir una sesión autenticada con rol `DENTIST`. Toda solicitud no autenticada MUST ser rechazada con 401, y las operaciones de mutación (PUT) MUST requerir la cabecera y cookie de validación CSRF (`X-XSRF-TOKEN`).

#### Scenario: Petición anónima a configuración o de horarios
- **WHEN** un cliente no autenticado intenta consultar o modificar `/api/v1/working-hours` o `/api/v1/practice/settings`
- **THEN** el sistema responde 401 con formato ProblemDetail

#### Scenario: Petición autenticada sin token CSRF en modificación
- **GIVEN** una sesión autenticada de odontólogo
- **WHEN** envía un `PUT /api/v1/working-hours` o `PUT /api/v1/practice/settings` sin la cabecera `X-XSRF-TOKEN` correspondiente
- **THEN** el sistema rechaza la solicitud con 403 CSRF_REQUIRED
