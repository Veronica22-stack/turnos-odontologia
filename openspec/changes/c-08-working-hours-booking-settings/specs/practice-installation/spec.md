# Spec Delta

## ADDED Requirements

### Requirement: Parámetros operativos de reserva en el consultorio
La entidad del consultorio SHALL contener los parámetros operativos de reserva: granularidad de slots en minutos, anticipación mínima en minutos, horizonte máximo en días y anticipación de recordatorio en horas. Dichos parámetros MUST tener valores por defecto asignados al crearse o migrarse la base de datos y ser editables por el odontólogo autenticado.

#### Scenario: Defaults de reserva disponibles
- **GIVEN** la instalación del consultorio
- **WHEN** un módulo o componente lee los parámetros de reserva
- **THEN** obtiene granularidad de 15 minutos, anticipación mínima de 120 minutos, horizonte máximo de 30 días y recordatorio de 24 horas a menos que hayan sido modificados

#### Scenario: Persistencia de parámetros modificados
- **GIVEN** nuevos parámetros válidos asignados al consultorio
- **WHEN** se guardan los cambios en la base de datos
- **THEN** la fila del consultorio almacena los nuevos valores y las consultas posteriores los reflejan
