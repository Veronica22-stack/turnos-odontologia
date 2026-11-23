# Spec Delta

## ADDED Requirements

### Requirement: Token lookup rate limit configuration
El sistema SHALL usar la variable existente `RATE_LIMIT_PUBLIC_PER_MIN` para limitar los intentos de resolución de tokens por dirección remota, con una instancia dedicada y clave hasheada. NO SHALL introducir variables nuevas ni confiar en `X-Forwarded-For`.

#### Scenario: Límite de intentos
- **GIVEN** `RATE_LIMIT_PUBLIC_PER_MIN = 3`
- **WHEN** un mismo IP intenta cuatro tokens inválidos en un minuto
- **THEN** el cuarto intento devuelve 429 `RATE_LIMITED` con `Retry-After`

#### Scenario: Clave del limitador
- **GIVEN** una petición con cabecera `X-Forwarded-For` falsa
- **WHEN** se registra el intento
- **THEN** la clave del limitador es la dirección remota real, nunca la cabecera, y no se guarda en claro
