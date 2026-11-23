# Spec Delta

## ADDED Requirements

### Requirement: Patient lookup or creation for public booking
El módulo `pacientes` SHALL exponer en su capa `api` la búsqueda o creación de un paciente por DNI para consumo de otros módulos. La operación NO SHALL sobreescribir datos existentes, NO SHALL exponer datos personales del paciente ni registrar `PATIENT_READ`.

#### Scenario: Reutilización sin sobreescritura
- **GIVEN** un paciente existente con el mismo DNI en el consultorio
- **WHEN** la reserva pública busca o crea el paciente con otros datos de contacto
- **THEN** se devuelve el paciente existente y sus datos no se modifican

#### Scenario: Creación con origen público
- **GIVEN** un DNI no registrado en el consultorio
- **WHEN** la reserva pública busca o crea el paciente
- **THEN** se crea un paciente con origen `PUBLIC_BOOKING` y se devuelve solo su identificador

#### Scenario: Sin exposición de datos personales
- **GIVEN** una consulta desde `reservas` a las operaciones de pacientes
- **WHEN** se ejecuta
- **THEN** el contrato devuelve solo identificadores, banderas o conteos, y no se agrega ninguna fila de auditoría de lectura de ficha
