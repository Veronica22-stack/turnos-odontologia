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
