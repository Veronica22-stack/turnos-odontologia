# Spec Delta

## ADDED Requirements

### Requirement: Public booking configuration
El sistema SHALL usar la variable de entorno existente `RATE_LIMIT_PUBLIC_PER_MIN` para limitar las reservas públicas y la variable existente `BOOKING_TOKEN_TTL_DAYS` para calcular la vigencia de los tokens de gestión. NO SHALL introducir variables nuevas ni credenciales en código o YAML versionado.

#### Scenario: Límite de reservas por minuto
- **GIVEN** `RATE_LIMIT_PUBLIC_PER_MIN = 3`
- **WHEN** un mismo IP envía cuatro reservas en un minuto
- **THEN** la cuarta devuelve 429 `RATE_LIMITED` con `Retry-After`

#### Scenario: Vigencia del token
- **GIVEN** `BOOKING_TOKEN_TTL_DAYS = 1` y un turno futuro
- **WHEN** se emite un token para ese turno
- **THEN** `expires_at` es el fin del turno más un día, de modo que el enlace siga siendo válido cuando se envíe el recordatorio (coherente con KB 08: vigencia posterior al turno)

#### Scenario: Configuración inválida
- **GIVEN** un valor no positivo para `BOOKING_TOKEN_TTL_DAYS`
- **WHEN** arranca la aplicación
- **THEN** el arranque falla nombrando la variable, sin mostrar valores sensibles
