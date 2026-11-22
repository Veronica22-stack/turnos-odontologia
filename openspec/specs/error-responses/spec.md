# error-responses Specification

## Purpose
Define un formato único de errores para la API (RFC 9457, `application/problem+json`) con mensajes genéricos y códigos estables, de modo que ningún error exponga datos personales, datos clínicos, valores enviados ni detalles internos (regla dura 4 y 6, Ley 25.326).

## Requirements

### Requirement: Formato único de error
Todo error que produzca un endpoint de la API SHALL responderse con tipo de contenido `application/problem+json` e incluir al menos `status`, `title` genérico según el código HTTP y un `code` estable legible por máquina.

#### Scenario: Recurso de dominio inexistente
- **WHEN** un endpoint señala que el recurso pedido no existe
- **THEN** la respuesta es 404 con `application/problem+json`, `status = 404`, un `title` genérico y `code` igual al código definido por el dominio

#### Scenario: Conflicto de dominio
- **WHEN** un endpoint señala un conflicto con el estado actual
- **THEN** la respuesta es 409 con `application/problem+json` y el `code` definido por el dominio

#### Scenario: Regla de negocio violada
- **WHEN** un endpoint señala la violación de una regla de negocio
- **THEN** la respuesta es 422 con `application/problem+json` y el `code` definido por el dominio

### Requirement: Errores sin datos sensibles
Las respuestas de error MUST NOT incluir stack traces, nombres de clases, sentencias SQL, nombres de restricciones de base, valores enviados por el cliente ni datos personales.

#### Scenario: Violación de unicidad en la base
- **WHEN** una operación falla porque la base rechaza un valor duplicado que contiene un email
- **THEN** la respuesta es 409 genérica y su cuerpo no contiene el email, el nombre de la restricción ni texto de PostgreSQL

#### Scenario: Cuerpo JSON mal formado
- **WHEN** un cliente envía un cuerpo JSON mal formado que contiene un DNI
- **THEN** la respuesta es 400 genérica y su cuerpo no contiene el DNI ni el mensaje del parser

### Requirement: Errores de validación por campo
Cuando la validación de una solicitud falla, la respuesta SHALL ser 400 e incluir la lista de campos inválidos con el nombre del campo y el código de la restricción incumplida, sin el valor rechazado.

#### Scenario: Campo obligatorio vacío
- **WHEN** un cliente envía una solicitud con un campo obligatorio vacío y otro campo con un valor demasiado largo
- **THEN** la respuesta es 400 y la lista de errores contiene ambos campos con sus códigos de restricción y ningún valor enviado

### Requirement: Error inesperado con identificador de correlación
Un error no previsto SHALL responderse con 500, un mensaje genérico y un identificador de error único que también se registra en el log del servidor junto con el tipo de excepción, sin el mensaje de la excepción.

#### Scenario: Excepción no controlada
- **WHEN** un endpoint lanza una excepción no prevista cuyo mensaje contiene un email
- **THEN** la respuesta es 500 con un `errorId`, el log contiene ese `errorId` y el tipo de excepción, y ni la respuesta ni el log contienen el email

### Requirement: Errores de protocolo HTTP en el mismo formato
Los errores de protocolo producidos por el framework (método no permitido, tipo de contenido no soportado, parámetro con tipo inválido, recurso no encontrado) SHALL responderse con el mismo formato y su código HTTP correspondiente.

#### Scenario: Método no permitido
- **WHEN** un cliente autorizado usa un método HTTP no soportado por un endpoint existente
- **THEN** la respuesta es 405 con `application/problem+json` y un `code` estable

#### Scenario: Parámetro con tipo inválido
- **WHEN** un cliente envía un parámetro numérico con texto
- **THEN** la respuesta es 400 con `application/problem+json` y no contiene el valor enviado

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
