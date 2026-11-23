# Spec Delta

## ADDED Requirements

### Requirement: Consulta de turno para notificaciones
La agenda SHALL ofrecer a otros módulos, por su api, la consulta de un turno por identificador que devuelve su estado, paciente, inicio, fin y el nombre actual de la prestación, sin notas ni datos personales, y vacío si el turno no existe en el consultorio de la instalación. La consulta MUST NOT modificar el turno.

#### Scenario: Turno existente
- **GIVEN** un turno `CONFIRMED` de la prestación "Limpieza" con notas
- **WHEN** otro módulo consulta ese turno por identificador
- **THEN** obtiene el estado `CONFIRMED`, el paciente, los instantes de inicio y fin y el nombre "Limpieza", y no obtiene las notas

#### Scenario: Turno cancelado
- **GIVEN** un turno `CANCELLED`
- **WHEN** otro módulo lo consulta
- **THEN** obtiene el estado `CANCELLED` con sus instantes originales

#### Scenario: Turno inexistente
- **WHEN** otro módulo consulta un identificador que no corresponde a ningún turno del consultorio
- **THEN** la consulta devuelve vacío sin lanzar error
