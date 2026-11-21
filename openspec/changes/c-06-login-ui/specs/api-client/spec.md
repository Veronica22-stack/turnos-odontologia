# Spec Delta: api-client

## MODIFIED Requirements

### Requirement: Sesión vencida
Una respuesta 401 a una llamada que no sea la consulta inicial de sesión ni un intento de credenciales (el envío de `POST /api/v1/auth/login`) SHALL producir un error `unauthorized` y notificar que la sesión venció; un 401 en un intento de credenciales SHALL devolverse como error con su código sin notificar vencimiento.

#### Scenario: 401 en una llamada privada
- **GIVEN** una sesión autenticada
- **WHEN** una llamada a `GET /patients` recibe 401
- **THEN** el resultado es un error `unauthorized` y se emite el aviso de sesión vencida una sola vez

#### Scenario: 401 en un intento de ingreso
- **GIVEN** una sesión anónima
- **WHEN** `POST /auth/login` recibe 401 `application/problem+json` con código `AUTH_INVALID_CREDENTIALS`
- **THEN** el resultado es un error con código `AUTH_INVALID_CREDENTIALS` y no se emite el aviso de sesión vencida
