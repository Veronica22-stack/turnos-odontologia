# Spec Delta

## ADDED Requirements

### Requirement: Auditoría obligatoria en accesos a pacientes
El sistema SHALL registrar un evento de auditoría con la acción `PATIENT_READ` cada vez que el odontólogo consulte los detalles de la ficha de un paciente, asociando el identificador del usuario y del paciente sin incluir datos personales sensibles en el detalle del evento.

#### Scenario: Lectura de ficha de paciente auditada
- **GIVEN** una consulta exitosa de la ficha de un paciente por el odontólogo autenticado
- **WHEN** se completa la operación
- **THEN** queda registrado un evento en `audit_log` con acción `PATIENT_READ`, `user_id` del profesional y `patient_id` correspondiente
