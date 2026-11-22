# Spec Delta

## ADDED Requirements

### Requirement: Esquema de horarios de atención creado en V5
La migración `V5` SHALL crear la tabla `working_hours` con clave primaria UUID, `practice_id` foráneo a `practice`, `weekday` entre 1 y 7, y horas de inicio y fin con restricción `end_time > start_time`.

#### Scenario: Base migrada hasta V5 con tabla de horarios
- **GIVEN** una base de datos con migraciones previas aplicadas
- **WHEN** arranca la aplicación y ejecuta Flyway
- **THEN** la migración `V5` queda registrada exitosamente y la tabla `working_hours` existe con sus restricciones

#### Scenario: Privilegios de gestión de horarios
- **GIVEN** la base migrada hasta `V5`
- **WHEN** el rol de aplicación ejecuta SELECT, INSERT, UPDATE o DELETE sobre `working_hours`
- **THEN** PostgreSQL permite cada operación sin errores de privilegios

#### Scenario: Restricción de horario en base de datos
- **GIVEN** la base migrada hasta `V5`
- **WHEN** se intenta insertar una fila en `working_hours` con `end_time <= start_time` o con `weekday` fuera del rango 1 a 7
- **THEN** PostgreSQL rechaza la sentencia por violación de restricción check

### Requirement: Columnas de parámetros de reserva en practice creadas en V5
La migración `V5` SHALL agregar a la tabla `practice` las columnas `slot_granularity_min`, `min_notice_min`, `max_advance_days` y `reminder_hours_before`, con valores por defecto no nulos y restricciones de validación de rango.

#### Scenario: Columnas de reserva disponibles en practice
- **GIVEN** la base migrada hasta `V5`
- **WHEN** se consulta la tabla `practice`
- **THEN** contiene las columnas de granularidad, anticipación mínima, horizonte máximo y recordatorio con sus defaults y restricciones check
