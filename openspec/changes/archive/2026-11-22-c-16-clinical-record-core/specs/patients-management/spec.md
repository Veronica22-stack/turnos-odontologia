# Spec Delta

## ADDED Requirements

### Requirement: Consulta de existencia y estado de paciente para otros módulos
El módulo de pacientes SHALL ofrecer a otros módulos una consulta por identificador de paciente que devuelve solo si existe en la instalación y si está activo, sin datos personales y sin registrar `PATIENT_READ`.

#### Scenario: Paciente activo
- **GIVEN** un paciente activo de la instalación
- **WHEN** otro módulo consulta su estado
- **THEN** obtiene que existe y está activo, sin nombre, DNI, teléfono ni email

#### Scenario: Paciente dado de baja
- **GIVEN** un paciente dado de baja
- **WHEN** otro módulo consulta su estado
- **THEN** obtiene que existe y está inactivo

#### Scenario: Paciente inexistente
- **WHEN** otro módulo consulta un identificador que no corresponde a ningún paciente de la instalación
- **THEN** obtiene que no existe
