# Spec Delta

## Purpose

Define el cálculo de horarios reservables del consultorio y las consultas interna y pública que lo exponen, de modo que la reserva online y la agenda del profesional ofrezcan solo slots realmente libres, dentro del horario de atención y de la ventana de reserva configurada.

## ADDED Requirements

### Requirement: Cálculo de slots libres
El sistema SHALL calcular los slots disponibles de una fecha local y una prestación restando de los tramos de atención los rangos bloqueados y los turnos `CONFIRMED` del consultorio. Un slot SHALL ofrecerse solo cuando la duración completa de la prestación quepa en un tramo libre continuo; la duración SHALL ser la de la prestación vigente (`startsAt + durationMin`), sin alterar la duración snapshot de turnos ya existentes (RN-AG-02/03/05, RN-AG-06).

#### Scenario: Día con varios tramos y un bloqueo parcial
- **GIVEN** tramos de 09:00–13:00 y 14:00–18:00, un bloqueo de 10:00–10:30 y una prestación de 30 minutos con granularidad de 15 minutos
- **WHEN** se consulta la disponibilidad de ese día
- **THEN** no se ofrecen inicios entre 09:30 y 10:30, se ofrecen 10:30 y 11:00, y los slots no cruzan el hueco de 13:00–14:00

#### Scenario: Turno confirmado que deja hueco insuficiente
- **GIVEN** un tramo de 09:00–11:00, un turno `CONFIRMED` de 09:00–10:00 y una prestación de 30 minutos
- **WHEN** se consulta la disponibilidad
- **THEN** se ofrecen 10:00 y 10:30 y no se ofrece 09:45

#### Scenario: Turno cancelado no ocupa el rango
- **GIVEN** un tramo libre salvo por un turno en estado `CANCELLED`
- **WHEN** se consulta la disponibilidad
- **THEN** el rango del turno cancelado se ofrece como libre

#### Scenario: Sin tramos de ese día
- **GIVEN** un consultorio sin horarios configurados para la fecha pedida
- **WHEN** se consulta la disponibilidad
- **THEN** la lista de slots es vacía y la respuesta no es un error

### Requirement: Intervalos semiabiertos, adyacencia y granularidad
Todos los cálculos SHALL usar intervalos semiabiertos `[inicio, fin)` en UTC. Un bloqueo o turno que termina exactamente cuando empieza otro tramo MUST NOT considerarse solapamiento. Los inicios ofrecidos SHALL avanzar en pasos de `slotGranularityMin` (default 15 minutos) y SOLO SHALL emitirse si el rango `[inicio, inicio + duración)` queda completamente contenido en un tramo libre.

#### Scenario: Turno y bloqueo adyacentes
- **GIVEN** un tramo de 09:00–12:00, un turno de 09:00–10:00 y un bloqueo de 10:00–10:30
- **WHEN** se consulta la disponibilidad con prestación de 30 minutos
- **THEN** no se ofrece el inicio de las 10:00 porque el bloqueo contiguo al turno cubre ese rango, y el primer inicio ofrecido es 10:30

#### Scenario: Granularidad con duración no múltiplo
- **GIVEN** granularidad de 20 minutos, un tramo de 09:00–10:10 y una prestación de 30 minutos
- **WHEN** se consulta la disponibilidad
- **THEN** se ofrecen 09:00, 09:20 y 09:40; el último termina exactamente a las 10:10 y no se ofrece 10:00 porque su duración excede el fin del tramo

#### Scenario: Borde final exacto
- **GIVEN** un tramo de 09:00–10:00 y una prestación de 30 minutos
- **WHEN** se consulta la disponibilidad con granularidad de 15 minutos
- **THEN** se ofrece 09:30, el slot termina exactamente a las 10:00 y no se ofrece 09:45

#### Scenario: Varias duraciones sobre el mismo tramo
- **GIVEN** un tramo de 09:00–10:00 libre y prestaciones de 15, 30 y 60 minutos
- **WHEN** se consulta la disponibilidad para cada prestación
- **THEN** las listas de inicios son respectivamente más amplias, intermedias y reducidas, y ninguna excede el fin del tramo

