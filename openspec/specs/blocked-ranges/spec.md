# blocked-ranges Specification

## Purpose

Define el contrato público con el que la disponibilidad de reservas y la carga de turnos manuales consultan qué rangos del consultorio están bloqueados, sin depender de la persistencia ni de la implementación del módulo de agenda.

## Requirements

### Requirement: Consulta de rangos bloqueados que solapan un intervalo
El sistema SHALL ofrecer un contrato consultable por cualquier módulo que reciba un consultorio y un intervalo semiabierto `[desde, hasta)` y devuelva los bloqueos cuyo intervalo `[inicio, fin)` tiene intersección no vacía con él. Cada resultado SHALL contener únicamente identificador de bloqueo, inicio y fin como instantes UTC; MUST exponer entidades de persistencia, filtros de base de datos ni tipos de infraestructura. Un intervalo con fin igual o anterior al inicio SHALL rechazarse.

#### Scenario: Solapamiento parcial
- **GIVEN** un bloqueo de 09:00 a 11:00 en el consultorio
- **WHEN** se consulta el contrato para el intervalo de 10:30 a 12:00
- **THEN** se devuelve ese bloqueo con su identificador, inicio y fin

#### Scenario: Rango consultado contenido en el bloqueo
- **GIVEN** un bloqueo de 09:00 a 11:00 en el consultorio
- **WHEN** se consulta el contrato para el intervalo de 09:30 a 10:00
- **THEN** se devuelve ese bloqueo

#### Scenario: Rango adyacente
- **GIVEN** un bloqueo de 09:00 a 11:00 en el consultorio
- **WHEN** se consulta el contrato para el intervalo de 11:00 a 11:30
- **THEN** no se devuelve ningún bloqueo porque los intervalos semiabiertos solo se tocan en el límite

#### Scenario: Sin bloqueos o intervalo inválido
- **GIVEN** un consultorio sin bloqueos en el rango pedido
- **WHEN** se consulta el contrato con un intervalo válido y luego con fin igual al inicio
- **THEN** la primera consulta devuelve una lista vacía y la segunda se rechaza con un error de rango inválido

### Requirement: Contrato de turnos afectados por un bloqueo
El sistema SHALL declarar un contrato para obtener los turnos existentes comprendidos en un rango bloqueado, devolviendo solo identificadores de turno e instantes de inicio y fin, sin datos personales ni clínicos. Mientras el módulo de turnos no esté disponible, el contrato SHALL comportarse como sin resultados sin impedir la creación del bloqueo; cuando esté disponible SHALL devolver los turnos afectados sin modificarlos.

#### Scenario: Sin proveedor de turnos
- **GIVEN** una instalación sin persistencia de turnos
- **WHEN** se consultan los turnos afectados por un bloqueo
- **THEN** se obtiene una lista vacía y la operación de bloqueo continúa

#### Scenario: Con proveedor de turnos
- **GIVEN** un turno confirmado dentro del rango de un bloqueo y un proveedor de turnos disponible
- **WHEN** se consultan los turnos afectados por ese bloqueo
- **THEN** se devuelve el identificador y los instantes del turno, sin modificar su estado

### Requirement: Aplicación de la regla de no reservabilidad
Todo horario ofrecido por la disponibilidad o aceptado por la carga manual de turnos SHALL comprobar previamente los rangos bloqueados del consultorio. Un intervalo que intersecte un bloqueo MUST NOT ofrecerse ni aceptarse; un intervalo adyacente o posterior al fin del bloqueo SHALL considerarse libre respecto de ese bloqueo.

#### Scenario: Slot dentro de un bloqueo
- **GIVEN** un bloqueo de 09:00 a 11:00 y un slot de 10:30 a 11:00
- **WHEN** el motor de disponibilidad o la carga manual evalúan ese slot
- **THEN** el slot se descarta por estar dentro del bloqueo

#### Scenario: Slot inmediatamente posterior al bloqueo
- **GIVEN** un bloqueo de 09:00 a 11:00 y un slot de 11:00 a 11:30
- **WHEN** el motor de disponibilidad o la carga manual evalúan ese slot
- **THEN** el slot no se descarta por este bloqueo
