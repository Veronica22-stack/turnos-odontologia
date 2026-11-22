# Proposal: C-10 `patients-management`

## Why

El consultorio dental requiere registrar, buscar y actualizar las fichas de sus pacientes (US-050, RN-PA-01..04) preservando la integridad de sus datos personales bajo la Ley 25.326 y auditando cada acceso (RN-AD-01). Sin este change no es posible agendar turnos (C-11), gestionar historias clínicas (C-16) ni vincular de forma segura a quienes reservan online mediante DNI sin permitir que terceros malintencionados sobreescriban fichas existentes (RN-PA-03).

Governance: **ALTO** (manejo de datos personales protegidos por la Ley 25.326 y registro de auditoría).

## What Changes

- **Migración V7 (`V7__patient.sql`)**:
  - Creación de tabla `patient`:
    - `id` UUID PK
    - `practice_id` UUID NOT NULL FK a `practice`
    - `first_name` text NOT NULL
    - `last_name` text NOT NULL
    - `dni` text NOT NULL (normalizado, solo dígitos o caracteres válidos de documento)
    - `phone` text NOT NULL
    - `email` text NOT NULL
    - `birth_date` date NULL
    - `address` text NULL
    - `source` text NOT NULL CHECK (`source IN ('MANUAL', 'PUBLIC_BOOKING')`)
    - `active` boolean NOT NULL DEFAULT true (baja lógica, nunca DELETE físico, RN-PA-04)
    - `created_at` timestamptz NOT NULL DEFAULT now()
    - `updated_at` timestamptz NOT NULL DEFAULT now()
    - `CONSTRAINT patient_practice_dni_key UNIQUE (practice_id, dni)`
  - Índices para optimizar búsquedas por nombre/apellido, DNI y teléfono.
  - Privilegios sobre `patient`: `REVOKE DELETE ON patient FROM ${app_role}` para forzar baja lógica y garantizar la inmutabilidad de la relación con historias clínicas.
- **Servicio de dominio `FindOrCreatePatientByDni` (RN-PA-03)**:
  - Resuelve pacientes a partir del DNI para la reserva pública y la carga manual.
  - Si el DNI no existe, crea el paciente con `source = PUBLIC_BOOKING` (o `MANUAL`).
  - Si el DNI ya existe, retorna el paciente existente **sin sobreescribir** sus datos de contacto ya registrados por el profesional; si los datos informados en la reserva difieren, se marcan para revisión sin pisar la ficha.
- **Endpoints REST protegidos `/api/v1/patients` (rol `DENTIST`)**:
  - `GET /api/v1/patients?q={termino}`: busca pacientes activos por coincidencia en nombre, apellido, DNI o teléfono.
  - `GET /api/v1/patients/{id}`: obtiene la ficha completa del paciente. Emite evento de auditoría `PATIENT_READ` obligatorio (RN-AD-01, C-03).
  - `POST /api/v1/patients`: crea manualmente un paciente con validación de DNI único por consultorio. Emite evento de auditoría.
  - `PUT /api/v1/patients/{id}`: actualiza los datos del paciente (nombre, teléfono, email, fecha de nacimiento, dirección). DNI no mutable o validado contra colisiones. Emite evento `PATIENT_UPDATE`.
  - `DELETE /api/v1/patients/{id}`: ejecuta baja lógica (`active = false`, nunca DELETE físico en BD).
- **Auditoría e integración**:
  - Toda lectura de ficha de paciente registra un `AuditEvent` con acción `PATIENT_READ` vía `AuditLogger` (C-03), capturando `userId`, `patientId`, IP y metadatos operacionales, sin datos sensibles en claro.
  - Borde arquitectónico `pacientes → auditoria.api` documentado y verificado en ArchUnit.

## Capabilities

### New Capabilities
- `patients-management`: administración de fichas de pacientes (alta, consulta, búsqueda, edición, baja lógica) y resolución de pacientes por DNI con protección contra sobreescritura no autorizada.

### Modified Capabilities
- `database-migrations`: documenta la migración V7 para la creación de la tabla `patient`, sus índices, unicidad y revocación de borrado físico.
- `audit-log`: especifica la emisión obligatoria de la acción `PATIENT_READ` ante cada acceso a la ficha del paciente.
- `api-access-policy`: incorpora los endpoints de `/api/v1/patients/**` bajo la política deny-by-default (requiere rol `DENTIST` y CSRF en mutaciones).
- `module-boundaries`: autoriza la dependencia de `pacientes` sobre `auditoria.api`.

## Impact

- **Base de datos**: nueva migración `V7__patient.sql`; tabla `patient` con unicidad `(practice_id, dni)` y sin privilegios de DELETE para `${app_role}`.
- **Backend**:
  - Paquete `ar.turnosodontologia.consultorio.pacientes`:
    - `api`: DTOs de paciente (`PatientResponse`, `CreatePatientRequest`, `UpdatePatientRequest`, `PatientSummaryResponse`), puerto o servicio expuesto.
    - `application`: casos de uso `SearchPatients`, `GetPatientById`, `CreatePatient`, `UpdatePatient`, `DeactivatePatient`, `FindOrCreatePatientByDni`.
    - `domain`: entidad `Patient`, repositorio `PatientRepository`, `PatientSource`.
    - `infrastructure`: controlador `PatientsController`, repositorio Spring Data JPA `SpringDataPatientRepository`.
  - Paquete `ar.turnosodontologia.consultorio.architecture`: actualización de `ModuleBoundaries` para autorizar `pacientes → auditoria.api`.
- **Tests**: unitarios de dominio y casos de uso en Strict TDD, pruebas de ArchUnit, e integración con PostgreSQL zonky (`PatientPersistenceIT`, `PatientsEndpointsIT`, `FindOrCreatePatientByDniIT`).
- **Dependencias posteriores**: desbloquea C-11 (`appointments-core`), C-16 (`clinical-record-core`), C-21 (`patients-ui`).
