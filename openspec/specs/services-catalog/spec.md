# services-catalog Specification

## Purpose
Permite al odontólogo mantener el catálogo de prestaciones de su consultorio con su duración real, editarlas, publicarlas u ocultarlas y darlas de baja de forma lógica, sin perder el historial de turnos ya agendados.

## Requirements

### Requirement: Alta de prestación
El sistema SHALL permitir al odontólogo autenticado crear una prestación con nombre obligatorio, descripción opcional, duración en minutos y marca de visibilidad pública. La prestación creada MUST quedar activa por defecto y la respuesta SHALL ser `201` con la prestación creada.

#### Scenario: Alta exitosa de prestación
- **GIVEN** un odontólogo autenticado y datos válidos de una prestación cuya duración es múltiplo de la granularidad vigente
- **WHEN** envía la creación de la prestación
- **THEN** el sistema persiste la prestación como activa, responde `201` con su identificador y los datos guardados

#### Scenario: Nombre vacío rechazado
- **GIVEN** un odontólogo autenticado
- **WHEN** envía la creación de una prestación sin nombre o con nombre en blanco
- **THEN** el sistema responde `400` con `application/problem+json` y `code = VALIDATION_FAILED`, sin crear la prestación

#### Scenario: Descripción omitida
- **GIVEN** un odontólogo autenticado y datos válidos de una prestación sin descripción
- **WHEN** envía la creación de la prestación
- **THEN** el sistema la crea con descripción ausente y responde `201`

### Requirement: Consulta y listado de prestaciones
El sistema SHALL permitir al odontólogo autenticado listar todas las prestaciones del consultorio, incluidas las dadas de baja, ordenadas por nombre, y consultar una prestación individual por su identificador.

#### Scenario: Listado con prestaciones activas e inactivas
- **GIVEN** un consultorio con prestaciones activas y al menos una dada de baja
- **WHEN** el odontólogo solicita el listado de prestaciones
- **THEN** el sistema responde `200` con todas las prestaciones, cada una indicando si está activa y si es visible públicamente

#### Scenario: Consulta individual de una prestación inactiva
- **GIVEN** una prestación dada de baja
- **WHEN** el odontólogo la consulta por su identificador
- **THEN** el sistema responde `200` con sus datos, para permitir decidir una reactivación

#### Scenario: Prestación inexistente
- **WHEN** el odontólogo consulta un identificador de prestación que no existe
- **THEN** el sistema responde `404` con `application/problem+json` y `code = SERVICE_NOT_FOUND`

### Requirement: Edición y reactivación de prestación
El sistema SHALL permitir al odontólogo autenticado modificar nombre, descripción, duración, visibilidad pública y estado activo de una prestación existente, de modo que pueda reactivar una prestación dada de baja.

#### Scenario: Edición exitosa
- **GIVEN** una prestación existente
- **WHEN** el odontólogo envía datos válidos con nuevo nombre y nueva duración
- **THEN** el sistema actualiza la prestación, registra el instante de modificación y responde `200` con los datos actualizados

#### Scenario: Reactivación de una prestación dada de baja
- **GIVEN** una prestación con baja lógica
- **WHEN** el odontólogo la actualiza indicando que está activa
- **THEN** el sistema la marca activa y vuelve a estar disponible para nuevas reservas

#### Scenario: Edición de prestación inexistente
- **WHEN** el odontólogo intenta actualizar un identificador de prestación que no existe
- **THEN** el sistema responde `404` con `code = SERVICE_NOT_FOUND` y no crea ninguna prestación

### Requirement: Validación de duración contra la granularidad
La duración de una prestación MUST ser mayor que cero y múltiplo de la granularidad de turnos vigente del consultorio. El sistema MUST rechazar una duración que no cumpla ambas condiciones y MUST NOT persistir la prestación.

#### Scenario: Duración múltiplo de la granularidad
- **GIVEN** una granularidad vigente de 15 minutos
- **WHEN** el odontólogo crea o edita una prestación con duración de 45 minutos
- **THEN** el sistema acepta y persiste la prestación

#### Scenario: Duración que no es múltiplo de la granularidad
- **GIVEN** una granularidad vigente de 15 minutos
- **WHEN** el odontólogo crea o edita una prestación con duración de 20 minutos
- **THEN** el sistema responde `422` con `code = DURATION_NOT_MULTIPLE_OF_GRANULARITY` y no persiste el cambio

