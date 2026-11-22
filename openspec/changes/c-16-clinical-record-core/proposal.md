# Proposal: C-16 `clinical-record-core`

## Why

El odontólogo necesita registrar la historia clínica (HC) de sus pacientes empezando por la anamnesis (US-060) y corregir errores sin perder el asiento original (US-063). La Ley 26.529 exige una HC completa, inalterable, con asientos sucesivos fechados y con autor; la Ley 25.326 trata estos datos como sensibles. Este change construye el **núcleo inmutable** de la HC (contenedor `clinical_record`, convención de entradas append-only y mecanismo genérico de correcciones) sobre el que se apoyan C-17 (odontograma) y C-18 (evoluciones).

Governance: **CRÍTICO** (datos de salud, Leyes 25.326 y 26.529; Ley 27.706 sin relevar — PA-01 abierta). Ningún código se escribe sin aprobación explícita de este proposal/design.

## What Changes

- **Migración `V11__clinical_record.sql`** (número reservado por `CHANGES.md`; ver riesgo de colisión en `design.md` D9):
  - Tabla `clinical_record` (contenedor, una por paciente: `UNIQUE (patient_id)`, `opened_at`, `opened_by`).
  - Tabla `anamnesis_entry` (entrada inmutable versionada: `version`, `content` jsonb, `corrects_entry_id`, `correction_reason`, `author_id`, `created_at`).
  - `REVOKE UPDATE, DELETE, TRUNCATE` sobre ambas tablas al rol `${app_role}` (RN-HC-01, RN-GL-01).
  - Constraints de integridad de corrección: la corrección referencia una entrada de la misma HC, exige motivo no vacío y una entrada solo puede corregirse una vez.
- **Núcleo genérico de entradas clínicas (`historiaclinica`)**: superclase de entrada inmutable (autor, `created_at`, `corrects_entry_id`, motivo) y servicio genérico de correcciones reutilizable por C-17/C-18.
- **Endpoints REST (solo `DENTIST`, deny-by-default, CSRF en mutaciones)**:
  - `GET /api/v1/patients/{patientId}/clinical-record` — resumen de la HC (abierta o no, `openedAt`, anamnesis vigente).
  - `GET /api/v1/patients/{patientId}/clinical-record/anamnesis` — versión vigente + historial completo, con marca de corregida (RN-HC-07).
  - `POST /api/v1/patients/{patientId}/clinical-record/anamnesis` — nueva versión de anamnesis; abre la HC en el primer registro (PA-15).
  - `POST /api/v1/patients/{patientId}/clinical-record/anamnesis/{entryId}/corrections` — corrección con motivo obligatorio (patrón `POST .../{tipo}/{entryId}/corrections` que replican C-17/C-18).
  - **No existen** `PUT`/`PATCH`/`DELETE` sobre la HC.
- **Auditoría**: `HC_READ` en cada lectura, `HC_CREATE` en cada alta, `HC_CORRECT` en cada corrección, en la misma transacción y fail-closed (reutiliza `AuditLogger` de C-03). Sin contenido clínico en `details`.
- **Autor y fecha del servidor**: `author_id` sale de la sesión y `created_at` del `Clock` del servidor; el cliente no puede fijarlos.
- **Puerto `pacientes.api.PatientLookup`**: consulta mínima de existencia/estado del paciente para que `historiaclinica` no acceda al dominio de `pacientes`.

### Supuestos registrados por PA-01 (Ley 27.706 — validación legal PENDIENTE)

La Ley 27.706 y su reglamentación **no fueron relevadas** (KB 10 PA-01, KB 11). Este change NO inventa requisitos de esa ley; implementa solo lo derivado de la Ley 26.529/25.326 y asume explícitamente:

