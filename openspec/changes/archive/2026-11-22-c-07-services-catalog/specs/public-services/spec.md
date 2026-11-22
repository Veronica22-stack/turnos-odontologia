# Spec Delta

## Purpose
Expone un catálogo público de solo lectura con las prestaciones que el consultorio decidió publicar, para que una persona sin cuenta pueda ver qué servicios se ofrecen y cuánto duran antes de reservar.

## ADDED Requirements

### Requirement: Catálogo público sin autenticación
El sistema SHALL exponer `GET /api/v1/public/services` sin requerir sesión ni credenciales, devolviendo únicamente las prestaciones activas y visibles públicamente del consultorio, ordenadas por nombre.

#### Scenario: Consulta anónima del catálogo
- **GIVEN** un consultorio con prestaciones activas y visibles
- **WHEN** un cliente sin sesión solicita el catálogo público
- **THEN** el sistema responde `200` con las prestaciones publicadas y sin exigir autenticación

#### Scenario: Prestaciones no publicadas excluidas
- **GIVEN** prestaciones activas no visibles y prestaciones inactivas visibles
- **WHEN** un cliente solicita el catálogo público
- **THEN** la respuesta incluye solo las prestaciones activas y visibles, y ninguna de las demás

#### Scenario: Catálogo vacío
- **GIVEN** un consultorio sin prestaciones activas y visibles
- **WHEN** un cliente solicita el catálogo público
- **THEN** el sistema responde `200` con una lista vacía

#### Scenario: Orden estable por nombre
- **GIVEN** varias prestaciones publicadas con nombres distintos
- **WHEN** un cliente solicita el catálogo público
- **THEN** las prestaciones se devuelven ordenadas por nombre de forma estable

### Requirement: Campos públicos mínimos
La respuesta del catálogo público SHALL incluir únicamente el identificador, el nombre, la descripción y la duración en minutos de cada prestación. El sistema MUST NOT exponer marcas internas, timestamps, identificadores del consultorio ni datos de configuración.

#### Scenario: Respuesta con solo campos públicos
- **GIVEN** una prestación publicada con datos internos asociados
- **WHEN** un cliente solicita el catálogo público
- **THEN** cada elemento incluye solo identificador, nombre, descripción y duración, sin marcas internas, timestamps ni datos del consultorio

#### Scenario: Descripción ausente
- **GIVEN** una prestación publicada sin descripción
- **WHEN** un cliente solicita el catálogo público
- **THEN** el elemento se devuelve igualmente con la descripción ausente y el resto de los campos públicos

### Requirement: Cache corto del catálogo
El sistema SHALL marcar la respuesta del catálogo público con una política de cache corta, de modo que un cliente o intermediario pueda reutilizarla por un lapso breve sin consultar la base en cada solicitud.

#### Scenario: Cabecera de cache en la respuesta
- **WHEN** un cliente solicita el catálogo público
- **THEN** la respuesta incluye una cabecera `Cache-Control` pública con un tiempo máximo de reutilización corto y acotado

### Requirement: Límite de tasa del catálogo público
El sistema SHALL limitar las solicitudes al catálogo público por dirección de origen, reutilizando la infraestructura de límite de tasa de la autenticación. Al excederse, SHALL responder `429` con `application/problem+json`, `code = RATE_LIMITED` y `Retry-After`.

#### Scenario: Solicitudes dentro del límite
- **GIVEN** un cliente que no superó el límite de solicitudes en la ventana vigente
- **WHEN** solicita el catálogo público
- **THEN** el sistema responde `200` con el catálogo

#### Scenario: Límite excedido
- **GIVEN** un cliente que superó el límite de solicitudes en la ventana vigente
- **WHEN** vuelve a solicitar el catálogo público
- **THEN** el sistema responde `429` con `code = RATE_LIMITED` y la cabecera `Retry-After`, sin incluir la dirección de origen en el cuerpo

### Requirement: Solo lectura sobre el catálogo público
El catálogo público SHALL ser de solo lectura: únicamente `GET` está soportado. Cualquier otro método MUST ser rechazado sin modificar prestaciones.

#### Scenario: Método no permitido
- **WHEN** un cliente envía una solicitud `POST`, `PUT` o `DELETE` a `/api/v1/public/services`
- **THEN** el sistema rechaza la solicitud sin crear, modificar ni dar de baja ninguna prestación
