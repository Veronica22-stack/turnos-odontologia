# Spec Delta

## ADDED Requirements

### Requirement: Protección de endpoints de historia clínica
Todos los endpoints bajo `/api/v1/patients/{patientId}/clinical-record/**` SHALL exigir sesión autenticada con rol `DENTIST` y sin cambio de contraseña pendiente; las mutaciones (`POST`) MUST exigir el token CSRF. Ningún endpoint de historia clínica SHALL aceptar `PUT`, `PATCH` ni `DELETE`.

#### Scenario: Historia clínica sin sesión
- **WHEN** un cliente sin sesión envía cualquier solicitud a `/api/v1/patients/{patientId}/clinical-record/anamnesis`
- **THEN** recibe `401` en formato `application/problem+json`

#### Scenario: Alta clínica sin CSRF
- **GIVEN** una sesión autenticada con rol `DENTIST`
- **WHEN** envía `POST .../clinical-record/anamnesis` sin la cabecera `X-XSRF-TOKEN`
- **THEN** recibe `403` con `code = CSRF_REQUIRED` y no se crea ninguna entrada

#### Scenario: Cambio de contraseña pendiente
- **GIVEN** una sesión `DENTIST` con cambio de contraseña obligatorio
- **WHEN** consulta la historia clínica de un paciente
- **THEN** recibe `403` con `code = PASSWORD_CHANGE_REQUIRED` y no se registra `HC_READ`
