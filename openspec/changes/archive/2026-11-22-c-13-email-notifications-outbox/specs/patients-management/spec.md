# Spec Delta

## ADDED Requirements

### Requirement: Email de contacto del paciente para notificaciones
El módulo de pacientes SHALL ofrecer a otros módulos, por su api, la consulta del email de contacto de un paciente por identificador, sin exponer ningún otro dato personal. Devuelve vacío si el paciente no existe o no tiene email. Es una lectura del sistema para notificar al paciente y MUST NOT registrar `PATIENT_READ` ni loguear el email.

#### Scenario: Paciente con email
- **GIVEN** un paciente con email `ana@ejemplo.com`
- **WHEN** otro módulo consulta su email de contacto
- **THEN** obtiene `ana@ejemplo.com` y ningún otro dato del paciente

#### Scenario: Paciente sin email o inexistente
- **WHEN** otro módulo consulta el email de un paciente sin email registrado, o de un identificador inexistente
- **THEN** la consulta devuelve vacío sin lanzar error

#### Scenario: Paciente dado de baja
- **GIVEN** un paciente inactivo con email
- **WHEN** otro módulo consulta su email de contacto
- **THEN** obtiene el email, porque las notificaciones de sus turnos existentes siguen correspondiendo

#### Scenario: Sin auditoría ni log
- **WHEN** otro módulo consulta el email de contacto de un paciente
- **THEN** no se agrega ninguna fila a `audit_log` y el log no contiene el email