### Requirement: Día local, reloj y zona horaria
El cálculo SHALL obtener la hora actual de un único reloj UTC inyectable y SHALL interpretar la `date` pedida como día local del consultorio según su `timezone`. El sistema SHALL convertir ese día local al intervalo UTC semiabierto que lo cubre y SHALL convertir cada slot a instantes UTC para su comparación con bloqueos y turnos. Una hora local inexistente por cambio de horario MUST NOT ofrecerse; una hora ambigua SHALL resolverse con el desfase más temprano (RN-AG-07).

#### Scenario: Día local distinto del día UTC
- **GIVEN** `timezone = America/Argentina/Buenos_Aires` y la fecha local 2026-03-10
- **WHEN** se consulta la disponibilidad
- **THEN** el rango considerado comienza el 2026-03-10T03:00:00Z y termina el 2026-03-11T03:00:00Z, y un turno a las 2026-03-11T02:00:00Z se considera del 2026-03-10 local

#### Scenario: Hora local inexistente
- **GIVEN** una zona con salto de horario y un tramo que cae en una hora local inexistente
- **WHEN** se calcula la disponibilidad de ese día
- **THEN** ese tramo no genera slots y la respuesta sigue siendo válida

#### Scenario: Reloj fijo en tests
- **GIVEN** el reloj fijado en 2026-03-10T12:00:00Z
- **WHEN** se calcula la ventana de reserva
- **THEN** la anticipación mínima y el horizonte se miden desde ese instante y no desde la hora real del host

### Requirement: Ventana de reserva pública
La disponibilidad pública SHALL excluir inicios con menos de `minNoticeMin` minutos de anticipación e inicios posteriores al horizonte `maxAdvanceDays` contado desde el día local actual del consultorio. La disponibilidad interna del odontólogo MUST NOT aplicar la anticipación mínima ni el horizonte configurable, para no impedir turnos manuales urgentes (RN-RS-10; límites de los turnos manuales en `appointments`).

#### Scenario: Anticipación mínima
- **GIVEN** `minNoticeMin = 120` y un reloj fijo a las 09:00 locales
- **WHEN** se consulta la disponibilidad pública de ese día
- **THEN** no se ofrece ningún inicio anterior a las 11:00 locales

#### Scenario: Horizonte máximo
- **GIVEN** `maxAdvanceDays = 60`
- **WHEN** se consulta la disponibilidad pública para una fecha a 61 días del día local actual
- **THEN** la lista de slots es vacía y la respuesta no es un error

#### Scenario: Vista interna sin filtros de reserva pública
- **GIVEN** los mismos parámetros anteriores
- **WHEN** el odontólogo consulta la disponibilidad interna
- **THEN** se listan los slots libres aunque por anticipación y horizonte quedarían excluidos de la vista pública

### Requirement: Consulta interna de disponibilidad
El sistema SHALL exponer `GET /api/v1/availability` para el odontólogo autenticado con rol `DENTIST`, con `serviceId` y `date` obligatorios. Sin sesión SHALL responder `401` con `code = UNAUTHENTICATED`; con rol distinto, `403` con `code = FORBIDDEN`. La consulta (`GET`) MUST NOT exigir token CSRF.

#### Scenario: Consulta interna autenticada
- **GIVEN** una sesión `DENTIST` sin token CSRF
- **WHEN** se solicita `GET /api/v1/availability?serviceId=<uuid>&date=2026-03-10`
- **THEN** se responde `200` con los slots libres de ese día y la prestación

#### Scenario: Consulta interna anónima
- **WHEN** un cliente sin sesión solicita `GET /api/v1/availability?...`
- **THEN** se responde `401` con `code = UNAUTHENTICATED` y sin revelar si la prestación existe

### Requirement: Consulta pública de disponibilidad
El sistema SHALL exponer `GET /api/v1/public/availability?serviceId=&date=` sin sesión, con `serviceId` (UUID) y `date` (fecha local ISO `yyyy-MM-dd`) obligatorios. Parámetros ausentes, mal formados o una fecha inválida SHALL responderse `400` con `code = VALIDATION_FAILED`; un `serviceId` inexistente en el consultorio SHALL responderse `404` con `code = SERVICE_NOT_FOUND`; una prestación inactiva SHALL responderse `422` con `code = SERVICE_INACTIVE`. La ruta SHALL ser de solo lectura.