1. **S-HC-01 Firma**: la identificación del autor por sesión autenticada (`author_id` + `created_at` del servidor) es suficiente como "firma" del asiento. **No** se implementa firma digital/electrónica certificada. Si la ley la exige, será un change posterior que agregue la firma sobre entradas existentes (el modelo append-only lo permite sin reescribir).
2. **S-HC-02 Inmutabilidad técnica**: `REVOKE UPDATE/DELETE` al rol de aplicación + ausencia de endpoints de edición/borrado. No se implementa sellado criptográfico (hash encadenado, timestamping de terceros).
3. **S-HC-03 Conservación (PA-11, RN-HC-06)**: las entradas se conservan indefinidamente; no hay purga ni supresión. El plazo legal exacto queda pendiente; la supresión de la Ley 25.326 frente a la HC queda sin implementar.
4. **S-HC-04 Registro de accesos**: el `audit_log` de C-03 (usuario, acción, paciente, instante, IP) satisface el registro de accesos requerido.
5. **S-HC-05 Interoperabilidad / formato**: no se implementa exportación ni estándares (HL7/FHIR); el contenido se guarda como JSON propio.
6. **S-HC-06 Alcance**: se asume que la ley aplica al consultorio privado independiente y que el diseño append-only es compatible con ella.
7. **S-HC-07 Apertura de la HC (PA-15)**: la HC se abre con el primer registro clínico; un paciente que solo reservó no tiene `clinical_record`.
8. **S-HC-08 Estructura de la anamnesis (PA-18)**: campos mínimos de texto libre opcionales (`allergies`, `medications`, `medicalConditions`, `habits`, `notes`); a confirmar con el profesional. El cambio de estructura no requiere migración (jsonb) pero sí versionar el formato (`schemaVersion`).

**Acción pendiente**: antes de pasar C-16 a producción (C-28), asesoría legal debe validar S-HC-01..06 y actualizar RN-HC / KB 11. Se registra como bloqueo de despliegue, no de desarrollo.

## Capabilities

### New Capabilities
- `clinical-record`: contenedor de la HC por paciente, convención de entradas clínicas inmutables, mecanismo genérico de correcciones, acceso exclusivo del odontólogo y auditoría de lectura/escritura.
- `clinical-anamnesis`: registro versionado de la anamnesis (US-060), consulta de versión vigente e historial, y su corrección (US-063).

### Modified Capabilities
- `database-migrations`: agrega la migración V11 con `clinical_record` y `anamnesis_entry` y la revocación de UPDATE/DELETE/TRUNCATE al rol de aplicación.
- `audit-log`: exige `HC_READ`/`HC_CREATE`/`HC_CORRECT` en toda lectura/alta/corrección de HC con referencias sin contenido clínico.
- `api-access-policy`: incorpora `/api/v1/patients/{id}/clinical-record/**` como exclusivo de `DENTIST` y la ausencia de métodos de edición/borrado.
- `patients-management` (introducida por C-10, aún sin archivar): agrega el puerto de consulta `PatientLookup` para otros módulos.

## Impact

- **Base de datos**: nueva `V11__clinical_record.sql`. Sin cambios a migraciones aplicadas.
- **Backend**:
  - `historiaclinica.domain`: `ClinicalRecord`, `ClinicalEntry` (`@MappedSuperclass`), `AnamnesisEntry`, `AnamnesisContent`, `CorrectionReason`, repositorios.
  - `historiaclinica.application`: `ClinicalRecordService` (apertura/lectura), `ClinicalCorrections` (genérico), `AnamnesisService`.
  - `historiaclinica.api`: DTOs record con Bean Validation.
  - `historiaclinica.infrastructure`: `ClinicalRecordController`, `AnamnesisController`, repositorios Spring Data.
  - `pacientes.api.PatientLookup` + adaptador en `pacientes.infrastructure`.
- **Seguridad**: sin cambios en `SecurityConfig` (`/api/v1/**` ya exige `DENTIST` vía `DentistAccessManager`); se agregan tests.
- **Tests**: JUnit 5 + PostgreSQL embebido (zonky), Strict TDD: privilegios (UPDATE/DELETE/TRUNCATE fallan con el rol app), esquema, dominio, servicios, endpoints, auditoría, autor/fecha no manipulables, ArchUnit.
- **Desbloquea**: C-17 (`odontogram`), C-18 (`evolutions`), C-22 (`clinical-record-ui`).
- **Riesgo legal**: PA-01 abierta (ver supuestos).
