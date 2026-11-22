# Design: C-10 `patients-management`

## Context

El consultorio atiende pacientes que requieren ser identificados de manera única y consistente (US-050, RN-PA-01).
Los pacientes proveen datos personales (nombre, DNI, teléfono, email, fecha de nacimiento, dirección) alcanzados por la Ley de Protección de Datos Personales (Ley 25.326).
En C-03 se construyó la tabla `audit_log` con una columna `patient_id` que no poseía clave foránea a la espera de que existiera la tabla `patient`.
El paciente puede ingresar al sistema por carga manual del odontólogo (`source = MANUAL`) o por reserva online autogestionada (`source = PUBLIC_BOOKING`).
Para prevenir que un tercero use el DNI de otra persona en una reserva online y modifique su ficha o tome control de sus datos, rige la regla RN-PA-03 (no sobreescritura de datos existentes).

Ver motivación completa en `proposal.md`.

## Goals / Non-Goals

**Goals:**
- Migración Flyway `V7__patient.sql` con tabla `patient`, índices y restricción de unicidad `UNIQUE (practice_id, dni)`.
- Revocar privilegios de `DELETE` sobre `patient` al rol de aplicación (`${app_role}`) para forzar la inmutabilidad relacional y la baja lógica.
- Entidad `Patient` en `pacientes.domain`, repositorios y adaptadores Spring Data JPA en `pacientes.infrastructure`.
- Servicio de dominio `FindOrCreatePatientByDni` que implementa la regla RN-PA-03.
- Endpoints REST `/api/v1/patients` para búsqueda, obtención por ID, creación, actualización y baja lógica.
- Registro mandatorio de auditoría (`PATIENT_READ`) al consultar la ficha de un paciente vía `AuditLogger`.
- Cobertura estricta con TDD (unitarios, ArchUnit e integración con PostgreSQL zonky).

**Non-Goals:**
- Creación de turnos asociados: corresponde a C-11 (`appointments-core`).
- Creación o vinculación de historia clínica: corresponde a C-16 (`clinical-record-core`).
- Interfaz gráfica de administración de pacientes: corresponde a C-21 (`patients-ui`).

## Decisions

### D1: Normalización de DNI y unicidad por consultorio
- **Decisión**: El DNI se normaliza quitando puntos, guiones y espacios en blanco (`btrim(regexp_replace(dni, '[^0-9A-Za-z]', '', 'g'))`), almacenándose en mayúsculas/dígitos limpios. La unicidad es a nivel de instalación: `UNIQUE (practice_id, dni)`.
- **Razón**: Evita duplicaciones accidentales por formato ("12.345.678" vs "12345678").
- **Alternativa descartada**: Permitir DNIs duplicados. Descartado porque el DNI es el identificador natural del paciente en Argentina.

### D2: Baja lógica estricta y revocación de DELETE en base de datos
- **Decisión**: La baja de pacientes se implementa mediante la columna `active boolean NOT NULL DEFAULT true`. El rol de la aplicación tiene revocado el privilegio `DELETE` sobre la tabla `patient` en la migración V7.
- **Razón**: Las leyes 26.529 y 25.326 exigen conservar la trazabilidad y la historia clínica de los pacientes atendidos. Un borrado físico rompería la integridad referencial histórica con turnos, auditoría y ficha clínica.

### D3: Inmutabilidad de datos en reserva pública (`FindOrCreatePatientByDni`, RN-PA-03)
- **Decisión**: Cuando la reserva pública busca al paciente por DNI:
  1. Si no existe, se crea con `source = PUBLIC_BOOKING` y los datos provistos.
  2. Si ya existe, se asocia al turno sin modificar los datos (`first_name`, `last_name`, `phone`, `email`) de la ficha existente del paciente.
- **Razón**: Evita ataques de suplantación o modificación de datos por terceros que conozcan el DNI de un paciente.

### D4: Borde de arquitectura `pacientes → auditoria.api`
- **Decisión**: Se autoriza la dependencia de `pacientes.application` hacia `auditoria.api.AuditLogger` para registrar `PATIENT_READ` y `PATIENT_UPDATE`.
- **Razón**: La lectura de datos de salud y fichas personales debe ser auditada por norma de seguridad y cumplimiento normativo (Ley 25.326, RN-AD-01).

## Risks / Trade-offs

- **[Riesgo] Concurrencia al crear un paciente con el mismo DNI simultáneamente**
  - *Mitigación*: La restricción de unicidad en base de datos `UNIQUE (practice_id, dni)` garantiza que la segunda transacción falle; el servicio captura la violación de unicidad y responde con un 409 amigable o recupera el paciente recién insertado.
- **[Riesgo] Paciente sin DNI o menores de edad (PA-13)**
  - *Mitigación*: Para v1, el DNI es obligatorio y se admite documento de identidad estándar. Casos especiales (recién nacidos sin DNI o extranjeros con pasaporte) usan el campo DNI con el identificador documental disponible.

## Migration Plan

1. Ejecutar migración `V7__patient.sql` con Flyway.
2. Comprobar que `${app_role}` tenga permisos de SELECT, INSERT y UPDATE sobre `patient`, pero NO de DELETE.
3. Rollback: No aplicable en caliente; se maneja con script de compensación o rollback a backup en entornos previos.

## Open Questions

- PA-13 (menores de edad sin DNI): en v1 se modela con el documento provisto por el adulto responsable.
- PA-15 (política de borrado de pacientes que solo reservaron pero nunca asistieron): en v1 no se borran; quedan con `source = PUBLIC_BOOKING`.