#### Scenario: Consulta pública válida
- **GIVEN** una prestación activa y visible públicamente
- **WHEN** un cliente sin sesión solicita `GET /api/v1/public/availability?serviceId=<uuid>&date=2026-03-10`
- **THEN** se responde `200` con la lista de slots en UTC, sin datos personales ni identificador del consultorio

#### Scenario: Parámetros inválidos
- **WHEN** se omite `serviceId`, se envía un UUID mal formado o una fecha como `2026-02-30`
- **THEN** se responde `400` con `code = VALIDATION_FAILED`, sin valores enviados ni nombres de clase

#### Scenario: Prestación inexistente o inactiva
- **GIVEN** un identificador que no pertenece al consultorio y luego una prestación dada de baja
- **WHEN** se consulta la disponibilidad para cada uno
- **THEN** se responde respectivamente `404 SERVICE_NOT_FOUND` y `422 SERVICE_INACTIVE`, sin exponer los datos de la prestación

#### Scenario: Solo GET
- **WHEN** se envía `POST`, `PUT` o `DELETE` a la ruta pública de disponibilidad
- **THEN** la solicitud se rechaza sin modificar turnos, bloqueos ni prestaciones

### Requirement: Sin prestaciones públicas activas
Cuando el consultorio NO tiene ninguna prestación activa y visible públicamente, la consulta pública SHALL responder `200` con `slots: []` y `status = NO_ACTIVE_SERVICES`, de modo que la SPA pueda mostrar «reservas no disponibles» sin interpretarlo como un error de parámetros. Cuando sí existen prestaciones activas, un `serviceId` inválido SHALL seguir las reglas de error de la consulta pública.

#### Scenario: Catálogo público vacío
- **GIVEN** un consultorio sin prestaciones activas y visibles
- **WHEN** un cliente consulta la disponibilidad pública
- **THEN** se responde `200`, `slots` es vacío y `status = NO_ACTIVE_SERVICES`

#### Scenario: Catálogo no vacío con identificador ajeno
- **GIVEN** al menos una prestación activa y visible
- **WHEN** se consulta con un `serviceId` que no existe
- **THEN** se responde `404 SERVICE_NOT_FOUND` y no se devuelve el flag `NO_ACTIVE_SERVICES`

### Requirement: Límite de tasa de la consulta pública
La consulta pública de disponibilidad SHALL limitar las solicitudes por dirección de origen reutilizando la infraestructura de límite de tasa de lecturas públicas. Al excederse SHALL responder `429` con `application/problem+json`, `code = RATE_LIMITED` y una cabecera `Retry-After` con segundos. El cuerpo MUST NOT incluir la dirección de origen ni datos de la clave del limitador.

#### Scenario: Límite excedido
- **GIVEN** un origen que agotó su ventana de solicitudes permitidas
- **WHEN** vuelve a consultar la disponibilidad pública
- **THEN** se responde `429` con `code = RATE_LIMITED` y `Retry-After >= 1`, sin la IP en el cuerpo

#### Scenario: Dentro del límite
- **GIVEN** un origen que no agotó su ventana
- **WHEN** consulta la disponibilidad pública
- **THEN** se responde `200` con los slots y sin cabecera de error

### Requirement: Contrato de disponibilidad sin datos personales
El sistema SHALL ofrecer un contrato público de consulta de disponibilidad reutilizable por la API de reserva, con tipos del JDK y records propios que no expongan entidades de persistencia, tipos de framework, datos del paciente, notas clínicas ni identificadores de turnos ocupados. La respuesta SHALL contener únicamente la prestación, la fecha, la zona horaria, los instantes de inicio y fin de cada slot y el estado de la consulta.

#### Scenario: Respuesta mínima
- **GIVEN** una consulta válida con tres slots libres
- **WHEN** se inspecciona el resultado devuelto por el contrato
- **THEN** cada slot tiene solo `startsAt` y `endsAt`, y no aparecen `patientId`, `appointmentId`, nombre, DNI, email, teléfono ni notas

#### Scenario: Consulta sin efectos
- **GIVEN** turnos y bloqueos existentes
- **WHEN** se consulta la disponibilidad por ambos endpoints
- **THEN** no se crea, modifica ni cancela ningún turno, bloqueo ni prestación
