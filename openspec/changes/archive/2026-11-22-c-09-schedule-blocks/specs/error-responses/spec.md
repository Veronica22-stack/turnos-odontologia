# Spec Delta

## ADDED Requirements

### Requirement: Códigos de error del dominio de bloqueos
Los errores de los endpoints de bloqueos SHALL usar `application/problem+json` con `status`, `title` genérico y los códigos estables: `VALIDATION_FAILED` para campos inválidos, `INVALID_RANGE` para rangos de bloqueo o de filtro temporalmente inconsistentes, `SCHEDULE_BLOCK_NOT_FOUND` para identificadores inexistentes en el consultorio y `SCHEDULE_BLOCK_OVERLAP` para conflictos de solapamiento. El cuerpo MUST NOT incluir el motivo del bloqueo, instantes de otros bloqueos, valores enviados por el cliente ni nombres de restricciones de la base.

#### Scenario: Rango de bloqueo inválido
- **WHEN** se crea un bloqueo con fin anterior o igual al inicio
- **THEN** el sistema responde `400` con `code = INVALID_RANGE` y un título genérico, sin devolver los instantes recibidos

#### Scenario: Bloqueo no encontrado
- **WHEN** se consulta, edita o elimina un bloqueo inexistente en el consultorio de la sesión
- **THEN** el sistema responde `404` con `code = SCHEDULE_BLOCK_NOT_FOUND`

#### Scenario: Solapamiento detectado por la aplicación
- **WHEN** se intenta crear o editar un bloqueo que se solapa con otro del consultorio
- **THEN** el sistema responde `409` con `code = SCHEDULE_BLOCK_OVERLAP`, sin incluir los datos del bloqueo existente

### Requirement: Conflicto de exclusión de base traducido a error genérico
Cuando la restricción de exclusión de PostgreSQL rechace una operación concurrentemente, el sistema SHALL traducirla a `409` con `code = SCHEDULE_BLOCK_OVERLAP`, sin filtrar el mensaje de PostgreSQL, el nombre de la restricción, sentencias SQL, nombres de clase ni datos del bloqueo. El log del servidor SHALL registrar solo identificadores y el tipo del error, nunca el motivo ni datos personales.

#### Scenario: Violación de exclusión por carrera
- **GIVEN** dos solicitudes concurrentes que intentan bloquear rangos solapados y una de ellas falla en la base por la restricción de exclusión
- **WHEN** se procesa esa falla
- **THEN** la respuesta es `409` con `code = SCHEDULE_BLOCK_OVERLAP`, sin nombre de restricción ni texto de PostgreSQL, y el log contiene solo identificadores y el tipo de excepción

#### Scenario: Motivo con datos personales en una solicitud rechazada
- **GIVEN** una solicitud rechazada cuyo motivo incluye nombre, DNI, email o teléfono
- **WHEN** se inspeccionan la respuesta y el log
- **THEN** ninguno de esos valores aparece en la respuesta ni en el log
