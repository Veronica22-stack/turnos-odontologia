# Spec Delta

## ADDED Requirements

### Requirement: Errores de autenticación sin enumeración
Los errores producidos por el login SHALL usar el mismo formato `application/problem+json` con `status = 401`, `code = INVALID_CREDENTIALS` y un título genérico, tanto para credenciales incorrectas como para cuenta bloqueada o email inexistente. Ningún error de autenticación SHALL incluir el email enviado, el nombre de la cuenta, el motivo exacto del fallo ni valores de comparación interna.

#### Scenario: Credenciales inválidas
- **WHEN** falla un login por contraseña incorrecta
- **THEN** la respuesta es `401` con `code = INVALID_CREDENTIALS`, sin el email ni la contraseña enviados

#### Scenario: Cuenta bloqueada
- **WHEN** falla un login porque la cuenta está bloqueada temporalmente
- **THEN** la respuesta es idéntica a la de credenciales inválidas, sin revelar el estado de la cuenta

#### Scenario: Contraseña actual incorrecta en cambio
- **GIVEN** una sesión autenticada que cambia su contraseña
- **WHEN** la contraseña actual no coincide
- **THEN** la respuesta es `401` con un código genérico que no revela si se rechazó la contraseña actual o la nueva

### Requirement: Errores de sesión, autorización y CSRF en formato único
Las respuestas `401` por sesión ausente o vencida, `403` por rol insuficiente, cambio de contraseña pendiente o token CSRF inválido SHALL usar `application/problem+json` con `status`, `title` genérico y un `code` estable: `UNAUTHENTICATED`, `FORBIDDEN`, `PASSWORD_CHANGE_REQUIRED` o `CSRF_REQUIRED`, según corresponda. El cuerpo MUST NOT contener datos de la cuenta, de la sesión ni trazas del framework.

#### Scenario: Sesión ausente
- **WHEN** se solicita una ruta protegida sin cookie de sesión
- **THEN** la respuesta es `401` con `code = UNAUTHENTICATED` y un título genérico

#### Scenario: Cambio de contraseña obligatorio
- **GIVEN** una sesión con cambio de contraseña pendiente
- **WHEN** se solicita una ruta privada no permitida en ese estado
- **THEN** la respuesta es `403` con `code = PASSWORD_CHANGE_REQUIRED`

#### Scenario: Token CSRF inválida
- **GIVEN** una sesión autenticada
- **WHEN** se envía una solicitud que cambia estado sin token CSRF válida
- **THEN** la respuesta es `403` con `code = CSRF_REQUIRED`, sin incluir la token esperada ni datos de la sesión

### Requirement: Error de límite de tasa
Cuando se exceda un límite de tasa, el sistema SHALL responder `429` con `application/problem+json`, `code = RATE_LIMITED` y una cabecera `Retry-After` con el número de segundos sugerido. La respuesta MUST NOT incluir la dirección IP, el email normalizado ni ningún otro valor usado como clave del límite.

#### Scenario: Límite de login excedido
- **GIVEN** una combinación IP+email que superó el límite de intentos por minuto
- **WHEN** se reenvía el login
- **THEN** la respuesta es `429` con `Retry-After`, `code = RATE_LIMITED` y un cuerpo sin la IP ni el email
