# Spec Delta

## ADDED Requirements

### Requirement: Esquema de pacientes creado en V7
La migración `V7` SHALL crear la tabla `patient` con clave primaria UUID, `practice_id` foráneo a `practice`, `first_name`, `last_name`, `dni`, `phone`, `email`, `source`, marca de baja lógica `active` booleana no nula por defecto verdadera, y restricción de unicidad sobre `(practice_id, dni)`.

#### Scenario: Base migrada hasta V7
- **GIVEN** una base de datos con migraciones previas aplicadas
- **WHEN** arranca la aplicación y ejecuta Flyway
- **THEN** la migración `V7` queda registrada exitosamente y la tabla `patient` existe con sus restricciones

#### Scenario: Restricción de unicidad por DNI en consultorio
- **GIVEN** la base migrada hasta `V7` con un paciente registrado
- **WHEN** se intenta insertar otro paciente con el mismo `dni` en el mismo `practice_id`
- **THEN** PostgreSQL rechaza la inserción por violación de la restricción de unicidad

#### Scenario: Privilegios sin borrado físico en patient
- **GIVEN** la base migrada hasta `V7` con una fila en `patient`
- **WHEN** el rol de aplicación intenta ejecutar DELETE sobre `patient`
- **THEN** PostgreSQL rechaza la sentencia por falta de privilegios