#### Scenario: Duración cero o negativa
- **WHEN** el odontólogo envía una prestación con duración cero o negativa
- **THEN** el sistema responde `400` con `code = VALIDATION_FAILED` y no persiste la prestación

#### Scenario: Granularidad modificada después del alta
- **GIVEN** una prestación con duración válida para la granularidad anterior
- **WHEN** el odontólogo edita la prestación manteniendo una duración que ya no es múltiplo de la granularidad vigente
- **THEN** el sistema rechaza la edición con `422` y conserva la prestación sin cambios

### Requirement: Baja lógica sin eliminación física
El sistema SHALL dar de baja una prestación marcándola como inactiva. La prestación MUST seguir existiendo en la base de datos y el sistema MUST NOT eliminarla físicamente. La baja MUST ser idempotente sobre una prestación ya inactiva.

#### Scenario: Baja lógica exitosa
- **GIVEN** una prestación activa
- **WHEN** el odontólogo solicita darla de baja
- **THEN** el sistema la marca inactiva, responde `204` y la fila permanece en la base de datos

#### Scenario: Baja repetida
- **GIVEN** una prestación ya inactiva
- **WHEN** el odontólogo solicita nuevamente darla de baja
- **THEN** el sistema responde `204` sin error y la prestación sigue existiendo

#### Scenario: Baja de prestación inexistente
- **WHEN** el odontólogo solicita dar de baja un identificador de prestación que no existe
- **THEN** el sistema responde `404` con `code = SERVICE_NOT_FOUND`

#### Scenario: La visibilidad pública se conserva al dar de baja
- **GIVEN** una prestación activa y visible públicamente
- **WHEN** el odontólogo la da de baja
- **THEN** la prestación queda inactiva conservando su marca de visibilidad pública para una eventual reactivación

### Requirement: Desactivación sin impacto en turnos existentes
Dar de baja o despublicar una prestación MUST NOT modificar, cancelar ni eliminar turnos ya agendados. Una prestación inactiva MUST NOT aceptar nuevas reservas.

#### Scenario: Turnos existentes intactos
- **GIVEN** un turno agendado que referencia una prestación
- **WHEN** el odontólogo da de baja esa prestación
- **THEN** el turno conserva su estado, su prestación y la duración registrada al reservarse

#### Scenario: Prestación inactiva no admite nuevas reservas
- **GIVEN** una prestación inactiva
- **WHEN** se intenta usar esa prestación para una nueva reserva
- **THEN** el sistema no la ofrece ni la acepta como opción válida

### Requirement: Independencia de las marcas activa y visible
Las marcas `active` y `public_visible` SHALL ser independientes: una prestación activa pero no visible no se publica, y una prestación inactiva nunca se publica aunque su marca de visibilidad sea verdadera.

#### Scenario: Activa pero no visible
- **GIVEN** una prestación activa con visibilidad pública desactivada
- **WHEN** se consulta el catálogo público de prestaciones
- **THEN** la prestación no aparece, aunque sí aparece en el listado de gestión del odontólogo

#### Scenario: Inactiva marcada como visible
- **GIVEN** una prestación inactiva cuya marca de visibilidad pública es verdadera
- **WHEN** se consulta el catálogo público de prestaciones
- **THEN** la prestación no aparece en el catálogo público

### Requirement: Consulta de prestación por identificador para disponibilidad
El sistema SHALL ofrecer un contrato de solo lectura, consultable desde `reservas`, que permita resolver un `serviceId` del consultorio a su duración en minutos y a sus marcas `active` y `publicVisible`. El contrato MUST NOT exponer descripción, marcas de auditoría ni datos de otras prestaciones. Un identificador ajeno al consultorio SHALL distinguirse de una prestación dada de baja para que la disponibilidad pueda responder `404` o `422` según corresponda.

#### Scenario: Prestación vigente
- **GIVEN** una prestación activa y visible de 45 minutos
- **WHEN** el motor la consulta por identificador
- **THEN** obtiene duración 45, `active = true` y `publicVisible = true`

#### Scenario: Identificador ajeno
- **WHEN** se consulta un identificador que no pertenece al consultorio
- **THEN** el contrato indica ausencia y la disponibilidad responde `404 SERVICE_NOT_FOUND`

#### Scenario: Prestación dada de baja
- **GIVEN** una prestación inactiva con visibilidad pública verdadera
- **WHEN** el motor la consulta por identificador
- **THEN** obtiene `active = false` y la disponibilidad responde `422 SERVICE_INACTIVE`, sin ofrecer slots
