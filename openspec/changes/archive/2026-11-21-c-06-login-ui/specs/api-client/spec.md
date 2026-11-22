# Spec Delta: api-client

## MODIFIED Requirements

### Requirement: Sesión vencida
Una respuesta 401 a una llamada que no sea la consulta inicial de sesión ni un intento de credenciales (`POST /api/v1/auth/login` y `POST /api/v1/auth/change-password`, cuya contraseña actual incorrecta responde 401 `INVALID_CREDENTIALS`) SHALL producir un error `unauthorized` y notificar que la sesión venció; un 401 en un intento de credenciales SHALL devolverse como error con su código, sin notificar vencimiento.

#### Scenario: 401 en una llamada privada
- **GIVEN** una sesión autenticada
- **WHEN** una llamada a `GET /patients` recibe 401
- **THEN** el resultado es un error `unauthorized` y se emite el aviso de sesión vencida una sola vez

#### Scenario: 401 en un intento de ingreso
- **GIVEN** una sesión anónima
- **WHEN** `POST /auth/login` recibe 401 `application/problem+json` con código `INVALID_CREDENTIALS`
- **THEN** el resultado es un error con código `INVALID_CREDENTIALS` y no se emite el aviso de sesión vencida

#### Scenario: 401 en el cambio de contraseña
- **GIVEN** una sesión autenticada
- **WHEN** `POST /auth/change-password` recibe 401 `application/problem+json` con código `INVALID_CREDENTIALS`
- **THEN** el resultado es un error con ese código y no se emite el aviso de sesión vencida; si el código es `UNAUTHENTICATED`, el resultado conserva ese código para que quien llama decida expirar la sesión

## ADDED Requirements

### Requirement: Espera sugerida del servidor
Un error de la API con cabecera `Retry-After` en segundos enteros no negativos SHALL exponer ese valor como `retryAfterSeconds`; cualquier otra forma de la cabecera (fecha HTTP, texto, negativo) MUST ignorarse.

#### Scenario: Retry-After en segundos
- **WHEN** una llamada recibe 429 `application/problem+json` con `Retry-After: 45`
- **THEN** el error es un `problem` con `status = 429`, `code = RATE_LIMITED` y `retryAfterSeconds = 45`

#### Scenario: Retry-After con otro formato
- **WHEN** una llamada recibe 429 con `Retry-After: Wed, 21 Nov 2026 10:00:00 GMT`
- **THEN** el error no incluye `retryAfterSeconds`
