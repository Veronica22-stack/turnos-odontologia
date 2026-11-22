# Spec Delta

## MODIFIED Requirements

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
