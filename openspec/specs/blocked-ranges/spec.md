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
El sistema SHALL ofrecer un contrato para obtener los turnos `CONFIRMED` de un consultorio cuyo rango intersecta un intervalo semiabierto dado, devolviendo solo identificador de turno e instantes de inicio y fin, ordenados por inicio, sin datos personales ni clínicos y sin modificar los turnos.

#### Scenario: Con proveedor de turnos
- **GIVEN** un turno confirmado de 10:00 a 10:30 dentro de un rango de 09:00 a 12:00 y el proveedor de turnos de C-11 registrado
- **WHEN** se consultan los turnos afectados por ese rango
- **THEN** se devuelve el identificador y los instantes del turno, sin modificar su estado

#### Scenario: Sin proveedor de turnos
- **GIVEN** una composición de la aplicación sin proveedor de turnos registrado
- **WHEN** se consultan los turnos afectados por un bloqueo
- **THEN** se obtiene una lista vacía y la operación de bloqueo continúa

#### Scenario: Sin turnos en el rango
- **GIVEN** ningún turno confirmado que intersecte el rango consultado
- **WHEN** se consultan los turnos afectados
- **THEN** se obtiene una lista vacía y la operación de bloqueo continúa

#### Scenario: Turnos excluidos
- **GIVEN** un rango de 09:00 a 12:00, un turno cancelado y uno completado dentro, un turno confirmado de 12:00 a 12:30 y un turno confirmado de otro consultorio dentro
- **WHEN** se consultan los turnos afectados del consultorio
- **THEN** no se devuelve ninguno de ellos

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
