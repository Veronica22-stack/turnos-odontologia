# Spec Delta

## ADDED Requirements

### Requirement: Tramos semanales como puerto de lectura para el motor
El sistema SHALL ofrecer un contrato de solo lectura, consultable desde `reservas`, que devuelva los tramos de atención vigentes del consultorio como día de la semana (1 = lunes … 7 = domingo) y horas locales de inicio y fin. El contrato SHALL devolver los tramos ordenados por día y hora, SHALL permitir varios tramos por día y MUST NOT modificar la configuración. Los tramos contiguos del mismo día SHALL poder unirse en el cálculo de disponibilidad sin alterar los datos devueltos por el contrato.

#### Scenario: Lectura de varios tramos por día
- **GIVEN** tramos de lunes 09:00–13:00 y 14:00–18:00
- **WHEN** el motor consulta los horarios del lunes
- **THEN** recibe ambos tramos ordenados por hora de inicio

#### Scenario: Sin tramos configurados
- **GIVEN** un consultorio sin horarios de atención
- **WHEN** el motor consulta los tramos de un día
- **THEN** recibe una lista vacía y la disponibilidad de ese día es vacía sin error

#### Scenario: Tramos contiguos
- **GIVEN** tramos de 09:00–12:00 y 12:00–15:00 el mismo día
- **WHEN** se calcula la disponibilidad con una prestación de 30 minutos
- **THEN** se ofrecen slots que cruzan las 12:00 como si fuera un único intervalo continuo
